/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori

import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

enum class SoriChartRegion(
    val code: String,
) {
    KOREA("kr"),
    GLOBAL("global"),
}

/** Where a chart entry moved since last week. */
sealed interface ChartMovement {
    data object New : ChartMovement

    /** Back on the chart after dropping off. */
    data object Reentry : ChartMovement

    data object Same : ChartMovement

    data class Up(
        val places: Int,
    ) : ChartMovement

    data class Down(
        val places: Int,
    ) : ChartMovement
}

fun chartMovement(
    rank: Int,
    previousRank: Int?,
    weeksOnChart: Int,
): ChartMovement =
    when {
        previousRank == null -> if (weeksOnChart <= 1) ChartMovement.New else ChartMovement.Reentry
        previousRank > rank -> ChartMovement.Up(previousRank - rank)
        previousRank < rank -> ChartMovement.Down(rank - previousRank)
        else -> ChartMovement.Same
    }

data class SoriChartEntry(
    val rank: Int,
    val previousRank: Int?,
    val weeksOnChart: Int,
    val song: SongItem,
) {
    val movement: ChartMovement get() = chartMovement(rank, previousRank, weeksOnChart)
}

data class SoriChartArtist(
    val rank: Int,
    val previousRank: Int?,
    val weeksOnChart: Int,
    val channelId: String?,
    val name: String,
    val thumbnail: String?,
) {
    val movement: ChartMovement get() = chartMovement(rank, previousRank, weeksOnChart)
}

/** A weekly chart; [weekEnd] is the last day of the charted week. */
data class SoriChart<T>(
    val weekEnd: LocalDate?,
    val entries: List<T>,
)

/** The seven days a weekly chart covers. */
fun chartWeek(weekEnd: LocalDate): ClosedRange<LocalDate> = weekEnd.minusDays(6)..weekEnd

/**
 * YouTube's official weekly charts (charts.youtube.com). YouTube Music's own charts are
 * Premium-only for free users in Korea (2026-09); these are public and need no account.
 */
object SoriCharts {
    private const val URL = "https://charts.youtube.com/youtubei/v1/browse?alt=json"
    private const val CLIENT_NAME = "WEB_MUSIC_ANALYTICS"
    private const val CLIENT_VERSION = "2.0"
    private const val CACHE_MS = 6 * 60 * 60 * 1000L

    private val cache = ConcurrentHashMap<String, Pair<Long, SoriChart<*>>>()

    suspend fun tracks(region: SoriChartRegion): SoriChart<SoriChartEntry>? = load("TRACKS", region, ::parseChartTracks)

    suspend fun artists(region: SoriChartRegion = SoriChartRegion.KOREA): SoriChart<SoriChartArtist>? =
        load("ARTISTS", region, ::parseChartArtists)

    private suspend fun <T> load(
        type: String,
        region: SoriChartRegion,
        parse: (String) -> SoriChart<T>,
    ): SoriChart<T>? {
        val key = "$type/${region.code}"
        cache[key]?.let { (loadedAt, chart) ->
            @Suppress("UNCHECKED_CAST")
            if (System.currentTimeMillis() - loadedAt < CACHE_MS) return chart as SoriChart<T>
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                parse(
                    SoriYouTubeWeb.postTo(URL, CLIENT_NAME, CLIENT_VERSION) {
                        put("browseId", "FEmusic_analytics_charts_home")
                        put(
                            "query",
                            "perspective=CHART_DETAILS&chart_params_country_code=${region.code}" +
                                "&chart_params_chart_type=$type&chart_params_period_type=WEEKLY",
                        )
                    },
                )
            }.onFailure { Timber.w(it, "Sori chart %s failed", key) }
                .getOrNull()
                ?.takeIf { it.entries.isNotEmpty() }
                ?.also { cache[key] = System.currentTimeMillis() to it }
        }
    }
}

fun parseChartTracks(json: String): SoriChart<SoriChartEntry> =
    parseChart(json, "trackViews") { view ->
        val meta = view.path("chartEntryMetadata") ?: return@parseChart null
        val rank = meta.int("currentPosition") ?: return@parseChart null
        // The song version (art track) plays audio with the square album cover.
        val songVersion = view.string("atvExternalVideoId")
        val id = songVersion ?: view.string("encryptedVideoId") ?: return@parseChart null
        val title = view.string("name") ?: return@parseChart null
        val artists =
            (view["artists"] as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.string("name") }
                ?.map { Artist(name = it, id = null) }
                .orEmpty()
        SoriChartEntry(
            rank = rank,
            previousRank = meta.int("previousPosition")?.takeIf { it > 0 },
            weeksOnChart = meta.int("periodsOnChart") ?: 1,
            song =
                SongItem(
                    id = id,
                    title = title,
                    artists = artists,
                    musicVideoType = songVersion?.let { MUSIC_VIDEO_TYPE_ATV },
                    chartPosition = rank,
                    thumbnail = view.cover() ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                ),
        )
    }

fun parseChartArtists(json: String): SoriChart<SoriChartArtist> =
    parseChart(json, "artistViews") { view ->
        val meta = view.path("chartEntryMetadata") ?: return@parseChart null
        SoriChartArtist(
            rank = meta.int("currentPosition") ?: return@parseChart null,
            previousRank = meta.int("previousPosition")?.takeIf { it > 0 },
            weeksOnChart = meta.int("periodsOnChart") ?: 1,
            channelId = view.string("externalChannelId"),
            name = view.string("name") ?: return@parseChart null,
            thumbnail = view.cover(),
        )
    }

private fun <T> parseChart(
    json: String,
    listKey: String,
    entry: (JsonObject) -> T?,
): SoriChart<T> {
    val root = SoriYouTubeWeb.parse(json) ?: return SoriChart(null, emptyList())
    var chart: SoriChart<T>? = null
    walk(root) { obj ->
        if (chart != null) return@walk
        val views = obj[listKey] as? JsonArray ?: return@walk
        chart =
            SoriChart(
                weekEnd = obj.string("endDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                entries = views.mapNotNull { (it as? JsonObject)?.let(entry) },
            )
    }
    return chart ?: SoriChart(null, emptyList())
}

private val coverSize = Regex("""=w\d+-h\d+""")

/** The entry's square cover, asked for at a size fit for large artwork. */
private fun JsonObject.cover(): String? =
    (path("thumbnail")?.get("thumbnails") as? JsonArray)
        ?.mapNotNull { (it as? JsonObject)?.string("url") }
        ?.lastOrNull()
        ?.replace(coverSize, "=w544-h544")
