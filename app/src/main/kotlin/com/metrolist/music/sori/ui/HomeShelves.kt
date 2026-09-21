/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.models.YTItem
import com.metrolist.music.sori.SoriHomeShelf
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.component.shimmer.GridItemPlaceHolder
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.ui.component.shimmer.TextPlaceholder

/**
 * Sori's search-backed Home shelves: a title and a horizontal row per shelf. Items are drawn by
 * [itemContent], so they look and behave (tap, long-press menu) like Home's other cards.
 */
fun LazyListScope.soriHomeShelves(
    shelves: List<SoriHomeShelf>,
    itemContent: @Composable (YTItem) -> Unit,
) {
    shelves.forEachIndexed { index, shelf ->
        item(key = "sori_shelf_title_$index") {
            NavigationTitle(title = shelf.title)
        }
        item(key = "sori_shelf_list_$index") {
            LazyRow(
                contentPadding =
                    WindowInsets.systemBars
                        .only(WindowInsetsSides.Horizontal)
                        .asPaddingValues(),
            ) {
                items(
                    items = shelf.items.distinctBy { it.id },
                    key = { "sori_shelf_${index}_${it.id}" },
                ) { item ->
                    itemContent(item)
                }
            }
        }
    }
}

/** Placeholder shown while the shelves load, matching Home's own loading shimmer. */
fun LazyListScope.soriHomeShelvesLoading() {
    item(key = "sori_shelves_loading") {
        ShimmerHost {
            repeat(2) {
                TextPlaceholder(
                    height = 36.dp,
                    modifier =
                        Modifier
                            .padding(12.dp)
                            .width(220.dp),
                )
                LazyRow(
                    contentPadding =
                        WindowInsets.systemBars
                            .only(WindowInsetsSides.Horizontal)
                            .asPaddingValues(),
                ) {
                    items(4) { GridItemPlaceHolder() }
                }
            }
        }
    }
}
