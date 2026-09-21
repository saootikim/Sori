/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori

import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.queues.Queue
import timber.log.Timber

/**
 * Playlist queues (play, shuffle, playlist radio from menus, cards and widgets) come back with at
 * most one song where YouTube Music is Premium-only (Korea, free users, 2026-09). Build those
 * queues from the youtube.com playlist instead. Single-song radios still work and are untouched.
 */
object SoriQueueFallback {
    data class Plan(
        val playlistId: String,
        val shuffle: Boolean,
    )

    private const val SHUFFLE_PARAM = "8gECKAE"
    private const val PLAYLIST_RADIO_PREFIX = "RDAMPL"

    fun plan(endpoint: WatchEndpoint): Plan? {
        val playlistId = endpoint.playlistId ?: return null
        return when {
            playlistId.startsWith(PLAYLIST_RADIO_PREFIX) -> Plan(playlistId.removePrefix(PLAYLIST_RADIO_PREFIX), shuffle = true)
            // Other mixes (song radio RDAMVM, artist mixes RDEM/RDAO...) are not browsable playlists.
            playlistId.startsWith("RD") && !playlistId.startsWith("RDCLAK") -> null
            else -> Plan(playlistId, shuffle = endpoint.params?.contains(SHUFFLE_PARAM) == true)
        }
    }

    /** A replacement initial status, or null to keep YouTube Music's answer. */
    suspend fun initialStatus(endpoint: WatchEndpoint): Queue.Status? {
        val plan = plan(endpoint) ?: return null
        val web = SoriWebPlaylist.load(plan.playlistId)?.takeIf { it.songs.size > 1 }
        Timber.d("Sori queue fallback for %s: %d songs", plan.playlistId, web?.songs?.size ?: 0)
        if (web == null) return null
        val songs = if (plan.shuffle) web.songs.shuffled() else web.songs
        val start = endpoint.videoId?.let { id -> songs.indexOfFirst { it.id == id } }?.takeIf { it >= 0 } ?: 0
        return Queue.Status(
            title = web.title,
            items = songs.map { it.toMediaItem() },
            mediaItemIndex = start,
        )
    }
}
