/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori

import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put
import timber.log.Timber

/**
 * youtube.com's auto-generated mix for a video ("RD" + video id), about 25 related songs.
 * Used where YouTube Music's song radio comes back nearly empty (logged-in free accounts in
 * Korea, 2026-09).
 */
object SoriWebRadio {
    suspend fun mix(videoId: String): List<SongItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                parseWebRadio(
                    SoriYouTubeWeb.post("next") {
                        put("videoId", videoId)
                        put("playlistId", "RD$videoId")
                    },
                )
            }.onFailure { Timber.w(it, "Sori web radio failed for %s", videoId) }
                .getOrDefault(emptyList())
        }
}

/** Songs of a youtube.com watch-next mix, in order. */
fun parseWebRadio(json: String): List<SongItem> {
    val root = SoriYouTubeWeb.parse(json) ?: return emptyList()
    val songs = mutableListOf<SongItem>()
    walk(root) { obj ->
        val panel = obj["playlistPanelVideoRenderer"] as? JsonObject ?: return@walk
        val id = panel.string("videoId") ?: return@walk
        val title = panel.text("title") ?: return@walk
        songs +=
            videoSongItem(
                id = id,
                rawTitle = title,
                channel = panel.text("shortBylineText"),
                duration = panel.text("lengthText")?.let(::parseDuration),
            )
    }
    return songs.distinctBy { it.id }
}

/**
 * Replaces a gated song radio with youtube.com mixes. A healthy YouTube Music radio starts with
 * dozens of songs and a continuation; the gated one (free accounts in Korea) returns a handful of
 * unrelated songs (even nursery rhymes) and continues with nothing. In that case the chosen song
 * is kept and the rest comes from youtube.com mixes, each seeded by the last queued song, until
 * a mix brings nothing new.
 */
class SoriRadioExtender(
    private val mix: suspend (String) -> List<SongItem> = SoriWebRadio::mix,
) {
    private val queued = mutableSetOf<String>()
    private var seed: String? = null

    fun hasMore(): Boolean = seed != null

    /** Returns the radio's first page, rebuilt from youtube.com when it looks gated. */
    suspend fun extend(
        items: List<SongItem>,
        seedVideoId: String?,
        hasContinuation: Boolean,
    ): List<SongItem> {
        if ((items.size >= HEALTHY_RADIO && hasContinuation) || seedVideoId == null) return items
        val head = items.take(1)
        queued += head.map { it.id } + seedVideoId
        val extra = mix(seedVideoId).filter { queued.add(it.id) }
        if (extra.isEmpty()) return items
        seed = extra.last().id
        return head + extra
    }

    suspend fun nextPage(): List<SongItem> {
        val current = seed ?: return emptyList()
        val extra = mix(current).filter { queued.add(it.id) }
        seed = extra.lastOrNull()?.id
        return extra
    }

    private companion object {
        const val HEALTHY_RADIO = 10
    }
}
