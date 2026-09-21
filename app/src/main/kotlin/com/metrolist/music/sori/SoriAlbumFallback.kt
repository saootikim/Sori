/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori

import com.metrolist.innertube.models.Album
import com.metrolist.innertube.pages.AlbumPage

/**
 * For logged-in free accounts in Korea (2026-09) YouTube Music returns album pages without any
 * songs. The album's own playlist still loads from youtube.com, so its songs are used instead,
 * tagged with the album, its artists and its cover.
 */
object SoriAlbumFallback {
    suspend fun fill(page: AlbumPage): AlbumPage {
        if (page.songs.isNotEmpty() || page.album.playlistId.isBlank()) return page
        val web = SoriWebPlaylist.load(page.album.playlistId) ?: return page
        return page.withSongsFrom(web)
    }
}

internal fun AlbumPage.withSongsFrom(web: WebPlaylist): AlbumPage =
    copy(
        songs =
            web.songs.map { song ->
                song.copy(
                    album = Album(name = album.title, id = album.browseId),
                    artists = album.artists?.takeIf { it.isNotEmpty() } ?: song.artists,
                    thumbnail = album.thumbnail,
                )
            },
    )
