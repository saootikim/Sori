package com.metrolist.music.sori

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoArtistTitleTest {
    @Test
    fun cleansChannelNames() {
        assertEquals("TAEYEON", cleanChannelName("TAEYEON Official"))
        assertEquals("Heize", cleanChannelName("Heize official"))
        assertEquals("IU", cleanChannelName("IU - Topic"))
        assertEquals("마크툽 MAKTUB", cleanChannelName("마크툽 MAKTUB Official"))
        assertEquals("BLACKPINK", cleanChannelName("BLACKPINKVEVO"))
        assertEquals("1theK (원더케이)", cleanChannelName("1theK (원더케이)"))
        assertEquals("이지금", cleanChannelName("이지금 [IU Official]"))
    }

    @Test
    fun splitsArtistDashTitle() {
        assertEquals("IU(아이유)" to "Through the Night(밤편지)", splitArtistTitle("IU(아이유) - Through the Night(밤편지)"))
        assertEquals("마크툽 (MAKTUB)" to "오늘도 빛나는 너에게(To You My Light)", splitArtistTitle("마크툽 (MAKTUB) - 오늘도 빛나는 너에게(To You My Light)"))
    }

    @Test
    fun splitsArtistQuotedTitle() {
        assertEquals("ILLIT (아일릿)" to "It’s Me", splitArtistTitle("ILLIT (아일릿) ‘It’s Me’"))
        assertEquals("BLACKPINK" to "뛰어(JUMP)", splitArtistTitle("BLACKPINK - ‘뛰어(JUMP)’"))
        assertEquals(
            "LE SSERAFIM (르세라핌) x ILLIT (아일릿) x KATSEYE (캣츠아이)" to "ICONIC BY MISTAKE",
            splitArtistTitle("LE SSERAFIM (르세라핌) x ILLIT (아일릿) x KATSEYE (캣츠아이) 'ICONIC BY MISTAKE'"),
        )
    }

    @Test
    fun leavesUnsplittableTitlesAlone() {
        assertNull(splitArtistTitle("The Moment My Heart (She is My Type♡ X KYUHYUN)"))
        assertNull(splitArtistTitle("그대라는 시 (그대라는 시)"))
        assertNull(splitArtistTitle("Supernova"))
        assertNull(splitArtistTitle("- leading dash"))
    }
}
