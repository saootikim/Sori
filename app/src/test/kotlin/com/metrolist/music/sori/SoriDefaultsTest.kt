package com.metrolist.music.sori

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.metrolist.music.constants.DarkModeKey
import com.metrolist.music.constants.DynamicThemeKey
import com.metrolist.music.constants.PlayerBackgroundStyle
import com.metrolist.music.constants.PlayerBackgroundStyleKey
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
    fun runsOnlyOncePerVersion() {
        val prefs = mutablePreferencesOf()
        SoriDefaults.applyTo(prefs)
        prefs.remove(DarkModeKey) // e.g. the user reset a setting later

        assertFalse(SoriDefaults.applyTo(prefs))
        assertEquals(null, prefs[DarkModeKey])
    }
}
