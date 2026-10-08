package com.metrolist.music.sori.lyrics

import android.content.Context
import com.metrolist.music.lyrics.LyricsProvider
import com.metrolist.music.lyrics.LyricsUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Picks the best lyrics across providers instead of taking whatever answers first.
 *
 * Upstream asked providers one by one and kept the first hit, so a plain-text result or the
 * lyrics of a live or extended cut (which drift out of sync) won over good synced lyrics further
 * down the list. Here every enabled provider is asked at once and results are ranked:
 * synced lyrics that fit the song's length first (word-synced before line-synced), then plain
 * text, then synced lyrics that run past the end of the song. Ties go to the user's provider order.
 */
object SoriLyricsPicker {
    private const val TAG = "SoriLyrics"
    private const val PER_PROVIDER_TIMEOUT_MS = 8_000L

    /** After a line-synced fit turns up, how long to keep waiting for a word-synced one. */
    private const val WORD_SYNC_GRACE_MS = 1_500L

    /** Synced lyrics whose last line starts this long after the song ends belong to another cut. */
    private const val DURATION_SLACK_MS = 15_000L

    /** Replaces upstream's one-by-one lookup (kept below the hook in LyricsHelper for merges). */
    val ENABLED = true

    data class Pick(val lyrics: String, val provider: String)

    suspend fun pick(
        context: Context,
        providers: List<LyricsProvider>,
        id: String,
        title: String,
        artist: String,
        durationSec: Int,
        album: String?,
    ): Pick? = coroutineScope {
        val jobs: List<Pair<LyricsProvider, Deferred<String?>>> = providers.map { provider ->
            provider to async {
                try {
                    withTimeoutOrNull(PER_PROVIDER_TIMEOUT_MS) {
                        provider.getLyrics(context, id, title, artist, durationSec, album).getOrNull()
                    }?.takeIf { it.isNotBlank() }?.let(LyricsUtils::filterLyricsCreditLines)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag(TAG).w("${provider.name} threw: ${e.message}")
                    null
                }
            }
        }

        var best: Pair<Int, Pick>? = null
        var graceStart: Long? = null
        for ((provider, job) in jobs) {
            val lyrics = if (graceStart == null) {
                job.await()
            } else {
                val left = WORD_SYNC_GRACE_MS - (System.currentTimeMillis() - graceStart)
                if (left <= 0) break
                withTimeoutOrNull(left) { job.await() } ?: continue
            }
            if (lyrics == null) continue
            val rank = rank(lyrics, durationSec * 1000L)
            Timber.tag(TAG).d("${provider.name}: rank $rank")
            if (best == null || rank > best.first) best = rank to Pick(lyrics, provider.name)
            if (rank == RANK_WORD_FIT) break
            if (rank == RANK_LINE_FIT && graceStart == null) graceStart = System.currentTimeMillis()
        }
        jobs.forEach { it.second.cancel() }
        best?.second
    }

    internal const val RANK_SYNCED_TOO_LONG = 1
    internal const val RANK_PLAIN = 2
    internal const val RANK_LINE_FIT = 3
    internal const val RANK_WORD_FIT = 4

    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val WORD_TAG = Regex("""<\d{1,3}:\d{1,2}[.:]\d{1,3}>""")
    private val WORD_LINE = Regex("""(?m)^\s*<[^<>]+:\d+(\.\d+)?:\d+(\.\d+)?(\|[^<>]+)*>\s*$""")

    internal fun rank(lyrics: String, durationMs: Long): Int {
        val starts = TIME_TAG.findAll(lyrics).map { m ->
            val (min, sec, frac) = m.destructured
            val fracMs = when (frac.length) {
                0 -> 0L
                1 -> frac.toLong() * 100
                2 -> frac.toLong() * 10
                else -> frac.toLong()
            }
            min.toLong() * 60_000 + sec.toLong() * 1_000 + fracMs
        }.toList()
        if (starts.size < 3) return RANK_PLAIN
        if (durationMs > 0 && starts.max() > durationMs + DURATION_SLACK_MS) return RANK_SYNCED_TOO_LONG
        val wordSynced = WORD_TAG.containsMatchIn(lyrics) || WORD_LINE.containsMatchIn(lyrics)
        return if (wordSynced) RANK_WORD_FIT else RANK_LINE_FIT
    }
}
