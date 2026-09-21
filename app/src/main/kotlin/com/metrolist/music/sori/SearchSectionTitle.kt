/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.metrolist.innertube.YouTubeConstants
import com.metrolist.music.R

/**
 * innertube groups untitled search results under fixed English names ("Songs", "Videos",
 * "Top result"...). Those names are shown as section titles, so map them to app strings.
 * Titles YouTube sent already localized return null and are shown as they are.
 */
@StringRes
fun searchSectionTitleRes(title: String): Int? =
    when (title) {
        YouTubeConstants.DEFAULT_TOP_RESULT -> R.string.sori_top_result
        YouTubeConstants.DEFAULT_OTHER_RESULTS -> R.string.sori_other_results
        "Songs" -> R.string.filter_songs
        "Videos" -> R.string.filter_videos
        "Albums" -> R.string.filter_albums
        "Artists" -> R.string.filter_artists
        "Playlists" -> R.string.playlists
        "Podcasts" -> R.string.filter_podcasts
        "Episodes" -> R.string.filter_episodes
        "Profiles" -> R.string.filter_profiles
        else -> null
    }

@Composable
fun searchSectionTitle(title: String): String = searchSectionTitleRes(title)?.let { stringResource(it) } ?: title
