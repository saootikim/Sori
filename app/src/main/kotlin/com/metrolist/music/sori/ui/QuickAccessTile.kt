/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori.ui

import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.music.R
import com.metrolist.music.ui.component.ItemThumbnail
import com.metrolist.music.ui.theme.SoriColors
import com.metrolist.music.ui.utils.resize

private val TileShape = RoundedCornerShape(6.dp)

/** Height of one quick-access tile, excluding the 4dp gutter HomeScreen adds around it. */
val QuickAccessTileHeight = 56.dp

/**
 * Wide quick-access tile (art on the left, title on the right) used for Home's top grid.
 * Drop-in replacement for SpeedDialGridItem: same inputs, the caller supplies click handling
 * through [modifier].
 */
@Composable
fun SoriQuickAccessTile(
    item: YTItem,
    isPinned: Boolean,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxSize()
                .clip(TileShape)
                .then(modifier)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        ItemThumbnail(
            thumbnailUrl = item.thumbnail?.resize(144, 144),
            isActive = isActive,
            isPlaying = isPlaying,
            shape = if (item is ArtistItem) CircleShape else RoundedCornerShape(0.dp),
            modifier =
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .then(if (item is ArtistItem) Modifier.padding(6.dp) else Modifier),
        )
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelLarge,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
        )
        if (isPinned) {
            Icon(
                painter = painterResource(R.drawable.ic_push_pin),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .padding(end = 8.dp)
                        .size(14.dp),
            )
        }
    }
}

/** Wide "surprise me" tile, replacing RandomizeGridItem in the quick-access grid. */
@Composable
fun SoriShuffleTile(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Only animate while loading, so the idle grid doesn't redraw every frame.
    val rotation =
        if (isLoading) {
            val spin by rememberInfiniteTransition(label = "shuffleSpin").animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 900)),
                label = "shuffleRotation",
            )
            spin
        } else {
            0f
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxSize()
                .clip(TileShape)
                .clickable(onClick = onClick)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .background(Brush.linearGradient(listOf(SoriColors.Violet, SoriColors.Coral))),
        ) {
            Icon(
                painter = painterResource(R.drawable.shuffle),
                contentDescription = null,
                tint = Color.White,
                modifier =
                    Modifier
                        .size(24.dp)
                        .rotate(rotation),
            )
        }
        Text(
            text = stringResource(R.string.sori_shuffle_pick),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}
