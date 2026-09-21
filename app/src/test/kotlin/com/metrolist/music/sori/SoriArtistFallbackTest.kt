package com.metrolist.music.sori

import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.BrowseEndpoint
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.pages.ArtistPage
import com.metrolist.innertube.pages.ArtistSection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SoriArtistFallbackTest {
    private val artistId = "UCTUR0sVEkD8T5MlSHqgaI_Q"
    private val gatedRadio = WatchEndpoint(playlistId = "RDEM79RRQ105L7V61p68gX8ahw", params = "wAEB")
    private val albums = ArtistSection("앨범", listOf(AlbumItem("MPREb_1", "OLAK5uy_1", title = "LILAC", artists = null, thumbnail = "t")), null)
    private val gatedPage =
        ArtistPage(
            artist = ArtistItem(id = artistId, title = "아이유", thumbnail = null, shuffleEndpoint = gatedRadio, radioEndpoint = gatedRadio),
            sections = listOf(albums),
            description = null,
            subscriberCountText = null,
        )

    private fun song(id: String) = SongItem(id = id, title = id, artists = listOf(Artist("IU", null)), thumbnail = "x")

    private val popular = WebPlaylist("인기 동영상", null, (1..8).map { song("s$it") })

    private fun fill(
        page: ArtistPage,
        loaded: WebPlaylist? = popular,
    ): Pair<ArtistPage, List<String>> {
        val requested = mutableListOf<String>()
        val result =
            runBlocking {
                SoriArtistFallback.fill(page, artistId, "인기곡") { id ->
                    requested += id
                    loaded
                }
            }
        return result to requested
    }

    @Test
    fun popularPlaylistIdFromChannelId() {
        assertEquals("UULPTUR0sVEkD8T5MlSHqgaI_Q", SoriArtistFallback.popularPlaylistId(artistId))
        assertNull(SoriArtistFallback.popularPlaylistId("MPLAUCxyz"))
    }

    @Test
    fun addsTopFivePopularSongsFirstWhenArtistHasNoSongs() {
        val (page, requested) = fill(gatedPage)

        assertEquals(listOf("UULPTUR0sVEkD8T5MlSHqgaI_Q"), requested)
        val section = page.sections.first()
        assertEquals("인기곡", section.title)
        assertEquals(listOf("s1", "s2", "s3", "s4", "s5"), section.items.map { it.id })
        assertEquals(listOf(Artist("아이유", artistId)), (section.items.first() as SongItem).artists)
        assertTrue(SoriArtistFallback.isSongSection(section))
        assertEquals(albums, page.sections[1])
    }

    @Test
    fun duplicateUploadsOfOneSongAreDropped() {
        val dupes = WebPlaylist(null, null, listOf(song("a"), song("b").copy(title = "a"), song("c")))
        val (page, _) = fill(gatedPage, loaded = dupes)
        assertEquals(listOf("a", "c"), page.sections.first().items.map { it.id })
    }

    @Test
    fun gatedRadioAndShuffleBecomeSongRadios() {
        val (page, _) = fill(gatedPage)

        assertEquals(WatchEndpoint(videoId = "s1", playlistId = "RDAMVMs1"), page.artist.radioEndpoint)
        val shuffle = page.artist.shuffleEndpoint!!
        assertTrue(shuffle.playlistId == "RDAMVM${shuffle.videoId}" && shuffle.videoId in popular.songs.map { it.id })
    }

    @Test
    fun moreLinkOpensFullPopularPlaylist() {
        val (page, _) = fill(gatedPage)
        val more = page.sections.first().moreEndpoint!!
        assertEquals("online_playlist/UULPTUR0sVEkD8T5MlSHqgaI_Q", SoriArtistFallback.moreRoute(more))
        assertNull(SoriArtistFallback.moreRoute(BrowseEndpoint("MPADUCxyz")))
    }

    @Test
    fun pagesWithSongsAreUntouched() {
        val songs = ArtistSection("노래", listOf(song("a").copy(album = Album("LILAC", "MPREb_1"))), null)
        val page = gatedPage.copy(sections = listOf(songs, albums))

        val (result, requested) = fill(page)

        assertSame(page, result)
        assertTrue(requested.isEmpty())
    }

    @Test
    fun unchangedWhenPopularListIsUnavailable() {
        val (result, _) = fill(gatedPage, loaded = null)
        assertSame(gatedPage, result)
        assertFalse(result.sections.any { SoriArtistFallback.isSongSection(it) })
    }
}
