/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori

import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.random.Random

/** An artist a mix is built around: one of the user's most played. */
@Serializable
data class MixArtist(
    val id: String,
    val name: String,
    val thumbnail: String? = null,
)

/** "Daily Mix [number]": the user's favorites by [artist] blended with related songs. */
data class SoriDailyMix(
    val number: Int,
    val artist: MixArtist,
    val songs: List<SongItem>,
    /** The artist's photo, or a favorite's cover when there is none. */
    val cover: String? = artist.thumbnail,
) {
    /** Up to three artists heard most in the mix, [artist] first. */
    val artistNames: List<String>
        get() {
            val others =
                songs
                    .mapNotNull { song -> song.artists.firstOrNull()?.name?.takeIf { it.isNotBlank() } }
                    .filter { it != artist.name }
                    .groupingBy { it }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .map { it.key }
            return (listOf(artist.name) + others).take(3)
        }
}

/**
 * Builds Spotify-style daily mixes from local listening history: one per top artist, each about a
 * quarter the user's own favorites by that artist and the rest related songs from youtube.com
 * mixes seeded by those favorites (YouTube Music's own recommendations are Premium-only for free
 * users in Korea, 2026-09). The order is fixed for a day and changes the next.
 */
class SoriDailyMixBuilder(
    private val topArtists: suspend () -> List<MixArtist>,
    private val favorites: suspend (artistId: String) -> List<SongItem>,
    private val mix: suspend (videoId: String) -> List<SongItem> = SoriWebRadio::mix,
) {
    suspend fun build(day: LocalDate): List<SoriDailyMix> {
        val artists = topArtists().distinctBy { it.id }
        if (artists.size < MIN_ARTISTS) return emptyList()
        val requests = Semaphore(PARALLEL_ARTISTS)
        val built =
            coroutineScope {
                artists
                    .take(MAX_MIXES)
                    .map { artist -> async { requests.withPermit { buildMix(artist, day) } } }
                    .awaitAll()
            }.filterNotNull()
        // One singer can appear under several names (a Topic channel, an official channel...):
        // a mix whose favorites mostly repeat an earlier mix's is the same mix again.
        val kept = mutableListOf<BuiltMix>()
        built.forEach { candidate ->
            val repeats =
                kept.any { earlier ->
                    candidate.favoriteIds.count { it in earlier.favoriteIds } * 2 >= candidate.favoriteIds.size
                }
            if (!repeats) kept += candidate
        }
        return kept.mapIndexed { index, it -> it.mix.copy(number = index + 1) }
    }

    private class BuiltMix(
        val mix: SoriDailyMix,
        val favoriteIds: Set<String>,
    )

    private suspend fun buildMix(
        artist: MixArtist,
        day: LocalDate,
    ): BuiltMix? {
        val familiar =
            favorites(artist.id)
                .distinctBy { it.id }
                .distinctBy(::titleKey)
                .take(FAVORITES_PER_MIX)
        if (familiar.isEmpty()) return null
        val random = Random(day.toEpochDay() * 31 + artist.id.hashCode())
        val seeds = listOfNotNull(familiar.first(), familiar.drop(1).randomOrNull(random))
        val seenIds = familiar.mapTo(mutableSetOf()) { it.id }
        val seenTitles = familiar.mapTo(mutableSetOf(), ::titleKey)
        val related =
            seeds
                .flatMap { mix(it.id) }
                .filter { seenIds.add(it.id) && seenTitles.add(titleKey(it)) }
        return BuiltMix(
            mix =
                SoriDailyMix(
                    number = 0,
                    artist = artist,
                    songs = interleave(familiar.shuffled(random), related.shuffled(random), MIX_SIZE),
                    cover = artist.thumbnail ?: familiar.first().thumbnail,
                ),
            favoriteIds = familiar.mapTo(mutableSetOf()) { it.id },
        )
    }

    private companion object {
        const val MIN_ARTISTS = 3
        const val MAX_MIXES = 6
        const val FAVORITES_PER_MIX = 8
        const val MIX_SIZE = 30
        const val PARALLEL_ARTISTS = 3
    }
}

/** Letters and digits only, so "Blueming" and "blueming " count as the same song. */
private fun titleKey(song: SongItem) = song.title.lowercase().filter { it.isLetterOrDigit() }

/** Every third song (starting with the first) is a favorite, while both kinds last. */
private fun interleave(
    familiar: List<SongItem>,
    related: List<SongItem>,
    size: Int,
): List<SongItem> {
    val favorites = familiar.iterator()
    val others = related.iterator()
    val result = mutableListOf<SongItem>()
    while (result.size < size && (favorites.hasNext() || others.hasNext())) {
        val wantFavorite = result.size % 3 == 0
        result +=
            when {
                wantFavorite && favorites.hasNext() -> favorites.next()
                others.hasNext() -> others.next()
                else -> favorites.next()
            }
    }
    return result
}

// Storage: the day's mixes are cached as JSON so Home shows them instantly and builds once a day.

@Serializable
private data class StoredMixes(
    val day: String,
    val mixes: List<StoredMix>,
)

@Serializable
private data class StoredMix(
    val number: Int,
    val artist: MixArtist,
    val songs: List<StoredSong>,
    val cover: String? = null,
)

@Serializable
private data class StoredSong(
    val id: String,
    val title: String,
    val artists: List<StoredArtist>,
    val albumId: String? = null,
    val albumName: String? = null,
    val duration: Int? = null,
    val thumbnail: String,
    val musicVideoType: String? = null,
    val explicit: Boolean = false,
)

@Serializable
private data class StoredArtist(
    val name: String,
    val id: String? = null,
)

private val mixJson = Json { ignoreUnknownKeys = true }

fun encodeDailyMixes(
    day: LocalDate,
    mixes: List<SoriDailyMix>,
): String =
    mixJson.encodeToString(
        StoredMixes.serializer(),
        StoredMixes(
            day = day.toString(),
            mixes =
                mixes.map { mix ->
                    StoredMix(
                        number = mix.number,
                        artist = mix.artist,
                        cover = mix.cover,
                        songs =
                            mix.songs.map { song ->
                                StoredSong(
                                    id = song.id,
                                    title = song.title,
                                    artists = song.artists.map { StoredArtist(it.name, it.id) },
                                    albumId = song.album?.id,
                                    albumName = song.album?.name,
                                    duration = song.duration,
                                    thumbnail = song.thumbnail,
                                    musicVideoType = song.musicVideoType,
                                    explicit = song.explicit,
                                )
                            },
                    )
                },
        ),
    )

/** The day the mixes were built for and the mixes, or null if [json] is not a stored mix list. */
fun decodeDailyMixes(json: String): Pair<LocalDate, List<SoriDailyMix>>? =
    runCatching {
        val stored = mixJson.decodeFromString(StoredMixes.serializer(), json)
        LocalDate.parse(stored.day) to
            stored.mixes.map { mix ->
                SoriDailyMix(
                    number = mix.number,
                    artist = mix.artist,
                    cover = mix.cover ?: mix.artist.thumbnail,
                    songs =
                        mix.songs.map { song ->
                            SongItem(
                                id = song.id,
                                title = song.title,
                                artists = song.artists.map { Artist(it.name, it.id) },
                                album = song.albumId?.let { Album(name = song.albumName.orEmpty(), id = it) },
                                duration = song.duration,
                                thumbnail = song.thumbnail,
                                musicVideoType = song.musicVideoType,
                                explicit = song.explicit,
                            )
                        },
                )
            }
    }.getOrNull()
