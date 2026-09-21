/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori

private const val TAG_WORDS =
    "official|music|video|audio|mv|m/v|lyrics?|lyric|visuali[sz]er|4k|hd|hq|1080p|가사|뮤직비디오|공식"

/** A bracket group made only of tag words, e.g. "(Official Music Video)", "[가사/Lyrics]", "[4K]". */
private val bracketTag =
    Regex("""[\[(【]\s*(?:(?:$TAG_WORDS)[\s/|·,&-]*)+\s*[\])】]""", RegexOption.IGNORE_CASE)

/** Trailing tags without brackets, e.g. "Official MV", "M/V", "| Lyric Video". */
private val trailingTag =
    Regex("""(?:\s*[|/-]\s*|\s+)(?:official\s+)?(?:music\s+video|lyric\s+video|mv|m/v|video|audio)\s*$""", RegexOption.IGNORE_CASE)

private val underscoreSeparator = Regex("""\s+_\s+""")
private val repeatedSpaces = Regex("""\s{2,}""")

private val onlyTags = Regex("""^(?:(?:$TAG_WORDS)[\s/|·,&-]*)+$""", RegexOption.IGNORE_CASE)

/** "Blueming (Blueming)": youtube.com translated the original half of a bilingual title. */
private val repeatedTranslation = Regex("""^(.+?)\s*\((\1)\)$""", RegexOption.IGNORE_CASE)

/**
 * Removes music-video noise from a video title for display ("[MV] IU _ Song" → "IU - Song").
 * Meaningful parentheses ("(Live)", translations) are kept; returns [title] unchanged if
 * cleaning would leave nothing.
 */
fun cleanVideoTitle(title: String): String {
    var cleaned = bracketTag.replace(title, " ")
    // Repeat: "Song (Official Video) [4K]" can leave a new trailing tag after the first pass.
    repeat(2) { cleaned = trailingTag.replace(cleaned, "") }
    cleaned =
        cleaned
            .replace(underscoreSeparator, " - ")
            .replace(repeatedSpaces, " ")
            .trim()
            .trim('-', '|', '/', '_', ' ')
            .replace(repeatedTranslation, "$1")
    // Nothing but tag words left (the whole title was e.g. "Official MV"): keep the original.
    return if (cleaned.isBlank() || onlyTags.matches(cleaned)) title else cleaned
}
