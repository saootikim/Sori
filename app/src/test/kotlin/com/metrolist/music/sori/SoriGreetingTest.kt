package com.metrolist.music.sori

import org.junit.Assert.assertEquals
import org.junit.Test

class SoriGreetingTest {
    @Test
    fun mapsHoursToPeriods() {
        assertEquals(GreetingPeriod.NIGHT, greetingPeriod(0))
        assertEquals(GreetingPeriod.NIGHT, greetingPeriod(4))
        assertEquals(GreetingPeriod.MORNING, greetingPeriod(5))
        assertEquals(GreetingPeriod.MORNING, greetingPeriod(11))
        assertEquals(GreetingPeriod.AFTERNOON, greetingPeriod(12))
        assertEquals(GreetingPeriod.AFTERNOON, greetingPeriod(17))
        assertEquals(GreetingPeriod.EVENING, greetingPeriod(18))
        assertEquals(GreetingPeriod.EVENING, greetingPeriod(22))
        assertEquals(GreetingPeriod.NIGHT, greetingPeriod(23))
    }
}
