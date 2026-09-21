/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori

import com.metrolist.innertube.YouTube.SearchFilter
import com.metrolist.innertube.pages.SearchResult

/**
 * Runs a filtered search; when the songs filter comes back empty, retries with the videos filter.
 *
 * Where YouTube Music is Premium-only (Korea for free users, 2026-09) the songs filter returns
 * nothing, while music videos are still searchable and play fine. Other filters are unchanged.
 */
suspend fun searchWithSongFallback(
    query: String,
    filter: SearchFilter,
    search: suspend (String, SearchFilter) -> Result<SearchResult>,
): Result<SearchResult> {
    val result = search(query, filter)
    if (filter != SearchFilter.FILTER_SONG || result.getOrNull()?.items?.isNotEmpty() == true) return result
    val videos = search(query, SearchFilter.FILTER_VIDEO)
    return if (videos.getOrNull()?.items?.isNotEmpty() == true) videos else result
}
