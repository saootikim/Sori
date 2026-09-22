/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori

import android.content.Context
import androidx.annotation.StringRes
import com.metrolist.innertube.models.BrowseEndpoint
import com.metrolist.innertube.pages.MoodAndGenres
import com.metrolist.music.R
import com.metrolist.music.sori.ui.SORI_CHARTS_ROUTE

/** One colored tile on the search screen's browse grid. Opens a YouTube Music page, a Sori screen or runs a search. */
data class SoriBrowseTile(
    val title: String,
    val color: Long,
    val endpoint: BrowseEndpoint? = null,
    val query: String? = null,
    /** Navigation route of a Sori screen. */
    val route: String? = null,
)

data class SoriBrowseSection(
    val title: String,
    val tiles: List<SoriBrowseTile>,
)

/** [tile] first in the first section, whichever source the sections came from. */
fun withLeadingTile(
    sections: List<SoriBrowseSection>,
    tile: SoriBrowseTile,
): List<SoriBrowseSection> {
    val first = sections.firstOrNull() ?: return listOf(SoriBrowseSection("", listOf(tile)))
    return listOf(first.copy(tiles = listOf(tile) + first.tiles)) + sections.drop(1)
}

fun MoodAndGenres.toBrowseSection() =
    SoriBrowseSection(
        title = title,
        tiles = items.map { SoriBrowseTile(title = it.title, color = it.stripeColor, endpoint = it.endpoint) },
    )

/**
 * Sori's own genre/mood tiles, used when YouTube Music's browse pages are unavailable.
 * In Korea (as of 2026-09) those pages answer non-Premium users with "YouTube Music is only
 * available to Premium members", while search still works, so each tile runs a search.
 */
object SoriBrowseCatalog {
    private data class Entry(
        @StringRes val title: Int,
        @StringRes val query: Int,
        val color: Long,
    )

    private val entries =
        listOf(
            Entry(R.string.sori_browse_kpop, R.string.sori_browse_kpop_query, 0xFFE8115B),
            Entry(R.string.sori_browse_ballad, R.string.sori_browse_ballad_query, 0xFF8D67AB),
            Entry(R.string.sori_browse_hiphop, R.string.sori_browse_hiphop_query, 0xFFBA5D07),
            Entry(R.string.sori_browse_rnb, R.string.sori_browse_rnb_query, 0xFF1E3264),
            Entry(R.string.sori_browse_indie, R.string.sori_browse_indie_query, 0xFF608108),
            Entry(R.string.sori_browse_rock, R.string.sori_browse_rock_query, 0xFFE91429),
            Entry(R.string.sori_browse_pop, R.string.sori_browse_pop_query, 0xFF148A08),
            Entry(R.string.sori_browse_jpop, R.string.sori_browse_jpop_query, 0xFFDC148C),
            Entry(R.string.sori_browse_workout, R.string.sori_browse_workout_query, 0xFFFF4632),
            Entry(R.string.sori_browse_focus, R.string.sori_browse_focus_query, 0xFF503750),
            Entry(R.string.sori_browse_drive, R.string.sori_browse_drive_query, 0xFF0D73EC),
            Entry(R.string.sori_browse_sleep, R.string.sori_browse_sleep_query, 0xFF283EA3),
            Entry(R.string.sori_browse_upbeat, R.string.sori_browse_upbeat_query, 0xFFF59B23),
            Entry(R.string.sori_browse_chill, R.string.sori_browse_chill_query, 0xFF477D95),
            Entry(R.string.sori_browse_anime, R.string.sori_browse_anime_query, 0xFF8C1932),
            Entry(R.string.sori_browse_jazz, R.string.sori_browse_jazz_query, 0xFF27856A),
            Entry(R.string.sori_browse_classical, R.string.sori_browse_classical_query, 0xFF7D4B32),
            Entry(R.string.sori_browse_2000s, R.string.sori_browse_2000s_query, 0xFFB49BC8),
        )

    /** Opens Sori's charts screen (YouTube's weekly charts). */
    fun chartsTile(context: Context) =
        SoriBrowseTile(
            title = context.getString(R.string.sori_charts),
            color = 0xFF7C5CFF,
            route = SORI_CHARTS_ROUTE,
        )

    fun sections(context: Context): List<SoriBrowseSection> =
        listOf(
            SoriBrowseSection(
                title = context.getString(R.string.sori_browse_all),
                tiles =
                    entries.map {
                        SoriBrowseTile(
                            title = context.getString(it.title),
                            color = it.color,
                            query = context.getString(it.query),
                        )
                    },
            ),
        )
}
