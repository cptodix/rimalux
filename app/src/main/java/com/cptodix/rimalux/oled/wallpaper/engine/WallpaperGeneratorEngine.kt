package com.cptodix.rimalux.oled.wallpaper.engine

import kotlin.random.Random

/**
 * High-level, platform-independent entry point for synthesizing complete mathematical vector
 * wallpapers. Orchestrates pattern selection, HSV/curated palette harmony selection, intricacy
 * sampling, 64-pattern vector generation, 2D coverage calibration, and True Black Ratio resolution.
 *
 * PORTABILITY CONTRACT:
 * 100% pure Kotlin (`kotlin.*` only). Can be invoked directly from any platform or test runner
 * without Android Context, ViewModel, DataStore, or OS schedulers.
 */
object WallpaperGeneratorEngine {

    const val MIN_RANDOM_INTRICACY: Float = 0.40f
    const val MAX_RANDOM_INTRICACY: Float = 0.60f

    /**
     * Samples geometric detail & intricacy uniformly in `[0.40f, 0.60f]` (1% steps).
     */
    fun sampleIntricacy(rng: Random): Float {
        return ((40 + rng.nextInt(21)) / 100f)
            .coerceIn(MIN_RANDOM_INTRICACY, MAX_RANDOM_INTRICACY)
    }

    /**
     * Deterministically samples intricacy in `[0.40f, 0.60f]` from a wallpaper [seed].
     */
    fun sampleIntricacyForSeed(seed: Long): Float {
        return sampleIntricacy(Random(seed xor 0x243F6A8885A308D3L))
    }

    /**
     * Generates a non-zero positive 47-bit seed guaranteed not to collide with [excludedSeeds].
     */
    fun nextDistinctSeed(
        rng: Random,
        excludedSeeds: Set<Long> = emptySet()
    ): Long {
        repeat(16) { attempt ->
            val raw = (rng.nextLong() and 0x7FFFFFFFFFFFL)
                .takeIf { it != 0L } ?: (0x4A91B23DL + attempt)
            if (raw !in excludedSeeds) return raw
        }
        var fallback = (rng.nextLong() and 0x7FFFFFFFFFFFL).coerceAtLeast(1L)
        while (fallback in excludedSeeds) {
            fallback = ((fallback xor 0x2F6E2B15L) + 1L) and 0x7FFFFFFFFFFFL
            if (fallback == 0L) fallback = 1L
        }
        return fallback
    }

    /**
     * Synthesizes a complete [SynthesizedWallpaper] from [seed] and [spec].
     */
    fun synthesize(
        seed: Long,
        spec: CoreGenerationSpec,
        availablePalettes: List<ColorPalette> = StarterPalettes.defaults,
        aspectRatio: Float = 2.15f,
        explicitColors: List<Long>? = null,
        excludePattern: ArtPattern? = null,
        excludeColors: List<Long>? = null,
        paletteRng: Random = Random(seed xor 0x6A09E667F3BCC908L),
        ratioRng: Random = Random(seed xor 0x517CC1B727220A95L),
        intricacyOverride: Float? = null
    ): SynthesizedWallpaper {
        val pattern = MathPatternGenerator.resolvePatternForSeed(
            seed = seed,
            patternsRandomized = spec.patternsRandomized,
            selectedPatterns = spec.selectedPatterns,
            excludePattern = excludePattern
        )
        val colors = if (!explicitColors.isNullOrEmpty()) {
            explicitColors
        } else {
            ColorHarmonyEngine.selectColorsForSeed(
                seed = seed,
                rng = paletteRng,
                paletteMode = spec.paletteMode,
                selectedPaletteIds = spec.selectedPaletteIds,
                availablePalettes = availablePalettes,
                excludeColors = excludeColors
            )
        }
        val resolvedBlackRatio = DeepBlackRatioEngine.resolveTargetBlackRatio(
            mode = spec.pitchBlackMode,
            singleRatio = spec.pitchBlackRatio,
            rangeMin = spec.pitchBlackRangeMin,
            rangeMax = spec.pitchBlackRangeMax,
            rng = ratioRng
        )
        val intricacy = intricacyOverride ?: sampleIntricacyForSeed(seed)

        return synthesizeFromParameters(
            seed = seed,
            pattern = pattern,
            paletteColors = colors,
            colorFillMode = spec.colorFillMode,
            pitchBlackRatio = resolvedBlackRatio,
            intricacy = intricacy,
            aspectRatio = aspectRatio,
            patternWeightMode = spec.patternWeightMode
        )
    }

    /**
     * Synthesizes a [SynthesizedWallpaper] from already-resolved pattern, colors, ratio, intricacy,
     * and [patternWeightMode].
     */
    fun synthesizeFromParameters(
        seed: Long,
        pattern: ArtPattern,
        paletteColors: List<Long>,
        colorFillMode: ColorFillMode,
        pitchBlackRatio: Float,
        intricacy: Float,
        aspectRatio: Float,
        patternWeightMode: PatternWeightMode = PatternWeightMode.NORMAL
    ): SynthesizedWallpaper {
        val clampedRatio = DeepBlackRatioEngine.clampPitchBlackRatio(pitchBlackRatio)
        val safeColors = paletteColors.ifEmpty { StarterPalettes.defaults.first().colors }
        val scene = MathPatternGenerator.generate(
            seed = seed,
            pattern = pattern,
            intricacy = intricacy,
            aspectRatio = aspectRatio,
            patternWeightMode = patternWeightMode
        )
        return SynthesizedWallpaper(
            seed = seed,
            pattern = pattern,
            paletteColors = safeColors,
            colorFillMode = colorFillMode,
            pitchBlackRatio = clampedRatio,
            intricacy = intricacy,
            aspectRatio = scene.aspectRatio,
            scene = scene,
            ratioStats = scene.computeStats(clampedRatio),
            patternWeightMode = patternWeightMode
        )
    }

    /**
     * Synthesizes two distinct wallpapers (e.g., for separate Home Screen and Lock Screen targets),
     * ensuring distinct seeds, non-repeating patterns when >1 pattern is available, and non-repeating
     * palettes when >1 custom palette is selected.
     */
    fun synthesizeDistinctPair(
        primarySeed: Long,
        secondarySeed: Long,
        spec: CoreGenerationSpec,
        availablePalettes: List<ColorPalette> = StarterPalettes.defaults,
        aspectRatio: Float = 2.15f,
        rng: Random = Random(primarySeed xor secondarySeed)
    ): Pair<SynthesizedWallpaper, SynthesizedWallpaper> {
        val safeSecondarySeed = if (secondarySeed != 0L && secondarySeed != primarySeed) {
            secondarySeed
        } else {
            nextDistinctSeed(rng, setOf(0L, primarySeed))
        }

        val primary = synthesize(
            seed = primarySeed,
            spec = spec,
            availablePalettes = availablePalettes,
            aspectRatio = aspectRatio,
            paletteRng = rng,
            ratioRng = rng,
            intricacyOverride = sampleIntricacy(rng)
        )
        val secondary = synthesize(
            seed = safeSecondarySeed,
            spec = spec,
            availablePalettes = availablePalettes,
            aspectRatio = aspectRatio,
            excludePattern = primary.pattern,
            excludeColors = primary.paletteColors,
            paletteRng = rng,
            ratioRng = rng,
            intricacyOverride = sampleIntricacy(rng)
        )
        return primary to secondary
    }
}
