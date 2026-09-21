/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori

private val channelSuffix =
    Regex("""(?:\s*-\s*Topic|\s*VEVO|\s+official|\s*[\[(][^\])]*official[^\])]*[\])])$""", RegexOption.IGNORE_CASE)

/** "TAEYEON Official" → "TAEYEON", "IU - Topic" → "IU", "BLACKPINKVEVO" → "BLACKPINK", "이지금 [IU Official]" → "이지금". */
fun cleanChannelName(channel: String): String {
    var name = channel.trim()
    while (true) {
        val next = channelSuffix.replace(name, "").trim()
        if (next == name || next.isBlank()) return name
        name = next
    }
}

fun isTopicChannel(channel: String?): Boolean = channel?.trim()?.endsWith("- Topic", ignoreCase = true) == true

private val quotedTitle = Regex("""^(.+?)\s+[‘'"“](.+)[’'"”]\s*$""")
private val surroundingQuotes = Regex("""^[‘'"“](.+)[’'"”]$""")

/**
 * Splits a (cleaned) music-video title into artist and song: "Artist - Song" or
 * "Artist 'Song'". Returns null when the title has neither shape.
 */
fun splitArtistTitle(title: String): Pair<String, String>? {
    val dash = title.indexOf(" - ")
    if (dash > 0) {
        val artist = title.substring(0, dash).trim()
        val song = title.substring(dash + 3).trim().let { surroundingQuotes.matchEntire(it)?.groupValues?.get(1) ?: it }.trim()
        return if (artist.isNotEmpty() && song.isNotEmpty()) artist to song else null
    }
    val quoted = quotedTitle.matchEntire(title) ?: return null
    val (artist, song) = quoted.destructured
    return if (artist.isNotBlank() && song.isNotBlank()) artist.trim() to song.trim() else null
}
