/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.sori.SoriDailyMix
import com.metrolist.music.sori.SoriDailyMixViewModel
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.ui.component.shimmer.ListItemPlaceHolder
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.theme.SoriColors

fun soriMixRoute(number: Int) = "sori_mix/$number"

@Composable
fun dailyMixName(mix: SoriDailyMix) = stringResource(R.string.sori_mix_name, mix.number)

@Composable
fun dailyMixArtists(mix: SoriDailyMix) = stringResource(R.string.sori_mix_artists_more, mix.artistNames.joinToString(", "))

fun PlayerConnection.playMix(
    title: String,
    mix: SoriDailyMix,
    index: Int = 0,
    shuffle: Boolean = false,
) {
    val songs = if (shuffle) mix.songs.shuffled() else mix.songs
    playQueue(ListQueue(title = title, items = songs.map { it.toMediaItem() }, startIndex = if (shuffle) 0 else index))
}

/**
 * A mix's cover: the artist's photo washed toward the cover color at the bottom, with the mix
 * name on it, like streaming apps' daily mixes.
 */
@Composable
fun DailyMixArt(
    mix: SoriDailyMix,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val tint = rememberCoverColor(mix.cover) ?: SoriColors.Violet
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(if (large) 6.dp else 8.dp))
                .background(tint),
    ) {
        AsyncImage(
            model = mix.cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to tint.copy(alpha = 0.95f))),
        )
        Text(
            text = dailyMixName(mix),
            style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(if (large) 16.dp else 10.dp),
        )
    }
}

/** Home card: cover and the artists in the mix. */
@Composable
fun DailyMixCard(
    mix: SoriDailyMix,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(160.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(6.dp),
    ) {
        DailyMixArt(mix, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Text(
            text = dailyMixArtists(mix),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SoriDailyMixScreen(
    navController: NavController,
    number: Int,
    viewModel: SoriDailyMixViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val mixes by viewModel.mixes.collectAsStateWithLifecycle()
    val mix = mixes?.firstOrNull { it.number == number }
    val tint = rememberCoverColor(mix?.cover)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                colors = coverTopBarColors(tint),
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(painter = painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        when {
            mix != null -> {
                val title = dailyMixName(mix)
                LazyColumn(
                    contentPadding =
                        LocalPlayerAwareWindowInsets.current
                            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                            .asPaddingValues(),
                    modifier = Modifier.padding(top = padding.calculateTopPadding()),
                ) {
                    item(key = "header") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .coverGradient(tint)
                                    .padding(top = 8.dp, bottom = 12.dp),
                        ) {
                            DailyMixArt(mix, large = true, modifier = Modifier.size(240.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = dailyMixArtists(mix),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 32.dp),
                            )
                            Text(
                                text = pluralStringResource(R.plurals.n_song, mix.songs.size, mix.songs.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                IconButton(onClick = { playerConnection.playMix(title, mix, shuffle = true) }) {
                                    Icon(painter = painterResource(R.drawable.shuffle), contentDescription = stringResource(R.string.shuffle))
                                }
                                FilledIconButton(
                                    onClick = { playerConnection.playMix(title, mix) },
                                    shape = CircleShape,
                                    colors =
                                        IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                    modifier = Modifier.size(60.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.play),
                                        contentDescription = stringResource(R.string.play),
                                        modifier = Modifier.size(32.dp),
                                    )
                                }
                                Spacer(Modifier.size(48.dp))
                            }
                        }
                    }
                    itemsIndexed(mix.songs, key = { _, song -> "mix_${song.id}" }) { index, song ->
                        val isActive = song.id == mediaMetadata?.id
                        val showMenu = {
                            menuState.show { YouTubeSongMenu(song = song, onDismiss = menuState::dismiss) }
                        }
                        YouTubeListItem(
                            item = song,
                            isActive = isActive,
                            isPlaying = isPlaying,
                            isSwipeable = false,
                            trailingContent = {
                                IconButton(onClick = showMenu) {
                                    Icon(painter = painterResource(R.drawable.more_vert), contentDescription = null)
                                }
                            },
                            modifier =
                                Modifier.combinedClickable(
                                    onClick = {
                                        if (isActive) playerConnection.togglePlayPause() else playerConnection.playMix(title, mix, index)
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showMenu()
                                    },
                                ),
                        )
                    }
                }
            }

            mixes == null ->
                ShimmerHost(modifier = Modifier.padding(padding)) {
                    repeat(8) { ListItemPlaceHolder() }
                }

            else ->
                Text(
                    text = stringResource(R.string.sori_mix_missing),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(padding).padding(32.dp),
                )
        }
    }
}
