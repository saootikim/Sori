package com.metrolist.music.sori

import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.AlbumPage
import org.junit.Assert.assertEquals
import org.junit.Test

class SoriAlbumFallbackTest {
    private val album =
        AlbumItem(
            browseId = "MPREb_lilac",
            playlistId = "OLAK5uy_lilac",
            title = "IU 5th Album 'LILAC'",
            artists = listOf(Artist("IU", "UC_iu")),
            thumbnail = "https://lh3/lilac.jpg",
        )

    @Test
    fun webSongsBecomeAlbumTracks() {
        val web =
            WebPlaylist(
                title = "Album - LILAC",
                thumbnail = null,
                songs = listOf(SongItem(id = "v1", title = "라일락", artists = listOf(Artist("IU - Topic", null)), duration = 214, thumbnail = "https://i.ytimg.com/vi/v1/hqdefault.jpg")),
            )

        val filled = AlbumPage(album, songs = emptyList(), otherVersions = emptyList()).withSongsFrom(web)

        val track = filled.songs.single()
        assertEquals("라일락", track.title)
        assertEquals(Album("IU 5th Album 'LILAC'", "MPREb_lilac"), track.album)
        assertEquals(listOf(Artist("IU", "UC_iu")), track.artists)
        assertEquals("https://lh3/lilac.jpg", track.thumbnail)
        assertEquals(214, track.duration)
    }

    @Test
    fun keepsChannelWhenAlbumHasNoArtists() {
        val web = WebPlaylist(null, null, listOf(SongItem(id = "v1", title = "t", artists = listOf(Artist("Chan", null)), thumbnail = "x")))

        val filled = AlbumPage(album.copy(artists = null), emptyList(), emptyList()).withSongsFrom(web)

        assertEquals(listOf(Artist("Chan", null)), filled.songs.single().artists)
    }
}
