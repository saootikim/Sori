/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.ListItemHeight
import com.metrolist.music.sori.ChartLoad
import com.metrolist.music.sori.SoriChart
import com.metrolist.music.sori.SoriChartEntry
import com.metrolist.music.sori.SoriChartsViewModel
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.utils.SnapLayoutInfoProvider

private const val CHART_SHELF_SIZE = 20

/** Everything Sori adds to Home below quick access. */
data class SoriDiscovery(
    val koreaChart: SoriChart<SoriChartEntry>?,
)

@Composable
fun rememberSoriDiscovery(): SoriDiscovery {
    val charts: SoriChartsViewModel = hiltViewModel()
    val korea by charts.korea.collectAsStateWithLifecycle()
    return SoriDiscovery(koreaChart = (korea as? ChartLoad.Loaded)?.chart)
}

/** Sori's Home shelves, meant to sit right under quick access. */
fun LazyListScope.soriDiscoveryShelves(
    discovery: SoriDiscovery,
    onNavigate: (String) -> Unit,
) {
    discovery.koreaChart?.takeIf { it.entries.isNotEmpty() }?.let { chart ->
        item(key = "sori_chart_title") {
            NavigationTitle(
                title = stringResource(R.string.sori_charts_this_week),
                label = stringResource(R.string.sori_charts_source),
                onClick = { onNavigate(SORI_CHARTS_ROUTE) },
            )
        }
        item(key = "sori_chart_grid") {
            ChartShelf(chart.entries, stringResource(R.string.sori_charts_korea_top))
        }
    }
}

/** The chart's top songs in four rows that page sideways, like Home's quick picks. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChartShelf(
    entries: List<SoriChartEntry>,
    queueTitle: String,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val widthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
        val itemWidth = maxWidth * widthFactor
        val gridState = rememberLazyGridState()
        val snapProvider =
            remember(gridState) {
                SnapLayoutInfoProvider(
                    lazyGridState = gridState,
                    positionInLayout = { layoutSize, itemSize -> layoutSize * widthFactor / 2f - itemSize / 2f },
                )
            }
        LazyHorizontalGrid(
            state = gridState,
            rows = GridCells.Fixed(4),
            flingBehavior = rememberSnapFlingBehavior(snapProvider),
            contentPadding =
                WindowInsets.systemBars
                    .only(WindowInsetsSides.Horizontal)
                    .asPaddingValues(),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(ListItemHeight * 4),
        ) {
            itemsIndexed(entries.take(CHART_SHELF_SIZE), key = { _, entry -> "sori_chart_${entry.song.id}" }) { index, entry ->
                val isActive = entry.song.id == mediaMetadata?.id
                ChartSongRow(
                    entry = entry,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    onPlay = {
                        if (isActive) playerConnection.togglePlayPause() else playerConnection.playChart(queueTitle, entries, index)
                    },
                    modifier = Modifier.width(itemWidth),
                )
            }
        }
    }
}
