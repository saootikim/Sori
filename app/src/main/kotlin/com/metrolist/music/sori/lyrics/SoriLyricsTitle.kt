package com.metrolist.music.sori.lyrics

/**
 * Title as lyrics sites list it. Upstream only drops (...) and [...]; this also drops full-width
 * brackets used on Korean and Japanese releases, featured artists, and "- Remastered 2011"-style
 * version suffixes, which otherwise make LrcLib and KuGou miss the song.
 */
object SoriLyricsTitle {
    private val BRACKETS = Regex("""\s*[(\[（【「『〈《][^)\]）】」』〉》]*[)\]）】」』〉》]""")
    private val FEATURING = Regex("""\s+(?:feat\.?|ft\.|featuring)\s+.*$""", RegexOption.IGNORE_CASE)
    private val VERSION_SUFFIX = Regex(
        """\s+[-–—]\s+[^-–—]*\b(?:remaster(?:ed)?|live|version|ver\.?|edit|mix|remix|mono|stereo|acoustic|instrumental|demo|from\b)[^-–—]*$""",
        RegexOption.IGNORE_CASE,
    )

    fun clean(title: String): String {
        val cleaned = title
            .replace(BRACKETS, "")
            .replace(VERSION_SUFFIX, "")
            .replace(FEATURING, "")
            .trim()
        return cleaned.ifEmpty { title.trim() }
    }
}
