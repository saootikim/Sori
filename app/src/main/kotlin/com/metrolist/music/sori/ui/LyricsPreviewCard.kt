/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.ShowIntervalIndicatorKey
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.metrolist.music.di.LyricsHelperEntryPoint
import com.metrolist.music.sori.PreviewLyrics
import com.metrolist.music.sori.soriLyricsCardColor
import com.metrolist.music.sori.soriPreviewCurrentIndex
import com.metrolist.music.sori.soriPreviewLyrics
import com.metrolist.music.ui.component.BottomSheetState
import com.metrolist.music.utils.rememberPreference
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * The portrait player made scrollable, so a card can sit under it. [page] is the player as it
 * was, laid out at exactly the screen's height, and [below] follows it. Dragging down at the top
 * still collapses the player through the sheet's nested scroll connection. While [scrollable] is
 * false (the full lyrics show), the page scrolls back to the top and [below] goes away.
 * [stripHeight] is the queue strip drawn over the bottom of the player: nothing scrolls into view
 * under it.
 */
@Composable
fun SoriPlayerScroll(
    state: BottomSheetState,
    scrollable: Boolean,
    stripHeight: Dp,
    below: @Composable () -> Unit,
    page: @Composable () -> Unit,
) {
    val scrollState = rememberScrollState()
    // The getter makes a new connection, with its own state, on every access.
    val sheetConnection = remember(state) { state.preUpPostDownNestedScrollConnection }
    var showBelow by remember { mutableStateOf(scrollable) }
    LaunchedEffect(scrollable) {
        if (scrollable) {
            showBelow = true
        } else {
            // Removing the card first would make the scroll jump; scroll up, then remove it.
            scrollState.animateScrollTo(0)
            showBelow = false
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pageHeight = maxHeight
        Column(
            Modifier
                .fillMaxSize()
                .then(if (scrollable) Modifier.clipAboveStrip(stripHeight).nestedScroll(sheetConnection) else Modifier)
                .verticalScroll(scrollState, enabled = scrollable, overscrollEffect = null),
        ) {
            Box(Modifier.fillMaxWidth().height(pageHeight)) { page() }
            if (showBelow) below()
        }
    }
}

private fun Modifier.clipAboveStrip(stripHeight: Dp) =
    drawWithContent {
        clipRect(bottom = size.height - stripHeight.toPx()) { this@drawWithContent.drawContent() }
    }

// Songs whose lyrics no provider has, so the card stops asking again this session. Not saved:
// an offline phone gets the same answer, and a saved one would stick.
private val noLyrics = mutableStateMapOf<String, Boolean>()

private sealed interface CardContent {
    data object Loading : CardContent

    data object Absent : CardContent

    data class Ready(
        val lyrics: PreviewLyrics,
    ) : CardContent
}

private val BodyHeight = 216.dp
private val Lip = 14.dp

/**
 * Spotify's lyrics preview: a card in the album's color with the lines around the one being sung,
 * following playback. Its top edge shows above the queue strip as a hint; tapping it opens the full
 * lyrics. Hidden for songs without lyrics. Fetches the song's lyrics itself, since otherwise only
 * the full lyrics view does.
 */
@Composable
fun SoriLyricsPreviewCard(
    positionProvider: () -> Long,
    accent: Color?,
    stripHeight: Dp,
    onOpenLyrics: () -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current
    val context = LocalContext.current
    val metadata by playerConnection.mediaMetadata.collectAsState()
    val lyricsEntity by playerConnection.currentLyrics.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState()
    val showGaps by rememberPreference(ShowIntervalIndicatorKey, true)
    val songId = metadata?.id
    // Right after a song change these can still hold the previous song's rows.
    val rawLyrics = lyricsEntity?.takeIf { it.id == songId }?.lyrics
    val offset = currentSong?.song?.takeIf { it.id == songId }?.lyricsOffset?.toLong() ?: 0L

    LaunchedEffect(songId, rawLyrics == null) {
        val song = metadata ?: return@LaunchedEffect
        if (rawLyrics != null || noLyrics.containsKey(song.id) || !context.isOnline()) return@LaunchedEffect
        delay(1000)
        val fetched =
            withContext(Dispatchers.IO) {
                runCatching {
                    if (database.lyrics(song.id).first() != null) {
                        null
                    } else {
                        EntryPointAccessors
                            .fromApplication(context.applicationContext, LyricsHelperEntryPoint::class.java)
                            .lyricsHelper()
                            .getLyrics(song)
                    }
                }.getOrNull()
            } ?: return@LaunchedEffect
        if (fetched.lyrics == LYRICS_NOT_FOUND) {
            if (context.isOnline()) noLyrics[song.id] = true
        } else {
            withContext(Dispatchers.IO) { database.query { upsert(LyricsEntity(song.id, fetched.lyrics, fetched.provider)) } }
        }
    }

    val content by produceState<CardContent>(CardContent.Loading, songId, rawLyrics, showGaps, noLyrics[songId]) {
        value =
            when {
                rawLyrics != null ->
                    withContext(Dispatchers.Default) { soriPreviewLyrics(rawLyrics, showGaps) }
                        ?.let { CardContent.Ready(it) } ?: CardContent.Absent
                noLyrics[songId] == true -> CardContent.Absent
                else -> CardContent.Loading
            }
    }
    // While the next song's lyrics load, the card stays as it was instead of blinking.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(content) {
        when (content) {
            is CardContent.Ready -> visible = true
            CardContent.Absent -> visible = false
            CardContent.Loading -> Unit
        }
    }
    val lyrics = (content as? CardContent.Ready)?.lyrics
    val cardColor = remember(accent) { soriLyricsCardColor(accent) }
    val overlap = stripHeight + Lip

    Box(
        Modifier.layout { measurable, constraints ->
            // Lifted so the card's top edge shows in the gap above the queue strip.
            val overlapPx = overlap.roundToPx()
            val placeable = measurable.measure(constraints)
            layout(placeable.width, (placeable.height - overlapPx).coerceAtLeast(0)) {
                placeable.place(0, -overlapPx)
            }
        },
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            Column {
                Column(
                    Modifier
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardColor)
                        .clickable(onClick = onOpenLyrics)
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.sori_lyrics_preview),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.sori_lyrics_show_all),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black,
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White)
                                    .clickable(onClick = onOpenLyrics)
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    PreviewLines(lyrics, offset, positionProvider)
                }
                Spacer(Modifier.height(stripHeight + 16.dp))
            }
        }
    }
}

@Composable
private fun PreviewLines(
    lyrics: PreviewLyrics?,
    offset: Long,
    positionProvider: () -> Long,
) {
    val lines = lyrics?.lines.orEmpty()
    val synced = lyrics?.synced == true
    // Only a change of line recomposes, not every position update.
    val currentIndex by remember(lines, offset, synced) {
        derivedStateOf { if (synced) soriPreviewCurrentIndex(lines, positionProvider() + offset) else -1 }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(lines, currentIndex) {
        val target = if (synced) (currentIndex - 1).coerceAtLeast(0) else 0
        if (abs(listState.firstVisibleItemIndex - target) > 3) {
            listState.scrollToItem(target)
        } else {
            listState.animateScrollToItem(target)
        }
    }
    // All lines are bold and only their color changes: a weight change would rewrap and jump.
    LazyColumn(
        state = listState,
        userScrollEnabled = false,
        modifier = Modifier.fillMaxWidth().height(BodyHeight),
    ) {
        itemsIndexed(lines) { index, line ->
            Text(
                text = line.text,
                fontSize = 21.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                color =
                    when {
                        !synced -> Color.White.copy(alpha = 0.85f)
                        index == currentIndex -> Color.White
                        else -> Color.White.copy(alpha = 0.45f)
                    },
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}

private fun Context.isOnline(): Boolean {
    val connectivity = getSystemService(ConnectivityManager::class.java) ?: return true
    val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
