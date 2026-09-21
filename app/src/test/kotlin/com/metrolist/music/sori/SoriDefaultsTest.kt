package com.metrolist.music.sori

import androidx.datastore.preferences.core.mutablePreferencesOf
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoriDefaultsTest {
    @Test
    fun freshInstallGetsSoriDefaults() {
        val prefs = mutablePreferencesOf()

        assertTrue(SoriDefaults.applyTo(prefs))

        assertEquals(DarkMode.ON.name, prefs[DarkModeKey])
        assertEquals(false, prefs[DynamicThemeKey])
        assertEquals(false, prefs[UseNewPlayerDesignKey])
        assertEquals(PlayerBackgroundStyle.GRADIENT.name, prefs[PlayerBackgroundStyleKey])
        assertEquals(SoriDefaults.VERSION, prefs[SoriDefaults.VersionKey])
    }

    @Test
    fun userChoicesAreNeverOverwritten() {
        val prefs =
            mutablePreferencesOf(
                DarkModeKey to DarkMode.OFF.name,
                DynamicThemeKey to true,
            )

        SoriDefaults.applyTo(prefs)

        assertEquals(DarkMode.OFF.name, prefs[DarkModeKey])
        assertEquals(true, prefs[DynamicThemeKey])
        // Keys the user never touched still get Sori defaults.
        assertEquals(false, prefs[UseNewPlayerDesignKey])
    }

    @Test
    fun freshInstallGetsGradientMiniPlayer() {
        val prefs = mutablePreferencesOf()
        SoriDefaults.applyTo(prefs)
        assertEquals(MiniPlayerBackgroundStyle.GRADIENT.name, prefs[MiniPlayerBackgroundStyleKey])
        assertEquals(true, prefs[CropAlbumArtKey])
        assertEquals(SliderStyle.SLIM.name, prefs[SliderStyleKey])
    }

    @Test
    fun koreanLyricsAreNotRomanized() {
        val prefs = mutablePreferencesOf()
        SoriDefaults.applyTo(prefs)

        val languages = prefs[LyricsRomanizeList]!!.split(",").associate { it.substringBefore(":") to it.substringAfter(":").toBoolean() }
        assertEquals(false, languages["Korean"])
        // Every other language keeps upstream's default: a missing entry would read as disabled.
        assertEquals(true, languages["Japanese"])
        assertEquals(true, languages["Chinese"])
        assertEquals(com.metrolist.music.ui.screens.settings.defaultList.size, languages.size)
    }

    @Test
    fun upgradeFromV1OnlyAddsNewDefaults() {
        // A v1 user who later switched to light mode keeps it; the v2 key is added.
        val prefs =
            mutablePreferencesOf(
                SoriDefaults.VersionKey to 1,
                DarkModeKey to DarkMode.OFF.name,
            )

        assertTrue(SoriDefaults.applyTo(prefs))

        assertEquals(DarkMode.OFF.name, prefs[DarkModeKey])
        assertEquals(MiniPlayerBackgroundStyle.GRADIENT.name, prefs[MiniPlayerBackgroundStyleKey])
        assertEquals(SoriDefaults.VERSION, prefs[SoriDefaults.VersionKey])
    }

    @Test
    fun runsOnlyOncePerVersion() {
        val prefs = mutablePreferencesOf()
        SoriDefaults.applyTo(prefs)
        prefs.remove(DarkModeKey) // e.g. the user reset a setting later

        assertFalse(SoriDefaults.applyTo(prefs))
        assertEquals(null, prefs[DarkModeKey])
    }
}
