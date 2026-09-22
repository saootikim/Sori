package com.metrolist.music.sori

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SoriReleaseRadarTest {
    private val day1 = LocalDate.of(2026, 9, 22)
    private val day2 = day1.plusDays(1)

    private val iu = ReleaseArtist("UC_IU", "아이유")
    private val akmu = ReleaseArtist("UC_AKMU", "AKMU")

    private fun album(
        id: String,
        artist: ReleaseArtist,
        year: Int? = 2021,
    ) = StoredRelease(id = id, playlistId = "OLAK_$id", title = id, thumbnail = "", year = year, artistId = artist.id, artistName = artist.name)

    private fun scan(
        state: ReleaseRadarState,
        catalog: Map<ReleaseArtist, List<StoredRelease>?>,
        day: LocalDate,
    ) = runBlocking { scanReleases(state, catalog.keys.toList(), { catalog[it] }, day) }

    @Test
    fun firstScanIsTheBaseline() {
        val state = scan(ReleaseRadarState(), mapOf(iu to listOf(album("lilac", iu), album("star", iu, year = 2026))), day1)

        val radar = state.radar(day1)
        // Nothing is "new" on the first scan; this year's release is still listed.
        assertEquals(listOf("star"), radar.map { it.release.id })
        assertTrue(radar.none { it.isNew })
        assertTrue(state.newSince(0).isEmpty())
    }

    @Test
    fun releasesAppearingLaterAreNew() {
        val first = scan(ReleaseRadarState(), mapOf(iu to listOf(album("lilac", iu))), day1)
        val second = scan(first, mapOf(iu to listOf(album("fresh", iu, year = 2026), album("lilac", iu))), day2)

        val radar = second.radar(day2)
        assertEquals(listOf("fresh"), radar.map { it.release.id })
        assertTrue(radar.single().isNew)
        assertEquals(day2, radar.single().discovered)
        assertEquals(listOf("fresh"), second.newSince(day1.toEpochDay()).map { it.release.id })
    }

    @Test
    fun anOldAlbumShowingUpLaterIsNotNew() {
        // Artist page carousels can reshuffle: a 2019 album appearing now is not a new release.
        val first = scan(ReleaseRadarState(), mapOf(iu to listOf(album("lilac", iu))), day1)
        val second = scan(first, mapOf(iu to listOf(album("old", iu, year = 2019), album("lilac", iu))), day2)

        assertTrue(second.newSince(day1.toEpochDay()).isEmpty())
    }

    @Test
    fun aNewlyWatchedArtistsCatalogIsNotNew() {
        val first = scan(ReleaseRadarState(), mapOf(iu to listOf(album("lilac", iu))), day1)
        // AKMU becomes a top artist later: their existing albums are a baseline, not news.
        val second = scan(first, mapOf(iu to listOf(album("lilac", iu)), akmu to listOf(album("sailing", akmu))), day2)

        assertTrue(second.radar(day2).none { it.isNew })
        assertTrue(second.newSince(day1.toEpochDay()).isEmpty())
    }

    @Test
    fun aFailedArtistKeepsItsLastReleases() {
        val first = scan(ReleaseRadarState(), mapOf(iu to listOf(album("star", iu, year = 2026))), day1)
        val second = scan(first, mapOf(iu to null), day2)

        assertEquals(listOf("star"), second.radar(day2).map { it.release.id })
    }

    @Test
    fun newReleasesComeFirstThenThisYearsInArtistOrder() {
        val first =
            scan(
                ReleaseRadarState(),
                mapOf(iu to listOf(album("iu2026", iu, year = 2026)), akmu to listOf(album("akmu2026", akmu, year = 2026), album("old", akmu, year = 2019))),
                day1,
            )
        val second =
            scan(
                first,
                mapOf(iu to listOf(album("iu2026", iu, year = 2026)), akmu to listOf(album("akmuNew", akmu, year = 2026), album("akmu2026", akmu, year = 2026))),
                day2,
            )

        assertEquals(listOf("akmuNew", "iu2026", "akmu2026"), second.radar(day2).map { it.release.id })
    }

    @Test
    fun newnessFadesAfterTwoMonths() {
        val first = scan(ReleaseRadarState(), mapOf(iu to listOf(album("lilac", iu))), day1)
        val second = scan(first, mapOf(iu to listOf(album("fresh", iu, year = 2025), album("lilac", iu))), day2)

        assertTrue(second.radar(day2.plusDays(61)).isEmpty())
    }

    @Test
    fun oneProlificArtistDoesNotFillTheRadar() {
        val state =
            scan(
                ReleaseRadarState(),
                mapOf(iu to (1..6).map { album("iu$it", iu, year = 2026) }, akmu to listOf(album("akmu", akmu, year = 2026))),
                day1,
            )

        assertEquals(listOf("iu1", "iu2", "iu3", "akmu"), state.radar(day1).map { it.release.id })
    }

    @Test
    fun earlyInTheYearLastYearsReleasesStillCount() {
        val january = LocalDate.of(2027, 1, 10)
        val state = scan(ReleaseRadarState(), mapOf(iu to listOf(album("dec", iu, year = 2026), album("older", iu, year = 2025))), january)

        assertEquals(listOf("dec"), state.radar(january).map { it.release.id })
    }
}
