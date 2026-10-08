package com.metrolist.music.sori.lyrics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.lyrics.LyricsHelper
import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.delay
import java.util.Collections

/**
 * Upstream saves "not found" the first time lyrics are missing and never looks again, even when
 * the miss came from being offline or a slow network. A song whose saved row says "not found" is
 * looked up once more per app run (after the network is back); only a real hit is saved.
 */
object SoriLyricsRetry {
    private val checked: MutableSet<String> = Collections.synchronizedSet(HashSet())

    /** Call after a fresh online lookup, so the same run does not look the song up twice. */
    fun markChecked(context: Context, id: String) {
        if (isOnline(context)) checked += id
    }

    fun shouldRetry(row: LyricsEntity?, id: String): Boolean =
        row != null && row.id == id && row.lyrics == LyricsEntity.LYRICS_NOT_FOUND && id !in checked

    /** Waits for the network (cancelled with the caller), then looks the song up again. */
    suspend fun retry(context: Context, database: MusicDatabase, helper: LyricsHelper, metadata: MediaMetadata) {
        while (!isOnline(context)) delay(5_000)
        checked += metadata.id
        val fetched = helper.getLyrics(metadata)
        if (fetched.lyrics != LyricsEntity.LYRICS_NOT_FOUND) {
            database.query { upsert(LyricsEntity(metadata.id, fetched.lyrics, fetched.provider)) }
        }
    }

    private fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
