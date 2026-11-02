package com.example.model

import android.app.WallpaperManager
import android.content.Context
import com.example.R
import com.example.engine.CoreGenerationSpec
import com.example.engine.DeepBlackRatioEngine
import com.example.engine.WallpaperGeneratorEngine
import kotlin.random.Random

/**
 * Re-exports pure mathematical core types from `com.example.engine` for convenient access
 * across the Android application layer while keeping Android-only app state (`WallpaperTarget`,
 * `MockupOverlayMode`, `GeneratorConfig`, `GeneratedHistoryItem`, `IntervalFormatter`) separated.
 */
typealias PatternCategory = com.example.engine.PatternCategory
typealias ArtPattern = com.example.engine.ArtPattern
typealias PatternWeightMode = com.example.engine.PatternWeightMode
typealias ColorFillMode = com.example.engine.ColorFillMode
typealias PaletteMode = com.example.engine.PaletteMode
typealias PitchBlackMode = com.example.engine.PitchBlackMode
typealias ColorPalette = com.example.engine.ColorPalette

object StarterPalettes {
    val defaults: List<ColorPalette>
        get() = com.example.engine.StarterPalettes.defaults
}

object RandomPaletteGenerator {
    fun generate(seed: Long): List<Long> =
        com.example.engine.RandomPaletteGenerator.generate(seed)
}

enum class WallpaperTarget(
    val id: String,
    val flag: Int,
    val labelRes: Int
) {
    HOME("home", WallpaperManager.FLAG_SYSTEM, R.string.target_home),
    LOCK("lock", WallpaperManager.FLAG_LOCK, R.string.target_lock),
    BOTH("both", WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK, R.string.target_both),
    BOTH_SEPARATE(
        "both_separate",
        WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK,
        R.string.target_both_separate
    );

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

enum class StudioGestureAction(
    val id: String,
    val variationResIds: List<Int>
) {
    SWIPE_LEFT(
        id = "swipe_left",
        variationResIds = listOf(
            R.string.mini_hint_swipe_left_1,
            R.string.mini_hint_swipe_left_2,
            R.string.mini_hint_swipe_left_3,
            R.string.mini_hint_swipe_left_4
        )
    ),
    SWIPE_RIGHT(
        id = "swipe_right",
        variationResIds = listOf(
            R.string.mini_hint_swipe_right_1,
            R.string.mini_hint_swipe_right_2,
            R.string.mini_hint_swipe_right_3,
            R.string.mini_hint_swipe_right_4
        )
    ),
    DOUBLE_TAP(
        id = "double_tap",
        variationResIds = listOf(
            R.string.mini_hint_double_tap_1,
            R.string.mini_hint_double_tap_2,
            R.string.mini_hint_double_tap_3,
            R.string.mini_hint_double_tap_4
        )
    ),
    SWIPE_UP(
        id = "swipe_up",
        variationResIds = listOf(
            R.string.mini_hint_swipe_up_1,
            R.string.mini_hint_swipe_up_2,
            R.string.mini_hint_swipe_up_3,
            R.string.mini_hint_swipe_up_4
        )
    ),
    SWIPE_DOWN(
        id = "swipe_down",
        variationResIds = listOf(
            R.string.mini_hint_swipe_down_1,
            R.string.mini_hint_swipe_down_2,
            R.string.mini_hint_swipe_down_3,
            R.string.mini_hint_swipe_down_4
        )
    );

    companion object {
        fun fromId(id: String): StudioGestureAction? =
            entries.find { it.id == id }
    }
}

data class GeneratorConfig(
    val patternsRandomized: Boolean = true,
    val selectedPatterns: Set<ArtPattern> = CoreGenerationSpec.DEFAULT_SELECTED_PATTERNS,
    val patternWeightMode: PatternWeightMode = PatternWeightMode.NORMAL,
    val colorFillMode: ColorFillMode = ColorFillMode.GRADIENT,
    val paletteMode: PaletteMode = PaletteMode.RANDOMIZED,
    val selectedPaletteIds: Set<String> = setOf("obsidian_aurora"),
    val pitchBlackMode: PitchBlackMode = PitchBlackMode.SINGLE,
    val pitchBlackRatio: Float = DEFAULT_PITCH_BLACK_RATIO,
    val pitchBlackRangeMin: Float = 0.75f,
    val pitchBlackRangeMax: Float = 0.90f,
    val manualTarget: WallpaperTarget = WallpaperTarget.BOTH,
    val autoRotateEnabled: Boolean = false,
    val intervalMinutes: Int = 60,
    val autoTarget: WallpaperTarget = WallpaperTarget.BOTH,
    val nextRunEpochMillis: Long = 0L
) {
    fun toCoreSpec(): CoreGenerationSpec = CoreGenerationSpec(
        patternsRandomized = patternsRandomized,
        selectedPatterns = selectedPatterns,
        patternWeightMode = patternWeightMode,
        colorFillMode = colorFillMode,
        paletteMode = paletteMode,
        selectedPaletteIds = selectedPaletteIds,
        pitchBlackMode = pitchBlackMode,
        pitchBlackRatio = pitchBlackRatio,
        pitchBlackRangeMin = pitchBlackRangeMin,
        pitchBlackRangeMax = pitchBlackRangeMax
    )

    fun resolvePitchBlackRatio(rng: Random): Float =
        DeepBlackRatioEngine.resolveTargetBlackRatio(
            mode = pitchBlackMode,
            singleRatio = pitchBlackRatio,
            rangeMin = pitchBlackRangeMin,
            rangeMax = pitchBlackRangeMax,
            rng = rng
        )

    companion object {
        const val DEFAULT_PITCH_BLACK_RATIO: Float = DeepBlackRatioEngine.DEFAULT_PITCH_BLACK_RATIO
        const val MIN_RANDOM_INTRICACY: Float = WallpaperGeneratorEngine.MIN_RANDOM_INTRICACY
        const val MAX_RANDOM_INTRICACY: Float = WallpaperGeneratorEngine.MAX_RANDOM_INTRICACY

        fun sampleIntricacy(rng: Random): Float =
            WallpaperGeneratorEngine.sampleIntricacy(rng)

        fun sampleIntricacyForSeed(seed: Long): Float =
            WallpaperGeneratorEngine.sampleIntricacyForSeed(seed)
    }
}

data class GeneratedHistoryItem(
    val id: String,
    val seed: Long,
    val pattern: ArtPattern,
    val colorFillMode: ColorFillMode,
    val paletteColors: List<Long>,
    val pitchBlackRatio: Float,
    val intricacy: Float,
    val timestampMillis: Long,
    val wasAutoScheduled: Boolean,
    val patternWeightMode: PatternWeightMode = PatternWeightMode.NORMAL
)

object IntervalFormatter {
    val presetMinutes: List<Int> = listOf(1, 5, 15, 30, 60, 180, 360, 720, 1440)

    fun formatMinutesShort(context: Context, minutes: Int): String {
        return when {
            minutes < 60 -> context.getString(R.string.interval_short_minutes, minutes)
            minutes % 1440 == 0 -> context.getString(R.string.interval_short_days, minutes / 1440)
            minutes % 60 == 0 -> context.getString(R.string.interval_short_hours, minutes / 60)
            else -> context.getString(
                R.string.interval_short_hours_minutes,
                minutes / 60,
                minutes % 60
            )
        }
    }

    fun formatMinutesDescriptive(context: Context, minutes: Int): String {
        val res = context.resources
        return when {
            minutes < 60 -> res.getQuantityString(R.plurals.interval_desc_minutes, minutes, minutes)
            minutes % 1440 == 0 -> {
                val days = minutes / 1440
                res.getQuantityString(R.plurals.interval_desc_days, days, days)
            }
            minutes % 60 == 0 -> {
                val hours = minutes / 60
                res.getQuantityString(R.plurals.interval_desc_hours, hours, hours)
            }
            else -> formatMinutesShort(context, minutes)
        }
    }

    fun formatMinutesShort(minutes: Int): String {
        return when {
            minutes < 60 -> "${minutes}m"
            minutes % 1440 == 0 -> "${minutes / 1440}d"
            minutes % 60 == 0 -> "${minutes / 60}h"
            else -> "${minutes / 60}h ${minutes % 60}m"
        }
    }

    fun formatMinutesDescriptive(minutes: Int): String {
        return when (minutes) {
            1 -> "1 Minute"
            5 -> "5 Minutes"
            15 -> "15 Minutes"
            30 -> "30 Minutes"
            60 -> "1 Hour"
            180 -> "3 Hours"
            360 -> "6 Hours"
            720 -> "12 Hours"
            1440 -> "1 Day"
            else -> when {
                minutes < 60 -> "$minutes Minutes"
                minutes % 1440 == 0 -> "${minutes / 1440} Days"
                minutes % 60 == 0 -> "${minutes / 60} Hours"
                else -> "${minutes / 60}h ${minutes % 60}m"
            }
        }
    }
}
