package com.example.engine

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Axis-aligned pixel rectangle representing the social media crop- and overlay-safe area
 * of an exported wallpaper image.
 */
data class SafeZoneRect(
    val leftPx: Int,
    val topPx: Int,
    val rightPx: Int,
    val bottomPx: Int
) {
    val widthPx: Int get() = (rightPx - leftPx).coerceAtLeast(1)
    val heightPx: Int get() = (bottomPx - topPx).coerceAtLeast(1)
}

/**
 * Resolved bounding box and local background telemetry for a share-only subtle watermark.
 */
data class WatermarkPlacement(
    val leftPx: Float,
    val topPx: Float,
    val widthPx: Float,
    val heightPx: Float,
    val meanLuminance: Float,
    val trueBlackFraction: Float,
    val safeZone: SafeZoneRect
)

/**
 * Pure Kotlin (zero Android/Compose dependencies) social-media safe-zone calculator and
 * integral-image emptiness solver for placing subtle share watermarks into the darkest,
 * least intrusive pocket of a rendered wallpaper.
 */
object WatermarkPlacementSolver {

    private const val SUB_SAMPLES = 2

    /**
     * Computes a general social media safe-zone rectangle that survives:
     * - 9:16 Stories / Reels / Shorts / TikTok crops and top/bottom/right UI chrome overlays,
     * - 4:5 portrait feed center crops (Instagram, Threads, LinkedIn) and 3:4 feed previews,
     * - Rounded screen corners and horizontal side gutters across portrait, foldable, and landscape ratios.
     */
    fun computeSocialSafeZone(widthPx: Int, heightPx: Int): SafeZoneRect {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val wf = w.toFloat()
        val hf = h.toFloat()

        val rawLeft: Float
        val rawRight: Float
        val rawTop: Float
        val rawBottom: Float

        if (h >= w) {
            // Portrait / square: intersect 9:16 story safe frame with centered 4:5 portrait feed crop
            val cropW916 = min(wf, hf * (9f / 16f))
            val left916 = (wf - cropW916) * 0.5f + cropW916 * 0.10f
            val right916 = (wf + cropW916) * 0.5f - cropW916 * 0.12f
            rawLeft = max(wf * 0.10f, left916)
            rawRight = min(wf * 0.88f, right916)

            val cropH916 = min(hf, wf * (16f / 9f))
            val top916 = (hf - cropH916) * 0.5f + cropH916 * 0.16f
            val bottom916 = (hf + cropH916) * 0.5f - cropH916 * 0.21f

            val cropH45 = min(hf, wf * 1.25f)
            val top45 = (hf - cropH45) * 0.5f + cropH45 * 0.08f
            val bottom45 = (hf + cropH45) * 0.5f - cropH45 * 0.08f

            rawTop = max(hf * 0.18f, max(top916, top45))
            rawBottom = min(hf * 0.80f, min(bottom916, bottom45))
        } else {
            // Landscape / wide tablet: intersect 4:3 horizontal and 16:9 vertical safe interiors
            val cropW43 = min(wf, hf * (4f / 3f))
            rawLeft = max(wf * 0.12f, (wf - cropW43) * 0.5f + cropW43 * 0.08f)
            rawRight = min(wf * 0.88f, (wf + cropW43) * 0.5f - cropW43 * 0.08f)

            val cropH169 = min(hf, wf * (9f / 16f))
            rawTop = max(hf * 0.16f, (hf - cropH169) * 0.5f + cropH169 * 0.12f)
            rawBottom = min(hf * 0.82f, (hf + cropH169) * 0.5f - cropH169 * 0.14f)
        }

        val finalLeft = rawLeft.roundToInt().coerceIn(0, (w - 1).coerceAtLeast(0))
        val finalRight = rawRight.roundToInt().coerceIn((finalLeft + 1).coerceAtMost(w), w)
        val finalTop = rawTop.roundToInt().coerceIn(0, (h - 1).coerceAtLeast(0))
        val finalBottom = rawBottom.roundToInt().coerceIn((finalTop + 1).coerceAtMost(h), h)

        return SafeZoneRect(
            leftPx = finalLeft,
            topPx = finalTop,
            rightPx = finalRight,
            bottomPx = finalBottom
        )
    }

    /**
     * Evaluates the rendered pixel buffer using a 2D summed-area table over the social safe zone
     * (plus a surrounding halo border) and locates the emptiest / darkest `#000000` placement
     * for a watermark box of size `[textWidthPx, textHeightPx]`.
     *
     * Note: [pixelArgbAt] is queried in monotonically non-decreasing `y` order so callers can
     * back it with a lightweight single-row scanline buffer.
     */
    fun findOptimalPlacement(
        widthPx: Int,
        heightPx: Int,
        textWidthPx: Float,
        textHeightPx: Float,
        gridBaseResolution: Int = 128,
        pixelArgbAt: (x: Int, y: Int) -> Int
    ): WatermarkPlacement {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val safeZone = computeSocialSafeZone(w, h)

        val boxW = textWidthPx.coerceIn(1f, safeZone.widthPx.toFloat())
        val boxH = textHeightPx.coerceIn(1f, safeZone.heightPx.toFloat())

        val haloX = max(8f, boxH * 2.4f)
        val haloY = max(8f, boxH * 2.0f)
        val padTight = max(2f, boxH * 0.35f)

        val scanLeft = (safeZone.leftPx - haloX.roundToInt()).coerceAtLeast(0)
        val scanRight = (safeZone.rightPx + haloX.roundToInt()).coerceAtMost(w)
        val scanTop = (safeZone.topPx - haloY.roundToInt()).coerceAtLeast(0)
        val scanBottom = (safeZone.bottomPx + haloY.roundToInt()).coerceAtMost(h)

        val scanW = (scanRight - scanLeft).coerceAtLeast(1)
        val scanH = (scanBottom - scanTop).coerceAtLeast(1)

        val cols = gridBaseResolution.coerceIn(32, 200)
        val rows = ((cols.toFloat() * scanH.toFloat()) / scanW.toFloat())
            .roundToInt()
            .coerceIn(32, 260)

        val cellCount = cols * rows
        val cellCost = DoubleArray(cellCount)
        val cellLum = DoubleArray(cellCount)
        val cellBlack = DoubleArray(cellCount)

        val invSub = 1f / SUB_SAMPLES.toFloat()
        val invSubSq = 1.0 / (SUB_SAMPLES * SUB_SAMPLES).toDouble()
        val invCols = 1f / cols.toFloat()
        val invRows = 1f / rows.toFloat()

        // Monotonic Y scan order enables 1-row scanline caching on Bitmap callers
        for (r in 0 until rows) {
            val rowOffset = r * cols
            for (sy in 0 until SUB_SAMPLES) {
                val normY = (r + (sy + 0.5f) * invSub) * invRows
                val py = (scanTop + (normY * scanH).toInt()).coerceIn(0, h - 1)
                for (c in 0 until cols) {
                    var subCost = 0.0
                    var subLum = 0.0
                    var subBlack = 0.0
                    for (sx in 0 until SUB_SAMPLES) {
                        val normX = (c + (sx + 0.5f) * invSub) * invCols
                        val px = (scanLeft + (normX * scanW).toInt()).coerceIn(0, w - 1)
                        val rgb = pixelArgbAt(px, py) and 0x00FFFFFF
                        if (rgb == 0) {
                            subBlack += 1.0
                        } else {
                            val red = (rgb ushr 16) and 0xFF
                            val green = (rgb ushr 8) and 0xFF
                            val blue = rgb and 0xFF
                            val lum = (0.2126 * red + 0.7152 * green + 0.0722 * blue) / 255.0
                            subLum += lum
                            // Fixed 0.35 occupancy penalty ensures pure #000000 void always beats dark shaded geometry
                            subCost += 0.35 + 0.65 * lum
                        }
                    }
                    val idx = rowOffset + c
                    cellCost[idx] += subCost * invSubSq
                    cellLum[idx] += subLum * invSubSq
                    cellBlack[idx] += subBlack * invSubSq
                }
            }
        }

        // Build 2D summed-area tables (integral images) for O(1) rectangle queries
        val stride = cols + 1
        val satSize = (rows + 1) * stride
        val costSat = DoubleArray(satSize)
        val lumSat = DoubleArray(satSize)
        val blackSat = DoubleArray(satSize)

        for (r in 0 until rows) {
            var rowCost = 0.0
            var rowLum = 0.0
            var rowBlack = 0.0
            val cellRow = r * cols
            val prevSatRow = r * stride
            val nextSatRow = (r + 1) * stride
            for (c in 0 until cols) {
                rowCost += cellCost[cellRow + c]
                rowLum += cellLum[cellRow + c]
                rowBlack += cellBlack[cellRow + c]
                val nextCol = c + 1
                costSat[nextSatRow + nextCol] = costSat[prevSatRow + nextCol] + rowCost
                lumSat[nextSatRow + nextCol] = lumSat[prevSatRow + nextCol] + rowLum
                blackSat[nextSatRow + nextCol] = blackSat[prevSatRow + nextCol] + rowBlack
            }
        }

        fun rectMean(sat: DoubleArray, rx0: Float, ry0: Float, rx1: Float, ry1: Float): Double {
            val gx0 = (((rx0 - scanLeft) / scanW) * cols).toInt().coerceIn(0, cols - 1)
            val gx1 = (((rx1 - scanLeft) / scanW) * cols + 0.999f).toInt().coerceIn(gx0 + 1, cols)
            val gy0 = (((ry0 - scanTop) / scanH) * rows).toInt().coerceIn(0, rows - 1)
            val gy1 = (((ry1 - scanTop) / scanH) * rows + 0.999f).toInt().coerceIn(gy0 + 1, rows)
            val sum = sat[gy1 * stride + gx1] -
                sat[gy0 * stride + gx1] -
                sat[gy1 * stride + gx0] +
                sat[gy0 * stride + gx0]
            val area = (gx1 - gx0) * (gy1 - gy0)
            return sum / area.toDouble()
        }

        val minLeft = safeZone.leftPx.toFloat()
        val maxLeft = max(minLeft, safeZone.rightPx.toFloat() - boxW)
        val minTop = safeZone.topPx.toFloat()
        val maxTop = max(minTop, safeZone.bottomPx.toFloat() - boxH)

        val stepsX = if (maxLeft - minLeft > 0.5f) 40 else 0
        val stepsY = if (maxTop - minTop > 0.5f) 56 else 0

        val centerCanvasX = w * 0.5f
        val centerCanvasY = h * 0.5f
        val halfDiag = hypot(centerCanvasX, centerCanvasY).coerceAtLeast(1f)

        var bestScore = Double.POSITIVE_INFINITY
        var bestLeft = minLeft
        var bestTop = maxTop

        for (iy in 0..stepsY) {
            val v = if (stepsY > 0) iy.toFloat() / stepsY.toFloat() else 1f
            val candTop = minTop + (maxTop - minTop) * v
            for (ix in 0..stepsX) {
                val u = if (stepsX > 0) ix.toFloat() / stepsX.toFloat() else 0f
                val candLeft = minLeft + (maxLeft - minLeft) * u

                val coreCost = rectMean(
                    costSat,
                    candLeft - padTight,
                    candTop - padTight,
                    candLeft + boxW + padTight,
                    candTop + boxH + padTight
                )
                val haloCost = rectMean(
                    costSat,
                    candLeft - haloX,
                    candTop - haloY,
                    candLeft + boxW + haloX,
                    candTop + boxH + haloY
                )

                // Subtle compositional tie-breaker (< 0.004) when multiple spots are equally empty:
                // favor lower safe-zone corners away from the focal center of the wallpaper.
                val edgeDist = min(u, 1f - u) * 2f
                val boxMidX = candLeft + boxW * 0.5f
                val boxMidY = candTop + boxH * 0.5f
                val centerProximity = 1f - (hypot(boxMidX - centerCanvasX, boxMidY - centerCanvasY) / halfDiag)
                    .coerceIn(0f, 1f)
                val tieBias = (1.0 - v) * 0.0018 +
                    edgeDist * 0.0011 +
                    u * 0.0002 +
                    centerProximity * 0.0005

                val totalScore = 0.74 * coreCost + 0.26 * haloCost + tieBias
                if (totalScore < bestScore) {
                    bestScore = totalScore
                    bestLeft = candLeft
                    bestTop = candTop
                }
            }
        }

        val finalMeanLum = rectMean(
            lumSat,
            bestLeft,
            bestTop,
            bestLeft + boxW,
            bestTop + boxH
        ).toFloat().coerceIn(0f, 1f)

        val finalBlackFraction = rectMean(
            blackSat,
            bestLeft,
            bestTop,
            bestLeft + boxW,
            bestTop + boxH
        ).toFloat().coerceIn(0f, 1f)

        return WatermarkPlacement(
            leftPx = bestLeft.coerceIn(minLeft, maxLeft),
            topPx = bestTop.coerceIn(minTop, maxTop),
            widthPx = boxW,
            heightPx = boxH,
            meanLuminance = finalMeanLum,
            trueBlackFraction = finalBlackFraction,
            safeZone = safeZone
        )
    }

    /**
     * Computes a barely-noticeable, low-contrast ARGB ink color tailored to the local background
     * luminance of the chosen watermark spot and subtly harmonized with [paletteColors].
     */
    fun computeSubtleWatermarkArgb(
        meanLuminance: Float,
        paletteColors: List<Long> = emptyList()
    ): Int {
        val lum = meanLuminance.coerceIn(0f, 1f)
        if (lum > 0.48f) {
            // Bright / pastel background (e.g. 0% True Black): whisper-dark charcoal at ~18% alpha
            val darkAlpha = 46
            return (darkAlpha shl 24) or (8 shl 16) or (8 shl 8) or 10
        }

        // Pure #000000 or dark colored region: ~15% opacity neutral-white kissed 18% with palette tint
        var accentR = 246
        var accentG = 246
        var accentB = 250
        if (paletteColors.isNotEmpty()) {
            var bestChannelMax = -1
            for (swatch in paletteColors) {
                val r = ((swatch ushr 16) and 0xFFL).toInt()
                val g = ((swatch ushr 8) and 0xFFL).toInt()
                val b = (swatch and 0xFFL).toInt()
                val peak = max(r, max(g, b))
                if (peak > bestChannelMax && peak > 0) {
                    bestChannelMax = peak
                    val scale = 255f / peak.toFloat()
                    accentR = (r * scale).roundToInt().coerceIn(0, 255)
                    accentG = (g * scale).roundToInt().coerceIn(0, 255)
                    accentB = (b * scale).roundToInt().coerceIn(0, 255)
                }
            }
        }

        val mix = 0.18f
        val r = (246f * (1f - mix) + accentR * mix).roundToInt().coerceIn(200, 255)
        val g = (246f * (1f - mix) + accentG * mix).roundToInt().coerceIn(200, 255)
        val b = (250f * (1f - mix) + accentB * mix).roundToInt().coerceIn(200, 255)

        val alpha = if (lum <= 0.08f) {
            38 // ~14.9% alpha on pure #000000 OLED void: barely noticeable, zero glare
        } else {
            val t = ((lum - 0.08f) / 0.40f).coerceIn(0f, 1f)
            (40f + t * 14f).roundToInt().coerceIn(40, 54)
        }

        return (alpha shl 24) or (r shl 16) or (g shl 8) or b
    }
}
