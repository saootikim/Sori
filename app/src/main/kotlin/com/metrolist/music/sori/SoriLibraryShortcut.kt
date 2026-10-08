/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori

import androidx.navigation.NavController
import com.metrolist.music.constants.LibraryFilter

/**
 * Library chips that open a screen instead of filtering the library. Downloaded opens the
 * downloaded songs (the same screen as the library's Downloaded tile). Returns true when [chip]
 * was one of them, so the chip row keeps its selection.
 */
fun soriLibraryShortcut(
    navController: NavController,
    chip: LibraryFilter,
): Boolean =
    when (chip) {
        LibraryFilter.DOWNLOADED -> {
            navController.navigate("auto_playlist/downloaded")
            true
        }
        else -> false
    }
