/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori

import android.graphics.Bitmap
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.abs
import kotlin.math.max

/** Pixel rectangle, right and bottom exclusive. */
data class ContentBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

private const val DARK = 24
private const val SAMPLES = 16
private const val MIN_BAR = 0.04
private const val MAX_BAR = 0.35
private const val SYMMETRY = 0.03

private val FRAMED_THUMBNAIL = Regex("""^https?://i\d*\.ytimg\.com/vi(_webp)?/[^/]+/(hq|sd)?(default|[0-3])\.(jpg|webp)""")

/**
 * YouTube's hqdefault/sddefault thumbnails are 4:3 frames: a 16:9 music video comes with black
 * bars baked in, a vertical one with side bars. Only those URLs are worth inspecting.
 */
fun isFramedYouTubeThumbnail(url: String): Boolean = FRAMED_THUMBNAIL.containsMatchIn(url)

/**
 * The picture inside symmetric black bars, or null when there are none. [pixel] returns ARGB.
 * Bars must be symmetric and between 4% and 35% of the side, so dark photos are left alone.
 */
fun letterboxContentBounds(
    width: Int,
    height: Int,
    pixel: (x: Int, y: Int) -> Int,
): ContentBounds? {
    if (width < SAMPLES || height < SAMPLES) return null
    val rows = bars(height) { y -> samples(width).all { x -> isDark(pixel(x, y)) } }
    val columns = bars(width) { x -> samples(height).all { y -> isDark(pixel(x, y)) } }
    if (rows == null && columns == null) return null
    val (top, bottom) = rows ?: (0 to 0)
    val (left, right) = columns ?: (0 to 0)
    return ContentBounds(left, top, width - right, height - bottom)
}

private fun samples(length: Int) = (0 until SAMPLES).map { (length - 1) * it / (SAMPLES - 1) }

private fun isDark(argb: Int) = (argb shr 16 and 0xFF) <= DARK && (argb shr 8 and 0xFF) <= DARK && (argb and 0xFF) <= DARK

/** Thickness of the bars at both ends of a side, or null when they don't look like letterboxing. */
private fun bars(
    length: Int,
    isBar: (Int) -> Boolean,
): Pair<Int, Int>? {
    val limit = (length * MAX_BAR).toInt()
    var start = 0
    while (start < limit && isBar(start)) start++
    var end = 0
    while (end < limit && isBar(length - 1 - end)) end++
    val min = max(2, (length * MIN_BAR).toInt())
    if (start < min || end < min || abs(start - end) > length * SYMMETRY) return null
    return start to end
}

/** Crops a framed YouTube thumbnail to the picture inside its bars. */
class LetterboxTrim : Transformation() {
    override val cacheKey = "sori.letterboxTrim"

    override suspend fun transform(
        input: Bitmap,
        size: Size,
    ): Bitmap {
        val bounds = letterboxContentBounds(input.width, input.height, input::getPixel) ?: return input
        return Bitmap.createBitmap(input, bounds.left, bounds.top, bounds.right - bounds.left, bounds.bottom - bounds.top)
    }
}

/** Adds [LetterboxTrim] to every framed YouTube thumbnail the app loads (lists, player, notification). */
class LetterboxTrimInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        if (!isFramedYouTubeThumbnail(request.data.toString()) || request.transformations.any { it is LetterboxTrim }) {
            return chain.proceed()
        }
        val trimmed = request.newBuilder().transformations(request.transformations + LetterboxTrim()).build()
        return chain.withRequest(trimmed).proceed()
    }
}
