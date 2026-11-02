package com.cptodix.rimalux.oled.wallpaper.engine

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Platform-agnostic linear gradient descriptor in viewport pixel coordinates.
 */
data class LinearGradientSegment(
    val startColorArgb: Long,
    val endColorArgb: Long,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float
)

/**
 * Pure Kotlin color-theory, shading, and gradient math engine.
 *
 * PORTABILITY CONTRACT:
 * Zero `android.graphics.Color` or Compose `Color`/`Brush` imports. All colors are represented
 * as 32-bit opaque ARGB `Long` values (`0xFFRRGGBBL`), guaranteeing identical color synthesis
 * across Android, iOS, Web, and Desktop.
 */
object ColorHarmonyEngine {

    const val PITCH_BLACK_ARGB: Long = 0xFF000000L
    const val FALLBACK_ACCENT_ARGB: Long = 0xFF00F5D4L

    /**
     * Pure mathematical HSV-to-ARGB conversion (`hueDeg` in `[0, 360)`, `saturation` & `value` in `[0, 1]`).
     * Enforces a non-black floor (`b = 4`) when `value > 0` so generated swatches never collide with `#000000`.
     */
    fun hsvToArgbLong(hueDeg: Float, saturation: Float, value: Float): Long {
        val h = ((hueDeg % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)

        val c = v * s
        val sector = h / 60f
        val x = c * (1f - abs((sector % 2f) - 1f))
        val m = v - c

        val r1: Float
        val g1: Float
        val b1: Float
        when (sector.toInt().coerceIn(0, 5)) {
            0 -> { r1 = c; g1 = x; b1 = 0f }
            1 -> { r1 = x; g1 = c; b1 = 0f }
            2 -> { r1 = 0f; g1 = c; b1 = x }
            3 -> { r1 = 0f; g1 = x; b1 = c }
            4 -> { r1 = x; g1 = 0f; b1 = c }
            else -> { r1 = c; g1 = 0f; b1 = x }
        }

        val r = ((r1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f).roundToInt().coerceIn(0, 255)
        var b = ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)
        if (v > 0f && r == 0 && g == 0 && b == 0) {
            b = 4
        }
        return (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }

    /**
     * Applies [shadeFactor] to a palette color while enforcing the OLED non-black safeguard:
     * any non-black swatch shaded down to `(0, 0, 0)` is clamped to `(0, 0, 4)` so colored
     * primitives never register as `#000000` background void.
     */
    fun resolveArgbColor(
        paletteColors: List<Long>,
        colorIndex: Int,
        shadeFactor: Float
    ): Long {
        if (paletteColors.isEmpty()) return FALLBACK_ACCENT_ARGB
        val raw = paletteColors[colorIndex.mod(paletteColors.size)]
        val safeShade = shadeFactor.coerceIn(0.18f, 1.35f)
        val r = (((raw shr 16) and 0xFF) * safeShade).roundToInt().coerceIn(0, 255)
        val g = (((raw shr 8) and 0xFF) * safeShade).roundToInt().coerceIn(0, 255)
        var b = ((raw and 0xFF) * safeShade).roundToInt().coerceIn(0, 255)
        if ((raw and 0xFFFFFFL) != 0L && r == 0 && g == 0 && b == 0) {
            b = 4
        }
        return (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }

    /**
     * Resolves primary and secondary shaded ARGB stops for gradient-filled primitives.
     */
    fun resolveGradientColors(
        paletteColors: List<Long>,
        primitive: VectorPrimitive
    ): Pair<Long, Long> {
        if (paletteColors.size <= 1) {
            val c1 = resolveArgbColor(paletteColors, primitive.colorIndex, primitive.shadeFactor * 1.18f)
            val c2 = resolveArgbColor(paletteColors, primitive.colorIndex, primitive.shadeFactor * 0.48f)
            return c1 to c2
        }
        val primaryIdx = primitive.colorIndex.mod(paletteColors.size)
        var secondaryIdx = primitive.secondaryColorIndex.mod(paletteColors.size)
        if (secondaryIdx == primaryIdx) {
            secondaryIdx = (primaryIdx + 1) % paletteColors.size
        }
        val c1 = resolveArgbColor(paletteColors, primaryIdx, primitive.shadeFactor * 1.10f)
        val c2 = resolveArgbColor(paletteColors, secondaryIdx, primitive.shadeFactor * 0.78f)
        return c1 to c2
    }

    /**
     * Computes exact linear gradient start/end coordinates and ARGB stops for a primitive's
     * bounding box `[minX, minY, maxX, maxY]`.
     */
    fun computeLinearGradient(
        paletteColors: List<Long>,
        primitive: VectorPrimitive,
        minX: Float,
        minY: Float,
        maxX: Float,
        maxY: Float
    ): LinearGradientSegment {
        val (c1, c2) = resolveGradientColors(paletteColors, primitive)
        val cx = (minX + maxX) * 0.5f
        val cy = (minY + maxY) * 0.5f
        val halfSpan = (max(maxX - minX, maxY - minY) * 0.62f).coerceAtLeast(6f)
        val dx = cos(primitive.gradientAngleRad) * halfSpan
        val dy = sin(primitive.gradientAngleRad) * halfSpan
        return LinearGradientSegment(
            startColorArgb = c1,
            endColorArgb = c2,
            startX = cx - dx,
            startY = cy - dy,
            endX = cx + dx,
            endY = cy + dy
        )
    }

    /**
     * Resolves a deterministic swatch list for [seed] according to [paletteMode] and active palettes.
     */
    fun selectColorsForSeed(
        seed: Long,
        rng: Random = Random(seed xor 0x6A09E667F3BCC908L),
        paletteMode: PaletteMode,
        selectedPaletteIds: Set<String>,
        availablePalettes: List<ColorPalette> = StarterPalettes.defaults,
        excludeColors: List<Long>? = null
    ): List<Long> {
        return when (paletteMode) {
            PaletteMode.RANDOMIZED -> RandomPaletteGenerator.generate(seed)
            PaletteMode.PALETTE_LIST -> {
                val activePalettes = availablePalettes.filter { it.id in selectedPaletteIds }
                val candidates = if (excludeColors != null && activePalettes.size > 1) {
                    activePalettes.filterNot { it.colors == excludeColors }.ifEmpty { activePalettes }
                } else {
                    activePalettes
                }
                val chosen = if (candidates.isNotEmpty()) {
                    candidates[rng.nextInt(candidates.size)]
                } else {
                    availablePalettes.firstOrNull() ?: StarterPalettes.defaults.first()
                }
                chosen.colors
            }
        }
    }
}

object RandomPaletteGenerator {
    /**
     * Synthesizes harmonious, vibrant designer palettes using 8 HSV color-theory rules
     * in pure Kotlin math. Never outputs `#000000` or near-black colors.
     */
    fun generate(seed: Long): List<Long> {
        val rng = Random(seed)
        val swatchCount = 4 + rng.nextInt(3) // 4 to 6 swatches
        val baseHue = rng.nextFloat() * 360f
        val harmonyType = rng.nextInt(8)

        val colors = ArrayList<Long>(swatchCount)
        for (i in 0 until swatchCount) {
            val hue = when (harmonyType) {
                // 0: Smooth analogous sweep
                0 -> (baseHue + i * 24f) % 360f
                // 1: Split-complementary
                1 -> {
                    val offsets = floatArrayOf(0f, 22f, 155f, 205f, 338f, 180f)
                    (baseHue + offsets[i % offsets.size]) % 360f
                }
                // 2: Triadic chord
                2 -> (baseHue + (i % 3) * 120f + (i / 3) * 20f) % 360f
                // 3: Tetradic rectangle
                3 -> {
                    val offsets = floatArrayOf(0f, 60f, 180f, 240f, 30f, 210f)
                    (baseHue + offsets[i % offsets.size]) % 360f
                }
                // 4: Golden-angle hue spiral
                4 -> (baseHue + i * 137.508f) % 360f
                // 5: Warm-cool cinematic contrast
                5 -> if (i % 2 == 0) (20f + rng.nextFloat() * 32f) else (188f + rng.nextFloat() * 35f)
                // 6: Pastel-luxe high brightness
                6 -> (baseHue + i * 48f) % 360f
                // 7: Analogous + complementary accent
                else -> {
                    if (i < swatchCount - 1) (baseHue + i * 18f) % 360f
                    else (baseHue + 180f) % 360f
                }
            }

            val saturation = if (harmonyType == 6) {
                (0.32f + rng.nextFloat() * 0.26f).coerceIn(0.30f, 0.60f)
            } else {
                (0.55f + rng.nextFloat() * 0.38f).coerceIn(0.46f, 0.95f)
            }
            val value = if (harmonyType == 6) {
                (0.92f + rng.nextFloat() * 0.08f).coerceIn(0.90f, 1.0f)
            } else {
                (0.80f + rng.nextFloat() * 0.20f).coerceIn(0.76f, 1.0f)
            }
            colors.add(ColorHarmonyEngine.hsvToArgbLong(hue, saturation, value))
        }
        return colors
    }
}
