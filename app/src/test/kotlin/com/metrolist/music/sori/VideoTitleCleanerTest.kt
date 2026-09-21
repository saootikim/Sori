package com.metrolist.music.sori

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoTitleCleanerTest {
    private fun check(
        raw: String,
        expected: String,
    ) = assertEquals(raw, expected, cleanVideoTitle(raw))

    @Test
    fun removesMusicVideoTags() {
        check("[MV] IU(아이유) _ Through the Night(밤편지)", "IU(아이유) - Through the Night(밤편지)")
        check("BLACKPINK - ‘뛰어(JUMP)’ M/V", "BLACKPINK - ‘뛰어(JUMP)’")
        check("ILLIT (아일릿) ‘It’s Me’ Official MV", "ILLIT (아일릿) ‘It’s Me’")
        check("Song Title (Official Music Video)", "Song Title")
        check("Song Title [Official Audio]", "Song Title")
        check("Artist - Song (Official Video) [4K]", "Artist - Song")
        check("[M/V] 아이유 - 좋은 날", "아이유 - 좋은 날")
    }

    @Test
    fun removesLyricsTags() {
        check("밤편지 [가사/Lyrics]", "밤편지")
        check("Song Title (Lyrics)", "Song Title")
        check("Song Title | Lyric Video", "Song Title")
    }

    @Test
    fun keepsMeaningfulParentheses() {
        check("밤편지 (Through the Night)", "밤편지 (Through the Night)")
        check("Love wins all (Live)", "Love wins all (Live)")
        check("Supernova", "Supernova")
    }

    @Test
    fun neverReturnsBlank() {
        check("[MV]", "[MV]")
        check("Official MV", "Official MV")
    }
}
