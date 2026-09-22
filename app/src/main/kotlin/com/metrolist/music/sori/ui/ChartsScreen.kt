/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.sori.ChartLoad
import com.metrolist.music.sori.SoriChartArtist
import com.metrolist.music.sori.SoriChartEntry
import com.metrolist.music.sori.SoriChartsViewModel
import com.metrolist.music.ui.component.ChipsRow
import com.metrolist.music.ui.component.shimmer.ListItemPlaceHolder
import com.metrolist.music.ui.component.shimmer.ShimmerHost

private enum class ChartTab { KOREA, GLOBAL, ARTISTS }

/** Plays [entries] from [index] as one queue; shuffles the whole chart when [shuffle] is set. */
fun PlayerConnection.playChart(
    title: String,
    entries: List<SoriChartEntry>,
    index: Int = 0,
    shuffle: Boolean = false,
) {
    val songs = entries.map { it.song }.let { if (shuffle) it.shuffled() else it }
    playQueue(ListQueue(title = title, items = songs.map { it.toMediaItem() }, startIndex = if (shuffle) 0 else index))
}

/** YouTube's weekly charts: Korea and global top songs, and Korea's top artists. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoriChartsScreen(
    navController: NavController,
    viewModel: SoriChartsViewModel = hiltViewModel(),
) {
    var tab by rememberSaveable { mutableStateOf(ChartTab.KOREA) }
    val korea by viewModel.korea.collectAsStateWithLifecycle()
    val global by viewModel.global.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sori_charts)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(painter = painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            ChipsRow(
                chips =
                    listOf(
                        ChartTab.KOREA to stringResource(R.string.sori_charts_korea),
                        ChartTab.GLOBAL to stringResource(R.string.sori_charts_global),
                        ChartTab.ARTISTS to stringResource(R.string.sori_charts_artists),
                    ),
                currentValue = tab,
                onValueUpdate = { tab = it },
            )
            when (tab) {
                ChartTab.KOREA -> ChartSongList(korea, stringResource(R.string.sori_charts_korea_top), viewModel::load)
                ChartTab.GLOBAL -> ChartSongList(global, stringResource(R.string.sori_charts_global_top), viewModel::load)
                ChartTab.ARTISTS ->
                    ChartArtistList(artists, viewModel::load) { channelId ->
                        navController.navigate("artist/$channelId")
                    }
            }
        }
    }
}

@Composable
private fun ChartSongList(
    load: ChartLoad<SoriChartEntry>,
    queueTitle: String,
    onRetry: () -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    when (load) {
        ChartLoad.Loading -> ChartLoading()
        ChartLoad.Failed -> ChartError(onRetry)
        is ChartLoad.Loaded -> {
            val entries = load.chart.entries
            LazyColumn(
                contentPadding =
                    LocalPlayerAwareWindowInsets.current
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                        .asPaddingValues(),
            ) {
                item(key = "header") {
                    ChartHeader(
                        weekLabel = chartWeekLabel(load.chart.weekEnd),
                        onPlay = { playerConnection.playChart(queueTitle, entries) },
                        onShuffle = { playerConnection.playChart(queueTitle, entries, shuffle = true) },
                    )
                }
                itemsIndexed(entries, key = { _, entry -> "chart_${entry.rank}_${entry.song.id}" }) { index, entry ->
                    val isActive = entry.song.id == mediaMetadata?.id
                    ChartSongRow(
                        entry = entry,
                        isActive = isActive,
                        isPlaying = isPlaying,
                        onPlay = {
                            if (isActive) playerConnection.togglePlayPause() else playerConnection.playChart(queueTitle, entries, index)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartArtistList(
    load: ChartLoad<SoriChartArtist>,
    onRetry: () -> Unit,
    onOpenArtist: (String) -> Unit,
) {
    when (load) {
        ChartLoad.Loading -> ChartLoading()
        ChartLoad.Failed -> ChartError(onRetry)
        is ChartLoad.Loaded ->
            LazyColumn(
                contentPadding =
                    LocalPlayerAwareWindowInsets.current
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                        .asPaddingValues(),
            ) {
                item(key = "header") {
                    ChartHeader(weekLabel = chartWeekLabel(load.chart.weekEnd))
                }
                items(load.chart.entries, key = { "chart_artist_${it.rank}_${it.name}" }) { artist ->
                    ChartArtistRow(
                        artist = artist,
                        onClick = { artist.channelId?.let(onOpenArtist) },
                    )
                }
            }
    }
}

/** Chart source and week, with play and shuffle for song charts. */
@Composable
private fun ChartHeader(
    weekLabel: String?,
    onPlay: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.sori_charts_source),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            weekLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        onShuffle?.let {
            IconButton(onClick = it) {
                Icon(painter = painterResource(R.drawable.shuffle), contentDescription = stringResource(R.string.shuffle))
            }
        }
        onPlay?.let {
            Spacer(Modifier.size(4.dp))
            FilledIconButton(
                onClick = it,
                shape = CircleShape,
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                modifier = Modifier.size(52.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = stringResource(R.string.play),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun ChartLoading() {
    ShimmerHost {
        repeat(10) { ListItemPlaceHolder() }
    }
}

@Composable
private fun ChartError(onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(32.dp),
    ) {
        Text(
            text = stringResource(R.string.sori_charts_load_failed),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}
