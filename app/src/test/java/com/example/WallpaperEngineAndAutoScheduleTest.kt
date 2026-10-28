package com.example

import android.content.Context
import android.util.DisplayMetrics
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.WallpaperPreferencesRepository
import com.example.engine.MathPatternGenerator
import com.example.engine.WallpaperApplyResult
import com.example.engine.WallpaperRenderer
import com.example.model.ArtStyle
import com.example.model.ColorFillMode
import com.example.model.RandomPaletteGenerator
import com.example.scheduler.WallpaperRotationWorker
import com.example.scheduler.WallpaperScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class WallpaperEngineAndAutoScheduleTest {

    @Test
    fun allSixteenMathArtStyles_solidAndGradient_respectPitchBlackRatio() {
        val randomPalette = RandomPaletteGenerator.generate(987654321L)
        assertTrue("Expected at least 4 harmonious colors", randomPalette.size >= 4)

        for (style in ArtStyle.entries) {
            val scene = MathPatternGenerator.generate(
                seed = 123456789L + style.ordinal * 7919L,
                style = style,
                intricacy = 0.60f,
                aspectRatio = 2.0f
            )
            assertTrue("Expected primitives for $style", scene.primitives.isNotEmpty())

            for (fillMode in ColorFillMode.entries) {
                val bitmapLow = WallpaperRenderer.renderToBitmap(
                    scene = scene,
                    paletteColors = randomPalette,
                    pitchBlackRatio = 0.05f,
                    widthPx = 280,
                    heightPx = 560,
                    colorFillMode = fillMode
                )
                val bitmapHigh = WallpaperRenderer.renderToBitmap(
                    scene = scene,
                    paletteColors = randomPalette,
                    pitchBlackRatio = 0.85f,
                    widthPx = 280,
                    heightPx = 560,
                    colorFillMode = fillMode
                )
                val measuredLow = WallpaperRenderer.sampleTrueBlackPercentage(bitmapLow)
                val measuredHigh = WallpaperRenderer.sampleTrueBlackPercentage(bitmapHigh)
                bitmapLow.recycle()
                bitmapHigh.recycle()
                assertTrue(
                    "Style $style ($fillMode): high #000000 void ($measuredHigh%) must exceed low void ($measuredLow%)",
                    measuredHigh > measuredLow
                )
            }
        }
    }

    @Test
    fun highEndFlagshipQhdAnd4kResolution_andDpi_renderAndRecycleCleanly() {
        val palette = RandomPaletteGenerator.generate(424242L)
        val scene = MathPatternGenerator.generate(
            seed = 777888999L,
            style = ArtStyle.CRYSTAL_VORONOI,
            intricacy = 0.65f,
            aspectRatio = 3840f / 1644f
        )

        // 1. QHD+ flagship (1440 x 3200 @ 560 dpi)
        val qhdBitmap = WallpaperRenderer.renderToBitmap(
            scene = scene,
            paletteColors = palette,
            pitchBlackRatio = 0.65f,
            widthPx = 1440,
            heightPx = 3200,
            colorFillMode = ColorFillMode.GRADIENT,
            densityDpi = DisplayMetrics.DENSITY_560
        )
        assertEquals(1440, qhdBitmap.width)
        assertEquals(3200, qhdBitmap.height)
        assertEquals(DisplayMetrics.DENSITY_560, qhdBitmap.density)
        qhdBitmap.recycle()
        assertTrue(qhdBitmap.isRecycled)

        // 2. 4K UHD flagship (1644 x 3840 @ 640 dpi / xxxhdpi)
        val uhdBitmap = WallpaperRenderer.renderToBitmap(
            scene = scene,
            paletteColors = palette,
            pitchBlackRatio = 0.65f,
            widthPx = 1644,
            heightPx = 3840,
            colorFillMode = ColorFillMode.GRADIENT,
            densityDpi = DisplayMetrics.DENSITY_XXXHIGH
        )
        assertEquals(1644, uhdBitmap.width)
        assertEquals(3840, uhdBitmap.height)
        assertEquals(DisplayMetrics.DENSITY_XXXHIGH, uhdBitmap.density)
        uhdBitmap.recycle()
        assertTrue(uhdBitmap.isRecycled)

        // 3. Extreme >4K input (6000 x 8000) proportionally clamps to 3072 x 4096 without OOM or aspect distortion
        val clampedBitmap = WallpaperRenderer.renderToBitmap(
            scene = scene,
            paletteColors = palette,
            pitchBlackRatio = 0.75f,
            widthPx = 6000,
            heightPx = 8000,
            colorFillMode = ColorFillMode.SOLID,
            densityDpi = 800
        )
        assertEquals(3072, clampedBitmap.width)
        assertEquals(WallpaperRenderer.MAX_WALLPAPER_AXIS_PX, clampedBitmap.height)
        assertEquals(DisplayMetrics.DENSITY_XXXHIGH, clampedBitmap.density)
        clampedBitmap.recycle()
    }

    @Test
    fun autoWallpaperSet_strictlyRespectsHomeMenuPitchBlackRatio() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val repository = WallpaperPreferencesRepository(context)

        try {
            // 1. Configure deterministic tiled style + low Home Pitch Black Ratio (10%)
            repository.updateConfig {
                it.copy(
                    autoRotateEnabled = true,
                    stylesRandomized = false,
                    selectedStyles = setOf(ArtStyle.CRYSTAL_VORONOI),
                    pitchBlackRatio = 0.10f
                )
            }

            val lowAutoResult = WallpaperScheduler.executeAutoRotationCycle(
                context = context,
                wasScheduled = true,
                force = true
            )
            assertTrue(lowAutoResult is WallpaperApplyResult.Success)
            val lowSuccess = lowAutoResult as WallpaperApplyResult.Success
            assertTrue(lowSuccess.widthPx >= 320 && lowSuccess.heightPx >= 480)
            assertTrue(lowSuccess.densityDpi >= DisplayMetrics.DENSITY_LOW)
            assertEquals(0.10f, repository.getSnapshot().history.first().pitchBlackRatio, 0.001f)

            // 2. Update Home Pitch Black Ratio to 85% atomically and run auto cycle
            repository.updateConfig {
                it.copy(pitchBlackRatio = 0.85f)
            }

            val highAutoResult = WallpaperScheduler.executeAutoRotationCycle(
                context = context,
                wasScheduled = true,
                force = true
            )
            assertTrue(highAutoResult is WallpaperApplyResult.Success)
            val highSuccess = highAutoResult as WallpaperApplyResult.Success

            val latestHistory = repository.getSnapshot().history.first()
            assertEquals(0.85f, latestHistory.pitchBlackRatio, 0.001f)
            assertTrue(latestHistory.wasAutoScheduled)
            assertTrue(
                "Auto wallpaper set with 85% Home pitch-black (${highSuccess.measuredBlackPercent}%) must have significantly more #000000 than 10% (${lowSuccess.measuredBlackPercent}%)",
                highSuccess.measuredBlackPercent > lowSuccess.measuredBlackPercent + 35
            )
        } finally {
            WallpaperScheduler.cancelAll(context)
        }
    }

    @Test
    fun backgroundWorkerAndWorkManager_runAndPersistWithoutActiveUi() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val repository = WallpaperPreferencesRepository(context)

        repository.updateConfig {
            it.copy(
                autoRotateEnabled = true,
                intervalMinutes = 30,
                pitchBlackRatio = 0.78f,
                colorFillMode = ColorFillMode.GRADIENT
            )
        }
        WallpaperScheduler.scheduleNext(context, 30)

        val periodicInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WallpaperScheduler.UNIQUE_PERIODIC_WORK_NAME)
            .get()
        assertTrue(
            "Expected enqueued PeriodicWorkRequest for >=15m interval",
            periodicInfos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
        )

        WallpaperScheduler.scheduleNext(context, 5)
        val chainedInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WallpaperScheduler.UNIQUE_CHAINED_WORK_NAME)
            .get()
        assertTrue(
            "Expected enqueued chained OneTimeWorkRequest for <15m interval",
            chainedInfos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
        )

        val seedBefore = repository.getSnapshot().currentSeed
        val worker = TestListenableWorkerBuilder<WallpaperRotationWorker>(context).build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)

        val snapshotAfter = repository.getSnapshot()
        assertNotEquals("Worker should synthesize and persist a fresh wallpaper seed", seedBefore, snapshotAfter.currentSeed)
        assertTrue("Worker should record scheduled history entry", snapshotAfter.history.any { it.wasAutoScheduled })
        assertEquals(0.78f, snapshotAfter.history.first().pitchBlackRatio, 0.001f)
        WallpaperScheduler.cancelAll(context)
    }
}
