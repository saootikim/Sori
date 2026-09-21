/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.music.sori.SoriBrowseSection
import com.metrolist.music.sori.SoriBrowseTile

/** White tile text needs a dark enough background; lighter stripe colors are darkened to this. */
const val MaxTileLuminance = 0.28f

/** YouTube Music's genre stripe color, darkened (hue kept) until white text is readable on it. */
fun browseTileColor(stripeColor: Long): Color {
    var color = Color(stripeColor.toInt()).copy(alpha = 1f)
    while (color.luminance() > MaxTileLuminance) {
        color = Color(red = color.red * 0.9f, green = color.green * 0.9f, blue = color.blue * 0.9f)
    }
    return color
}

/**
 * Spotify-style "browse all" landing for the search screen: genre and mood sections as
 * two-column colored tiles. Adds nothing until [sections] has loaded.
 */
fun LazyListScope.soriBrowseAll(
    sections: List<SoriBrowseSection>?,
    onOpen: (SoriBrowseTile) -> Unit,
) {
    sections?.forEachIndexed { sectionIndex, section ->
        if (section.tiles.isEmpty()) return@forEachIndexed
        item(key = "sori_browse_title_$sectionIndex") {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
            )
        }
        section.tiles.chunked(2).forEachIndexed { rowIndex, row ->
            item(key = "sori_browse_row_${sectionIndex}_$rowIndex") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    row.forEach { tile ->
                        BrowseTile(
                            tile = tile,
                            onClick = { onOpen(tile) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BrowseTile(
    tile: SoriBrowseTile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val base = remember(tile.color) { browseTileColor(tile.color) }
    // Lighter than the tile, so the tilted block reads even when the tile color was already dark.
    val stripe = remember(tile.color) { lerp(Color(tile.color.toInt()).copy(alpha = 1f), Color.White, 0.22f) }
    Box(
        modifier =
            modifier
                .height(96.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .background(base),
    ) {
        // Tilted two-tone block in the corner, echoing the tilted cover art of streaming apps.
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 18.dp, y = 12.dp)
                    .size(68.dp)
                    .rotate(25f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(stripe, stripe.copy(alpha = 0.55f)))),
        )
        Text(
            text = tile.title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(0.78f)
                    .padding(12.dp),
        )
    }
}
