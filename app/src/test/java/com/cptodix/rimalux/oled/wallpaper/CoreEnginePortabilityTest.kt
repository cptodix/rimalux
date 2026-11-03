package com.cptodix.rimalux.oled.wallpaper

import com.cptodix.rimalux.oled.wallpaper.engine.ArtPattern
import com.cptodix.rimalux.oled.wallpaper.engine.ColorFillMode
import com.cptodix.rimalux.oled.wallpaper.engine.ColorHarmonyEngine
import com.cptodix.rimalux.oled.wallpaper.engine.CoreGenerationSpec
import com.cptodix.rimalux.oled.wallpaper.engine.DeepBlackRatioEngine
import com.cptodix.rimalux.oled.wallpaper.engine.PaletteMode
import com.cptodix.rimalux.oled.wallpaper.engine.PatternCategory
import com.cptodix.rimalux.oled.wallpaper.engine.PatternWeightMode
import com.cptodix.rimalux.oled.wallpaper.engine.PitchBlackMode
import com.cptodix.rimalux.oled.wallpaper.engine.PolygonPrimitive
import com.cptodix.rimalux.oled.wallpaper.engine.PolylineRibbonPrimitive
import com.cptodix.rimalux.oled.wallpaper.engine.ArcSectorPrimitive
import com.cptodix.rimalux.oled.wallpaper.engine.RandomPaletteGenerator
import com.cptodix.rimalux.oled.wallpaper.engine.StarterPalettes
import com.cptodix.rimalux.oled.wallpaper.engine.VectorPrimitive
import com.cptodix.rimalux.oled.wallpaper.engine.WallpaperGeneratorEngine
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
 * `android.*` or `androidx.*`. Running on the bare host JVM guarantees that `com.cptodix.rimalux.oled.wallpaper.engine`
 * is 100% portable to non-Android targets (iOS, Web, Desktop, Kotlin Multiplatform).
 */
class CoreEnginePortabilityTest {

    private fun locateEngineSourceDir(): File {
        val candidates = listOf(
            File("src/main/java/com/cptodix/rimalux/oled/wallpaper/engine"),
            File("app/src/main/java/com/cptodix/rimalux/oled/wallpaper/engine"),
            File("../app/src/main/java/com/cptodix/rimalux/oled/wallpaper/engine")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("Could not locate com/cptodix/rimalux/oled/wallpaper/engine source directory from CWD=${File(".").absolutePath}")
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
            "com.cptodix.rimalux.oled.wallpaper.R",
            "com.cptodix.rimalux.oled.wallpaper.model",
            "com.cptodix.rimalux.oled.wallpaper.platform",
            "com.cptodix.rimalux.oled.wallpaper.data",
            "com.cptodix.rimalux.oled.wallpaper.scheduler",
            "com.cptodix.rimalux.oled.wallpaper.ui",
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
            "com.cptodix.rimalux.oled.wallpaper.R.",
            "com.cptodix.rimalux.oled.wallpaper.model.",
            "com.cptodix.rimalux.oled.wallpaper.platform.",
            "com.cptodix.rimalux.oled.wallpaper.data.",
            "com.cptodix.rimalux.oled.wallpaper.scheduler.",
            "com.cptodix.rimalux.oled.wallpaper.ui."
        )

        val violations = ArrayList<String>()

        for (file in ktFiles) {
            val lines = file.readLines()
            val packageLine = lines.firstOrNull { it.trim().startsWith("package ") }?.trim()
            if (packageLine != "package com.cptodix.rimalux.oled.wallpaper.engine") {
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
        assertEquals(41, StarterPalettes.defaults.size)

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

    @Test
    fun pureJvm_watermarkPlacementSolver_placesInsideSocialSafeZoneAndFindsDarkestEmptyPocket() {
        // 1. Safe-zone perimeter invariants across portrait phone, 16:9 phone, foldable, and landscape tablet
        val dims = listOf(1080 to 2400, 1080 to 1920, 1840 to 2208, 2560 to 1600)
        for ((w, h) in dims) {
            val safe = com.cptodix.rimalux.oled.wallpaper.engine.WatermarkPlacementSolver.computeSocialSafeZone(w, h)
            assertTrue("Safe left must clear outer edge on ${w}x$h", safe.leftPx >= (w * 0.06f).toInt())
            assertTrue("Safe right must clear outer edge on ${w}x$h", safe.rightPx <= (w * 0.94f).toInt())
            assertTrue("Safe top must clear status/story header on ${w}x$h", safe.topPx >= (h * 0.10f).toInt())
            assertTrue("Safe bottom must clear bottom chrome on ${w}x$h", safe.bottomPx <= (h * 0.89f).toInt())
            assertTrue("Safe width must span broad canvas on ${w}x$h", safe.widthPx > (w * 0.70f).toInt())
            assertTrue("Safe height must span broad canvas on ${w}x$h", safe.heightPx > (h * 0.60f).toInt())
        }

        val w = 1080
        val h = 2400
        val safe = com.cptodix.rimalux.oled.wallpaper.engine.WatermarkPlacementSolver.computeSocialSafeZone(w, h)

        // 2. Strict anti-center invariant:
        // Even when ONLY the middle of the wallpaper is pure #000000 and all outer edges are bright cyan,
        // the watermark MUST NEVER enter the center — it stays strictly pinned to one of the 4 perimeter rails.
        val centerVoidPlacement = com.cptodix.rimalux.oled.wallpaper.engine.WatermarkPlacementSolver.findOptimalPlacement(
            widthPx = w,
            heightPx = h,
            textWidthPx = 280f,
            textHeightPx = 22f
        ) { x, y ->
            val inCenterVoid = x in (safe.leftPx + 140)..(safe.rightPx - 140) &&
                y in (safe.topPx + 180)..(safe.bottomPx - 180)
            if (inCenterVoid) 0xFF000000.toInt() else 0xFF00F5D4.toInt()
        }
        val touchesPerimeterRail =
            kotlin.math.abs(centerVoidPlacement.topPx - safe.topPx) <= 1f ||
                kotlin.math.abs((centerVoidPlacement.topPx + centerVoidPlacement.heightPx) - safe.bottomPx) <= 1f ||
                kotlin.math.abs(centerVoidPlacement.leftPx - safe.leftPx) <= 1f ||
                kotlin.math.abs((centerVoidPlacement.leftPx + centerVoidPlacement.widthPx) - safe.rightPx) <= 1f
        assertTrue(
            "Watermark must stay strictly pinned to a safe-zone perimeter rail, got $centerVoidPlacement",
            touchesPerimeterRail
        )

        // 3. Dynamic empty #000000 pocket selection across all 4 perimeter rails:
        // 3a. Top horizontal rail pocket
        val topPocketPlacement = com.cptodix.rimalux.oled.wallpaper.engine.WatermarkPlacementSolver.findOptimalPlacement(
            widthPx = w,
            heightPx = h,
            textWidthPx = 260f,
            textHeightPx = 20f
        ) { x, y ->
            if (x in (safe.rightPx - 340)..safe.rightPx && y in safe.topPx..(safe.topPx + 60)) {
                0xFF000000.toInt()
            } else {
                0xFF00F5D4.toInt()
            }
        }
        assertEquals(com.cptodix.rimalux.oled.wallpaper.engine.WatermarkEdge.TOP, topPocketPlacement.edge)
        assertEquals(com.cptodix.rimalux.oled.wallpaper.engine.WatermarkOrientation.HORIZONTAL, topPocketPlacement.orientation)
        assertEquals(safe.topPx.toFloat(), topPocketPlacement.topPx, 0.5f)
        assertTrue(topPocketPlacement.leftPx >= safe.rightPx - 350f)
        assertTrue(topPocketPlacement.trueBlackFraction >= 0.90f)

        // 3b. Bottom horizontal rail pocket
        val bottomPocketPlacement = com.cptodix.rimalux.oled.wallpaper.engine.WatermarkPlacementSolver.findOptimalPlacement(
            widthPx = w,
            heightPx = h,
            textWidthPx = 260f,
            textHeightPx = 20f
        ) { x, y ->
            if (x in safe.leftPx..(safe.leftPx + 340) && y in (safe.bottomPx - 60)..safe.bottomPx) {
                0xFF000000.toInt()
            } else {
                0xFFFF006E.toInt()
            }
        }
        assertEquals(com.cptodix.rimalux.oled.wallpaper.engine.WatermarkEdge.BOTTOM, bottomPocketPlacement.edge)
        assertEquals(com.cptodix.rimalux.oled.wallpaper.engine.WatermarkOrientation.HORIZONTAL, bottomPocketPlacement.orientation)
        assertEquals(safe.bottomPx - 20f, bottomPocketPlacement.topPx, 0.5f)
        assertTrue(bottomPocketPlacement.leftPx <= safe.leftPx + 90f)
        assertTrue(bottomPocketPlacement.trueBlackFraction >= 0.90f)

        // 3c. Left vertical side rail pocket (-90° CCW)
        val leftPocketPlacement = com.cptodix.rimalux.oled.wallpaper.engine.WatermarkPlacementSolver.findOptimalPlacement(
            widthPx = w,
            heightPx = h,
            textWidthPx = 260f,
            textHeightPx = 20f
        ) { x, y ->
            if (x in safe.leftPx..(safe.leftPx + 60) && y in (safe.topPx + 420)..(safe.topPx + 780)) {
                0xFF000000.toInt()
            } else {
                0xFF3A86FF.toInt()
            }
        }
        assertEquals(com.cptodix.rimalux.oled.wallpaper.engine.WatermarkEdge.LEFT, leftPocketPlacement.edge)
        assertEquals(com.cptodix.rimalux.oled.wallpaper.engine.WatermarkOrientation.VERTICAL_CCW, leftPocketPlacement.orientation)
        assertEquals(safe.leftPx.toFloat(), leftPocketPlacement.leftPx, 0.5f)
        assertEquals(20f, leftPocketPlacement.widthPx, 0.1f)
        assertEquals(260f, leftPocketPlacement.heightPx, 0.1f)
        assertTrue(leftPocketPlacement.topPx >= safe.topPx + 400f && leftPocketPlacement.topPx + leftPocketPlacement.heightPx <= safe.topPx + 800f)
        assertTrue(leftPocketPlacement.trueBlackFraction >= 0.90f)

        // 3d. Right vertical side rail pocket (+90° CW)
        val rightPocketPlacement = com.cptodix.rimalux.oled.wallpaper.engine.WatermarkPlacementSolver.findOptimalPlacement(
            widthPx = w,
            heightPx = h,
            textWidthPx = 260f,
            textHeightPx = 20f
        ) { x, y ->
            if (x in (safe.rightPx - 60)..safe.rightPx && y in (safe.topPx + 300)..(safe.topPx + 660)) {
                0xFF000000.toInt()
            } else {
                0xFFFFBE0B.toInt()
            }
        }
        assertEquals(com.cptodix.rimalux.oled.wallpaper.engine.WatermarkEdge.RIGHT, rightPocketPlacement.edge)
        assertEquals(com.cptodix.rimalux.oled.wallpaper.engine.WatermarkOrientation.VERTICAL_CW, rightPocketPlacement.orientation)
        assertEquals(safe.rightPx - 20f, rightPocketPlacement.leftPx, 0.5f)
        assertEquals(20f, rightPocketPlacement.widthPx, 0.1f)
        assertEquals(260f, rightPocketPlacement.heightPx, 0.1f)
        assertTrue(rightPocketPlacement.topPx >= safe.topPx + 280f && rightPocketPlacement.topPx + rightPocketPlacement.heightPx <= safe.topPx + 680f)
        assertTrue(rightPocketPlacement.trueBlackFraction >= 0.90f)

        // 4. 0% True Black fallback: chooses dark navy perimeter pocket over bright yellow background
        val darkSpotPlacement = com.cptodix.rimalux.oled.wallpaper.engine.WatermarkPlacementSolver.findOptimalPlacement(
            widthPx = w,
            heightPx = h,
            textWidthPx = 240f,
            textHeightPx = 18f
        ) { x, y ->
            if (x in safe.leftPx..(safe.leftPx + 320) && y in safe.topPx..(safe.topPx + 60)) {
                0xFF0A0D18.toInt()
            } else {
                0xFFFFEA60.toInt()
            }
        }
        assertEquals(com.cptodix.rimalux.oled.wallpaper.engine.WatermarkEdge.TOP, darkSpotPlacement.edge)
        assertTrue(darkSpotPlacement.leftPx <= safe.leftPx + 90f)
        assertTrue(darkSpotPlacement.meanLuminance < 0.12f)

        // 5. Subtle muted ink alpha stays legible above OLED black crush & downscaling (~43%-60%) without glare
        val darkInk = com.cptodix.rimalux.oled.wallpaper.engine.WatermarkPlacementSolver.computeSubtleWatermarkArgb(
            meanLuminance = 0.0f,
            paletteColors = listOf(0xFF00F5D4L)
        )
        val darkInkAlpha = (darkInk ushr 24) and 0xFF
        assertTrue("Expected muted OLED-visible alpha on #000000, got $darkInkAlpha", darkInkAlpha in 108..136)

        val brightBgInk = com.cptodix.rimalux.oled.wallpaper.engine.WatermarkPlacementSolver.computeSubtleWatermarkArgb(
            meanLuminance = 0.80f,
            paletteColors = listOf(0xFFFFEA60L)
        )
        val brightBgAlpha = (brightBgInk ushr 24) and 0xFF
        assertTrue("Expected subtle dark-ink alpha on bright pastel fill, got $brightBgAlpha", brightBgAlpha in 120..156)
    }
}
