package com.metrolist.music.sori

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoriHomeShelvesTest {
    @Test
    fun eachPeriodShowsSixDistinctShelves() {
        for (period in GreetingPeriod.entries) {
            val shelves = orderedShelves(period)
            assertEquals(6, shelves.size)
            assertEquals(shelves.size, shelves.toSet().size)
        }
    }

    @Test
    fun leadShelfFitsTheTimeOfDay() {
        assertEquals(SoriShelf.FOCUS, orderedShelves(GreetingPeriod.MORNING).first())
        assertEquals(SoriShelf.KPOP_HITS, orderedShelves(GreetingPeriod.AFTERNOON).first())
        assertEquals(SoriShelf.CHILL, orderedShelves(GreetingPeriod.EVENING).first())
        assertEquals(SoriShelf.SLEEP, orderedShelves(GreetingPeriod.NIGHT).first())
    }

    @Test
    fun sleepShelfOnlyAtNight() {
        for (period in GreetingPeriod.entries - GreetingPeriod.NIGHT) {
            assertTrue(SoriShelf.SLEEP !in orderedShelves(period))
        }
    }
}
