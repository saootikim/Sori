package com.metrolist.music.sori

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.metrolist.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.metrolist.music.sori.ui.MaxTileLuminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SoriLyricsPreviewTest {
    private fun texts(lyrics: PreviewLyrics?) = lyrics!!.lines.map { it.text }

    private fun times(lyrics: PreviewLyrics?) = lyrics!!.lines.map { it.time }

    @Test
    fun noLyricsGiveNoCard() {
        assertNull(soriPreviewLyrics(null, showGaps = true))
        assertNull(soriPreviewLyrics("  ", showGaps = true))
        assertNull(soriPreviewLyrics(LYRICS_NOT_FOUND, showGaps = true))
        // Synced lyrics made only of breaks have nothing to show.
        assertNull(soriPreviewLyrics("[00:01.00]\n[00:09.00]", showGaps = true))
    }

    @Test
    fun longIntroIsMarkedShortOneIsNot() {
        val long = soriPreviewLyrics("[00:05.00]First\n[00:08.00]Second", showGaps = true)
        assertTrue(long!!.synced)
        assertEquals(listOf(PREVIEW_GAP_SYMBOL, "First", "Second"), texts(long))
        assertEquals(listOf(0L, 5000L, 8000L), times(long))

        val short = soriPreviewLyrics("[00:03.00]First\n[00:06.00]Second", showGaps = true)
        assertEquals(listOf("First", "Second"), texts(short))
    }

    @Test
    fun blankLinesMarkOnlyBreaksLongerThanFourSeconds() {
        val long = soriPreviewLyrics("[00:01.00]A\n[00:10.00]\n[00:20.00]B", showGaps = true)
        assertEquals(listOf("A", PREVIEW_GAP_SYMBOL, "B"), texts(long))
        assertEquals(listOf(1000L, 10000L, 20000L), times(long))
        assertTrue(long!!.lines[1].isGap)

        val short = soriPreviewLyrics("[00:01.00]A\n[00:02.00]\n[00:05.00]B", showGaps = true)
        assertEquals(listOf("A", "B"), texts(short))

        // Of two blank lines in a row, the break is measured from the last one.
        val consecutive = soriPreviewLyrics("[00:01.00]A\n[00:10.00]\n[00:12.00]\n[00:20.00]B", showGaps = true)
        assertEquals(listOf("A", PREVIEW_GAP_SYMBOL, "B"), texts(consecutive))
        assertEquals(12000L, consecutive!!.lines[1].time)
    }

    @Test
    fun breaksAreNotMarkedWhenTheIndicatorIsOff() {
        val lyrics = soriPreviewLyrics("[00:05.00]First\n[00:10.00]\n[00:20.00]Second", showGaps = false)
        assertEquals(listOf("First", "Second"), texts(lyrics))
    }

    @Test
    fun backgroundVocalsAreLeftOut() {
        val lyrics =
            soriPreviewLyrics(
                "[00:10.00]<00:10.00>Main <00:10.50>line\n[bg: <00:10.20>ooh<00:11.00>]\n[00:12.00]<00:12.00>Next <00:12.40>line",
                showGaps = false,
            )
        assertEquals(2, lyrics!!.lines.size)
        assertFalse(lyrics.lines.any { "ooh" in it.text })
    }

    @Test
    fun unsyncedLyricsKeepTheirTextLines() {
        // A section label in brackets is not a timestamp: still unsynced.
        val lyrics = soriPreviewLyrics("[Intro]\nHello\n\n  World  ", showGaps = true)
        assertFalse(lyrics!!.synced)
        assertEquals(listOf("[Intro]", "Hello", "World"), texts(lyrics))
    }

    @Test
    fun currentLineFollowsTheFullViewsLead() {
        val lines = listOf(PreviewLine(1000, "A"), PreviewLine(2000, "B"), PreviewLine(2000, "C"), PreviewLine(5000, "D"))
        assertEquals(-1, soriPreviewCurrentIndex(lines, 0))
        // A line counts from 100 ms before its time.
        assertEquals(-1, soriPreviewCurrentIndex(lines, 900))
        assertEquals(0, soriPreviewCurrentIndex(lines, 901))
        // Lines with the same time: the last of them.
        assertEquals(2, soriPreviewCurrentIndex(lines, 1950))
        assertEquals(3, soriPreviewCurrentIndex(lines, 60_000))
        assertEquals(-1, soriPreviewCurrentIndex(emptyList(), 1000))
    }

    @Test
    fun cardColorIsDarkEnoughForWhiteTextAndVisibleOnBlack() {
        for (accent in listOf(null, Color.White, Color.Black, Color(0xFF1E88E5), Color(0xFFFFEB3B))) {
            val luminance = soriLyricsCardColor(accent).luminance()
            assertTrue("$accent → $luminance", luminance <= MaxTileLuminance)
            assertTrue("$accent → $luminance", luminance >= 0.03f)
        }
        // The album's hue stays.
        val blue = soriLyricsCardColor(Color(0xFF1E88E5))
        assertTrue(blue.blue > blue.red)
    }
}
