package com.metrolist.music.sori

import com.metrolist.innertube.YouTube.SearchFilter
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.SearchResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SongSearchFallbackTest {
    private fun song(id: String) = SongItem(id = id, title = id, artists = emptyList(), thumbnail = "")

    private fun run(results: Map<SearchFilter, List<SongItem>>, filter: SearchFilter): Pair<List<String>, List<SearchFilter>> {
        val calls = mutableListOf<SearchFilter>()
        val result =
            runBlocking {
                searchWithSongFallback("IU", filter) { _, f ->
                    calls += f
                    Result.success(SearchResult(items = results[f].orEmpty(), continuation = null))
                }
            }
        return result.getOrThrow().items.map { it.id } to calls
    }

    @Test
    fun songsAreReturnedWhenPresent() {
        val (ids, calls) = run(mapOf(SearchFilter.FILTER_SONG to listOf(song("a"))), SearchFilter.FILTER_SONG)
        assertEquals(listOf("a"), ids)
        assertEquals(listOf(SearchFilter.FILTER_SONG), calls)
    }

    @Test
    fun emptySongsFallBackToVideos() {
        // Korea, free users: the songs filter comes back empty.
        val (ids, calls) = run(mapOf(SearchFilter.FILTER_VIDEO to listOf(song("mv"))), SearchFilter.FILTER_SONG)
        assertEquals(listOf("mv"), ids)
        assertEquals(listOf(SearchFilter.FILTER_SONG, SearchFilter.FILTER_VIDEO), calls)
    }

    @Test
    fun otherFiltersAreUntouched() {
        val (ids, calls) = run(emptyMap(), SearchFilter.FILTER_ALBUM)
        assertEquals(emptyList<String>(), ids)
        assertEquals(listOf(SearchFilter.FILTER_ALBUM), calls)
    }
}
