/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.music.R
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.utils.dataStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Whether Sori posts a notification when watched artists release something. On by default. */
val SoriReleaseNotifyKey = booleanPreferencesKey("soriReleaseNotify")

/**
 * New releases from the artists the user follows or plays most, found on their YouTube Music
 * artist pages (which stay available to free users in Korea). Scanned at most once a day, from
 * Home or from the daily background job; the state lives in a file.
 */
@Singleton
class SoriReleaseRadarRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
    ) {
        private val file get() = File(context.filesDir, "sori/release_radar.json")
        private val json = Json { ignoreUnknownKeys = true }
        private val lock = Mutex()
        private var state: ReleaseRadarState? = null

        private val _radar = MutableStateFlow<List<SoriRelease>?>(null)
        val radar: StateFlow<List<SoriRelease>?> = _radar

        /** Scans if today's scan has not run yet; returns releases not notified about yet. */
        suspend fun refresh(): List<SoriRelease> =
            withContext(Dispatchers.IO) {
                lock.withLock {
                    val today = LocalDate.now()
                    var current = state ?: read() ?: ReleaseRadarState()
                    _radar.value = current.radar(today)
                    if (current.lastScanDay != today.toEpochDay()) {
                        val artists = runCatching { watchedArtists() }.getOrDefault(emptyList())
                        if (artists.isNotEmpty()) {
                            current = scanReleases(current, artists, ::releasesOf, today)
                            write(current)
                        }
                    }
                    state = current
                    _radar.value = current.radar(today)
                    current.newSince(current.notifiedDay)
                }
            }

        suspend fun markNotified() =
            withContext(Dispatchers.IO) {
                lock.withLock {
                    state = state?.copy(notifiedDay = LocalDate.now().toEpochDay())?.also(::write)
                }
            }

        /** Most played artists of the last 90 days first, then followed ones; YouTube artists only. */
        private suspend fun watchedArtists(): List<ReleaseArtist> {
            val now = LocalDateTime.now()
            val top =
                database
                    .mostPlayedArtists(now.minusDays(90), limit = 20, toTimeStamp = now)
                    .first()
                    .filter { it.artist.isYouTubeArtist }
                    .sortedByDescending { it.timeListened ?: 0 }
                    .map { ReleaseArtist(it.artist.id, it.artist.name) }
            val followed =
                database
                    .bookmarkedArtistEntitiesByNameAsc()
                    .filter { it.isYouTubeArtist }
                    .map { ReleaseArtist(it.id, it.name) }
            return (top + followed).distinctBy { it.id }.take(MAX_ARTISTS)
        }

        private suspend fun releasesOf(artist: ReleaseArtist): List<StoredRelease>? =
            YouTube
                .artist(artist.id)
                .onFailure { Timber.w(it, "Sori release radar: artist %s failed", artist.id) }
                .getOrNull()
                ?.sections
                ?.flatMap { it.items }
                ?.filterIsInstance<AlbumItem>()
                ?.map {
                    StoredRelease(
                        id = it.browseId,
                        playlistId = it.playlistId,
                        title = it.title,
                        thumbnail = it.thumbnail,
                        year = it.year,
                        artistId = artist.id,
                        artistName = artist.name,
                    )
                }

        private fun read(): ReleaseRadarState? =
            runCatching {
                file.takeIf { it.exists() }?.readText()?.let { json.decodeFromString(ReleaseRadarState.serializer(), it) }
            }.getOrNull()

        private fun write(state: ReleaseRadarState) {
            runCatching {
                file.parentFile?.mkdirs()
                file.writeText(json.encodeToString(ReleaseRadarState.serializer(), state))
            }.onFailure { Timber.w(it, "Sori release radar not saved") }
        }

        private companion object {
            const val MAX_ARTISTS = 20
        }
    }

@HiltViewModel
class SoriReleaseRadarViewModel
    @Inject
    constructor(
        private val repository: SoriReleaseRadarRepository,
    ) : ViewModel() {
        val radar: StateFlow<List<SoriRelease>?> = repository.radar

        init {
            viewModelScope.launch { repository.refresh() }
        }
    }

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SoriReleaseRadarEntryPoint {
    fun releaseRadar(): SoriReleaseRadarRepository
}

/** The daily check: scans the radar and posts one notification for anything new. */
class SoriReleaseWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val radar = EntryPointAccessors.fromApplication(applicationContext, SoriReleaseRadarEntryPoint::class.java).releaseRadar()
        val fresh = runCatching { radar.refresh() }.getOrElse { return Result.retry() }
        if (fresh.isNotEmpty() && SoriReleaseWork.notify(applicationContext, fresh)) radar.markNotified()
        return Result.success()
    }
}

object SoriReleaseWork {
    private const val WORK_NAME = "sori_release_radar"
    private const val CHANNEL_ID = "sori_releases"
    private const val NOTIFICATION_ID = 2601

    /** Schedules or cancels the daily check to match the user's setting. */
    suspend fun sync(context: Context) {
        val enabled = context.dataStore.data.first()[SoriReleaseNotifyKey] ?: true
        val work = WorkManager.getInstance(context)
        if (!enabled) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        // No initial delay: the repository scans at most once a day, whether Home or this job asks.
        val request =
            PeriodicWorkRequestBuilder<SoriReleaseWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    internal fun notify(
        context: Context,
        releases: List<SoriRelease>,
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.sori_releases_title), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val first = releases.first().release
        val text =
            if (releases.size == 1) {
                context.getString(R.string.sori_releases_notification_one, first.artistName, first.title)
            } else {
                context.getString(R.string.sori_releases_notification_more, first.artistName, first.title, releases.size - 1)
            }
        val open =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                PendingIntent.getActivity(context, NOTIFICATION_ID, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.small_icon)
                .setContentTitle(context.getString(R.string.sori_releases_title))
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        return runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }.isSuccess
    }
}
