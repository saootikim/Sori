/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.metrolist.music.LocalDatabase
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Album
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.LocalItem
import com.metrolist.music.db.entities.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * When library albums, artists and playlists were last played, by id, in the database's epoch
 * milliseconds. Play history is kept per song only, so an item counts as played when one of its
 * songs was: for a playlist that includes plays of its songs from anywhere else.
 */
data class LibraryRecency(
    val albums: Map<String, Long> = emptyMap(),
    val artists: Map<String, Long> = emptyMap(),
    val playlists: Map<String, Long> = emptyMap(),
) {
    /** The library's "Recents" time of [item], like Spotify's: last played or added, whichever is later. */
    fun of(item: LocalItem): Long =
        when (item) {
            is Album -> soriRecencyMillis(albums[item.id], item.album.bookmarkedAt)
            is Artist -> soriRecencyMillis(artists[item.id], item.artist.bookmarkedAt)
            is Playlist -> soriRecencyMillis(playlists[item.id], item.playlist.createdAt ?: item.playlist.bookmarkedAt)
            // Songs only join the library list while searching, which orders by kind and name.
            else -> 0L
        }
}

/** The later of [lastPlayed] and [added], in the database's epoch milliseconds (see Converters). */
fun soriRecencyMillis(
    lastPlayed: Long?,
    added: LocalDateTime?,
): Long = maxOf(lastPlayed ?: 0L, added?.atZone(ZoneOffset.UTC)?.toInstant()?.toEpochMilli() ?: 0L)

object SoriLibraryRecents {
    private const val ALBUMS =
        "SELECT m.albumId, MAX(e.timestamp) FROM event e JOIN song_album_map m ON m.songId = e.songId GROUP BY m.albumId"
    private const val ARTISTS =
        "SELECT m.artistId, MAX(e.timestamp) FROM event e JOIN song_artist_map m ON m.songId = e.songId GROUP BY m.artistId"
    private const val PLAYLISTS =
        "SELECT m.playlistId, MAX(e.timestamp) FROM event e JOIN playlist_song_map m ON m.songId = e.songId GROUP BY m.playlistId"

    /** Read again each time a play is recorded (the newest history event changes). */
    fun flow(database: MusicDatabase): Flow<LibraryRecency> =
        database
            .latestEvent()
            .map { it?.event?.id }
            .distinctUntilChanged()
            .map { read(database) }
            .flowOn(Dispatchers.IO)

    private fun read(database: MusicDatabase): LibraryRecency {
        val db = database.openHelper.readableDatabase
        fun lastPlayed(sql: String): Map<String, Long> =
            buildMap {
                db.query(sql).use { cursor ->
                    while (cursor.moveToNext()) {
                        if (!cursor.isNull(0) && !cursor.isNull(1)) put(cursor.getString(0), cursor.getLong(1))
                    }
                }
            }
        return LibraryRecency(albums = lastPlayed(ALBUMS), artists = lastPlayed(ARTISTS), playlists = lastPlayed(PLAYLISTS))
    }
}

/** The library's recency, kept up to date only while [enabled] (the Recents sort is chosen). */
@Composable
fun rememberSoriLibraryRecency(enabled: Boolean): LibraryRecency {
    val database = LocalDatabase.current
    val flow = remember(database, enabled) { if (enabled) SoriLibraryRecents.flow(database) else flowOf(LibraryRecency()) }
    return flow.collectAsState(LibraryRecency()).value
}
