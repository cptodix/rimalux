package com.example

import com.example.engine.ArtPattern
import com.example.engine.ColorFillMode
import com.example.engine.ColorHarmonyEngine
import com.example.engine.CoreGenerationSpec
import com.example.engine.DeepBlackRatioEngine
import com.example.engine.PaletteMode
import com.example.engine.PatternCategory
import com.example.engine.PatternWeightMode
import com.example.engine.PitchBlackMode
import com.example.engine.PolygonPrimitive
import com.example.engine.PolylineRibbonPrimitive
import com.example.engine.ArcSectorPrimitive
import com.example.engine.RandomPaletteGenerator
import com.example.engine.StarterPalettes
import com.example.engine.VectorPrimitive
import com.example.engine.WallpaperGeneratorEngine
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM architectural boundary and core mathematical engine test suite.
 *
 * IMPORTANT:
 * Do NOT annotate this class with `@RunWith(RobolectricTestRunner::class)` and do NOT import
 * `android.*` or `androidx.*`. Running on the bare host JVM guarantees that `com.example.engine`
 * is 100% portable to non-Android targets (iOS, Web, Desktop, Kotlin Multiplatform).
 */
class CoreEnginePortabilityTest {

    private fun locateEngineSourceDir(): File {
        val candidates = listOf(
            File("src/main/java/com/example/engine"),
            File("app/src/main/java/com/example/engine"),
            File("../app/src/main/java/com/example/engine")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("Could not locate com/example/engine source directory from CWD=${File(".").absolutePath}")
    }

    @Test
    fun coreEngineDirectory_containsZeroAndroidComposeOrAppLayerDependencies() {
        val engineDir = locateEngineSourceDir()
        val ktFiles = engineDir.listFiles { f -> f.isFile && f.extension == "kt" }
            ?.sortedBy { it.name }
            .orEmpty()

        assertTrue("Expected at least 17 pure Kotlin files in engine/, found ${ktFiles.size}", ktFiles.size >= 17)

        val forbiddenImportPrefixes = listOf(
            "android.",
            "androidx.",
            "com.example.R",
            "com.example.model",
            "com.example.platform",
            "com.example.data",
            "com.example.scheduler",
            "com.example.ui",
            "kotlinx.coroutines.android"
        )
        val forbiddenInlineTokens = listOf(
            "android.graphics.",
            "android.app.",
            "android.content.",
            "android.os.",
            "android.provider.",
            "android.view.",
            "androidx.",
            "com.example.R.",
            "com.example.model.",
            "com.example.platform.",
            "com.example.data.",
            "com.example.scheduler.",
            "com.example.ui."
        )

        val violations = ArrayList<String>()

        for (file in ktFiles) {
            val lines = file.readLines()
            val packageLine = lines.firstOrNull { it.trim().startsWith("package ") }?.trim()
            if (packageLine != "package com.example.engine") {
                violations.add("${file.name}: unexpected package declaration '$packageLine'")
            }

            var inBlockComment = false
            lines.forEachIndexed { idx, rawLine ->
                val lineNum = idx + 1
                val trimmed = rawLine.trim()

                if (inBlockComment) {
                    if (trimmed.contains("*/")) {
                        inBlockComment = false
                    }
                    return@forEachIndexed
                }
                if (trimmed.startsWith("/*")) {
                    if (!trimmed.contains("*/")) {
                        inBlockComment = true
                    }
                    return@forEachIndexed
                }
                if (trimmed.startsWith("//") || trimmed.startsWith("*")) {
                    return@forEachIndexed
                }

                val codeWithoutLineComment = trimmed.substringBefore("//").trim()
                if (codeWithoutLineComment.startsWith("import ")) {
                    val importedSymbol = codeWithoutLineComment.removePrefix("import ").trim()
                    if (forbiddenImportPrefixes.any { importedSymbol.startsWith(it) }) {
                        violations.add("${file.name}:$lineNum forbidden import '$importedSymbol'")
                    }
                    if (!importedSymbol.startsWith("kotlin.")) {
                        violations.add("${file.name}:$lineNum non-stdlib import '$importedSymbol' (only kotlin.* allowed)")
                    }
                } else {
                    for (token in forbiddenInlineTokens) {
                        if (codeWithoutLineComment.contains(token)) {
                            violations.add("${file.name}:$lineNum forbidden inline reference '$token'")
                        }
                    }
                }
            }
        }

        assertTrue(
            "Core engine portability violations detected:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun pureJvm_wallpaperGeneratorEngine_synthesizesAll80PatternsWithAccurateAnalyticalBlackRatios() {
        assertEquals(4, PatternCategory.entries.size)
        assertEquals(80, ArtPattern.entries.size)
        assertEquals(40, StarterPalettes.defaults.size)

        val targetRatios = floatArrayOf(0.25f, 0.60f, 0.85f)
        val deviations = ArrayList<String>()

        for (pattern in ArtPattern.entries) {
            assertTrue("Pattern ${pattern.id} must have non-blank defaultTitle", pattern.defaultTitle.isNotBlank())
            assertTrue("Pattern ${pattern.id} must have non-blank defaultSubtitle", pattern.defaultSubtitle.isNotBlank())
            assertEquals(pattern, ArtPattern.fromId(pattern.id))

            val seed = 900_001L + pattern.ordinal * 10_4729L
            val spec = CoreGenerationSpec(
                patternsRandomized = false,
                selectedPatterns = setOf(pattern),
                colorFillMode = if (pattern.ordinal % 2 == 0) ColorFillMode.GRADIENT else ColorFillMode.SOLID,
                paletteMode = PaletteMode.RANDOMIZED,
                pitchBlackMode = PitchBlackMode.SINGLE,
                pitchBlackRatio = 0.85f
            )

            val synthesized = WallpaperGeneratorEngine.synthesize(
                seed = seed,
                spec = spec,
                aspectRatio = 2.15f
            )
            assertEquals(pattern, synthesized.pattern)
            assertTrue(synthesized.intricacy in 0.40f..0.60f)
            assertTrue(synthesized.scene.primitives.isNotEmpty())
            assertTrue(synthesized.paletteColors.none { (it and 0xFFFFFFL) == 0L })

            var prevBlackPct = -1
            for (ratio in targetRatios) {
                val stats = synthesized.scene.computeStats(ratio)
                val expectedPct = (ratio * 100f).toInt()
                val diff = abs(stats.pitchBlackPercent - expectedPct)
                if (diff > 5) {
                    deviations.add("${pattern.id}: target=$expectedPct% analytical=${stats.pitchBlackPercent}%")
                }
                if (stats.pitchBlackPercent <= prevBlackPct) {
                    deviations.add("${pattern.id}: non-monotonic analytical black ${stats.pitchBlackPercent}% <= $prevBlackPct%")
                }
                assertEquals(100, stats.pitchBlackPercent + stats.coloredPercent)
                prevBlackPct = stats.pitchBlackPercent
            }
        }

        assertTrue(
            "Analytical True Black ratio deviations:\n${deviations.joinToString("\n")}",
            deviations.isEmpty()
        )
    }

    @Test
    fun pureJvm_centerWeightedMode_clumpsGeometryTowardCenterAndPreservesAnalyticalBlackRatioAcrossAll80Patterns() {
        val aspect = 2.15f
        val cx = 0.5f
        val cy = aspect * 0.5f
        val deviations = ArrayList<String>()
        var totalNormalRadius = 0.0
        var totalCenterWeightedRadius = 0.0
        var closerCount = 0

        fun primitiveNormalizedCenterDistance(prim: VectorPrimitive): Float {
            val (px, py) = when (prim) {
                is PolygonPrimitive -> {
                    var sx = 0f
                    var sy = 0f
                    for (pt in prim.vertices) {
                        sx += pt.x
                        sy += pt.y
                    }
                    val inv = 1f / prim.vertices.size.coerceAtLeast(1)
                    (sx * inv) to (sy * inv)
                }
                is ArcSectorPrimitive -> {
                    val midR = (prim.innerRadius + prim.outerRadius) * 0.5f
                    val midRad = ((prim.startAngleDeg + prim.sweepAngleDeg * 0.5f) * (kotlin.math.PI / 180.0)).toFloat()
                    (prim.center.x + kotlin.math.cos(midRad) * midR) to (prim.center.y + kotlin.math.sin(midRad) * midR)
                }
                is PolylineRibbonPrimitive -> {
                    var sx = 0f
                    var sy = 0f
                    for (pt in prim.points) {
                        sx += pt.x
                        sy += pt.y
                    }
                    val inv = 1f / prim.points.size.coerceAtLeast(1)
                    (sx * inv) to (sy * inv)
                }
            }
            val dx = (px - cx) / 0.5f
            val dy = (py - cy) / (aspect * 0.5f)
            return hypot(dx, dy)
        }

        for (pattern in ArtPattern.entries) {
            val seed = 410_017L + pattern.ordinal * 8_191L
            val normalSynth = WallpaperGeneratorEngine.synthesize(
                seed = seed,
                spec = CoreGenerationSpec(
                    patternsRandomized = false,
                    selectedPatterns = setOf(pattern),
                    patternWeightMode = PatternWeightMode.NORMAL,
                    pitchBlackMode = PitchBlackMode.SINGLE,
                    pitchBlackRatio = 0.80f
                ),
                aspectRatio = aspect
            )
            val centerSynth = WallpaperGeneratorEngine.synthesize(
                seed = seed,
                spec = CoreGenerationSpec(
                    patternsRandomized = false,
                    selectedPatterns = setOf(pattern),
                    patternWeightMode = PatternWeightMode.CENTER_WEIGHTED,
                    pitchBlackMode = PitchBlackMode.SINGLE,
                    pitchBlackRatio = 0.80f
                ),
                aspectRatio = aspect
            )

            assertEquals(PatternWeightMode.NORMAL, normalSynth.patternWeightMode)
            assertEquals(PatternWeightMode.CENTER_WEIGHTED, centerSynth.patternWeightMode)

            var prevPct = -1
            for (ratio in floatArrayOf(0.25f, 0.60f, 0.85f)) {
                val stats = centerSynth.scene.computeStats(ratio)
                val expected = (ratio * 100f).toInt()
                if (abs(stats.pitchBlackPercent - expected) > 5) {
                    deviations.add("${pattern.id}(CENTER_WEIGHTED): target=$expected% analytical=${stats.pitchBlackPercent}%")
                }
                if (stats.pitchBlackPercent <= prevPct) {
                    deviations.add("${pattern.id}(CENTER_WEIGHTED): non-monotonic ${stats.pitchBlackPercent}% <= $prevPct%")
                }
                prevPct = stats.pitchBlackPercent
            }

            fun weightedMeanRadius(scenePrimitives: List<VectorPrimitive>, blackRatio: Float): Double {
                var wSum = 0.0
                var rSum = 0.0
                for (p in scenePrimitives) {
                    if (DeepBlackRatioEngine.isPrimitiveVisible(p, blackRatio)) {
                        val w = p.areaWeight.toDouble().coerceAtLeast(1e-6)
                        wSum += w
                        rSum += w * primitiveNormalizedCenterDistance(p)
                    }
                }
                return if (wSum > 0.0) rSum / wSum else 0.0
            }

            val normR = weightedMeanRadius(normalSynth.scene.primitives, 0.80f)
            val centR = weightedMeanRadius(centerSynth.scene.primitives, 0.80f)
            totalNormalRadius += normR
            totalCenterWeightedRadius += centR
            if (centR <= normR + 0.03) {
                closerCount += 1
            }
        }

        assertTrue(
            "Center-weighted analytical ratio deviations:\n${deviations.joinToString("\n")}",
            deviations.isEmpty()
        )
        val meanNormal = totalNormalRadius / ArtPattern.entries.size
        val meanCenter = totalCenterWeightedRadius / ArtPattern.entries.size
        assertTrue(
            "Expected CENTER_WEIGHTED mean radial distance ($meanCenter) to be substantially smaller than NORMAL ($meanNormal)",
            meanCenter < meanNormal * 0.72
        )
        assertTrue(
            "Expected vast majority of 80 patterns ($closerCount/80) to cluster closer to center in CENTER_WEIGHTED mode",
            closerCount >= 72
        )
    }

    @Test
    fun pureJvm_colorHarmonyAndDeepBlackRatioEngines_preserveNonBlackSafeguardsAndPixelSampling() {
        // 1. Pure HSV harmony generation across 50 seeds never produces #000000
        for (i in 1..50) {
            val swatches = RandomPaletteGenerator.generate(i * 65537L)
            assertTrue("Expected 4..6 swatches", swatches.size in 4..6)
            for (color in swatches) {
                assertEquals("Alpha must be 0xFF opaque", 0xFF000000L, color and 0xFF000000L)
                assertNotEquals("Swatch must never be #000000 black", 0L, color and 0xFFFFFFL)
            }
        }

        // 2. Non-black OLED shading floor clamps ultra-dark non-black color to (0, 0, 4) instead of #000000
        val ultraDarkBlue = listOf(0xFF000001L)
        val shaded = ColorHarmonyEngine.resolveArgbColor(ultraDarkBlue, colorIndex = 0, shadeFactor = 0.18f)
        assertFalse(
            "Shaded non-black swatch must never collapse to #000000",
            DeepBlackRatioEngine.isTrueBlackRgb(shaded.toInt())
        )
        assertEquals(0xFF000004L, shaded)

        // 3. Pure pixel-grid #000000 percentage sampler on synthetic 100x100 framebuffer (exact 80% black)
        val width = 100
        val height = 100
        val buffer = IntArray(width * height) { idx ->
            val y = idx / width
            if (y < 80) 0xFF000000.toInt() else 0xFF00F5D4.toInt()
        }
        val measuredPct = DeepBlackRatioEngine.measureTrueBlackPercentage(
            widthPx = width,
            heightPx = height,
            gridDivisions = 50
        ) { x, y ->
            buffer[y * width + x]
        }
        assertEquals(80, measuredPct)

        // 4. Distinct pair synthesis produces distinct seeds & patterns
        val (home, lock) = WallpaperGeneratorEngine.synthesizeDistinctPair(
            primarySeed = 111_222L,
            secondarySeed = 333_444L,
            spec = CoreGenerationSpec(
                patternsRandomized = true,
                pitchBlackMode = PitchBlackMode.RANGE,
                pitchBlackRangeMin = 0.60f,
                pitchBlackRangeMax = 0.85f
            ),
            rng = Random(42L)
        )
        assertNotEquals(home.seed, lock.seed)
        assertNotEquals(home.pattern, lock.pattern)
        assertTrue(home.pitchBlackRatio in 0.60f..0.85f)
        assertTrue(lock.pitchBlackRatio in 0.60f..0.85f)

        // 5. Max-axis clamping preserves aspect ratio
        val (clampedW, clampedH) = DeepBlackRatioEngine.clampDimensionsToMaxAxis(6000, 8000)
        assertEquals(3072, clampedW)
        assertEquals(4096, clampedH)
    }
}
