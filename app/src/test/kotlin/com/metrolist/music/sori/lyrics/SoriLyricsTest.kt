package com.metrolist.music.sori.lyrics

import com.metrolist.music.lyrics.LyricsUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoriLyricsTest {
    @Test
    fun oddTimeTagsParseInsteadOfVanishing() {
        val lyrics = "[0:01.5]one\n[00:03]two\n[00:05:20]three"
        val lines = LyricsUtils.parseLyrics(lyrics)
        assertEquals(listOf(1_500L, 3_000L, 5_200L), lines.map { it.time })
        assertEquals(listOf("one", "two", "three"), lines.map { it.text })
    }

    @Test
    fun offsetTagShiftsLines() {
        // Positive offset: lyrics show earlier.
        val lines = LyricsUtils.parseLyrics("[offset:+500]\n[00:02.00]a\n[00:04.00]b")
        assertEquals(listOf(1_500L, 3_500L), lines.map { it.time })
        val later = LyricsUtils.parseLyrics("[offset:-250]\n[00:02.00]a")
        assertEquals(2_250L, later.single().time)
    }

    @Test
    fun standardLyricsAreLeftAlone() {
        assertFalse(SoriLrc.needsPrepare("[ar:Someone]\n[00:01.23]a\n[01:02.345]b"))
    }

    @Test
    fun lineSyncedLinesGetWordsWithinTheLine() {
        val lines = LyricsUtils.parseLyrics("[00:10.00]hello there world\n[00:12.00]next")
        val words = assertNotNull(lines[0].words).let { lines[0].words!! }
        assertEquals(listOf("hello", "there", "world"), words.map { it.text })
        assertEquals(10.0, words.first().startTime, 0.001)
        assertTrue(words.last().endTime <= 12.0 + 1e-9)
        assertFalse(words.last().hasTrailingSpace)
    }

    @Test
    fun japaneseLinesSplitPerCharacter() {
        val words = SoriLrc.estimateWords("愛してる", 0, 5_000)!!
        assertEquals(listOf("愛", "し", "て", "る"), words.map { it.text })
        assertTrue(words.none { it.hasTrailingSpace })
    }

    @Test
    fun rankPrefersFittingSyncedLyrics() {
        val line = "[00:01.00]a\n[00:02.00]b\n[03:00.00]c"
        assertEquals(SoriLyricsPicker.RANK_LINE_FIT, SoriLyricsPicker.rank(line, 190_000))
        assertEquals(SoriLyricsPicker.RANK_SYNCED_TOO_LONG, SoriLyricsPicker.rank(line, 120_000))
        assertEquals(SoriLyricsPicker.RANK_PLAIN, SoriLyricsPicker.rank("just\ntext", 120_000))
        val word = "[00:01.00]<00:01.00>a <00:01.50>b\n[00:02.00]c\n[00:03.00]d"
        assertEquals(SoriLyricsPicker.RANK_WORD_FIT, SoriLyricsPicker.rank(word, 120_000))
    }

    @Test
    fun titleCleanup() {
        assertEquals("Hype Boy", SoriLyricsTitle.clean("Hype Boy (Official MV)"))
        assertEquals("Ditto", SoriLyricsTitle.clean("Ditto【Live】"))
        assertEquals("Song", SoriLyricsTitle.clean("Song feat. Someone"))
        assertEquals("Let It Be", SoriLyricsTitle.clean("Let It Be - Remastered 2009"))
        assertEquals("Stay With Me", SoriLyricsTitle.clean("Stay With Me"))
        assertEquals("Up - Down", SoriLyricsTitle.clean("Up - Down"))
    }

    @Test
    fun kanaToHangul() {
        assertEquals("아이시테루", SoriKana.toHangul("アイシテル"))
        assertEquals("콘니치와", SoriKana.toHangul("コンニチワ"))
        assertEquals("갓코", SoriKana.toHangul("ガッコー"))
        assertEquals("샤신", SoriKana.toHangul("シャシン"))
        assertEquals("차", SoriKana.toHangul("チャ"))
        assertEquals("화", SoriKana.toHangul("ファ"))
        assertEquals("쓰키", SoriKana.toHangul("つき"))
    }

    @Test
    fun translateResponseParses() {
        val body = """[[["안녕\n","hello\n",null],["세상","world",null]],null,"en"]"""
        assertEquals(listOf("안녕", "세상"), SoriFreeTranslate.parse(body))
    }
}
