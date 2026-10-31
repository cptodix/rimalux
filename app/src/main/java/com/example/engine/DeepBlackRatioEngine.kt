package com.example.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Platform-independent projection parameters mapping normalized Euclidean scene space
 * `[0f, 1f] x [0f, sceneAspectRatio]` onto a target pixel viewport without distortion.
 */
data class ViewportTransform(
    val canvasWidth: Float,
    val canvasHeight: Float,
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val drawMicroSeams: Boolean
)

/**
 * Pure mathematical engine responsible for #000000 OLED True Black Ratio resolution,
 * analytical coverage statistics, viewport coordinate projection, and rendered pixel-grid
 * True Black percentage measurement across any platform.
 *
 * PORTABILITY CONTRACT:
 * Zero Android SDK, Compose, or platform bitmap imports allowed.
 */
object DeepBlackRatioEngine {

    const val DEFAULT_PITCH_BLACK_RATIO: Float = 0.85f
    const val MIN_PITCH_BLACK_RATIO: Float = 0.0f
    const val MAX_PITCH_BLACK_RATIO: Float = 0.95f

    const val MIN_WALLPAPER_AXIS_PX: Int = 240
    const val MAX_WALLPAPER_AXIS_PX: Int = 4096
    const val MICRO_SEAM_MIN_SCALE: Float = 220f

    fun clampPitchBlackRatio(ratio: Float): Float {
        return ratio.coerceIn(MIN_PITCH_BLACK_RATIO, MAX_PITCH_BLACK_RATIO)
    }

    fun normalizePitchBlackRange(minRatio: Float, maxRatio: Float): Pair<Float, Float> {
        val lo = min(minRatio, maxRatio).coerceIn(MIN_PITCH_BLACK_RATIO, MAX_PITCH_BLACK_RATIO)
        val hi = max(minRatio, maxRatio).coerceIn(lo, MAX_PITCH_BLACK_RATIO)
        return lo to hi
    }

    /**
     * Resolves an effective target #000000 True Black Ratio in `[0.0f, 0.95f]` for either
     * single-value mode or uniform 1%-quantized range sampling.
     */
    fun resolveTargetBlackRatio(
        mode: PitchBlackMode,
        singleRatio: Float,
        rangeMin: Float,
        rangeMax: Float,
        rng: Random
    ): Float {
        return when (mode) {
            PitchBlackMode.SINGLE -> clampPitchBlackRatio(singleRatio)
            PitchBlackMode.RANGE -> {
                val (lo, hi) = normalizePitchBlackRange(rangeMin, rangeMax)
                if (hi - lo <= 0.005f) {
                    lo
                } else {
                    val sampled = lo + rng.nextFloat() * (hi - lo)
                    ((sampled * 100f).roundToInt() / 100f).coerceIn(lo, hi)
                }
            }
        }
    }

    /**
     * Visibility predicate shared by all renderers and analytical estimators.
     * Primitives calibrated by [CoverageCalibrator] are visible iff `threshold >= clampedBlackRatio`.
     */
    fun isPrimitiveVisible(primitive: VectorPrimitive, pitchBlackRatio: Float): Boolean {
        return primitive.threshold >= clampPitchBlackRatio(pitchBlackRatio)
    }

    /**
     * Computes analytical #000000 True Black vs colored coverage percentages directly from
     * calibrated [VectorPrimitive.threshold] and [VectorPrimitive.areaWeight] shares.
     */
    fun computeAnalyticalSceneStats(
        primitives: List<VectorPrimitive>,
        pitchBlackRatio: Float
    ): SceneRatioStats {
        val clamped = clampPitchBlackRatio(pitchBlackRatio)
        if (primitives.isEmpty()) {
            val pct = (clamped * 100f).roundToInt()
            return SceneRatioStats(pitchBlackPercent = pct, coloredPercent = 100 - pct)
        }
        var coloredFraction = 0f
        for (p in primitives) {
            if (p.threshold >= clamped) {
                coloredFraction += p.areaWeight
            }
        }
        val clampedColored = coloredFraction.coerceIn(0f, 1f)
        val measuredBlackPct = ((1f - clampedColored) * 100f).roundToInt().coerceIn(0, 100)
        return SceneRatioStats(
            pitchBlackPercent = measuredBlackPct,
            coloredPercent = 100 - measuredBlackPct
        )
    }

    /**
     * Returns true iff the 24-bit RGB channels of [argbPixel] are strictly `#000000`
     * (OLED subpixels completely powered off).
     */
    fun isTrueBlackRgb(argbPixel: Int): Boolean {
        return (argbPixel and 0x00FFFFFF) == 0
    }

    /**
     * Platform-independent 2D grid sampler that measures the actual rendered `#000000`
     * True Black pixel percentage of any rasterized buffer via [pixelArgbAt].
     */
    inline fun measureTrueBlackPercentage(
        widthPx: Int,
        heightPx: Int,
        gridDivisions: Int = 50,
        pixelArgbAt: (x: Int, y: Int) -> Int
    ): Int {
        if (widthPx <= 0 || heightPx <= 0) return 0
        val safeDivisions = gridDivisions.coerceAtLeast(1)
        val stepX = (widthPx / safeDivisions).coerceAtLeast(1)
        val stepY = (heightPx / safeDivisions).coerceAtLeast(1)
        var blackPixels = 0
        var totalSamples = 0
        var y = stepY / 2
        while (y < heightPx) {
            var x = stepX / 2
            while (x < widthPx) {
                if (isTrueBlackRgb(pixelArgbAt(x, y))) {
                    blackPixels++
                }
                totalSamples++
                x += stepX
            }
            y += stepY
        }
        if (totalSamples == 0) return 0
        return ((blackPixels * 100f) / totalSamples).roundToInt().coerceIn(0, 100)
    }

    /**
     * Proportionally clamps target pixel dimensions so neither axis exceeds [maxAxisPx]
     * while preserving exact aspect ratio and enforcing [minAxisPx].
     */
    fun clampDimensionsToMaxAxis(
        widthPx: Int,
        heightPx: Int,
        minAxisPx: Int = MIN_WALLPAPER_AXIS_PX,
        maxAxisPx: Int = MAX_WALLPAPER_AXIS_PX
    ): Pair<Int, Int> {
        val rawW = widthPx.coerceAtLeast(minAxisPx)
        val rawH = heightPx.coerceAtLeast(minAxisPx)
        val maxAxis = max(rawW, rawH)
        return if (maxAxis > maxAxisPx) {
            val factor = maxAxisPx.toFloat() / maxAxis
            Pair(
                (rawW * factor).roundToInt().coerceAtLeast(minAxisPx),
                (rawH * factor).roundToInt().coerceAtLeast(minAxisPx)
            )
        } else {
            Pair(rawW, rawH)
        }
    }

    /**
     * Computes center-crop isotropic scale and translation offsets from `[0, 1] x [0, aspect]`
     * into `[0, canvasWidth] x [0, canvasHeight]`.
     */
    fun computeViewportTransform(
        canvasWidth: Float,
        canvasHeight: Float,
        sceneAspectRatio: Float
    ): ViewportTransform {
        val safeAspect = sceneAspectRatio.coerceAtLeast(0.1f)
        val scale = max(canvasWidth / 1f, canvasHeight / safeAspect)
        val offsetX = (canvasWidth - scale * 1f) * 0.5f
        val offsetY = (canvasHeight - scale * safeAspect) * 0.5f
        return ViewportTransform(
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY,
            drawMicroSeams = scale >= MICRO_SEAM_MIN_SCALE
        )
    }
}
