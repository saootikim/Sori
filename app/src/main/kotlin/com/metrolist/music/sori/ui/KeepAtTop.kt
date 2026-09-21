/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first

/**
 * Keeps a list at its top until it is first scrolled.
 *
 * Home sections arrive in any order. LazyColumn stays anchored to the first item it showed, so a
 * section that loads late above it (quick access) would open the screen already scrolled down.
 */
@Composable
fun KeepAtTopUntilScrolled(state: LazyListState) {
    // Saveable: coming back to the screen must not undo the user's scroll position.
    var scrolled by rememberSaveable { mutableStateOf(false) }
    if (scrolled) return
    LaunchedEffect(state) {
        // Any real scroll (drag, fling, accessibility) runs as a scroll in progress; the
        // anchoring jump after an insert does not.
        snapshotFlow { state.isScrollInProgress }.first { it }
        scrolled = true
    }
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }.collect { index ->
            if (index != 0) state.requestScrollToItem(0)
        }
    }
}
