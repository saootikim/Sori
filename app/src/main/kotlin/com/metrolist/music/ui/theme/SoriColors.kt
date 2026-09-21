/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Sori brand palette: neutral near-black surfaces, white text, a single coral accent.
 *
 * Content (album art) carries the color; app chrome stays calm and consistent, the way large
 * streaming apps do it. Elevation is tinted white, so raised surfaces read as lighter greys.
 */
object SoriColors {
    val Coral = Color(0xFFFF6B6B)
    val Violet = Color(0xFF7C5CFF)

    val Background = Color(0xFF121212)
    val SurfaceLowest = Color(0xFF0A0A0A)
    val SurfaceLow = Color(0xFF181818)
    val Surface = Color(0xFF1E1E1E)
    val SurfaceHigh = Color(0xFF282828)
    val SurfaceHighest = Color(0xFF333333)
    val SurfaceBright = Color(0xFF3A3A3A)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB3B3B3)
    val Outline = Color(0xFF727272)
    val OutlineVariant = Color(0xFF3E3E3E)
}

fun soriDarkColorScheme(): ColorScheme =
    darkColorScheme(
        primary = SoriColors.Coral,
        onPrimary = Color(0xFF1A0506),
        primaryContainer = Color(0xFF5C1F24),
        onPrimaryContainer = Color(0xFFFFDAD8),
        inversePrimary = Color(0xFFB3261E),
        // Secondary is what upstream uses for subtitles (artist, track count), so it is the
        // neutral secondary-text grey.
        secondary = SoriColors.TextSecondary,
        onSecondary = SoriColors.Background,
        // Selected navigation pill and selected chips stay neutral, the accent is reserved for
        // the primary action on each screen.
        secondaryContainer = Color(0xFF2A2A2A),
        onSecondaryContainer = SoriColors.TextPrimary,
        tertiary = Color(0xFF9D8CFF),
        onTertiary = Color(0xFF1B0F4D),
        tertiaryContainer = Color(0xFF2E2466),
        onTertiaryContainer = Color(0xFFE5DEFF),
        background = SoriColors.Background,
        onBackground = SoriColors.TextPrimary,
        surface = SoriColors.Background,
        onSurface = SoriColors.TextPrimary,
        surfaceVariant = SoriColors.SurfaceHigh,
        onSurfaceVariant = SoriColors.TextSecondary,
        surfaceTint = Color.White,
        inverseSurface = Color(0xFFE6E6E6),
        inverseOnSurface = SoriColors.Background,
        error = Color(0xFFF15E6C),
        onError = Color.Black,
        errorContainer = Color(0xFF5C1A22),
        onErrorContainer = Color(0xFFFFDADC),
        outline = SoriColors.Outline,
        outlineVariant = SoriColors.OutlineVariant,
        scrim = Color.Black,
        surfaceBright = SoriColors.SurfaceBright,
        surfaceDim = SoriColors.Background,
        surfaceContainerLowest = SoriColors.SurfaceLowest,
        surfaceContainerLow = SoriColors.SurfaceLow,
        surfaceContainer = SoriColors.Surface,
        surfaceContainerHigh = SoriColors.SurfaceHigh,
        surfaceContainerHighest = SoriColors.SurfaceHighest,
    )
