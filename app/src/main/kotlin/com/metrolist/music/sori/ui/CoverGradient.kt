/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.ui.theme.extractThemeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The cover's dominant color (null until loaded), for tinting a page header like streaming apps do. */
@Composable
fun rememberCoverColor(imageUrl: String?): Color? {
    val context = LocalContext.current
    var color by remember(imageUrl) { mutableStateOf<Color?>(null) }
    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrBlank()) return@LaunchedEffect
        color =
            withContext(Dispatchers.IO) {
                runCatching {
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(imageUrl)
                            .size(128)
                            .allowHardware(false)
                            .build()
                    context.imageLoader.execute(request).image?.toBitmap()?.extractThemeColor()
                }.getOrNull()
            }
    }
    return color
}

/** Header backdrop fading from the cover color to transparent; fades in once the color is known. */
@Composable
fun Modifier.coverGradient(color: Color?): Modifier {
    val animated by animateColorAsState(
        targetValue = color?.copy(alpha = 0.55f) ?: Color.Transparent,
        animationSpec = tween(durationMillis = 450),
        label = "coverGradient",
    )
    return background(Brush.verticalGradient(listOf(animated, Color.Transparent)))
}
