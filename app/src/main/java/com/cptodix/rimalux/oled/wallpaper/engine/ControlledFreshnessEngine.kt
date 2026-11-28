package com.cptodix.rimalux.oled.wallpaper.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Portable, serializable anti-repetition state shared across interactive preview browsing and
 * background auto-change rotations. Prevents Birthday-Paradox repeats, same-category pattern runs,
 * repeated HSV harmony recipes, and adjacent hue-sector clustering.
 *
 * PORTABILITY CONTRACT:
 * 100% pure Kotlin (`kotlin.*` only).
 */
data class FreshnessLedger(
    val seenPatternIds: List<String> = emptyList(),
    val recentPatternIds: List<String> = emptyList(),
    val recentCategoryIds: List<String> = emptyList(),
    val seenPaletteIds: List<String> = emptyList(),
    val recentPaletteIds: List<String> = emptyList(),
    val recentHarmonyTypes: List<Int> = emptyList(),
    val recentHueSectors: List<Int> = emptyList(),
    val lastIntricacy: Float = -1f
)

data class FreshnessSynthesisOutcome(
    val wallpaper: SynthesizedWallpaper,
    val updatedLedger: FreshnessLedger
)

data class FreshnessPairOutcome(
    val primary: SynthesizedWallpaper,
    val secondary: SynthesizedWallpaper,
    val updatedLedger: FreshnessLedger
)

/**
 * Controlled shuffle-bag & hue/category pacing synthesizer in pure Kotlin math.
 */
object ControlledFreshnessEngine {

    const val HUE_SECTOR_COUNT: Int = 12
    private const val MAX_RECENT_PATTERNS: Int = 18
    private const val MAX_RECENT_CATEGORIES: Int = 3
    private const val MAX_RECENT_PALETTES: Int = 12
    private const val MAX_RECENT_HARMONIES: Int = 7
    private const val MAX_RECENT_HUE_SECTORS: Int = 4

    fun synthesizeControlled(
        seed: Long,
        spec: CoreGenerationSpec,
        ledger: FreshnessLedger,
        availablePalettes: List<ColorPalette> = StarterPalettes.defaults,
        aspectRatio: Float = 2.15f,
        explicitColors: List<Long>? = null,
        excludePattern: ArtPattern? = null,
        excludeColors: List<Long>? = null,
        rng: Random = Random(seed xor 0x6A09E667F3BCC908L)
    ): FreshnessSynthesisOutcome {
        val (pattern, afterPatternLedger) = pickNextPattern(
            rng = rng,
            patternsRandomized = spec.patternsRandomized,
            selectedPatterns = spec.selectedPatterns,
            ledger = ledger,
            excludePattern = excludePattern
        )

        val (colors, afterColorLedger) = if (!explicitColors.isNullOrEmpty()) {
            val updated = recordColorsInLedger(
                ledger = afterPatternLedger,
                paletteId = null,
                colors = explicitColors,
                harmonyType = null
            )
            explicitColors to updated
        } else {
            pickNextColors(
                seed = seed,
                rng = rng,
                paletteMode = spec.paletteMode,
                selectedPaletteIds = spec.selectedPaletteIds,
                availablePalettes = availablePalettes,
                ledger = afterPatternLedger,
                excludeColors = excludeColors
            )
        }

        val resolvedBlackRatio = DeepBlackRatioEngine.resolveTargetBlackRatio(
            mode = spec.pitchBlackMode,
            singleRatio = spec.pitchBlackRatio,
            rangeMin = spec.pitchBlackRangeMin,
            rangeMax = spec.pitchBlackRangeMax,
            rng = rng
        )
        val intricacy = sampleControlledIntricacy(rng, afterColorLedger.lastIntricacy)
        val finalLedger = afterColorLedger.copy(lastIntricacy = intricacy)

        val wallpaper = WallpaperGeneratorEngine.synthesizeFromParameters(
            seed = seed,
            pattern = pattern,
            paletteColors = colors,
            colorFillMode = spec.colorFillMode,
            pitchBlackRatio = resolvedBlackRatio,
            intricacy = intricacy,
            aspectRatio = aspectRatio,
            patternWeightMode = spec.patternWeightMode
        )
        return FreshnessSynthesisOutcome(wallpaper = wallpaper, updatedLedger = finalLedger)
    }

    fun synthesizeControlledPair(
        primarySeed: Long,
        secondarySeed: Long,
        spec: CoreGenerationSpec,
        ledger: FreshnessLedger,
        availablePalettes: List<ColorPalette> = StarterPalettes.defaults,
        aspectRatio: Float = 2.15f,
        rng: Random = Random(primarySeed xor secondarySeed)
    ): FreshnessPairOutcome {
        val safeSecondarySeed = if (secondarySeed != 0L && secondarySeed != primarySeed) {
            secondarySeed
        } else {
            WallpaperGeneratorEngine.nextDistinctSeed(rng, setOf(0L, primarySeed))
        }

        val first = synthesizeControlled(
            seed = primarySeed,
            spec = spec,
            ledger = ledger,
            availablePalettes = availablePalettes,
            aspectRatio = aspectRatio,
            rng = rng
        )
        val second = synthesizeControlled(
            seed = safeSecondarySeed,
            spec = spec,
            ledger = first.updatedLedger,
            availablePalettes = availablePalettes,
            aspectRatio = aspectRatio,
            excludePattern = first.wallpaper.pattern,
            excludeColors = first.wallpaper.paletteColors,
            rng = rng
        )
        return FreshnessPairOutcome(
            primary = first.wallpaper,
            secondary = second.wallpaper,
            updatedLedger = second.updatedLedger
        )
    }

    fun pickNextPattern(
        rng: Random,
        patternsRandomized: Boolean,
        selectedPatterns: Set<ArtPattern>,
        ledger: FreshnessLedger,
        excludePattern: ArtPattern? = null
    ): Pair<ArtPattern, FreshnessLedger> {
        val basePool = if (patternsRandomized || selectedPatterns.isEmpty()) {
            ArtPattern.entries
        } else {
            selectedPatterns.toList()
        }
        if (basePool.size == 1) {
            val sole = basePool.first()
            return sole to recordPatternInLedger(ledger, sole, poolIds = listOf(sole.id), resetSeen = false)
        }

        val basePoolIds = basePool.map { it.id }.toSet()
        val seenInPool = ledger.seenPatternIds.filter { it in basePoolIds }.toSet()
        var resetSeen = false
        var candidates = basePool.filter { it.id !in seenInPool && it != excludePattern }

        if (candidates.isEmpty()) {
            // Current shuffle-bag sweep exhausted: start a fresh cycle while protecting recent history
            resetSeen = true
            val cooldownSize = min(basePool.size - 1, min(MAX_RECENT_PATTERNS, max(1, basePool.size / 3)))
            val recentBlock = ledger.recentPatternIds.takeLast(cooldownSize).toSet()
            candidates = basePool.filter { it.id !in recentBlock && it != excludePattern }
            if (candidates.isEmpty()) {
                candidates = if (excludePattern != null) {
                    basePool.filterNot { it == excludePattern }.ifEmpty { basePool }
                } else {
                    basePool
                }
            }
        }

        // Category cool-down: prefer patterns from categories not used in the immediate previous generations
        val lastCatId = ledger.recentCategoryIds.lastOrNull()
        val lastTwoCatIds = ledger.recentCategoryIds.takeLast(2).toSet()
        val distinctCategoryCandidates = candidates.filter { it.category.id !in lastTwoCatIds }
            .ifEmpty { candidates.filter { it.category.id != lastCatId } }
            .ifEmpty { candidates }

        val chosen = distinctCategoryCandidates[rng.nextInt(distinctCategoryCandidates.size)]
        val updatedLedger = recordPatternInLedger(
            ledger = ledger,
            pattern = chosen,
            poolIds = basePool.map { it.id },
            resetSeen = resetSeen
        )
        return chosen to updatedLedger
    }

    fun pickNextColors(
        seed: Long,
        rng: Random,
        paletteMode: PaletteMode,
        selectedPaletteIds: Set<String>,
        availablePalettes: List<ColorPalette>,
        ledger: FreshnessLedger,
        excludeColors: List<Long>? = null
    ): Pair<List<Long>, FreshnessLedger> {
        return when (paletteMode) {
            PaletteMode.RANDOMIZED -> {
                val chosenHarmony = pickNextHarmonyType(rng, ledger.recentHarmonyTypes)
                val chosenHueDeg = pickFreshBaseHue(rng, ledger.recentHueSectors)
                val colors = RandomPaletteGenerator.generateControlled(
                    seed = seed xor rng.nextLong(),
                    harmonyType = chosenHarmony,
                    baseHueDeg = chosenHueDeg
                )
                val updatedLedger = recordColorsInLedger(
                    ledger = ledger,
                    paletteId = null,
                    colors = colors,
                    harmonyType = chosenHarmony,
                    anchorHueDeg = chosenHueDeg
                )
                colors to updatedLedger
            }
            PaletteMode.PALETTE_LIST -> {
                val activePalettes = availablePalettes.filter { it.id in selectedPaletteIds }
                    .ifEmpty { availablePalettes }
                    .ifEmpty { StarterPalettes.defaults }
                if (activePalettes.size == 1) {
                    val sole = activePalettes.first()
                    val updated = recordColorsInLedger(
                        ledger = ledger,
                        paletteId = sole.id,
                        colors = sole.colors,
                        harmonyType = null,
                        resetSeenPalette = false
                    )
                    return sole.colors to updated
                }

                val activeIds = activePalettes.map { it.id }.toSet()
                val seenInActive = ledger.seenPaletteIds.filter { it in activeIds }.toSet()
                var resetSeen = false
                var candidates = activePalettes.filter {
                    it.id !in seenInActive && (excludeColors == null || it.colors != excludeColors)
                }

                if (candidates.isEmpty()) {
                    resetSeen = true
                    val cooldownSize = min(activePalettes.size - 1, min(MAX_RECENT_PALETTES, max(1, activePalettes.size / 3)))
                    val recentBlock = ledger.recentPaletteIds.takeLast(cooldownSize).toSet()
                    candidates = activePalettes.filter {
                        it.id !in recentBlock && (excludeColors == null || it.colors != excludeColors)
                    }
                    if (candidates.isEmpty()) {
                        candidates = activePalettes.filterNot { excludeColors != null && it.colors == excludeColors }
                            .ifEmpty { activePalettes }
                    }
                }

                // Hue-region separation: avoid consecutive palettes anchored in the same hue pocket when alternatives exist
                val lastSector = ledger.recentHueSectors.lastOrNull()
                val hueSeparated = if (lastSector != null && candidates.size > 1) {
                    val lastHueCenter = sectorToCenterDeg(lastSector)
                    candidates.filter { pal ->
                        val domHue = computeDominantHueDeg(pal.colors)
                        domHue == null || hueDistanceDeg(domHue, lastHueCenter) >= 42f
                    }.ifEmpty { candidates }
                } else {
                    candidates
                }

                val chosen = hueSeparated[rng.nextInt(hueSeparated.size)]
                val updated = recordColorsInLedger(
                    ledger = ledger,
                    paletteId = chosen.id,
                    colors = chosen.colors,
                    harmonyType = null,
                    resetSeenPalette = resetSeen
                )
                chosen.colors to updated
            }
        }
    }

    fun recordManualSelection(
        ledger: FreshnessLedger,
        pattern: ArtPattern? = null,
        paletteId: String? = null,
        colors: List<Long>? = null,
        intricacy: Float? = null
    ): FreshnessLedger {
        var next = ledger
        if (pattern != null) {
            next = recordPatternInLedger(next, pattern, poolIds = null, resetSeen = false)
        }
        if (paletteId != null || !colors.isNullOrEmpty()) {
            next = recordColorsInLedger(
                ledger = next,
                paletteId = paletteId,
                colors = colors ?: emptyList(),
                harmonyType = null
            )
        }
        if (intricacy != null) {
            next = next.copy(lastIntricacy = intricacy)
        }
        return next
    }

    fun sampleControlledIntricacy(rng: Random, lastIntricacy: Float): Float {
        var best = WallpaperGeneratorEngine.sampleIntricacy(rng)
        if (lastIntricacy < 0f) return best
        repeat(4) {
            if (abs(best - lastIntricacy) >= 0.04f) return best
            best = WallpaperGeneratorEngine.sampleIntricacy(rng)
        }
        return best
    }

    private fun pickNextHarmonyType(rng: Random, recentHarmonies: List<Int>): Int {
        val count = RandomPaletteGenerator.HARMONY_COUNT
        val last = recentHarmonies.lastOrNull()
        val recentSet = recentHarmonies.toSet()
        val unseen = (0 until count).filter { it !in recentSet && it != last }
        if (unseen.isNotEmpty()) {
            return unseen[rng.nextInt(unseen.size)]
        }
        val pool = (0 until count).filter { it != last }.ifEmpty { (0 until count).toList() }
        return pool[rng.nextInt(pool.size)]
    }

    private fun pickFreshBaseHue(rng: Random, recentSectors: List<Int>): Float {
        val blocked = recentSectors.takeLast(MAX_RECENT_HUE_SECTORS)
        val lastSector = blocked.lastOrNull()
        val eligible = (0 until HUE_SECTOR_COUNT).filter { sector ->
            sector !in blocked && (lastSector == null || sectorCircularDistance(sector, lastSector) >= 2)
        }.ifEmpty {
            (0 until HUE_SECTOR_COUNT).filter { sector ->
                lastSector == null || sector != lastSector
            }
        }
        val chosenSector = eligible[rng.nextInt(eligible.size)]
        val sectorWidth = 360f / HUE_SECTOR_COUNT
        return (chosenSector * sectorWidth + rng.nextFloat() * sectorWidth) % 360f
    }

    private fun recordPatternInLedger(
        ledger: FreshnessLedger,
        pattern: ArtPattern,
        poolIds: List<String>?,
        resetSeen: Boolean
    ): FreshnessLedger {
        val baseSeen = if (resetSeen) {
            emptyList()
        } else {
            val poolSet = poolIds?.toSet()
            if (poolSet != null) ledger.seenPatternIds.filter { it in poolSet } else ledger.seenPatternIds
        }
        val nextSeen = (baseSeen + pattern.id).distinct()
        val nextRecentPatterns = (ledger.recentPatternIds.filterNot { it == pattern.id } + pattern.id)
            .takeLast(MAX_RECENT_PATTERNS)
        val nextRecentCategories = (ledger.recentCategoryIds + pattern.category.id)
            .takeLast(MAX_RECENT_CATEGORIES)
        return ledger.copy(
            seenPatternIds = nextSeen,
            recentPatternIds = nextRecentPatterns,
            recentCategoryIds = nextRecentCategories
        )
    }

    private fun recordColorsInLedger(
        ledger: FreshnessLedger,
        paletteId: String?,
        colors: List<Long>,
        harmonyType: Int?,
        anchorHueDeg: Float? = null,
        resetSeenPalette: Boolean = false
    ): FreshnessLedger {
        val nextSeenPalettes = if (paletteId != null) {
            val base = if (resetSeenPalette) emptyList() else ledger.seenPaletteIds
            (base + paletteId).distinct()
        } else {
            ledger.seenPaletteIds
        }
        val nextRecentPalettes = if (paletteId != null) {
            (ledger.recentPaletteIds.filterNot { it == paletteId } + paletteId).takeLast(MAX_RECENT_PALETTES)
        } else {
            ledger.recentPaletteIds
        }
        val nextHarmonies = if (harmonyType != null) {
            (ledger.recentHarmonyTypes.filterNot { it == harmonyType } + harmonyType).takeLast(MAX_RECENT_HARMONIES)
        } else {
            ledger.recentHarmonyTypes
        }
        val effectiveHue = anchorHueDeg ?: computeDominantHueDeg(colors)
        val nextSectors = if (effectiveHue != null) {
            val sector = hueToSector(effectiveHue)
            (ledger.recentHueSectors + sector).takeLast(MAX_RECENT_HUE_SECTORS)
        } else {
            ledger.recentHueSectors
        }
        return ledger.copy(
            seenPaletteIds = nextSeenPalettes,
            recentPaletteIds = nextRecentPalettes,
            recentHarmonyTypes = nextHarmonies,
            recentHueSectors = nextSectors
        )
    }

    fun hueToSector(hueDeg: Float): Int {
        val normalized = ((hueDeg % 360f) + 360f) % 360f
        return ((normalized / (360f / HUE_SECTOR_COUNT)).toInt()).coerceIn(0, HUE_SECTOR_COUNT - 1)
    }

    private fun sectorToCenterDeg(sector: Int): Float {
        val width = 360f / HUE_SECTOR_COUNT
        return sector * width + width * 0.5f
    }

    private fun sectorCircularDistance(a: Int, b: Int): Int {
        val diff = abs(a - b)
        return min(diff, HUE_SECTOR_COUNT - diff)
    }

    fun hueDistanceDeg(a: Float, b: Float): Float {
        val diff = abs(((a - b) % 360f + 360f) % 360f)
        return min(diff, 360f - diff)
    }

    /**
     * Extracts the saturation-weighted dominant hue in `[0, 360)` for chromatic palettes,
     * or returns `null` for neutral/achromatic monochrome palettes.
     */
    fun computeDominantHueDeg(colors: List<Long>): Float? {
        if (colors.isEmpty()) return null
        var bestSat = 0f
        var bestHue: Float? = null
        for ((index, argb) in colors.withIndex()) {
            val r = ((argb shr 16) and 0xFF) / 255f
            val g = ((argb shr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f
            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val delta = maxC - minC
            val sat = if (maxC > 1e-4f) delta / maxC else 0f
            if (delta < 0.06f || sat < 0.14f) continue
            val hue = when {
                maxC == r -> 60f * (((g - b) / delta).let { if (it < 0f) it + 6f else it })
                maxC == g -> 60f * ((b - r) / delta + 2f)
                else -> 60f * ((r - g) / delta + 4f)
            }.let { ((it % 360f) + 360f) % 360f }
            val weight = sat * if (index == 0) 1.25f else 1.0f
            if (weight > bestSat) {
                bestSat = weight
                bestHue = hue
            }
        }
        return bestHue
    }
}
