/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori.ui

import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.ui.theme.extractThemeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val COVER_TINT_ALPHA = 0.55f

// Header and top bar ask for the same cover, and pages are revisited: extract each color once.
private val coverColors = LruCache<String, Color>(64)

/** The cover's dominant color (null until loaded), for tinting a page header like streaming apps do. */
@Composable
fun rememberCoverColor(imageUrl: String?): Color? {
    val context = LocalContext.current
    var color by remember(imageUrl) { mutableStateOf(imageUrl?.let { coverColors.get(it) }) }
    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrBlank() || color != null) return@LaunchedEffect
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
            }?.also { coverColors.put(imageUrl, it) }
    }
    return color
}

/** The translucent cover tint, fading in once the color is known. */
@Composable
private fun animatedCoverTint(color: Color?): Color {
    val tint by animateColorAsState(
        targetValue = color?.copy(alpha = COVER_TINT_ALPHA) ?: Color.Transparent,
        animationSpec = tween(durationMillis = 450),
        label = "coverTint",
    )
    return tint
}

/** Header backdrop fading from the cover color to transparent. */
@Composable
fun Modifier.coverGradient(color: Color?): Modifier =
    background(Brush.verticalGradient(listOf(animatedCoverTint(color), Color.Transparent)))

/** Top bar in the color the [coverGradient] header starts with, so bar and header read as one. */
@Composable
fun coverTopBarColors(color: Color?): TopAppBarColors {
    val container = animatedCoverTint(color).compositeOver(MaterialTheme.colorScheme.surface)
    return TopAppBarDefaults.topAppBarColors(containerColor = container, scrolledContainerColor = container)
}
