/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/** A youtube.com search result that might be a song's music video. */
data class VideoCandidate(
    val id: String,
    val title: String,
    val channel: String?,
    val duration: Int?,
)

/**
 * Finds the music video for a song, for the player's Song / Video switch. YouTube Music pairs
 * songs with their videos only for some accounts (anonymous and free Korean clients get no
 * counterpart, 2026-10), so the video is looked up on youtube.com instead.
 */
object SoriMusicVideo {
    private val cache = ConcurrentHashMap<String, String>()
    private const val NONE = ""

    /**
     * Returns the video id to show for the playing item, or null when it has none. A playing
     * music video is its own video.
     */
    suspend fun find(
        songId: String,
        title: String,
        artist: String?,
        duration: Int?,
        isVideo: Boolean,
    ): String? {
        if (isVideo) return songId.takeIf { videoIdPattern.matches(it) }
        cache[songId]?.let { return it.takeIf { id -> id != NONE } }
        // Failed lookups are not cached, so they are retried the next time the song plays.
        val found =
            withContext(Dispatchers.IO) {
                runCatching {
                    val query = listOfNotNull(artist, title, "MV").joinToString(" ")
                    pickMusicVideo(parseVideoSearch(SoriYouTubeWeb.post("search") { put("query", query) }), title, artist, duration)
                }
            }.onFailure { Timber.w(it, "Sori music video lookup failed for %s", songId) }
                .getOrElse { return null }
        cache[songId] = found ?: NONE
        return found
    }
}

/** Plain video results of a youtube.com search, in ranking order. */
fun parseVideoSearch(json: String): List<VideoCandidate> {
    val root = SoriYouTubeWeb.parse(json) ?: return emptyList()
    val videos = mutableListOf<VideoCandidate>()
    walk(root) { obj ->
        val video = obj["videoRenderer"] as? JsonObject ?: return@walk
        videos +=
            VideoCandidate(
                // The id goes into the player page's script, so only real video ids are accepted.
                id = video.string("videoId")?.takeIf { videoIdPattern.matches(it) } ?: return@walk,
                title = video.text("title") ?: return@walk,
                channel = video.text("ownerText"),
                duration = video.text("lengthText")?.let(::parseDuration),
            )
    }
    return videos.distinctBy { it.id }
}

private val videoIdPattern = Regex("[A-Za-z0-9_-]{11}")

private val musicVideoMarker =
    Regex("""\bM/?V\b|music\s*video|official\s*video|뮤직\s*비디오|뮤비""", RegexOption.IGNORE_CASE)

private val notTheMusicVideo =
    Regex(
        """\blyrics?\b|가사|karaoke|노래방|\bcover\b|커버|\blive\b|라이브|reaction|리액션|teaser|티저|\bmaking\b|메이킹|""" +
            """\bbehind\b|비하인드|\bpractice\b|안무|fancam|직캠|\bstage\b|무대|sketchbook|스케치북|교차\s*편집|""" +
            """playlist|플레이리스트|\bhours?\b|시간\s*듣기|\bloop\b|sped\s*up|slowed|nightcore|instrumental|""" +
            """\binst\b|\bMR\b|shorts""",
        RegexOption.IGNORE_CASE,
    )

private val bracketed = Regex("""[(\[]([^)\]]+)[)\]]""")

/** Lowercase letters and digits only, so "IU(아이유) _ Through the Night" ≈ "iu아이유throughthenight". */
private fun normalize(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

/** "Through the Night (밤편지)" → ["throughthenight(밤편지)" without brackets, "throughthenight", "밤편지"]. */
private fun nameVariants(name: String): List<String> {
    val inner = bracketed.findAll(name).map { it.groupValues[1] }.toList()
    val outer = bracketed.replace(name, " ")
    return (listOf(name, outer) + inner).map(::normalize).filter { it.length >= 2 }.distinct()
}

/**
 * The search result that is the official music video of [title] by [artist]: it must be marked as
 * a music video, name the song, credit the artist in its title or channel, not be a lyric, live,
 * cover or similar upload (unless the song itself is, like "Alive (Live)"), and not be much
 * shorter than the song.
 */
fun pickMusicVideo(
    candidates: List<VideoCandidate>,
    title: String,
    artist: String?,
    duration: Int?,
): String? {
    val titles = nameVariants(cleanVideoTitle(title))
    val artists = artist?.let(::nameVariants).orEmpty()
    if (titles.isEmpty()) return null
    val songWords = notTheMusicVideo.findAll(title).map { it.value.lowercase() }.toSet()
    return candidates
        .firstOrNull { video ->
            val videoTitle = normalize(video.title)
            val channel = video.channel?.let { normalize(cleanChannelName(it)) }.orEmpty()
            musicVideoMarker.containsMatchIn(video.title) &&
                notTheMusicVideo.findAll(video.title).all { it.value.lowercase() in songWords } &&
                titles.any { videoTitle.contains(it) } &&
                (artists.isEmpty() || artists.any { videoTitle.contains(it) || channel.contains(it) }) &&
                (duration == null || video.duration == null || video.duration >= duration * 7 / 10)
        }?.id
}

