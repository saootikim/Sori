/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** An artist whose releases are watched: followed, or one of the user's most played. */
data class ReleaseArtist(
    val id: String,
    val name: String,
)

/** An album, single or EP from a watched artist's page. */
@Serializable
data class StoredRelease(
    val id: String,
    val playlistId: String,
    val title: String,
    val thumbnail: String,
    val year: Int? = null,
    val artistId: String,
    val artistName: String,
)

/** A release on the radar; [discovered] is the day it first appeared, null for an artist's back catalog. */
data class SoriRelease(
    val release: StoredRelease,
    val discovered: LocalDate?,
) {
    val isNew: Boolean get() = discovered != null
}

/**
 * What the radar remembers between scans. Artist pages only give a release year, so "new" means
 * "appeared on a page we had already seen": the first scan of each artist is a baseline.
 */
@Serializable
data class ReleaseRadarState(
    val lastScanDay: Long? = null,
    val scannedArtists: Set<String> = emptySet(),
    /** Album id to the epoch day it was first seen; 0 when it was part of an artist's first scan. */
    val firstSeen: Map<String, Long> = emptyMap(),
    /** Everything the last scan found, in watched-artist order. */
    val releases: List<StoredRelease> = emptyList(),
    /** Epoch day of the last "new releases" notification. */
    val notifiedDay: Long = 0,
)

private const val NEW_DAYS = 60
private const val RADAR_SIZE = 30
private const val RECENT_PER_ARTIST = 3
private const val PARALLEL_ARTISTS = 3

/** Scans [artists]' pages; an artist whose page fails keeps what the last scan found. */
suspend fun scanReleases(
    state: ReleaseRadarState,
    artists: List<ReleaseArtist>,
    releasesOf: suspend (ReleaseArtist) -> List<StoredRelease>?,
    today: LocalDate,
): ReleaseRadarState {
    val requests = Semaphore(PARALLEL_ARTISTS)
    val pages =
        coroutineScope {
            artists
                .distinctBy { it.id }
                .map { artist -> async { artist to requests.withPermit { releasesOf(artist) } } }
                .awaitAll()
        }
    val scanned = state.scannedArtists.toMutableSet()
    val firstSeen = state.firstSeen.toMutableMap()
    val releases = mutableListOf<StoredRelease>()
    pages.forEach { (artist, found) ->
        if (found == null) {
            releases += state.releases.filter { it.artistId == artist.id }
            return@forEach
        }
        val known = artist.id in scanned
        found.forEach { release ->
            // Carousels can reshuffle, so only a recent release that shows up later counts as new.
            val recent = (release.year ?: today.year) >= today.year - 1
            if (release.id !in firstSeen) firstSeen[release.id] = if (known && recent) today.toEpochDay() else 0L
        }
        scanned += artist.id
        releases += found
    }
    return state.copy(
        lastScanDay = today.toEpochDay(),
        scannedArtists = scanned,
        firstSeen = firstSeen,
        releases = releases.distinctBy { it.id },
    )
}

/**
 * The radar: releases that appeared in the last two months (newest first), then this year's
 * releases in watched-artist order. Early in the year, last year's still count.
 */
fun ReleaseRadarState.radar(today: LocalDate): List<SoriRelease> {
    val fresh =
        releases
            .mapNotNull { release ->
                val day = firstSeen[release.id]?.takeIf { it > 0 && today.toEpochDay() - it <= NEW_DAYS }
                day?.let { SoriRelease(release, LocalDate.ofEpochDay(it)) }
            }.sortedByDescending { it.discovered }
    val freshIds = fresh.mapTo(mutableSetOf()) { it.release.id }
    val sinceYear = if (today.monthValue <= 3) today.year - 1 else today.year
    val recent =
        releases
            .filter { it.id !in freshIds && (it.year ?: 0) >= sinceYear }
            // Artist pages list newest first; a few per artist keeps one prolific artist from filling the radar.
            .groupBy { it.artistId }
            .values
            .flatMap { it.take(RECENT_PER_ARTIST) }
            .map { SoriRelease(it, discovered = null) }
    return (fresh + recent).take(RADAR_SIZE)
}

/** Releases first seen after [epochDay], for a "new releases" notification. */
fun ReleaseRadarState.newSince(epochDay: Long): List<SoriRelease> =
    releases.mapNotNull { release ->
        firstSeen[release.id]?.takeIf { it > 0 && it > epochDay }?.let { SoriRelease(release, LocalDate.ofEpochDay(it)) }
    }
