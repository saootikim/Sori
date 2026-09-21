package com.metrolist.music.sori

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailLetterboxTest {
    private val black = 0xFF000000.toInt()
    private val jpegBlack = 0xFF0A0806.toInt()
    private val bright = 0xFFC08040.toInt()
    private val night = 0xFF1E2230.toInt()

    /** A width × height image that is [fill] inside [content] and black outside it. */
    private fun image(
        content: ContentBounds,
        fill: Int = bright,
        bar: Int = black,
    ): (Int, Int) -> Int =
        { x, y -> if (x in content.left until content.right && y in content.top until content.bottom) fill else bar }

    @Test
    fun trimsLetterboxedHqDefault() {
        // hqdefault.jpg: a 16:9 video inside a 480x360 frame, 45px bars top and bottom.
        val content = ContentBounds(0, 45, 480, 315)
        assertEquals(content, letterboxContentBounds(480, 360, image(content, bar = jpegBlack)))
    }

    @Test
    fun trimsPillarboxedVerticalVideo() {
        val content = ContentBounds(139, 0, 341, 360)
        assertEquals(content, letterboxContentBounds(480, 360, image(content)))
    }

    @Test
    fun leavesFullFrameArtAlone() {
        assertNull(letterboxContentBounds(480, 360) { _, _ -> bright })
    }

    @Test
    fun leavesDarkPhotosAlone() {
        // A night shot: dark but not black edges, and a black strip only at the top.
        assertNull(letterboxContentBounds(480, 360) { _, _ -> night })
        assertNull(letterboxContentBounds(480, 360, image(ContentBounds(0, 45, 480, 360))))
    }

    @Test
    fun ignoresBarsTooThinToBeLetterbox() {
        assertNull(letterboxContentBounds(480, 360, image(ContentBounds(0, 3, 480, 357))))
    }

    @Test
    fun matchesOnlyFramedYouTubeThumbnails() {
        assertTrue(isFramedYouTubeThumbnail("https://i.ytimg.com/vi/B76TuNFymjk/hqdefault.jpg"))
        assertTrue(isFramedYouTubeThumbnail("https://i.ytimg.com/vi/B76TuNFymjk/sddefault.jpg?sqp=-oaymwEWCJADEOEC&rs=AMzJL3k"))
        assertTrue(isFramedYouTubeThumbnail("https://i9.ytimg.com/vi_webp/B76TuNFymjk/0.webp"))
        assertFalse(isFramedYouTubeThumbnail("https://i.ytimg.com/vi/B76TuNFymjk/mqdefault.jpg"))
        assertFalse(isFramedYouTubeThumbnail("https://i.ytimg.com/vi/B76TuNFymjk/hq720.jpg"))
        assertFalse(isFramedYouTubeThumbnail("https://lh3.googleusercontent.com/abc=w544-h544"))
    }
}
