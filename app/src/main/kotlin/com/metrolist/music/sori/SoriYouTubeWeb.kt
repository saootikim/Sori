/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal youtube.com (WEB client) InnerTube access for Sori's fallbacks, used where YouTube
 * Music's own endpoints are Premium-only. Anonymous, no cookies.
 */
internal object SoriYouTubeWeb {
    private const val BASE = "https://www.youtube.com/youtubei/v1/"
    private const val CLIENT_VERSION = "2.20260915.00.00"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

    /** POSTs to `youtubei/v1/[endpoint]` with a WEB client context plus [fields]; returns the body. */
    fun post(
        endpoint: String,
        fields: JsonObjectBuilder.() -> Unit,
    ): String = postTo("$BASE$endpoint?prettyPrint=false", clientName = "WEB", clientVersion = CLIENT_VERSION, fields)

    /** POSTs [fields] with an InnerTube context for [clientName] to any InnerTube [url]; returns the body. */
    fun postTo(
        url: String,
        clientName: String,
        clientVersion: String,
        fields: JsonObjectBuilder.() -> Unit,
    ): String {
        val body =
            buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", clientName)
                        put("clientVersion", clientVersion)
                        put("hl", YouTube.locale.hl)
                        put("gl", YouTube.locale.gl)
                    }
                }
                fields()
            }.toString()
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.outputStream.use { it.write(body.toByteArray()) }
            check(connection.responseCode == 200) { "$url returned HTTP ${connection.responseCode}" }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): JsonElement? = runCatching { json.parseToJsonElement(body) }.getOrNull()
}

/**
 * A song built from a youtube.com music video: cleaned title, "Artist - Song" split and a tidied
 * uploader name as the artist. Auto-generated "Artist - Topic" tracks keep their titles.
 */
internal fun videoSongItem(
    id: String,
    rawTitle: String,
    channel: String?,
    duration: Int?,
): SongItem {
    val cleanTitle = cleanVideoTitle(rawTitle)
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

internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

internal fun JsonObject.path(vararg keys: String): JsonObject? =
    keys.fold(this as JsonObject?) { obj, key -> obj?.get(key) as? JsonObject }

/** Text of a youtube.com text object, either `simpleText` or joined `runs`. */
internal fun JsonObject.text(key: String): String? {
    val obj = this[key] as? JsonObject ?: return null
    obj.string("simpleText")?.let { return it }
    return (obj["runs"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.string("text") }?.joinToString("")?.takeIf { it.isNotEmpty() }
}

internal fun walk(
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
