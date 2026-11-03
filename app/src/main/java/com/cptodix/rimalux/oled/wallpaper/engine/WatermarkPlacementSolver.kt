package com.cptodix.rimalux.oled.wallpaper.engine

import kotlin.math.abs
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
 * Orientation of the rendered share watermark along the perimeter of [SafeZoneRect].
 */
enum class WatermarkOrientation {
    /** Standard horizontal text (`0°`), used along the top and bottom safe-zone rails. */
    HORIZONTAL,

    /** Vertical text rotated `-90°` (reading bottom-to-top), used along the left safe-zone rail. */
    VERTICAL_CCW,

    /** Vertical text rotated `+90°` (reading top-to-bottom), used along the right safe-zone rail. */
    VERTICAL_CW
}

/**
 * Perimeter rail of [SafeZoneRect] on which the watermark is anchored.
 */
enum class WatermarkEdge {
    TOP,
    BOTTOM,
    LEFT,
    RIGHT
}

/**
 * Resolved axis-aligned bounding box, orientation, and local background telemetry
 * for a share-only subtle perimeter watermark.
 */
data class WatermarkPlacement(
    val leftPx: Float,
    val topPx: Float,
    val widthPx: Float,
    val heightPx: Float,
    val orientation: WatermarkOrientation,
    val edge: WatermarkEdge,
    val meanLuminance: Float,
    val trueBlackFraction: Float,
    val safeZone: SafeZoneRect
)

/**
 * Pure Kotlin (zero Android/Compose dependencies) social-media safe-zone calculator and
 * integral-image emptiness solver for placing subtle share watermarks strictly along the
 * perimeter edges (horizontal top/bottom or vertical left/right) of the social safe zone—never
 * across the interior focal area of a wallpaper.
 */
object WatermarkPlacementSolver {

    private const val SUB_SAMPLES = 2
    private const val HORIZONTAL_STEPS = 48
    private const val VERTICAL_STEPS = 64

    /**
     * Computes a social media safe-zone perimeter rectangle that survives:
     * - 9:16 Stories / Reels / Shorts / TikTok crops and top header / bottom reply UI chrome,
     * - Horizontal side crops and rounded screen corners across portrait, foldable, and landscape ratios,
     * while keeping the 4 perimeter rails cleanly near the outer margins away from the center artwork.
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
            // Portrait / foldable: clear horizontal 4:5 / phone side gutters and rounded corners
            val cropW45 = min(wf, hf * 0.80f)
            val leftSide = (wf - cropW45) * 0.5f + cropW45 * 0.065f
            val rightSide = (wf + cropW45) * 0.5f - cropW45 * 0.065f
            rawLeft = max(wf * 0.065f, leftSide)
            rawRight = min(wf * 0.935f, rightSide)

            // Clear 9:16 top story header/progress bar and bottom reply/caption chrome
            val cropH916 = min(hf, wf * (16f / 9f))
            val top916 = (hf - cropH916) * 0.5f + cropH916 * 0.065f
            val bottom916 = (hf + cropH916) * 0.5f - cropH916 * 0.095f
            rawTop = max(hf * 0.12f, top916)
            rawBottom = min(hf * 0.85f, bottom916)
        } else {
            // Landscape / wide tablet: perimeter margins clearing 16:9 / status & dock bars
            val cropH169 = min(hf, wf * (9f / 16f))
            rawLeft = wf * 0.065f
            rawRight = wf * 0.935f
            rawTop = max(hf * 0.11f, (hf - cropH169) * 0.5f + cropH169 * 0.10f)
            rawBottom = min(hf * 0.88f, (hf + cropH169) * 0.5f - cropH169 * 0.12f)
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
     * Evaluates the rendered pixel buffer using a 2D summed-area table and locates the
     * emptiest / darkest `#000000` placement strictly along the 4 perimeter rails of [SafeZoneRect]:
     * - [WatermarkEdge.BOTTOM] (`HORIZONTAL`)
     * - [WatermarkEdge.TOP] (`HORIZONTAL`)
     * - [WatermarkEdge.LEFT] (`VERTICAL_CCW`, `-90°`)
     * - [WatermarkEdge.RIGHT] (`VERTICAL_CW`, `+90°`)
     *
     * The interior / center of the wallpaper is never considered.
     *
     * Note: [pixelArgbAt] is queried in monotonically non-decreasing `y` order so callers can
     * back it with a lightweight single-row scanline buffer.
     */
    fun findOptimalPlacement(
        widthPx: Int,
        heightPx: Int,
        textWidthPx: Float,
        textHeightPx: Float,
        gridBaseResolution: Int = 180,
        pixelArgbAt: (x: Int, y: Int) -> Int
    ): WatermarkPlacement {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val safeZone = computeSocialSafeZone(w, h)

        val rawSpan = textWidthPx.coerceAtLeast(1f)
        val rawThickness = textHeightPx.coerceAtLeast(1f)

        val horizW = rawSpan.coerceIn(1f, safeZone.widthPx.toFloat())
        val horizH = rawThickness.coerceIn(1f, safeZone.heightPx.toFloat())
        val vertW = rawThickness.coerceIn(1f, safeZone.widthPx.toFloat())
        val vertH = rawSpan.coerceIn(1f, safeZone.heightPx.toFloat())

        val haloPad = max(8f, rawThickness * 2.2f)
        val padTight = max(2f, rawThickness * 0.35f)

        val scanLeft = (safeZone.leftPx - haloPad.roundToInt()).coerceAtLeast(0)
        val scanRight = (safeZone.rightPx + haloPad.roundToInt()).coerceAtMost(w)
        val scanTop = (safeZone.topPx - haloPad.roundToInt()).coerceAtLeast(0)
        val scanBottom = (safeZone.bottomPx + haloPad.roundToInt()).coerceAtMost(h)

        val scanW = (scanRight - scanLeft).coerceAtLeast(1)
        val scanH = (scanBottom - scanTop).coerceAtLeast(1)

        val cols = gridBaseResolution.coerceIn(32, 240)
        val rows = ((cols.toFloat() * scanH.toFloat()) / scanW.toFloat())
            .roundToInt()
            .coerceIn(32, 360)

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

        fun sampleSat(sat: DoubleArray, fx: Float, fy: Float): Double {
            val cx = fx.coerceIn(0f, cols.toFloat())
            val cy = fy.coerceIn(0f, rows.toFloat())
            val ix0 = cx.toInt().coerceIn(0, cols)
            val iy0 = cy.toInt().coerceIn(0, rows)
            val ix1 = (ix0 + 1).coerceAtMost(cols)
            val iy1 = (iy0 + 1).coerceAtMost(rows)
            val dx = (cx - ix0.toFloat()).toDouble()
            val dy = (cy - iy0.toFloat()).toDouble()

            val s00 = sat[iy0 * stride + ix0]
            val s10 = sat[iy0 * stride + ix1]
            val s01 = sat[iy1 * stride + ix0]
            val s11 = sat[iy1 * stride + ix1]

            val top = s00 + (s10 - s00) * dx
            val bottom = s01 + (s11 - s01) * dx
            return top + (bottom - top) * dy
        }

        fun rectMean(sat: DoubleArray, rx0: Float, ry0: Float, rx1: Float, ry1: Float): Double {
            val fx0 = (((rx0 - scanLeft) / scanW) * cols).coerceIn(0f, cols - 0.01f)
            val fx1 = (((rx1 - scanLeft) / scanW) * cols).coerceIn(fx0 + 0.01f, cols.toFloat())
            val fy0 = (((ry0 - scanTop) / scanH) * rows).coerceIn(0f, rows - 0.01f)
            val fy1 = (((ry1 - scanTop) / scanH) * rows).coerceIn(fy0 + 0.01f, rows.toFloat())
            val sum = sampleSat(sat, fx1, fy1) -
                sampleSat(sat, fx1, fy0) -
                sampleSat(sat, fx0, fy1) +
                sampleSat(sat, fx0, fy0)
            val area = (fx1 - fx0).toDouble() * (fy1 - fy0).toDouble()
            return (sum / area).coerceAtLeast(0.0)
        }

        val safeLeft = safeZone.leftPx.toFloat()
        val safeRight = safeZone.rightPx.toFloat()
        val safeTop = safeZone.topPx.toFloat()
        val safeBottom = safeZone.bottomPx.toFloat()

        val fitsHoriz = rawSpan <= safeZone.widthPx * 1.02f
        val fitsVert = rawSpan <= safeZone.heightPx * 1.02f
        val evalHoriz = fitsHoriz || !fitsVert
        val evalVert = fitsVert || !fitsHoriz

        var bestScore = Double.POSITIVE_INFINITY
        var bestLeft = safeLeft
        var bestTop = max(safeTop, safeBottom - horizH)
        var bestW = horizW
        var bestH = horizH
        var bestOrientation = WatermarkOrientation.HORIZONTAL
        var bestEdge = WatermarkEdge.BOTTOM

        fun evaluateCandidate(
            candLeft: Float,
            candTop: Float,
            candW: Float,
            candH: Float,
            orientation: WatermarkOrientation,
            edge: WatermarkEdge,
            tieBias: Double
        ) {
            val coreCost = rectMean(
                costSat,
                candLeft - padTight,
                candTop - padTight,
                candLeft + candW + padTight,
                candTop + candH + padTight
            )
            val haloCost = rectMean(
                costSat,
                candLeft - haloPad,
                candTop - haloPad,
                candLeft + candW + haloPad,
                candTop + haloPad.let { candH + it }
            )
            val totalScore = 0.74 * coreCost + 0.26 * haloCost + tieBias
            if (totalScore < bestScore) {
                bestScore = totalScore
                bestLeft = candLeft
                bestTop = candTop
                bestW = candW
                bestH = candH
                bestOrientation = orientation
                bestEdge = edge
            }
        }

        if (evalHoriz) {
            val minX = safeLeft
            val maxX = max(minX, safeRight - horizW)
            val stepsX = if (maxX - minX > 0.5f) HORIZONTAL_STEPS else 0

            // 1. Bottom horizontal safe-zone rail
            val bottomY = max(safeTop, safeBottom - horizH)
            for (ix in 0..stepsX) {
                val u = if (stepsX > 0) ix.toFloat() / stepsX.toFloat() else 0.5f
                val candX = minX + (maxX - minX) * u
                val tieBias = 0.0000 + abs(u - 0.5f) * 0.0008
                evaluateCandidate(
                    candLeft = candX,
                    candTop = bottomY,
                    candW = horizW,
                    candH = horizH,
                    orientation = WatermarkOrientation.HORIZONTAL,
                    edge = WatermarkEdge.BOTTOM,
                    tieBias = tieBias
                )
            }

            // 2. Top horizontal safe-zone rail
            val topY = safeTop
            for (ix in 0..stepsX) {
                val u = if (stepsX > 0) ix.toFloat() / stepsX.toFloat() else 0.5f
                val candX = minX + (maxX - minX) * u
                val tieBias = 0.0006 + abs(u - 0.5f) * 0.0008
                evaluateCandidate(
                    candLeft = candX,
                    candTop = topY,
                    candW = horizW,
                    candH = horizH,
                    orientation = WatermarkOrientation.HORIZONTAL,
                    edge = WatermarkEdge.TOP,
                    tieBias = tieBias
                )
            }
        }

        if (evalVert) {
            val minY = safeTop
            val maxY = max(minY, safeBottom - vertH)
            val stepsY = if (maxY - minY > 0.5f) VERTICAL_STEPS else 0

            // 3. Left vertical safe-zone rail (-90° reading bottom-to-top)
            val leftX = safeLeft
            for (iy in 0..stepsY) {
                val v = if (stepsY > 0) iy.toFloat() / stepsY.toFloat() else 0.5f
                val candY = minY + (maxY - minY) * v
                val tieBias = 0.0011 + abs(v - 0.5f) * 0.0007
                evaluateCandidate(
                    candLeft = leftX,
                    candTop = candY,
                    candW = vertW,
                    candH = vertH,
                    orientation = WatermarkOrientation.VERTICAL_CCW,
                    edge = WatermarkEdge.LEFT,
                    tieBias = tieBias
                )
            }

            // 4. Right vertical safe-zone rail (+90° reading top-to-bottom; gently favors upper half when empty)
            val rightX = max(safeLeft, safeRight - vertW)
            for (iy in 0..stepsY) {
                val v = if (stepsY > 0) iy.toFloat() / stepsY.toFloat() else 0.35f
                val candY = minY + (maxY - minY) * v
                val tieBias = 0.0015 + abs(v - 0.35f) * 0.0008
                evaluateCandidate(
                    candLeft = rightX,
                    candTop = candY,
                    candW = vertW,
                    candH = vertH,
                    orientation = WatermarkOrientation.VERTICAL_CW,
                    edge = WatermarkEdge.RIGHT,
                    tieBias = tieBias
                )
            }
        }

        val clampedLeft = bestLeft.coerceIn(safeLeft, max(safeLeft, safeRight - bestW))
        val clampedTop = bestTop.coerceIn(safeTop, max(safeTop, safeBottom - bestH))

        val finalMeanLum = rectMean(
            lumSat,
            clampedLeft,
            clampedTop,
            clampedLeft + bestW,
            clampedTop + bestH
        ).toFloat().coerceIn(0f, 1f)

        val finalBlackFraction = rectMean(
            blackSat,
            clampedLeft,
            clampedTop,
            clampedLeft + bestW,
            clampedTop + bestH
        ).toFloat().coerceIn(0f, 1f)

        return WatermarkPlacement(
            leftPx = clampedLeft,
            topPx = clampedTop,
            widthPx = bestW,
            heightPx = bestH,
            orientation = bestOrientation,
            edge = bestEdge,
            meanLuminance = finalMeanLum,
            trueBlackFraction = finalBlackFraction,
            safeZone = safeZone
        )
    }

    /**
     * Computes a subtle, muted ARGB ink color tailored to the local background luminance
     * of the chosen watermark spot and softly harmonized with [paletteColors].
     *
     * Calibrated so glyphs remain clearly legible above OLED near-black gamma roll-off,
     * system share-sheet thumbnail downscaling, and social/messaging JPEG compression
     * while staying understated (~45%–58% opacity) so the wallpaper artwork dominates.
     */
    fun computeSubtleWatermarkArgb(
        meanLuminance: Float,
        paletteColors: List<Long> = emptyList()
    ): Int {
        val lum = meanLuminance.coerceIn(0f, 1f)
        if (lum > 0.48f) {
            // Bright / pastel background (e.g. 0% True Black): muted charcoal at ~50%–58% alpha
            val tBright = ((lum - 0.48f) / 0.52f).coerceIn(0f, 1f)
            val darkAlpha = (128f + tBright * 20f).roundToInt().coerceIn(128, 148)
            return (darkAlpha shl 24) or (10 shl 16) or (10 shl 8) or 14
        }

        // Pure #000000 or dark/mid colored region: muted silver-white kissed 22% with palette tint
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

        val mix = 0.22f
        val r = (246f * (1f - mix) + accentR * mix).roundToInt().coerceIn(210, 255)
        val g = (246f * (1f - mix) + accentG * mix).roundToInt().coerceIn(210, 255)
        val b = (250f * (1f - mix) + accentB * mix).roundToInt().coerceIn(210, 255)

        val alpha = if (lum <= 0.08f) {
            116 // ~45.5% alpha on #000000 OLED void (~#717175): crisp, zero white glare, immune to OLED black crush
        } else {
            val t = ((lum - 0.08f) / 0.40f).coerceIn(0f, 1f)
            (124f + t * 40f).roundToInt().coerceIn(124, 164)
        }

        return (alpha shl 24) or (r shl 16) or (g shl 8) or b
    }
}
