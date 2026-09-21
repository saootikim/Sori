/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.metrolist.music.R
import java.time.LocalTime

enum class GreetingPeriod { MORNING, AFTERNOON, EVENING, NIGHT }

fun greetingPeriod(hour: Int): GreetingPeriod =
    when (hour) {
        in 5..11 -> GreetingPeriod.MORNING
        in 12..17 -> GreetingPeriod.AFTERNOON
        in 18..22 -> GreetingPeriod.EVENING
        else -> GreetingPeriod.NIGHT
    }

/** Time-of-day greeting shown as the Home title. */
@Composable
fun soriGreeting(): String =
    stringResource(
        when (greetingPeriod(LocalTime.now().hour)) {
            GreetingPeriod.MORNING -> R.string.sori_greeting_morning
            GreetingPeriod.AFTERNOON -> R.string.sori_greeting_afternoon
            GreetingPeriod.EVENING -> R.string.sori_greeting_evening
            GreetingPeriod.NIGHT -> R.string.sori_greeting_night
        },
    )
