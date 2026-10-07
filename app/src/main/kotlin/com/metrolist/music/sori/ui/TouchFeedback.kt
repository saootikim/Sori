/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalHapticFeedback

private const val PressedScale = 0.96f

/**
 * Cards and tiles shrink a little while pressed. Put it first in the chain so the clip and
 * background shrink too. It only watches touches, so it works whoever handles the click, and a
 * scroll that takes over the touch lets the tile go.
 */
fun Modifier.soriPressScale(): Modifier =
    composed {
        var pressed by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(
            targetValue = if (pressed) PressedScale else 1f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "pressScale",
        )
        graphicsLayer {
            scaleX = scale
            scaleY = scale
        }.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                awaitRelease()
                pressed = false
            }
        }
    }

/**
 * A light tick when this is tapped, for the player's main buttons (play, like). It only watches
 * touches, beside whatever click handler the button has; a touch that becomes a scroll or ends
 * outside the button stays silent.
 */
fun Modifier.soriTapHaptic(type: HapticFeedbackType = HapticFeedbackType.ContextClick): Modifier =
    composed {
        val haptic = LocalHapticFeedback.current
        pointerInput(type) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val lifted = awaitRelease() ?: return@awaitEachGesture
                if (lifted.x in 0f..size.width.toFloat() && lifted.y in 0f..size.height.toFloat()) {
                    haptic.performHapticFeedback(type)
                }
            }
        }
    }

/** [soriTapHaptic] with the firmer tick of a like. */
fun Modifier.soriLikeHaptic(): Modifier = soriTapHaptic(HapticFeedbackType.Confirm)

/**
 * Waits until every finger is up. Returns where the last one lifted, or null when a scrolling
 * parent took the moves over first.
 */
private suspend fun AwaitPointerEventScope.awaitRelease(): Offset? {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Final)
        if (event.changes.any { it.isConsumed && it.positionChanged() }) return null
        if (event.changes.none { it.pressed }) return event.changes.firstOrNull()?.position
    }
}
