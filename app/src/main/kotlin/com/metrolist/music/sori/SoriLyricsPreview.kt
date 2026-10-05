/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.metrolist.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.metrolist.music.lyrics.LyricsEntry
import com.metrolist.music.lyrics.LyricsUtils
import com.metrolist.music.sori.ui.darkenForWhiteText

/** One line of the player's lyrics preview card. A gap line is an instrumental break, shown as ♪. */
data class PreviewLine(
    val time: Long,
    val text: String,
    val isGap: Boolean = false,
)

/** Lyrics for the preview card. Synced lines follow playback; unsynced ones are shown from the top. */
data class PreviewLyrics(
    val lines: List<PreviewLine>,
    val synced: Boolean,
)

const val PREVIEW_GAP_SYMBOL = "♪"

// The same checks the full lyrics view makes (LyricsViewModel), so both agree on what is synced
// and where a break is long enough to mark.
private val timestampRegex = Regex("\\[\\d{1,2}:\\d{2}")
private const val GAP_MS = 4000L

/**
 * The lines the preview card shows, or null when there are none. Background vocals and blank lines
 * are left out; with [showGaps], breaks longer than 4 s become a ♪ line, as in the full view.
 */
fun soriPreviewLyrics(
    raw: String?,
    showGaps: Boolean,
): PreviewLyrics? {
    val text = raw?.trim()
    if (text.isNullOrEmpty() || text == LYRICS_NOT_FOUND) return null
    val parsed =
        if (timestampRegex.containsMatchIn(text)) LyricsUtils.parseLyrics(text).filter { !it.isBackground } else emptyList()
    if (parsed.isEmpty()) {
        val lines =
            text
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !timestampRegex.containsMatchIn(it) }
                .map { PreviewLine(0L, it) }
        return lines.takeIf { it.isNotEmpty() }?.let { PreviewLyrics(it, synced = false) }
    }
    val entries = listOf(LyricsEntry.HEAD_LYRICS_ENTRY) + parsed
    val lines = mutableListOf<PreviewLine>()
    entries.forEachIndexed { i, entry ->
        if (entry.text.isNotBlank()) lines += PreviewLine(entry.time, entry.text.trim())
        if (showGaps && i < entries.lastIndex) {
            val end =
                when {
                    !entry.words.isNullOrEmpty() -> (entry.words.last().endTime * 1000).toLong()
                    entry.text.isBlank() -> entry.time
                    else -> null
                }
            if (end != null && entries[i + 1].time - end > GAP_MS) lines += PreviewLine(end, PREVIEW_GAP_SYMBOL, isGap = true)
        }
    }
    return lines.takeIf { all -> all.any { !it.isGap } }?.let { PreviewLyrics(it, synced = true) }
}

/**
 * Index of the line playing at [positionMs], or -1 before the first line. Uses the full view's
 * 100 ms lead (LyricsUtils.findCurrentLineIndex) so both highlight the same line.
 */
fun soriPreviewCurrentIndex(
    lines: List<PreviewLine>,
    positionMs: Long,
): Int {
    val target = positionMs + 100
    var low = 0
    var high = lines.lastIndex
    var found = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (lines[mid].time < target) {
            found = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return found
}

private val FallbackCardColor = Color(0xFF3A3A3A)

// Just light enough to stand out from the black bottom of the player's gradient.
private const val MinCardLuminance = 0.03f

/** The card in the album's main color, dark enough for white text and never lost on black. */
fun soriLyricsCardColor(accent: Color?): Color {
    var color = darkenForWhiteText(accent ?: FallbackCardColor)
    while (color.luminance() < MinCardLuminance) color = lerp(color, Color.White, 0.08f)
    return color
}
