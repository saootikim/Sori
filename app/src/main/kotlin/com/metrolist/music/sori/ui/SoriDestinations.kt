/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori.ui

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.composable

const val SORI_CHARTS_ROUTE = "sori_charts"

/** Sori's own screens, registered from the app's navigation graph. */
fun NavGraphBuilder.soriDestinations(navController: NavController) {
    composable(SORI_CHARTS_ROUTE) {
        SoriChartsScreen(navController)
    }
    composable(
        route = "sori_mix/{number}",
        arguments = listOf(navArgument("number") { type = NavType.IntType }),
    ) { entry ->
        SoriDailyMixScreen(navController, number = entry.arguments?.getInt("number") ?: 1)
    }
}
