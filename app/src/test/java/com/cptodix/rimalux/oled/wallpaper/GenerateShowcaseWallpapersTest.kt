package com.cptodix.rimalux.oled.wallpaper

import android.graphics.Bitmap
import com.cptodix.rimalux.oled.wallpaper.engine.MathPatternGenerator
import com.cptodix.rimalux.oled.wallpaper.model.ArtPattern
import com.cptodix.rimalux.oled.wallpaper.model.StarterPalettes
import com.cptodix.rimalux.oled.wallpaper.platform.WallpaperRenderer
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class GenerateShowcaseWallpapersTest {

    @Test
    fun generateHdWallpaperJpegsForPlayStoreShowcase() {
        val outDir = File("build/outputs/showcase_wallpapers").apply { mkdirs() }
        val publicDir = File("public/showcase_wallpapers").apply { mkdirs() }
        val artifactDir = File("/.aistudio/artifacts/brain/b476f231-ba4b-4ca3-9c02-cc2ef6b81a16").apply { mkdirs() }

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

            val fileName = "${pattern.id}.jpg"
            val fileBuild = File(outDir, fileName)
            FileOutputStream(fileBuild).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            bitmap.recycle()

            fileBuild.copyTo(File(publicDir, fileName), overwrite = true)
            generatedFiles.add(fileBuild)
        }

        assertEquals(80, generatedFiles.size)

        // Package into ZIP files
        val zipFiles = listOf(
            File("showcase_wallpapers.zip"),
            File("public/showcase_wallpapers.zip"),
            File(artifactDir, "showcase_wallpapers.zip")
        )

        zipFiles.forEach { zipFile ->
            zipFile.parentFile?.mkdirs()
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
}
