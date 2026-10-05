/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.metrolist.music.LocalPlayerConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the player's play button shows: the video's state while the video shows. */
@Composable
fun soriEffectiveIsPlaying(songIsPlaying: Boolean): Boolean {
    val showing by SoriVideoMode.showing.collectAsState()
    val videoPlaying by SoriVideoMode.videoPlaying.collectAsState()
    return if (showing != null) videoPlaying == true else songIsPlaying
}

/**
 * While the video shows, the player's time bar shows the video's position and length, and a
 * position picked on it seeks the video. [sliderPosition] is the bar's drag value (null when not
 * dragging) and [onTime] sets the bar's position and length. The song's come back when the video
 * closes.
 */
@Composable
fun SoriVideoTimeSync(
    sliderPosition: () -> Long?,
    onTime: (positionMs: Long, durationMs: Long) -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val currentOnTime by rememberUpdatedState(onTime)
    LaunchedEffect(playerConnection) {
        // A released drag seeks the video. The bar also seeks the paused song, which does no
        // harm: the song continues from the video's position when the video closes.
        launch {
            var dragged: Long? = null
            snapshotFlow(sliderPosition).collect { drag ->
                if (drag != null) {
                    dragged = drag
                } else {
                    dragged?.let(SoriVideoMode::seek)
                    dragged = null
                }
            }
        }
        // The player writes the song's time to the bar on its own events, so the video's time is
        // written again several times a second rather than only when it changes.
        var showed = false
        while (isActive) {
            if (SoriVideoMode.showing.value != null) {
                showed = true
                val time = SoriVideoMode.videoTime.value
                if (time != null && time.durationMs > 0 && sliderPosition() == null) {
                    currentOnTime(time.positionMs, time.durationMs)
                }
            } else if (showed) {
                showed = false
                val songDuration =
                    playerConnection.mediaMetadata.value?.duration?.takeIf { it > 0 }?.times(1000L)
                        ?: playerConnection.player.duration
                if (songDuration > 0) currentOnTime(playerConnection.player.currentPosition, songDuration)
            }
            delay(200)
        }
    }
}
