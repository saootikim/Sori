package com.metrolist.music.sori

import com.metrolist.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SoriChartsTest {
    private val tracks = javaClass.getResource("/sori/chart_tracks.json")!!.readText()
    private val artists = javaClass.getResource("/sori/chart_artists.json")!!.readText()

    @Test
    fun parsesWeeklyTracks() {
        val chart = parseChartTracks(tracks)

        assertEquals(LocalDate.of(2026, 9, 17), chart.weekEnd)
        assertEquals(listOf(1, 3, 10, 46, 47, 28), chart.entries.map { it.rank })
        val first = chart.entries.first()
        assertEquals("Dear my crazy soulmate", first.song.title)
        assertEquals(listOf("아이유"), first.song.artists.map { it.name })
        assertEquals(25, first.previousRank)
        assertEquals(2, first.weeksOnChart)
    }

    @Test
    fun prefersTheSongVersionOverTheMusicVideo() {
        val chart = parseChartTracks(tracks)

        // The song version ("art track") plays audio with the square album cover.
        val first = chart.entries.first().song
        assertEquals("IHIt9S2DWcQ", first.id)
        assertEquals(MUSIC_VIDEO_TYPE_ATV, first.musicVideoType)
        // Without one, the music video is played.
        val noSongVersion = chart.entries.single { it.rank == 47 }.song
        assertEquals("JNy_pVfs6yQ", noSongVersion.id)
        assertNull(noSongVersion.musicVideoType)
    }

    @Test
    fun usesTheLargeSquareCover() {
        val cover = parseChartTracks(tracks).entries.first().song.thumbnail

        assertEquals(true, cover.startsWith("https://yt3.googleusercontent.com/"))
        assertEquals(true, cover.contains("=w544-h544"))
    }

    @Test
    fun describesRankMovement() {
        val movement = parseChartTracks(tracks).entries.associate { it.rank to it.movement }

        assertEquals(ChartMovement.Up(24), movement[1])
        assertEquals(ChartMovement.Down(1), movement[3])
        assertEquals(ChartMovement.New, movement[10])
        assertEquals(ChartMovement.Reentry, movement[46])
        assertEquals(ChartMovement.Up(8), movement[47])
        assertEquals(ChartMovement.Same, movement[28])
    }

    @Test
    fun parsesWeeklyArtists() {
        val chart = parseChartArtists(artists)

        assertEquals(LocalDate.of(2026, 9, 17), chart.weekEnd)
        val first = chart.entries.first()
        assertEquals(1, first.rank)
        assertEquals("아이유", first.name)
        assertEquals("UC3SyT4_WLHzN7JmHQwKQZww", first.channelId)
        assertEquals(true, first.thumbnail?.startsWith("https://yt3.googleusercontent.com/"))
    }

    @Test
    fun weekRangeEndsOnTheChartDate() {
        val range = chartWeek(LocalDate.of(2026, 9, 17))

        assertEquals(LocalDate.of(2026, 9, 11), range.start)
        assertEquals(LocalDate.of(2026, 9, 17), range.endInclusive)
    }

    @Test
    fun brokenResponsesGiveEmptyCharts() {
        assertEquals(0, parseChartTracks("{}").entries.size)
        assertEquals(0, parseChartArtists("not json").entries.size)
    }
}
