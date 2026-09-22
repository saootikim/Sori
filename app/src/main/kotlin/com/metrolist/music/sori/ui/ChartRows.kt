/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.sori.ChartMovement
import com.metrolist.music.sori.SoriChartArtist
import com.metrolist.music.sori.SoriChartEntry
import com.metrolist.music.sori.chartWeek
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.ui.menu.YouTubeSongMenu
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** "Weekly · Sep 11 – Sep 17" for a chart ending on [weekEnd]. */
@Composable
fun chartWeekLabel(weekEnd: LocalDate?): String? {
    weekEnd ?: return null
    val pattern = stringResource(R.string.sori_charts_date_pattern)
    val format = remember(pattern) { DateTimeFormatter.ofPattern(pattern) }
    val week = chartWeek(weekEnd)
    return stringResource(R.string.sori_charts_week, week.start.format(format), week.endInclusive.format(format))
}

@Composable
private fun ChartMovementLabel(movement: ChartMovement) {
    val rising = MaterialTheme.colorScheme.primary
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val (text, color) =
        when (movement) {
            ChartMovement.New -> stringResource(R.string.sori_chart_new) to rising
            ChartMovement.Reentry -> stringResource(R.string.sori_chart_reentry) to quiet
            ChartMovement.Same -> "–" to quiet
            is ChartMovement.Up -> "▲${movement.places}" to rising
            is ChartMovement.Down -> "▼${movement.places}" to quiet
        }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
    )
}

/** Rank number with last week's movement underneath. */
@Composable
private fun ChartRank(
    rank: Int,
    movement: ChartMovement,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(40.dp),
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        ChartMovementLabel(movement)
    }
}

/** A chart song: rank and movement, then the regular song row with its menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChartSongRow(
    entry: SoriChartEntry,
    isActive: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val showMenu = {
        menuState.show {
            YouTubeSongMenu(song = entry.song, onDismiss = menuState::dismiss)
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .combinedClickable(
                    onClick = onPlay,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu()
                    },
                ).padding(start = 8.dp),
    ) {
        ChartRank(entry.rank, entry.movement)
        YouTubeListItem(
            item = entry.song,
            isActive = isActive,
            isPlaying = isPlaying,
            isSwipeable = false,
            trailingContent = {
                IconButton(onClick = showMenu) {
                    Icon(painter = painterResource(R.drawable.more_vert), contentDescription = null)
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/** A chart artist: rank and movement, round photo and name. */
@Composable
fun ChartArtistRow(
    artist: SoriChartArtist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
    ) {
        ChartRank(artist.rank, artist.movement)
        Spacer(Modifier.width(8.dp))
        AsyncImage(
            model = artist.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(52.dp)
                    .clip(CircleShape),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
