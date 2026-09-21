package com.metrolist.music.sori

import com.metrolist.innertube.models.WatchEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoriQueueFallbackTest {
    @Test
    fun playlistPlayLoadsInOrder() {
        assertEquals(
            SoriQueueFallback.Plan("RDCLAK5uy_abc", shuffle = false),
            SoriQueueFallback.plan(WatchEndpoint(playlistId = "RDCLAK5uy_abc", params = "wAEB")),
        )
    }

    @Test
    fun playlistShuffleLoadsShuffled() {
        assertEquals(
            SoriQueueFallback.Plan("RDCLAK5uy_abc", shuffle = true),
            SoriQueueFallback.plan(WatchEndpoint(playlistId = "RDCLAK5uy_abc", params = "wAEB8gECKAE%3D")),
        )
    }

    @Test
    fun playlistRadioShufflesThePlaylistItself() {
        assertEquals(
            SoriQueueFallback.Plan("RDCLAK5uy_abc", shuffle = true),
            SoriQueueFallback.plan(WatchEndpoint(playlistId = "RDAMPLRDCLAK5uy_abc", params = "wAEB8gECGAE%3D")),
        )
    }

    @Test
    fun songRadiosAndPlainVideosAreLeftAlone() {
        assertNull(SoriQueueFallback.plan(WatchEndpoint(videoId = "v", playlistId = "RDAMVMv")))
        assertNull(SoriQueueFallback.plan(WatchEndpoint(videoId = "v")))
        assertNull(SoriQueueFallback.plan(WatchEndpoint(playlistId = "RDEM79RRQ105L7V61p68gX8ahw")))
    }
}
