package com.metrolist.music.sori

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoriMusicVideoTest {
    private val search = javaClass.getResource("/sori/web_search_music_video.json")!!.readText()

    @Test
    fun parsesVideoResults() {
        val videos = parseVideoSearch(search)

        assertEquals(10, videos.size)
        val mv = videos.first()
        assertEquals("BzYnNdJhZQw", mv.id)
        assertEquals("[MV] IU(아이유) _ Through the Night(밤편지)", mv.title)
        assertEquals("1theK (원더케이)", mv.channel)
        assertEquals(283, mv.duration)
    }

    @Test
    fun picksOfficialVideoOverLyricsKaraokeAndLive() {
        val videos = parseVideoSearch(search)

        assertEquals("BzYnNdJhZQw", pickMusicVideo(videos, title = "밤편지", artist = "아이유", duration = 254))
        // English metadata matches the same video.
        assertEquals("BzYnNdJhZQw", pickMusicVideo(videos, title = "Through the Night", artist = "IU", duration = 254))
        // Without the official video only lyric, karaoke and live uploads remain: no video.
        assertNull(pickMusicVideo(videos.drop(1), title = "밤편지", artist = "아이유", duration = 254))
    }

    private fun video(
        title: String,
        channel: String = "Label",
        duration: Int = 200,
    ) = VideoCandidate(id = title, title = title, channel = channel, duration = duration)

    @Test
    fun requiresArtistAndSong() {
        val other = listOf(video("[MV] Someone Else _ 밤편지"))
        assertNull(pickMusicVideo(other, title = "밤편지", artist = "아이유", duration = 200))

        val byChannel = listOf(video("Song A (Official Video)", channel = "ArtistVEVO"))
        assertEquals("Song A (Official Video)", pickMusicVideo(byChannel, title = "Song A", artist = "Artist", duration = 200))
    }

    @Test
    fun rejectedWordsInTheSongTitleAreAllowed() {
        val videos = listOf(video("Band - Alive (Live) [Official Video]"))
        assertEquals(videos.single().id, pickMusicVideo(videos, title = "Alive (Live)", artist = "Band", duration = 200))
        assertNull(pickMusicVideo(videos, title = "Alive", artist = "Band", duration = 200))
    }

    @Test
    fun skipsClipsMuchShorterThanTheSong() {
        val videos = listOf(video("Artist - Song (Official MV)", duration = 30))
        assertNull(pickMusicVideo(videos, title = "Song", artist = "Artist", duration = 200))
    }
}
