/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori

import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put
import timber.log.Timber

data class WebPlaylistPage(
    val songs: List<SongItem>,
    val continuation: String?,
    /** Only present on the first page. */
    val title: String? = null,
    val thumbnail: String? = null,
)

data class WebPlaylist(
    val title: String?,
    val thumbnail: String?,
    val songs: List<SongItem>,
)

/**
 * Fallback loader for playlist contents via youtube.com (WEB client).
 *
 * Where YouTube Music is Premium-only (Korea for free users, 2026-09), its playlist page only
 * returns the first song, while youtube.com still returns the whole playlist. Items are music
 * videos, so titles and artists are cleaned up (see [videoSongItem]).
 */
object SoriWebPlaylist {
    private const val DEFAULT_MAX_PAGES = 5

    /** Title, cover and up to [maxPages] pages of songs, or null if youtube.com gave nothing usable. */
    suspend fun load(
        playlistId: String,
        maxPages: Int = DEFAULT_MAX_PAGES,
    ): WebPlaylist? =
        withContext(Dispatchers.IO) {
            runCatching {
                val first = parseWebPlaylistPage(SoriYouTubeWeb.post("browse") { put("browseId", "VL$playlistId") })
                val songs = first.songs.toMutableList()
                var page = first
                repeat(maxPages - 1) {
                    val token = page.continuation ?: return@repeat
                    page = parseWebPlaylistPage(SoriYouTubeWeb.post("browse") { put("continuation", token) })
                    songs += page.songs
                }
                songs
                    .distinctBy { it.id }
                    .takeIf { it.isNotEmpty() }
                    ?.let { WebPlaylist(title = first.title, thumbnail = first.thumbnail, songs = it) }
            }.onFailure { Timber.w(it, "Sori web playlist fallback failed for %s", playlistId) }
                .getOrNull()
        }
}

/** Parses one youtube.com playlist browse (or continuation) response. */
fun parseWebPlaylistPage(json: String): WebPlaylistPage {
    val root = SoriYouTubeWeb.parse(json) ?: return WebPlaylistPage(emptyList(), null)
    val songs = mutableListOf<SongItem>()
    var continuation: String? = null
    walk(root) { obj ->
        (obj["lockupViewModel"] as? JsonObject)?.let { lockup -> lockup.toSongItem()?.let(songs::add) }
        if (continuation == null) {
            continuation = (obj["continuationCommand"] as? JsonObject)?.string("token")?.takeIf { it.isNotBlank() }
        }
    }
    val rootObject = root as? JsonObject
    val title = rootObject?.path("metadata", "playlistMetadataRenderer")?.string("title")
    val thumbnail =
        (rootObject?.path("microformat", "microformatDataRenderer", "thumbnail")?.get("thumbnails") as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.string("url") }
            ?.lastOrNull()
    return WebPlaylistPage(songs, continuation, title, thumbnail)
}

/** "3:20" → 200, "1:02:03" → 3723, anything else → null. */
fun parseDuration(text: String): Int? {
    val parts = text.split(":").map { it.toIntOrNull() ?: return null }
    if (parts.isEmpty() || parts.size > 3) return null
    return parts.fold(0) { total, part -> total * 60 + part }
}

private fun JsonObject.toSongItem(): SongItem? {
    if (string("contentType") != "LOCKUP_CONTENT_TYPE_VIDEO") return null
    val id = string("contentId") ?: return null
    val metadata = path("metadata", "lockupMetadataViewModel") ?: return null
    val title = metadata.path("title")?.string("content") ?: return null
    var channel: String? = null
    walk(metadata.path("metadata") ?: JsonObject(emptyMap())) { obj ->
        if (channel == null) channel = (obj["text"] as? JsonObject)?.string("content")
    }
    var duration: Int? = null
    walk(this["contentImage"] ?: JsonObject(emptyMap())) { obj ->
        if (duration == null) {
            duration = (obj["thumbnailBadgeViewModel"] as? JsonObject)?.string("text")?.let(::parseDuration)
        }
    }
    return videoSongItem(id = id, rawTitle = title, channel = channel, duration = duration)
}
