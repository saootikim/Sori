/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.metrolist.music.constants.CropAlbumArtKey
import com.metrolist.music.constants.DarkModeKey
import com.metrolist.music.constants.DynamicThemeKey
import com.metrolist.music.constants.LyricsRomanizeList
import com.metrolist.music.constants.MiniPlayerBackgroundStyle
import com.metrolist.music.constants.MiniPlayerBackgroundStyleKey
import com.metrolist.music.constants.PlayerBackgroundStyle
import com.metrolist.music.constants.PlayerBackgroundStyleKey
import com.metrolist.music.constants.SliderStyle
import com.metrolist.music.constants.SliderStyleKey
import com.metrolist.music.constants.UseNewPlayerDesignKey
import com.metrolist.music.ui.screens.settings.DarkMode
import com.metrolist.music.ui.screens.settings.defaultList as romanizeLanguages

/**
 * Sori's opinionated defaults (always-dark, fixed brand colors, classic now-playing layout).
 *
 * Upstream repeats `defaultValue = ...` for these keys across many files, so instead of editing
 * all of them (and conflicting on every upstream merge) Sori seeds the preferences once.
 * A key is only written when it is absent, so a choice the user already made is never touched.
 */
object SoriDefaults {
    /** Bump when adding defaults; each version is applied once. */
    const val VERSION = 5

    val VersionKey = intPreferencesKey("sori_defaults_version")

    // Plain kotlin.Pair: `key to value` would resolve to DataStore's Preferences.Pair.
    private val defaults: List<Pair<Preferences.Key<*>, Any>> =
        listOf(
            Pair(DarkModeKey, DarkMode.ON.name),
            Pair(DynamicThemeKey, false),
            Pair(UseNewPlayerDesignKey, false),
            Pair(PlayerBackgroundStyleKey, PlayerBackgroundStyle.GRADIENT.name),
            // v2: the mini player takes the album-art color, like the full player.
            Pair(MiniPlayerBackgroundStyleKey, MiniPlayerBackgroundStyle.GRADIENT.name),
            // v3: fill square art slots instead of letterboxing 16:9 video thumbnails.
            Pair(CropAlbumArtKey, true),
            // v4: thin seek bar.
            Pair(SliderStyleKey, SliderStyle.SLIM.name),
            // v5: Korean users read Hangul; romanized lines under Korean lyrics are noise. The
            // preference is the full list in the settings screen's format, other languages as upstream.
            Pair(
                LyricsRomanizeList,
                romanizeLanguages.joinToString(",") { (lang, on) -> "$lang:${on && lang != "Korean"}" },
            ),
        )

    /** Seeds missing defaults. Returns true when [prefs] was changed. */
    fun applyTo(prefs: MutablePreferences): Boolean {
        if ((prefs[VersionKey] ?: 0) >= VERSION) return false
        defaults.forEach { (key, value) ->
            if (key !in prefs) {
                @Suppress("UNCHECKED_CAST")
                prefs[key as Preferences.Key<Any>] = value
            }
        }
        prefs[VersionKey] = VERSION
        return true
    }
}
