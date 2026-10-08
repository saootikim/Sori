package com.metrolist.music.sori.lyrics

import com.metrolist.music.lyrics.LyricsEntry
import com.metrolist.music.lyrics.WordTimestamp

/**
 * Cleans up synced lyrics before the upstream parser sees them, and fills in word timings for
 * line-synced lyrics so they highlight word by word.
 *
 * The upstream parser only understands `[mm:ss.xx]`. Providers also send `[m:ss.xx]`, `[mm:ss]`,
 * `[mm:ss:xx]` and `[mm:ss.x]`; such lyrics used to parse to nothing and showed as a blank screen.
 * It also dropped the `[offset:]` tag instead of applying it, so those lyrics ran early or late.
 */
object SoriLrc {
    // A time tag in [] (line) or <> (word): minutes, seconds, optional 1-3 digit fraction after . or :
    private val TIME_TAG = Regex("""([\[<])(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?([\]>])""")
    private val OFFSET_TAG = Regex("""^\s*\[offset:\s*([+-]?\d+)\s*]\s*$""", RegexOption.IGNORE_CASE)
    // What prepare() writes; 3-digit minutes count as done so prepare() never runs twice.
    private val STANDARD_TAG = Regex("""^\d{2,3}:\d{2}\.\d{2,3}$""")

    /** True when [prepare] would change [lyrics]. */
    fun needsPrepare(lyrics: String): Boolean =
        lyrics.lineSequence().any { OFFSET_TAG.matches(it) } ||
            TIME_TAG.findAll(lyrics).any { !STANDARD_TAG.matches(it.value.substring(1, it.value.length - 1)) }

    /**
     * Rewrites every time tag as `[mm:ss.xxx]` and applies the `[offset:]` tag (positive values show
     * lyrics earlier, as in the LRC format), then removes it.
     */
    fun prepare(lyrics: String): String {
        val offsetMs = lyrics.lineSequence().firstNotNullOfOrNull { OFFSET_TAG.matchEntire(it) }
            ?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        return lyrics.lineSequence()
            .filterNot { OFFSET_TAG.matches(it) }
            .joinToString("\n") { line ->
                TIME_TAG.replace(line) { m ->
                    val (open, min, sec, frac, close) = m.destructured
                    val fracMs = when (frac.length) {
                        0 -> 0L
                        1 -> frac.toLong() * 100
                        2 -> frac.toLong() * 10
                        else -> frac.toLong()
                    }
                    val ms = min.toLong() * 60_000 + sec.toLong() * 1_000 + fracMs - offsetMs
                    open + format(ms.coerceAtLeast(0)) + close
                }
            }
    }

    private fun format(ms: Long): String {
        val minutes = ms / 60_000
        val seconds = ms / 1_000 % 60
        return "%02d:%02d.%03d".format(minutes, seconds, ms % 1_000)
    }

    /**
     * Gives each line-synced line word timings spread over the time it is sung, so word-by-word
     * highlighting works without word-synced lyrics. Lines that already have words, background
     * lines and blank lines are left alone.
     */
    fun fillWords(lines: List<LyricsEntry>): List<LyricsEntry> =
        lines.mapIndexed { index, line ->
            if (line.words != null || line.isBackground || line.text.isBlank()) return@mapIndexed line
            val nextTime = lines.drop(index + 1).firstOrNull { it.time > line.time && !it.isBackground }?.time
            val words = estimateWords(line.text, line.time, nextTime) ?: return@mapIndexed line
            line.copy(words = words)
        }

    // Rough singing pace: a syllable (CJK character, Hangul block, or ~3 Latin letters) per 0.28 s.
    private const val SECONDS_PER_UNIT = 0.28
    private const val MIN_LINE_SECONDS = 0.8

    internal fun estimateWords(text: String, startMs: Long, nextStartMs: Long?): List<WordTimestamp>? {
        val pieces = splitPieces(text.trim())
        if (pieces.isEmpty()) return null
        val units = pieces.map { units(it.first) }
        val totalUnits = units.sum()
        val estimate = maxOf(totalUnits * SECONDS_PER_UNIT, MIN_LINE_SECONDS)
        val start = startMs / 1000.0
        val gap = nextStartMs?.let { (it - startMs) / 1000.0 }
        val span = if (gap != null && gap > 0) minOf(gap, estimate) else estimate
        var cursor = start
        return pieces.mapIndexed { i, (piece, trailingSpace) ->
            val length = span * units[i] / totalUnits
            WordTimestamp(piece, cursor, cursor + length, trailingSpace).also { cursor += length }
        }
    }

    /** Space-separated words; runs of CJK characters (no spaces between words) split per character. */
    private fun splitPieces(text: String): List<Pair<String, Boolean>> {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        return words.flatMapIndexed { wi, word ->
            val trailing = wi < words.lastIndex
            if (word.any(::isCjkNoSpace) && word.length > 1) {
                word.toGraphemes().let { chars ->
                    chars.mapIndexed { ci, c -> c to (trailing && ci == chars.lastIndex) }
                }
            } else {
                listOf(word to trailing)
            }
        }
    }

    private fun units(piece: String): Double {
        if (piece.any { isCjkNoSpace(it) || it in '가'..'힣' }) {
            return piece.count { isCjkNoSpace(it) || it in '가'..'힣' }.toDouble()
        }
        val letters = piece.count { it.isLetterOrDigit() }
        return maxOf(1.0, letters / 3.0)
    }

    // Japanese kana and Han characters, written without spaces between words.
    private fun isCjkNoSpace(c: Char) =
        c in '぀'..'ヿ' || c in '一'..'鿿' || c in '㐀'..'䶿'

    private fun String.toGraphemes(): List<String> {
        val it = java.text.BreakIterator.getCharacterInstance()
        it.setText(this)
        val out = mutableListOf<String>()
        var start = it.first()
        var end = it.next()
        while (end != java.text.BreakIterator.DONE) {
            out += substring(start, end)
            start = end
            end = it.next()
        }
        return out
    }
}
