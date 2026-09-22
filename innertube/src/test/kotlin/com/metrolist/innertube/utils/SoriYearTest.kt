package com.metrolist.innertube.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoriYearTest {
    @Test
    fun readsTheYearInAnyLanguage() {
        assertEquals(2026, releaseYear("2026년"))
        assertEquals(2025, releaseYear("2025"))
        assertEquals(2025, releaseYear("Single • 2025"))
        assertEquals(2024, releaseYear("2024 年"))
    }

    @Test
    fun noYearIsNull() {
        assertNull(releaseYear("싱글"))
        assertNull(releaseYear("12곡"))
        assertNull(releaseYear(null))
    }
}
