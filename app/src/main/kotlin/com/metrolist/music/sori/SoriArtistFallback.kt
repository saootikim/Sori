/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori

import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.BrowseEndpoint
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.pages.ArtistPage
import com.metrolist.innertube.pages.ArtistSection

/**
 * Artist pages for free users in Korea (2026-09) come without the songs section, and the
 * artist radio/shuffle queues are empty. youtube.com still serves the channel's auto-generated
 * "popular videos" playlist (UULP + channel id), which for an artist's Topic channel is their
 * songs by popularity. It is used for a "Popular" section and for song-based radio/shuffle,
 * since single-song radio (RDAMVM) still plays.
 */
/** The popular list can hold several uploads of one song (e.g. two "Celebrity"); keep the most popular. */
private fun List<SongItem>.distinctBySongTitle() = distinctBy { it.title.trim().lowercase() }

object SoriArtistFallback {
    private const val POPULAR_PREFIX = "UULP"
    private const val SECTION_SIZE = 5

    fun popularPlaylistId(channelId: String): String? =
        channelId.takeIf { it.startsWith("UC") && it.length > 2 }?.let { POPULAR_PREFIX + it.removePrefix("UC") }

    fun isPopularSection(section: ArtistSection): Boolean = section.moreEndpoint?.browseId?.startsWith("VL$POPULAR_PREFIX") == true

    /** Upstream treats a section as "songs" when its songs carry an album; Sori's popular section counts too. */
    fun isSongSection(section: ArtistSection): Boolean =
        (section.items.firstOrNull() as? SongItem)?.album != null || isPopularSection(section)

    /** Route for a section's "more" link when it is Sori's popular section (full list via the playlist screen). */
    fun moreRoute(endpoint: BrowseEndpoint): String? =
        endpoint.browseId.takeIf { it.startsWith("VL$POPULAR_PREFIX") }?.let { "online_playlist/${it.removePrefix("VL")}" }

    /** The whole popular list behind Sori's popular section (falls back to the section's own songs). */
    suspend fun popularSongs(section: ArtistSection): List<SongItem> {
        val playlistId = section.moreEndpoint?.browseId?.removePrefix("VL")
        val artists = (section.items.firstOrNull() as? SongItem)?.artists
        val loaded = playlistId?.let { SoriWebPlaylist.load(it, maxPages = 1)?.songs }
        return loaded?.distinctBySongTitle()?.map { song -> artists?.let { song.copy(artists = it) } ?: song }
            ?: section.items.filterIsInstance<SongItem>()
    }

    suspend fun fill(
        page: ArtistPage,
        artistId: String,
        sectionTitle: String,
        load: suspend (String) -> WebPlaylist? = { SoriWebPlaylist.load(it, maxPages = 1) },
    ): ArtistPage {
        if (page.sections.any(::isSongSection)) return page
        val playlistId = popularPlaylistId(artistId) ?: return page
        val popular = load(playlistId)?.songs?.takeIf { it.isNotEmpty() } ?: return page

        val artist = Artist(name = page.artist.title, id = artistId)
        val songs = popular.distinctBySongTitle().map { it.copy(artists = listOf(artist)) }
        val top = songs.first()
        val pick = songs.random()
        return page.copy(
            artist =
                page.artist.copy(
                    radioEndpoint = WatchEndpoint(videoId = top.id, playlistId = "RDAMVM${top.id}"),
                    shuffleEndpoint = WatchEndpoint(videoId = pick.id, playlistId = "RDAMVM${pick.id}"),
                ),
            sections = listOf(ArtistSection(sectionTitle, songs.take(SECTION_SIZE), BrowseEndpoint("VL$playlistId"))) + page.sections,
        )
    }
}
