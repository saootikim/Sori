/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em

/**
 * Sori type scale: Material sizes and line heights (so layouts don't shift), with heavier,
 * tighter headings and semibold titles for a clear hierarchy.
 */
private fun TextStyle.sori(
    weight: FontWeight,
    tracking: Float,
) = copy(fontWeight = weight, letterSpacing = tracking.em)

val SoriTypography: Typography =
    Typography().let { base ->
        base.copy(
            displayLarge = base.displayLarge.sori(FontWeight.ExtraBold, -0.03f),
            displayMedium = base.displayMedium.sori(FontWeight.ExtraBold, -0.03f),
            displaySmall = base.displaySmall.sori(FontWeight.Bold, -0.02f),
            headlineLarge = base.headlineLarge.sori(FontWeight.Bold, -0.02f),
            headlineMedium = base.headlineMedium.sori(FontWeight.Bold, -0.02f),
            headlineSmall = base.headlineSmall.sori(FontWeight.Bold, -0.015f),
            titleLarge = base.titleLarge.sori(FontWeight.Bold, -0.01f),
            titleMedium = base.titleMedium.sori(FontWeight.SemiBold, 0f),
            titleSmall = base.titleSmall.sori(FontWeight.SemiBold, 0f),
            labelLarge = base.labelLarge.sori(FontWeight.SemiBold, 0f),
        )
    }
