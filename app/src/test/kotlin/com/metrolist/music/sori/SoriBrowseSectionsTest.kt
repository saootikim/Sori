package com.metrolist.music.sori

import com.metrolist.innertube.models.BrowseEndpoint
import com.metrolist.innertube.pages.MoodAndGenres
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SoriBrowseSectionsTest {
    private fun item(title: String) =
        MoodAndGenres.Item(title, 0xFF336699, BrowseEndpoint(browseId = "FEmusic_moods_and_genres_category", params = title))

    private val curated = listOf(SoriBrowseSection("Browse all", listOf(SoriBrowseTile("K-Pop", 0xFFE8115B, query = "k-pop playlist"))))

    private fun load(
        full: Result<List<MoodAndGenres>>,
        explore: Result<List<MoodAndGenres.Item>>,
    ) = runBlocking {
        loadBrowseSections(full = { full }, explore = { explore }, exploreTitle = "Moods & genres", curated = { curated })
    }

    @Test
    fun usesYouTubeMusicSectionsWhenAvailable() {
        val full = listOf(MoodAndGenres("Genres", listOf(item("K-Pop"))))

        val result = load(Result.success(full), Result.failure(IllegalStateException("unused")))

        assertEquals(full.map { it.toBrowseSection() }, result)
        assertEquals(item("K-Pop").endpoint, result.single().tiles.single().endpoint)
    }

    @Test
    fun fallsBackToExplore() {
        val explored = listOf(item("Chill"), item("Workout"))

        val result = load(Result.success(emptyList()), Result.success(explored))

        assertEquals(listOf(MoodAndGenres("Moods & genres", explored).toBrowseSection()), result)
    }

    @Test
    fun fallsBackToSoriCatalogWhenYouTubeMusicIsGated() {
        // Korea, non-Premium: both pages come back empty ("Premium members only").
        assertEquals(curated, load(Result.success(emptyList()), Result.success(emptyList())))
        assertEquals(curated, load(Result.failure(RuntimeException()), Result.failure(RuntimeException())))
    }
}
