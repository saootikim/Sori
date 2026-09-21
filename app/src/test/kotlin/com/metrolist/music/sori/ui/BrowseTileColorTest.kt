package com.metrolist.music.sori.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseTileColorTest {
    @Test
    fun darkColorsAreKept() {
        val navy = 0xFF1E3264L
        assertEquals(Color(navy), browseTileColor(navy))
    }

    @Test
    fun lightColorsAreDarkenedForWhiteText() {
        for (stripe in listOf(0xFFFFFFFFL, 0xFFFFE066L, 0xFF9EF0C0L)) {
            val tile = browseTileColor(stripe)
            assertTrue("luminance ${tile.luminance()} for ${stripe.toString(16)}", tile.luminance() <= MaxTileLuminance + 0.01f)
        }
    }

    @Test
    fun hueIsPreservedWhenDarkening() {
        val tile = browseTileColor(0xFFFFE066L) // light yellow
        assertTrue(tile.red > tile.blue && tile.green > tile.blue)
    }
}
