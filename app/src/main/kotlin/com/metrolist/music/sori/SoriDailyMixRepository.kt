/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.models.toMediaMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Today's daily mixes. The last built set is kept in a file so Home shows it at once; a new set
 * is built the first time Home opens on a new day (the app has no background jobs).
 */
@Singleton
class SoriDailyMixRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
    ) {
        private val file get() = File(context.filesDir, "sori/daily_mixes.json")
        private val lock = Mutex()
        private var builtFor: LocalDate? = null

        private val _mixes = MutableStateFlow<List<SoriDailyMix>?>(null)

        /** Null until the stored or built mixes are known; empty when there is too little history. */
        val mixes: StateFlow<List<SoriDailyMix>?> = _mixes

        private val builder =
            SoriDailyMixBuilder(
                topArtists = ::topArtists,
                favorites = { artistId ->
                    database
                        .mostPlayedSongsByArtist(artistId, EPOCH, LocalDateTime.now())
                        .first()
                        .map { it.toMediaMetadata().toYTItem() }
                },
            )

        suspend fun refresh() =
            withContext(Dispatchers.IO) {
                lock.withLock {
                    if (builtFor == null) {
                        runCatching { file.takeIf { it.exists() }?.readText()?.let(::decodeDailyMixes) }
                            .getOrNull()
                            ?.takeIf { (_, mixes) -> mixes.isNotEmpty() }
                            ?.let { (day, mixes) ->
                                builtFor = day
                                _mixes.value = mixes
                            }
                    }
                    val today = LocalDate.now()
                    if (builtFor == today) return@withLock
                    val built =
                        runCatching { builder.build(today) }
                            .onFailure { Timber.w(it, "Sori daily mixes failed") }
                            .getOrNull() ?: return@withLock
                    _mixes.value = built
                    // Too little history today: try again next time Home opens instead of waiting a day.
                    if (built.isEmpty()) return@withLock
                    builtFor = today
                    runCatching {
                        file.parentFile?.mkdirs()
                        file.writeText(encodeDailyMixes(today, built))
                    }.onFailure { Timber.w(it, "Sori daily mixes not saved") }
                }
            }

        /** The artists heard most in the last 90 days, topped up from all-time history. */
        private suspend fun topArtists(): List<MixArtist> {
            val now = LocalDateTime.now()
            val recent = database.mostPlayedArtists(now.minusDays(90), limit = 12, toTimeStamp = now).first().toMixArtists()
            if (recent.size >= 6) return recent
            val allTime = database.mostPlayedArtists(EPOCH, limit = 12, toTimeStamp = now).first().toMixArtists()
            return (recent + allTime).distinctBy { it.id }
        }

        // Not only YouTube Music artists: songs from Sori's youtube.com fallbacks carry artists
        // without a YouTube Music id, and their favorites still seed mixes.
        private fun List<Artist>.toMixArtists() =
            sortedByDescending { it.timeListened ?: 0 }
                .map { MixArtist(id = it.artist.id, name = it.artist.name, thumbnail = it.artist.thumbnailUrl) }

        private companion object {
            val EPOCH: LocalDateTime = LocalDateTime.of(2000, 1, 1, 0, 0)
        }
    }

@HiltViewModel
class SoriDailyMixViewModel
    @Inject
    constructor(
        private val repository: SoriDailyMixRepository,
    ) : ViewModel() {
        val mixes: StateFlow<List<SoriDailyMix>?> = repository.mixes

        init {
            viewModelScope.launch { repository.refresh() }
        }
    }
