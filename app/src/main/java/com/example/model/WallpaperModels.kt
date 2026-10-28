package com.example.model

import android.app.WallpaperManager
import android.graphics.Color as AndroidColor
import com.example.R
import kotlin.random.Random

enum class ArtStyle(
    val id: String,
    val titleRes: Int,
    val subtitleRes: Int
) {
    SILK_RIBBONS("silk_ribbons", R.string.style_silk_ribbons, R.string.style_silk_ribbons_sub),
    CRYSTAL_VORONOI("crystal_voronoi", R.string.style_crystal_voronoi, R.string.style_crystal_voronoi_sub),
    SACRED_MANDALA("sacred_mandala", R.string.style_sacred_mandala, R.string.style_sacred_mandala_sub),
    TOPO_CONTOURS("topo_contours", R.string.style_topo_contours, R.string.style_topo_contours_sub),
    GUILLOCHE_ROSETTE("guilloche_rosette", R.string.style_guilloche_rosette, R.string.style_guilloche_rosette_sub),
    PENROSE_QUASICRYSTAL("penrose_quasicrystal", R.string.style_penrose_quasicrystal, R.string.style_penrose_quasicrystal_sub),
    ISOMETRIC_MONUMENT("isometric_monument", R.string.style_isometric_monument, R.string.style_isometric_monument_sub),
    ORIGAMI_MIURA("origami_miura", R.string.style_origami_miura, R.string.style_origami_miura_sub),
    FIBONACCI_LOTUS("fibonacci_lotus", R.string.style_fibonacci_lotus, R.string.style_fibonacci_lotus_sub),
    ART_DECO_SUNBURST("art_deco_sunburst", R.string.style_art_deco_sunburst, R.string.style_art_deco_sunburst_sub),
    GIRIH_STARWORK("girih_starwork", R.string.style_girih_starwork, R.string.style_girih_starwork_sub),
    OP_ART_GRAVITY("op_art_gravity", R.string.style_op_art_gravity, R.string.style_op_art_gravity_sub),
    HARMONIC_CARDIOID("harmonic_cardioid", R.string.style_harmonic_cardioid, R.string.style_harmonic_cardioid_sub),
    BAUHAUS_CONSTRUCT("bauhaus_construct", R.string.style_bauhaus_construct, R.string.style_bauhaus_construct_sub),
    FRACTAL_LATTICE("fractal_lattice", R.string.style_fractal_lattice, R.string.style_fractal_lattice_sub),
    HORIZON_WAVES("horizon_waves", R.string.style_horizon_waves, R.string.style_horizon_waves_sub);

    companion object {
        fun fromId(id: String): ArtStyle? = entries.find { it.id == id }
    }
}

enum class ColorFillMode(val id: String) {
    SOLID("solid"),
    GRADIENT("gradient");

    companion object {
        fun fromId(id: String): ColorFillMode =
            entries.find { it.id == id } ?: GRADIENT
    }
}

enum class PaletteMode(val id: String) {
    RANDOMIZED("randomized"),
    PALETTE_LIST("palette_list");

    companion object {
        fun fromId(id: String): PaletteMode =
            entries.find { it.id == id } ?: RANDOMIZED
    }
}

enum class WallpaperTarget(
    val id: String,
    val flag: Int,
    val labelRes: Int
) {
    HOME("home", WallpaperManager.FLAG_SYSTEM, R.string.target_home),
    LOCK("lock", WallpaperManager.FLAG_LOCK, R.string.target_lock),
    BOTH("both", WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK, R.string.target_both);

    companion object {
        fun fromId(id: String): WallpaperTarget =
            entries.find { it.id == id } ?: BOTH
    }
}

enum class MockupOverlayMode {
    NONE,
    LOCK_SCREEN,
    HOME_GRID
}

data class ColorPalette(
    val id: String,
    val name: String,
    val colors: List<Long>
)

object StarterPalettes {
    val defaults: List<ColorPalette> = listOf(
        ColorPalette(
            id = "obsidian_aurora",
            name = "Aurora Silk",
            colors = listOf(0xFF00F5D4, 0xFF00BBF9, 0xFF7B2CBF, 0xFFF15BB5, 0xFFFFD166)
        ),
        ColorPalette(
            id = "imperial_gold",
            name = "Imperial Gold",
            colors = listOf(0xFFFFD166, 0xFFF4A261, 0xFFE76F51, 0xFF2A9D8F, 0xFFE9C46A)
        ),
        ColorPalette(
            id = "bauhaus_studio",
            name = "Bauhaus Modern",
            colors = listOf(0xFFE63946, 0xFFFFB703, 0xFF219EBC, 0xFFF1FAEE)
        ),
        ColorPalette(
            id = "nordic_glacier",
            name = "Nordic Glacier",
            colors = listOf(0xFFE0FBFC, 0xFF98C1D9, 0xFF3D5A80, 0xFF48CAE4)
        ),
        ColorPalette(
            id = "titanium_mono",
            name = "Silver Silk",
            colors = listOf(0xFFFFFFFF, 0xFFD4D4D8, 0xFF9CA3AF, 0xFF52525B)
        )
    )
}

object RandomPaletteGenerator {
    /**
     * Synthesizes harmonious, curated designer color palettes using HSV color-theory rules.
     */
    fun generate(seed: Long): List<Long> {
        val rng = Random(seed)
        val swatchCount = 4 + rng.nextInt(2)
        val baseHue = rng.nextFloat() * 360f
        val harmonyType = rng.nextInt(4)

        val colors = ArrayList<Long>(swatchCount)
        for (i in 0 until swatchCount) {
            val hue = when (harmonyType) {
                0 -> (baseHue + i * 26f) % 360f
                1 -> {
                    val offsets = floatArrayOf(0f, 24f, 165f, 195f, 340f)
                    (baseHue + offsets[i % offsets.size]) % 360f
                }
                2 -> (baseHue + (i % 3) * 120f + (i / 3) * 22f) % 360f
                else -> {
                    if (i < swatchCount - 1) (baseHue + i * 20f) % 360f
                    else (baseHue + 180f) % 360f
                }
            }
            val saturation = (0.55f + rng.nextFloat() * 0.36f).coerceIn(0.45f, 0.94f)
            val value = (0.80f + rng.nextFloat() * 0.20f).coerceIn(0.76f, 1.0f)
            val rgbInt = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
            colors.add(0xFF000000L or (rgbInt.toLong() and 0xFFFFFFL))
        }
        return colors
    }
}

data class GeneratorConfig(
    val stylesRandomized: Boolean = true,
    val selectedStyles: Set<ArtStyle> = setOf(
        ArtStyle.SILK_RIBBONS,
        ArtStyle.CRYSTAL_VORONOI,
        ArtStyle.SACRED_MANDALA,
        ArtStyle.PENROSE_QUASICRYSTAL
    ),
    val colorFillMode: ColorFillMode = ColorFillMode.GRADIENT,
    val paletteMode: PaletteMode = PaletteMode.RANDOMIZED,
    val selectedPaletteIds: Set<String> = setOf("obsidian_aurora"),
    val pitchBlackRatio: Float = 0.65f,
    val intricacy: Float = 0.60f,
    val autoApplyOnGenerate: Boolean = true,
    val manualTarget: WallpaperTarget = WallpaperTarget.BOTH,
    val autoRotateEnabled: Boolean = false,
    val intervalMinutes: Int = 60,
    val autoTarget: WallpaperTarget = WallpaperTarget.BOTH,
    val nextRunEpochMillis: Long = 0L
)

data class GeneratedHistoryItem(
    val id: String,
    val seed: Long,
    val style: ArtStyle,
    val colorFillMode: ColorFillMode,
    val paletteColors: List<Long>,
    val pitchBlackRatio: Float,
    val intricacy: Float,
    val timestampMillis: Long,
    val wasAutoScheduled: Boolean
)

object IntervalFormatter {
    val presetMinutes: List<Int> = listOf(1, 5, 15, 30, 60, 180, 360, 720, 1440)

    fun formatMinutesShort(minutes: Int): String {
        return when {
            minutes < 60 -> "${minutes}m"
            minutes % 1440 == 0 -> "${minutes / 1440}d"
            minutes % 60 == 0 -> "${minutes / 60}h"
            else -> "${minutes / 60}h ${minutes % 60}m"
        }
    }

    fun formatCountdown(totalSeconds: Long): String {
        val clamped = totalSeconds.coerceAtLeast(0L)
        val hours = clamped / 3600
        val minutes = (clamped % 3600) / 60
        val seconds = clamped % 60
        return if (hours > 0) {
            "%dh %02dm".format(hours, minutes)
        } else {
            "%02dm %02ds".format(minutes, seconds)
        }
    }
}
