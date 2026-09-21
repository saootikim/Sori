package com.metrolist.music.sori

import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoriWebRadioTest {
    private val next = javaClass.getResource("/sori/web_radio_next.json")!!.readText()

    @Test
    fun parsesMixPanels() {
        val songs = parseWebRadio(next)

        assertEquals(listOf("B76TuNFymjk", "Oo3h88vaDWs", "LswcI2jIciw", "8q85sL9T7YM"), songs.map { it.id })
        val wish = songs[1]
        assertEquals("I wish (바라고 바라)", wish.title)
        assertEquals("휘인 Whee In", wish.artists.single().name)
        assertEquals(242, wish.duration)
    }

    private fun song(id: String) = SongItem(id = id, title = id, artists = emptyList(), thumbnail = "")

    @Test
    fun thinRadioIsPaddedAndKeepsGoing() {
        val mixes = mapOf("a" to listOf(song("a"), song("b"), song("c")), "c" to listOf(song("b"), song("d"), song("e")), "e" to listOf(song("a")))
        val extender = SoriRadioExtender { seed -> mixes[seed].orEmpty() }

        val initial = runBlocking { extender.extend(listOf(song("a")), seedVideoId = "a", hasContinuation = false) }
        assertEquals(listOf("a", "b", "c"), initial.map { it.id })
        assertTrue(extender.hasMore())

        assertEquals(listOf("d", "e"), runBlocking { extender.nextPage() }.map { it.id })
        assertTrue(extender.hasMore())
        // The next mix only repeats songs already queued: the radio ends instead of looping.
        assertTrue(runBlocking { extender.nextPage() }.isEmpty())
        assertFalse(extender.hasMore())
    }

    @Test
    fun workingRadiosAreLeftAlone() {
        val extender = SoriRadioExtender { error("should not fetch") }
        val items = (1..10).map { song("s$it") }

        assertEquals(items, runBlocking { extender.extend(items, seedVideoId = "s1", hasContinuation = true) })
        assertFalse(extender.hasMore())
    }

    @Test
    fun gatedRadiosAreRebuiltFromMixes() {
        val extender = SoriRadioExtender { listOf(song("x"), song("y")) }
        val items = (1..5).map { song("s$it") }

        val padded = runBlocking { extender.extend(items, seedVideoId = "s1", hasContinuation = false) }

        // The gated radio's own tail (s2..s5) is replaced by the mix.
        assertEquals(listOf("s1", "x", "y"), padded.map { it.id })
        assertTrue(extender.hasMore())
    }
}
