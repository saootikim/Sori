/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.queues

import androidx.media3.common.MediaItem
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.sori.SoriQueueFallback
import com.metrolist.music.sori.SoriRadioExtender
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class YouTubeQueue(
    private var endpoint: WatchEndpoint,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    private var continuation: String? = null
    private val soriRadio = SoriRadioExtender() // Sori: keeps thin song radios going
    private var retryCount = 0
    private val maxRetries = 3

    private class EmptyRadioQueueException : IllegalStateException()

    override suspend fun getInitialStatus(): Queue.Status {
        return withContext(IO) {
            var lastException: Throwable? = null

            if (endpoint.videoId != null && endpoint.playlistId == null) {
                endpoint = WatchEndpoint(
                    videoId = endpoint.videoId,
                    playlistId = "RDAMVM${endpoint.videoId}"
                )
            }

            val isRadioRequest =
                endpoint.playlistId?.startsWith("RDAMVM") == true ||
                (endpoint.videoId != null && endpoint.playlistId == null)

            for (attempt in 0..maxRetries) {
                try {
                    val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
                    
                    var items = nextResult.items
                    val relEndpoint = nextResult.relatedEndpoint
                    
                    // Sori: rebuild a gated song radio from youtube.com mixes. Free accounts in Korea
                    // get a few unrelated songs; anonymous users only the seed song, and the retry
                    // with a bare video id below fails outright, so this must run before it.
                    if (isRadioRequest) {
                        items =
                            soriRadio.extend(
                                items,
                                seedVideoId = endpoint.videoId ?: items.firstOrNull()?.id,
                                hasContinuation = nextResult.continuation != null,
                            )
                    }

                    if (isRadioRequest && continuation == null && items.size <= 1) {
                        if (endpoint.playlistId?.startsWith("RDAMVM") == true) {
                            throw EmptyRadioQueueException()
                        } else if (relEndpoint != null) {
                            val relatedPage = YouTube.related(relEndpoint).getOrNull()
                            if (relatedPage != null && relatedPage.songs.isNotEmpty()) {
                                val relatedSongs = relatedPage.songs.filter { it.id != endpoint.videoId }
                                items = items + relatedSongs
                            }
                        }
                    }

                    // Sori: a playlist queue with at most one song is gated (Premium-only for free
                    // users in Korea); build it from the youtube.com playlist instead.
                    if (!isRadioRequest && continuation == null && items.size <= 1) {
                        SoriQueueFallback.initialStatus(endpoint)?.let { return@withContext it }
                    }

                    endpoint = nextResult.endpoint
                    continuation = nextResult.continuation
                    retryCount = 0
                    return@withContext Queue.Status(
                        title = nextResult.title,
                        items = items.map { it.toMediaItem() },
                        mediaItemIndex = nextResult.currentIndex ?: 0,
                    )
                } catch (e: Exception) {
                    lastException = e
                    // Sori: gated playlists can also fail outright; try the youtube.com playlist.
                    if (!isRadioRequest && attempt == 0) {
                        SoriQueueFallback.initialStatus(endpoint)?.let { return@withContext it }
                    }
                    if (
                        e is EmptyRadioQueueException &&
                        endpoint.playlistId?.startsWith("RDAMVM") == true &&
                        endpoint.videoId != null
                    ) {
                        endpoint = WatchEndpoint(videoId = endpoint.videoId)
                        // It will loop again and try with just videoId
                    }
                }
            }
            throw lastException ?: Exception("Failed to get initial status")
        }
    }

    override fun hasNextPage(): Boolean = soriRadio.hasMore() || continuation != null

    override suspend fun nextPage(): List<MediaItem> {
        return withContext(IO) {
            if (soriRadio.hasMore()) {
                return@withContext soriRadio.nextPage().map { it.toMediaItem() } // Sori
            }
            var lastException: Throwable? = null

            for (attempt in 0..maxRetries) {
                try {
                    val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
                    endpoint = nextResult.endpoint
                    continuation = nextResult.continuation
                    retryCount = 0
                    return@withContext nextResult.items.map { it.toMediaItem() }
                } catch (e: Exception) {
                    lastException = e
                    retryCount++
                    if (retryCount >= maxRetries) {
                        continuation = null // Stop trying to load more
                    }
                }
            }
            throw lastException ?: Exception("Failed to get next page")
        }
    }

    companion object {
        /**
         * Creates a radio queue based on a song.
         * Explicitly requests the RDAMVM playlist to trigger automotive/radio mixing.
         */
        fun radio(song: MediaMetadata): YouTubeQueue {
            return YouTubeQueue(
                WatchEndpoint(
                    videoId = song.id,
                    playlistId = "RDAMVM${song.id}"
                ),
                song
            )
        }
    }
}
