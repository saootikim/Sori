package com.metrolist.music.sori

import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SoriDailyMixTest {
    private val day = LocalDate.of(2026, 9, 22)

    private fun song(
        id: String,
        artist: String = "a",
        title: String = id,
    ) = SongItem(id = id, title = title, artists = listOf(Artist(artist, null)), thumbnail = "")

    private fun artist(id: String) = MixArtist(id = id, name = "Artist $id", thumbnail = null)

    /** Each artist has 8 favorites; each seed's mix has 25 songs by others plus the seed itself. */
    private fun builder(
        artists: List<MixArtist> = listOf(artist("A"), artist("B"), artist("C")),
        favorites: (String) -> List<SongItem> = { id -> (1..8).map { song("$id-fav$it", artist = "Artist $id") } },
        mix: (String) -> List<SongItem> = { seed -> listOf(song(seed)) + (1..25).map { song("$seed-mix$it", artist = "Other") } },
    ) = SoriDailyMixBuilder(topArtists = { artists }, favorites = { favorites(it) }, mix = { mix(it) })

    @Test
    fun oneMixPerTopArtistInOrder() {
        val mixes = runBlocking { builder().build(day) }

        assertEquals(listOf("A", "B", "C"), mixes.map { it.artist.id })
        assertEquals(listOf(1, 2, 3), mixes.map { it.number })
        mixes.forEach { assertEquals(30, it.songs.size) }
    }

    @Test
    fun tooLittleHistoryShowsNoMixes() {
        val mixes = runBlocking { builder(artists = listOf(artist("A"), artist("B"))).build(day) }

        assertTrue(mixes.isEmpty())
    }

    @Test
    fun atMostSixMixes() {
        val artists = ('A'..'H').map { artist(it.toString()) }

        assertEquals(6, runBlocking { builder(artists = artists).build(day) }.size)
    }

    @Test
    fun blendsFavoritesWithNewSongs() {
        val mix = runBlocking { builder().build(day) }.first()
        val isFavorite = { song: SongItem -> song.id.matches(Regex("A-fav\\d+")) }

        // Every third slot is a favorite, starting with one.
        assertEquals(8, mix.songs.count(isFavorite))
        assertTrue(isFavorite(mix.songs.first()))
        assertTrue(mix.songs.drop(1).take(2).none(isFavorite))
    }

    @Test
    fun noDuplicateSongsOrTitles() {
        val mix =
            runBlocking {
                builder(
                    mix = { seed ->
                        // The mix repeats the seed, a favorite by another id with the same title, and itself.
                        listOf(song(seed), song("dup-video", title = "A-fav2"), song("x1"), song("x1"), song("x2"))
                    },
                ).build(day)
            }.first()

        assertEquals(mix.songs.size, mix.songs.distinctBy { it.id }.size)
        assertEquals(mix.songs.size, mix.songs.distinctBy { it.title.lowercase() }.size)
        assertTrue(mix.songs.none { it.id == "dup-video" })
    }

    @Test
    fun sameDaySameMixNextDayDifferentOrder() {
        val today = runBlocking { builder().build(day) }.first().songs.map { it.id }
        val again = runBlocking { builder().build(day) }.first().songs.map { it.id }
        val tomorrow = runBlocking { builder().build(day.plusDays(1)) }.first().songs.map { it.id }

        assertEquals(today, again)
        assertNotEquals(today, tomorrow)
    }

    @Test
    fun failedMixesStillGiveFavorites() {
        val mixes = runBlocking { builder(mix = { emptyList() }).build(day) }

        assertEquals(3, mixes.size)
        assertEquals(8, mixes.first().songs.size)
    }

    @Test
    fun artistsWithoutFavoritesAreSkipped() {
        val mixes =
            runBlocking {
                builder(
                    artists = listOf(artist("A"), artist("B"), artist("C"), artist("D")),
                    favorites = { id -> if (id == "B") emptyList() else (1..8).map { song("$id-fav$it") } },
                ).build(day)
            }

        assertEquals(listOf("A", "C", "D"), mixes.map { it.artist.id })
        assertEquals(listOf(1, 2, 3), mixes.map { it.number })
    }

    @Test
    fun namesTheArtistsHeardMost() {
        val mix =
            runBlocking {
                builder(
                    // Both seeds give the same 17 songs, so all of them fit in the mix.
                    mix = {
                        (1..10).map { song("x$it", artist = "Frequent") } + (1..5).map { song("y$it", artist = "Rare") } +
                            (1..2).map { song("z$it", artist = "Seldom") }
                    },
                ).build(day)
            }.first()

        assertEquals(listOf("Artist A", "Frequent", "Rare"), mix.artistNames)
    }

    @Test
    fun mixesThatMostlyRepeatAnEarlierOneAreDropped() {
        // "아이유" and "이지금" are one singer under two channel names: same favorites, same mix.
        val mixes =
            runBlocking {
                builder(
                    artists = listOf(artist("IU"), artist("LJG"), artist("B"), artist("C")),
                    favorites = { id ->
                        val owner = if (id == "LJG") "IU" else id
                        (1..8).map { song("$owner-fav$it") }
                    },
                ).build(day)
            }

        assertEquals(listOf("IU", "B", "C"), mixes.map { it.artist.id })
        assertEquals(listOf(1, 2, 3), mixes.map { it.number })
    }

    @Test
    fun coverFallsBackToAFavoriteWithoutArtistPhoto() {
        val mixes =
            runBlocking {
                builder(
                    artists = listOf(MixArtist("A", "Artist A", "https://photo/a"), artist("B"), artist("C")),
                    favorites = { id -> (1..8).map { song("$id-fav$it").copy(thumbnail = "https://cover/$id") } },
                ).build(day)
            }

        assertEquals("https://photo/a", mixes[0].cover)
        assertEquals("https://cover/B", mixes[1].cover)
    }

    @Test
    fun storedMixesRoundTrip() {
        val mixes = runBlocking { builder().build(day) }

        val restored = decodeDailyMixes(encodeDailyMixes(day, mixes))

        assertEquals(day, restored?.first)
        assertEquals(mixes.map { it.songs.map { s -> s.id } }, restored?.second?.map { it.songs.map { s -> s.id } })
        assertEquals(mixes.map { it.artist }, restored?.second?.map { it.artist })
        assertEquals(mixes.map { it.cover }, restored?.second?.map { it.cover })
    }
}
