/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.music.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

/**
 * Search-backed Home shelves of YouTube Music's own featured playlists.
 *
 * Used when YouTube Music's home feed is unavailable. In Korea it is Premium-only for free
 * users as of 2026-09, but featured-playlist search still works, so Home isn't empty on day one.
 */
enum class SoriShelf(
    @StringRes val title: Int,
    @StringRes val query: Int,
) {
    KPOP_HITS(R.string.sori_shelf_kpop_hits, R.string.sori_shelf_kpop_hits_query),
    CHILL(R.string.sori_shelf_chill, R.string.sori_shelf_chill_query),
    FOCUS(R.string.sori_shelf_focus, R.string.sori_shelf_focus_query),
    WORKOUT(R.string.sori_shelf_workout, R.string.sori_shelf_workout_query),
    DRIVE(R.string.sori_shelf_drive, R.string.sori_shelf_drive_query),
    SLEEP(R.string.sori_shelf_sleep, R.string.sori_shelf_sleep_query),
    HIPHOP(R.string.sori_shelf_hiphop, R.string.sori_shelf_hiphop_query),
    BALLAD(R.string.sori_shelf_ballad, R.string.sori_shelf_ballad_query),
    PARTY(R.string.sori_shelf_party, R.string.sori_shelf_party_query),
}

/** Six shelves, the most fitting for the time of day first. */
fun orderedShelves(period: GreetingPeriod): List<SoriShelf> =
    when (period) {
        GreetingPeriod.MORNING -> listOf(SoriShelf.FOCUS, SoriShelf.KPOP_HITS, SoriShelf.DRIVE, SoriShelf.WORKOUT, SoriShelf.BALLAD, SoriShelf.CHILL)
        GreetingPeriod.AFTERNOON -> listOf(SoriShelf.KPOP_HITS, SoriShelf.WORKOUT, SoriShelf.DRIVE, SoriShelf.HIPHOP, SoriShelf.FOCUS, SoriShelf.PARTY)
        GreetingPeriod.EVENING -> listOf(SoriShelf.CHILL, SoriShelf.KPOP_HITS, SoriShelf.HIPHOP, SoriShelf.BALLAD, SoriShelf.DRIVE, SoriShelf.PARTY)
        GreetingPeriod.NIGHT -> listOf(SoriShelf.SLEEP, SoriShelf.CHILL, SoriShelf.BALLAD, SoriShelf.HIPHOP, SoriShelf.KPOP_HITS, SoriShelf.FOCUS)
    }

data class SoriHomeShelf(
    val title: String,
    val items: List<YTItem>,
)

@HiltViewModel
class SoriHomeShelvesViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _shelves = MutableStateFlow<List<SoriHomeShelf>>(emptyList())
        val shelves: StateFlow<List<SoriHomeShelf>> = _shelves

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading

        /** Loads once; later calls are no-ops unless the previous attempt came back empty. */
        fun ensureLoaded() {
            if (_isLoading.value || _shelves.value.isNotEmpty()) return
            _isLoading.value = true
            viewModelScope.launch(Dispatchers.IO) {
                _shelves.value = fetch(orderedShelves(greetingPeriod(LocalTime.now().hour)))
                _isLoading.value = false
            }
        }

        private suspend fun fetch(shelves: List<SoriShelf>): List<SoriHomeShelf> =
            coroutineScope {
                shelves
                    .map { shelf ->
                        async {
                            val items =
                                YouTube
                                    .search(context.getString(shelf.query), YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST)
                                    .getOrNull()
                                    ?.items
                                    ?.filterIsInstance<PlaylistItem>()
                                    ?.take(12)
                                    .orEmpty()
                            SoriHomeShelf(context.getString(shelf.title), items)
                        }
                    }.awaitAll()
                    .filter { it.items.isNotEmpty() }
            }
    }
