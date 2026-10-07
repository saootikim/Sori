package com.metrolist.music.sori

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class SoriLibraryRecentsTest {
    private val added = LocalDateTime.of(2026, 10, 1, 12, 0)
    private val addedMillis = added.toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun theLaterOfPlayedAndAddedCounts() {
        assertEquals(addedMillis + 1, soriRecencyMillis(lastPlayed = addedMillis + 1, added = added))
        // Added after it was last played (e.g. bookmarked again): the add is the recent thing.
        assertEquals(addedMillis, soriRecencyMillis(lastPlayed = addedMillis - 1, added = added))
    }

    @Test
    fun neverPlayedFallsBackToWhenItWasAdded() {
        assertEquals(addedMillis, soriRecencyMillis(lastPlayed = null, added = added))
        assertEquals(42L, soriRecencyMillis(lastPlayed = 42L, added = null))
        assertEquals(0L, soriRecencyMillis(lastPlayed = null, added = null))
    }

    @Test
    fun addedTimesUseTheDatabasesClock() {
        // Converters store LocalDateTime as UTC epoch millis, and history events the same way.
        assertEquals(1_790_856_000_000L, soriRecencyMillis(null, added))
    }
}
