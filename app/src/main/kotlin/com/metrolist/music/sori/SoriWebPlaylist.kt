/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

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
 * videos, so titles are video titles and the "artist" is the uploading channel.
 */
object SoriWebPlaylist {
    private const val MAX_PAGES = 5
    private const val ENDPOINT = "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false"
    private const val CLIENT_VERSION = "2.20260915.00.00"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

    /** Title, cover and up to [MAX_PAGES] pages of songs, or null if youtube.com gave nothing usable. */
    suspend fun load(playlistId: String): WebPlaylist? =
        withContext(Dispatchers.IO) {
            runCatching {
                val first = parseWebPlaylistPage(post(browseId = "VL$playlistId", continuation = null))
                val songs = first.songs.toMutableList()
                var page = first
                repeat(MAX_PAGES - 1) {
                    val token = page.continuation ?: return@repeat
                    page = parseWebPlaylistPage(post(browseId = null, continuation = token))
                    songs += page.songs
                }
                songs
                    .distinctBy { it.id }
                    .takeIf { it.isNotEmpty() }
                    ?.let { WebPlaylist(title = first.title, thumbnail = first.thumbnail, songs = it) }
            }.onFailure { Timber.w(it, "Sori web playlist fallback failed for %s", playlistId) }
                .getOrNull()
        }

    private fun post(
        browseId: String?,
        continuation: String?,
    ): String {
        val body =
            buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", "WEB")
                        put("clientVersion", CLIENT_VERSION)
                        put("hl", YouTube.locale.hl)
                        put("gl", YouTube.locale.gl)
                    }
                }
                browseId?.let { put("browseId", it) }
                continuation?.let { put("continuation", it) }
            }.toString()
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.outputStream.use { it.write(body.toByteArray()) }
            check(connection.responseCode == 200) { "youtube.com browse returned HTTP ${connection.responseCode}" }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

private val lenientJson = Json { ignoreUnknownKeys = true }

/** Parses one youtube.com playlist browse (or continuation) response. */
fun parseWebPlaylistPage(json: String): WebPlaylistPage {
    val root = runCatching { lenientJson.parseToJsonElement(json) }.getOrNull() ?: return WebPlaylistPage(emptyList(), null)
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
    // Music videos are titled "Artist - Song" / "Artist 'Song'" and uploaded by a label or an
    // "Artist Official" channel; auto-generated "Artist - Topic" tracks are already clean.
    val cleanTitle = cleanVideoTitle(title)
    val split = if (isTopicChannel(channel)) null else splitArtistTitle(cleanTitle)
    val artist = split?.first ?: channel?.let(::cleanChannelName)
    return SongItem(
        id = id,
        title = split?.second ?: cleanTitle,
        artists = listOfNotNull(artist?.let { Artist(name = it, id = null) }),
        duration = duration,
        thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
    )
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.path(vararg keys: String): JsonObject? =
    keys.fold(this as JsonObject?) { obj, key -> obj?.get(key) as? JsonObject }

private fun walk(
    element: JsonElement,
    visit: (JsonObject) -> Unit,
) {
    when (element) {
        is JsonObject -> {
            visit(element)
            element.values.forEach { walk(it, visit) }
        }
        is JsonArray -> element.forEach { walk(it, visit) }
        else -> Unit
    }
}
