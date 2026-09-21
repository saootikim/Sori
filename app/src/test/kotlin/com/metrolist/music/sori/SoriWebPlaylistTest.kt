package com.metrolist.music.sori

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoriWebPlaylistTest {
    private val page = javaClass.getResource("/sori/web_playlist_page.json")!!.readText()

    @Test
    fun parsesSongsFromLockups() {
        val parsed = parseWebPlaylistPage(page)

        assertEquals(listOf("27C4pfRsf9g", "bMhDJ0S0OBA", "CgCVZdcKcqY"), parsed.songs.map { it.id })
        val first = parsed.songs.first()
        // "LE SSERAFIM (르세라핌) x ILLIT (아일릿) x KATSEYE (캣츠아이) 'ICONIC BY MISTAKE' Official MV"
        assertEquals("ICONIC BY MISTAKE", first.title)
        assertEquals("LE SSERAFIM (르세라핌) x ILLIT (아일릿) x KATSEYE (캣츠아이)", first.artists.single().name)
        assertEquals(200, first.duration) // "3:20"
        assertEquals("https://i.ytimg.com/vi/27C4pfRsf9g/hqdefault.jpg", first.thumbnail)
    }

    @Test
    fun readsPlaylistTitleAndLargestThumbnail() {
        val parsed = parseWebPlaylistPage(page)
        assertEquals("파티를 위한 케이팝 댄스", parsed.title)
        assertEquals("https://i9.ytimg.com/s_p/RDCLAK5uy_l7K78k4EkjcFojhd1617rmUjY-aet6-t0/maxresdefault.jpg", parsed.thumbnail)
    }

    @Test
    fun topicChannelTracksKeepTheirTitle() {
        val json =
            """{"items":[{"lockupViewModel":{"contentId":"v1","contentType":"LOCKUP_CONTENT_TYPE_VIDEO",
            "metadata":{"lockupMetadataViewModel":{"title":{"content":"Blueming - Acoustic ver."},
            "metadata":{"contentMetadataViewModel":{"metadataRows":[{"metadataParts":[{"text":{"content":"IU - Topic"}}]}]}}}}}}]}"""

        val song = parseWebPlaylistPage(json).songs.single()

        assertEquals("Blueming - Acoustic ver.", song.title)
        assertEquals("IU", song.artists.single().name)
    }

    @Test
    fun findsContinuationToken() {
        assertTrue(parseWebPlaylistPage(page).continuation!!.startsWith("4qmFsgK"))
    }

    @Test
    fun emptyOrForeignJsonYieldsNothing() {
        val parsed = parseWebPlaylistPage("""{"contents":{}}""")
        assertTrue(parsed.songs.isEmpty())
        assertNull(parsed.continuation)
    }

    @Test
    fun parsesDurations() {
        assertEquals(200, parseDuration("3:20"))
        assertEquals(3723, parseDuration("1:02:03"))
        assertNull(parseDuration("LIVE"))
    }
}
