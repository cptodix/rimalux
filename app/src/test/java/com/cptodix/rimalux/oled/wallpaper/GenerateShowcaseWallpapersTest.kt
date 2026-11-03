package com.cptodix.rimalux.oled.wallpaper

import android.graphics.Bitmap
import com.cptodix.rimalux.oled.wallpaper.engine.ColorFillMode
import com.cptodix.rimalux.oled.wallpaper.engine.MathPatternGenerator
import com.cptodix.rimalux.oled.wallpaper.engine.PatternWeightMode
import com.cptodix.rimalux.oled.wallpaper.engine.WallpaperGeneratorEngine
import com.cptodix.rimalux.oled.wallpaper.model.ArtPattern
import com.cptodix.rimalux.oled.wallpaper.model.StarterPalettes
import com.cptodix.rimalux.oled.wallpaper.platform.WallpaperRenderer
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@Ignore("On-demand distribution asset generator; remove @Ignore locally only when regenerating distribution/ media.")
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class GenerateShowcaseWallpapersTest {

    private fun resolveProjectRoot(): File {
        val cwd = File(".").canonicalFile
        return if (cwd.name == "app" && cwd.parentFile != null) cwd.parentFile!! else cwd
    }

    @Test
    fun generatePromotionalTrueBlackRatioSeries() {
        val projectRoot = resolveProjectRoot()
        val promoDir = File(projectRoot, "distribution/promotional_wallpapers").apply {
            mkdirs()
            listFiles()?.filter { it.isFile && it.name.endsWith(".png") }?.forEach { it.delete() }
        }

        val width = 1080
        val height = 2400
        val aspect = height.toFloat() / width.toFloat()

        val pattern = ArtPattern.PENROSE_QUASICRYSTAL
        val palette = StarterPalettes.defaults.first { it.id == "obsidian_aurora" }
        val seed = 20260777L
        val intricacy = 0.60f
        val weightMode = PatternWeightMode.CENTER_WEIGHTED
        val fillMode = ColorFillMode.GRADIENT

        // Synthesize the shared base geometry once so all 4 images have 100% identical shapes & colors
        val baseSynthesized = WallpaperGeneratorEngine.synthesizeFromParameters(
            seed = seed,
            pattern = pattern,
            paletteColors = palette.colors,
            colorFillMode = fillMode,
            pitchBlackRatio = 0.25f,
            intricacy = intricacy,
            aspectRatio = aspect,
            patternWeightMode = weightMode
        )

        val targetRatios = listOf(0.25f, 0.50f, 0.75f, 0.90f)
        var previousMeasuredBlack = -1

        targetRatios.forEachIndexed { idx, targetRatio ->
            val targetPct = (targetRatio * 100f).roundToInt()
            val fileName = String.format(
                Locale.ROOT,
                "rimalux_promo_%02d_%s_%s_true_black_%02dpct.png",
                idx + 1,
                pattern.id,
                palette.id,
                targetPct
            )
            val outFile = File(promoDir, fileName)

            val bitmap = WallpaperRenderer.renderToBitmap(
                scene = baseSynthesized.scene,
                paletteColors = palette.colors,
                pitchBlackRatio = targetRatio,
                widthPx = width,
                heightPx = height,
                colorFillMode = fillMode
            )
            try {
                val measuredBlack = WallpaperRenderer.sampleTrueBlackPercentage(bitmap)
                assertTrue(
                    "Expected strictly increasing #000000 ratio: prev=$previousMeasuredBlack, curr=$measuredBlack",
                    measuredBlack > previousMeasuredBlack
                )
                previousMeasuredBlack = measuredBlack

                FileOutputStream(outFile).buffered().use { out ->
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
                    out.flush()
                }
                assertTrue("Expected non-empty PNG at ${outFile.absolutePath}", outFile.exists() && outFile.length() > 0L)
                println(
                    "Saved promotional wallpaper [${idx + 1}/4]: ${outFile.absolutePath} " +
                        "(target=${targetPct}%, measured=${measuredBlack}%, size=${outFile.length() / 1024} KB)"
                )
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun generateHdWallpaperJpegsForPlayStoreShowcase() {
        val projectRoot = resolveProjectRoot()
        val showcaseRoot = File(projectRoot, "distribution/showcase_wallpapers").apply { mkdirs() }
        val patternsDir = File(showcaseRoot, "patterns").apply { mkdirs() }

        val patterns = ArtPattern.entries
        assertEquals(80, patterns.size)

        val width = 1080
        val height = 1920
        val aspect = height.toFloat() / width.toFloat()

        println("Generating ${patterns.size} HD showcase wallpapers at ${width}x${height}...")

        val generatedFiles = mutableListOf<File>()

        patterns.forEachIndexed { index, pattern ->
            val palette = StarterPalettes.defaults[index % StarterPalettes.defaults.size]
            val seed = (index + 1) * 7777L + 12345L
            val scene = MathPatternGenerator.generate(
                seed = seed,
                pattern = pattern,
                intricacy = 0.65f,
                aspectRatio = aspect
            )

            val bitmap = WallpaperRenderer.renderToBitmap(
                scene = scene,
                paletteColors = palette.colors,
                pitchBlackRatio = 0.70f,
                widthPx = width,
                heightPx = height
            )

            val outFile = File(patternsDir, "${pattern.id}.jpg")
            try {
                FileOutputStream(outFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
            } finally {
                bitmap.recycle()
            }
            generatedFiles.add(outFile)
        }

        assertEquals(80, generatedFiles.size)

        val zipFile = File(showcaseRoot, "showcase_wallpapers.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            generatedFiles.forEach { imgFile ->
                zos.putNextEntry(ZipEntry("showcase_wallpapers/${imgFile.name}"))
                imgFile.inputStream().use { input ->
                    input.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
        assertTrue("Expected zip file ${zipFile.path} created", zipFile.exists() && zipFile.length() > 0)
        println("Created zip package: ${zipFile.absolutePath} (${zipFile.length() / 1024} KB)")
    }
}
