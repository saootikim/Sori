/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.pages.MoodAndGenres
import com.metrolist.music.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sections for the search screen's browse grid, best source first: YouTube Music's full
 * moods-and-genres page, then the flat list on its Explore page, then Sori's own catalog
 * (which works where those pages are Premium-only, see [SoriBrowseCatalog]).
 */
internal suspend fun loadBrowseSections(
    full: suspend () -> Result<List<MoodAndGenres>>,
    explore: suspend () -> Result<List<MoodAndGenres.Item>>,
    exploreTitle: String,
    curated: () -> List<SoriBrowseSection>,
): List<SoriBrowseSection> {
    full().getOrNull()
        ?.filter { it.items.isNotEmpty() }
        ?.takeIf { it.isNotEmpty() }
        ?.let { sections -> return sections.map { it.toBrowseSection() } }
    explore().getOrNull()
        ?.takeIf { it.isNotEmpty() }
        ?.let { return listOf(MoodAndGenres(exploreTitle, it).toBrowseSection()) }
    return curated()
}

@HiltViewModel
class SoriBrowseViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : ViewModel() {
        private val _sections = MutableStateFlow<List<SoriBrowseSection>?>(null)
        val sections: StateFlow<List<SoriBrowseSection>?> = _sections

        init {
            val exploreTitle = context.getString(R.string.mood_and_genres)
            viewModelScope.launch(Dispatchers.IO) {
                _sections.value =
                    withLeadingTile(
                        loadBrowseSections(
                            full = { YouTube.moodAndGenres() },
                            explore = { YouTube.explore().map { it.moodAndGenres } },
                            exploreTitle = exploreTitle,
                            curated = { SoriBrowseCatalog.sections(context) },
                        ),
                        SoriBrowseCatalog.chartsTile(context),
                    )
            }
        }
    }
