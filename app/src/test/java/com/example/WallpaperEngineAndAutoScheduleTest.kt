package com.example

import android.app.AlarmManager
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.util.DisplayMetrics
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.WallpaperPreferencesRepository
import com.example.engine.MathPatternGenerator
import com.example.platform.WallpaperApplyResult
import com.example.platform.WallpaperRenderer
import com.example.model.ArtPattern
import com.example.model.ColorFillMode
import com.example.model.GeneratedHistoryItem
import com.example.model.GeneratorConfig
import com.example.model.PatternWeightMode
import com.example.model.PitchBlackMode
import com.example.model.RandomPaletteGenerator
import com.example.scheduler.BootCompletedReceiver
import com.example.scheduler.WallpaperAlarmReceiver
import com.example.scheduler.WallpaperRotationWorker
import com.example.scheduler.WallpaperScheduler
import com.example.ui.RimaluxViewModel
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class WallpaperEngineAndAutoScheduleTest {

    @Test
    fun allSixtyFourMathPatterns_calibratedPixelCoverage_accuratelyRespectPitchBlackRatioAcrossSeeds() {
        assertEquals(80, ArtPattern.entries.size)
        val randomPalette = RandomPaletteGenerator.generate(987654321L)
        assertTrue("Expected at least 4 harmonious colors", randomPalette.size >= 4)

        val targetRatios = floatArrayOf(0.25f, 0.65f, 0.85f)
        val failures = ArrayList<String>()

        for (pattern in ArtPattern.entries) {
            val seed = 123456789L + pattern.ordinal * 7919L
            val scene = MathPatternGenerator.generate(
                seed = seed,
                pattern = pattern,
                intricacy = 0.60f,
                aspectRatio = 2.0f
            )
            assertTrue("Expected primitives for $pattern", scene.primitives.isNotEmpty())

            val fillMode = if (pattern.ordinal % 2 == 0) ColorFillMode.GRADIENT else ColorFillMode.SOLID
            var previousMeasured = -1
            for (target in targetRatios) {
                val bitmap = WallpaperRenderer.renderToBitmap(
                    scene = scene,
                    paletteColors = randomPalette,
                    pitchBlackRatio = target,
                    widthPx = 420,
                    heightPx = 840,
                    colorFillMode = fillMode
                )
                val measured = WallpaperRenderer.sampleTrueBlackPercentage(bitmap)
                bitmap.recycle()

                val targetPct = (target * 100f).toInt()
                val diff = abs(measured - targetPct)
                if (diff > 7) {
                    failures.add("$pattern($fillMode): target=$targetPct% measured=$measured% (diff=$diff%)")
                }
                if (measured <= previousMeasured) {
                    failures.add("$pattern($fillMode): non-monotonic $measured% <= $previousMeasured%")
                }
                previousMeasured = measured
            }
        }
        assertTrue(
            "Pitch-black calibration deviations:\n${failures.joinToString("\n")}",
            failures.isEmpty()
        )
    }

    @Test
    fun centerWeightedMode_clumpsGraphicsInMiddleWithEmptySidesAndRespectsTrueBlackRatio() {
        val palette = RandomPaletteGenerator.generate(246813579L)
        val representativePatterns = listOf(
            ArtPattern.SILK_RIBBONS,
            ArtPattern.CRYSTAL_VORONOI,
            ArtPattern.SACRED_MANDALA,
            ArtPattern.ISOMETRIC_MONUMENT,
            ArtPattern.CLIFFORD_ATTRACTOR,
            ArtPattern.FARADAY_RIPPLES,
            ArtPattern.ENNEAGRAM_STAR,
            ArtPattern.SRI_YANTRA_TRIANGLES,
            ArtPattern.CALATRAVA_RIBS,
            ArtPattern.DIAGRID_SKYSCRAPER,
            ArtPattern.LAVES_FLORET,
            ArtPattern.GOSPER_FLOWSNAKE
        )
        val failures = ArrayList<String>()
        var totalSideBlackSum = 0.0
        var totalCenterBlackSum = 0.0

        for (pattern in representativePatterns) {
            val seed = 555_001L + pattern.ordinal * 4099L
            val centerScene = MathPatternGenerator.generate(
                seed = seed,
                pattern = pattern,
                intricacy = 0.55f,
                aspectRatio = 2.0f,
                patternWeightMode = PatternWeightMode.CENTER_WEIGHTED
            )

            var prevMeasured = -1
            for (target in floatArrayOf(0.25f, 0.65f, 0.85f)) {
                val bmp = WallpaperRenderer.renderToBitmap(
                    scene = centerScene,
                    paletteColors = palette,
                    pitchBlackRatio = target,
                    widthPx = 420,
                    heightPx = 840,
                    colorFillMode = ColorFillMode.GRADIENT
                )
                val measured = WallpaperRenderer.sampleTrueBlackPercentage(bmp)
                bmp.recycle()

                val targetPct = (target * 100f).toInt()
                val diff = abs(measured - targetPct)
                if (diff > 7) {
                    failures.add("$pattern(CENTER_WEIGHTED): target=$targetPct% measured=$measured% (diff=$diff%)")
                }
                if (measured <= prevMeasured) {
                    failures.add("$pattern(CENTER_WEIGHTED): non-monotonic $measured% <= $prevMeasured%")
                }
                prevMeasured = measured
            }

            // Measure side margin emptiness vs center richness at 82% True Black Ratio
            val bmp82 = WallpaperRenderer.renderToBitmap(
                scene = centerScene,
                paletteColors = palette,
                pitchBlackRatio = 0.82f,
                widthPx = 360,
                heightPx = 720,
                colorFillMode = ColorFillMode.GRADIENT
            )
            var sideTotal = 0
            var sideBlack = 0
            var midTotal = 0
            var midBlack = 0
            for (y in 0 until bmp82.height step 4) {
                val ny = y.toFloat() / bmp82.height
                for (x in 0 until bmp82.width step 4) {
                    val nx = x.toFloat() / bmp82.width
                    val isBlack = (bmp82.getPixel(x, y) and 0x00FFFFFF) == 0
                    if (nx < 0.14f || nx > 0.86f) {
                        sideTotal += 1
                        if (isBlack) sideBlack += 1
                    } else if (nx in 0.30f..0.70f && ny in 0.32f..0.68f) {
                        midTotal += 1
                        if (isBlack) midBlack += 1
                    }
                }
            }
            bmp82.recycle()

            val sideBlackRatio = sideBlack.toDouble() / sideTotal.coerceAtLeast(1)
            val midBlackRatio = midBlack.toDouble() / midTotal.coerceAtLeast(1)
            totalSideBlackSum += sideBlackRatio
            totalCenterBlackSum += midBlackRatio
        }

        assertTrue(
            "Center-weighted pixel calibration deviations:\n${failures.joinToString("\n")}",
            failures.isEmpty()
        )
        val avgSideBlack = totalSideBlackSum / representativePatterns.size
        val avgCenterBlack = totalCenterBlackSum / representativePatterns.size
        assertTrue(
            "Expected side margins in CENTER_WEIGHTED mode to stay mostly empty OLED black (>= 90%, got ${(avgSideBlack * 100).toInt()}%)",
            avgSideBlack >= 0.90
        )
        assertTrue(
            "Expected middle region to hold significantly more colored pattern than sides (midBlack=${(avgCenterBlack * 100).toInt()}% vs sideBlack=${(avgSideBlack * 100).toInt()}%)",
            avgCenterBlack + 0.25 < avgSideBlack
        )
    }

    @Test
    fun highEndFlagshipQhdAnd4kResolution_andDpi_renderAndRecycleCleanly() {
        val palette = RandomPaletteGenerator.generate(424242L)
        val scene = MathPatternGenerator.generate(
            seed = 777888999L,
            pattern = ArtPattern.CRYSTAL_VORONOI,
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
    fun autoWallpaperSet_singleAndRangeModes_strictlyRespectPitchBlackSettings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val repository = WallpaperPreferencesRepository(context)

        try {
            // 1. SINGLE VALUE MODE (72% Pitch Black) across 5 consecutive randomized pattern/palette auto-rotations
            repository.updateConfig {
                it.copy(
                    autoRotateEnabled = true,
                    patternsRandomized = true,
                    pitchBlackMode = PitchBlackMode.SINGLE,
                    pitchBlackRatio = 0.72f
                )
            }

            val sampledIntricacies = mutableSetOf<Int>()
            repeat(5) { cycle ->
                val result = WallpaperScheduler.executeAutoRotationCycle(
                    context = context,
                    wasScheduled = true,
                    force = true
                )
                assertTrue(result is WallpaperApplyResult.Success)
                val success = result as WallpaperApplyResult.Success
                val latestHistory = repository.getSnapshot().history.first()
                assertEquals(0.72f, latestHistory.pitchBlackRatio, 0.001f)
                assertTrue(
                    "Auto cycle intricacy ${latestHistory.intricacy} must stay inside [0.40, 0.60]",
                    latestHistory.intricacy in 0.40f..0.60f
                )
                sampledIntricacies.add((latestHistory.intricacy * 100f).toInt())
                assertTrue(
                    "Auto cycle #$cycle (${latestHistory.pattern}) expected ~72% black but got ${success.measuredBlackPercent}%",
                    abs(success.measuredBlackPercent - 72) <= 8
                )
            }

            // 2. RANGE MODE (35% - 75% Pitch Black) across 6 consecutive randomized auto-rotations
            repository.updateConfig {
                it.copy(
                    pitchBlackMode = PitchBlackMode.RANGE,
                    pitchBlackRangeMin = 0.35f,
                    pitchBlackRangeMax = 0.75f
                )
            }

            val sampledRatios = mutableSetOf<Int>()
            repeat(6) { cycle ->
                val result = WallpaperScheduler.executeAutoRotationCycle(
                    context = context,
                    wasScheduled = true,
                    force = true
                )
                assertTrue(result is WallpaperApplyResult.Success)
                val success = result as WallpaperApplyResult.Success
                val latestHistory = repository.getSnapshot().history.first()
                assertTrue(
                    "Sampled ratio ${latestHistory.pitchBlackRatio} must stay inside [0.35, 0.75]",
                    latestHistory.pitchBlackRatio in 0.35f..0.75f
                )
                assertTrue(
                    "Sampled intricacy ${latestHistory.intricacy} must stay inside [0.40, 0.60]",
                    latestHistory.intricacy in 0.40f..0.60f
                )
                sampledIntricacies.add((latestHistory.intricacy * 100f).toInt())
                val expectedPct = (latestHistory.pitchBlackRatio * 100f).toInt()
                sampledRatios.add(expectedPct)
                assertTrue(
                    "Range cycle #$cycle (${latestHistory.pattern}) sampled=$expectedPct% measured=${success.measuredBlackPercent}%",
                    abs(success.measuredBlackPercent - expectedPct) <= 8
                )
            }
            assertTrue("Range mode should sample varied ratios across cycles", sampledRatios.size >= 2)
            assertTrue("Intricacy should sample varied values between 40% and 60%", sampledIntricacies.size >= 2)
        } finally {
            WallpaperScheduler.cancelAll(context)
            repository.updateConfig { com.example.model.GeneratorConfig() }
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
                pitchBlackMode = PitchBlackMode.SINGLE,
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

        // 1. Verify existence checks & strict no-op when both AlarmManager and WorkManager are already scheduled
        val initialScheduledTarget = WallpaperScheduler.scheduleNext(context, 30)
        assertTrue(WallpaperScheduler.isWorkScheduled(context, 30))
        assertTrue(WallpaperScheduler.isAlarmScheduled(context))

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarmManager = Shadows.shadowOf(alarmManager)
        assertEquals(1, shadowAlarmManager.scheduledAlarms.size)
        @Suppress("DEPRECATION")
        val initialAlarmTrigger = shadowAlarmManager.scheduledAlarms.single().triggerAtTime
        val initialWorkId = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WallpaperScheduler.UNIQUE_PERIODIC_WORK_NAME)
            .get()
            .first { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
            .id

        // Pass a different valid future timestamp: if ensureScheduled truly checks existence first,
        // it must NOT re-arm AlarmManager (initialAlarmTrigger stays untouched) and must keep initialWorkId.
        val probeFutureTarget = initialScheduledTarget - 60_000L
        val noOpReturnedTarget = WallpaperScheduler.ensureScheduled(
            context = context,
            intervalMinutes = 30,
            desiredNextRunMillis = probeFutureTarget
        )
        assertEquals(probeFutureTarget, noOpReturnedTarget)
        assertEquals(1, shadowAlarmManager.scheduledAlarms.size)
        @Suppress("DEPRECATION")
        val afterNoOpAlarmTrigger = shadowAlarmManager.scheduledAlarms.single().triggerAtTime
        assertEquals(
            "ensureScheduled must NOT re-arm AlarmManager when PendingIntent and WorkManager already exist",
            initialAlarmTrigger,
            afterNoOpAlarmTrigger
        )
        val afterNoOpWorkId = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WallpaperScheduler.UNIQUE_PERIODIC_WORK_NAME)
            .get()
            .first { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
            .id
        assertEquals(
            "ensureScheduled must NOT recreate WorkManager request when already enqueued",
            initialWorkId,
            afterNoOpWorkId
        )

        // 2. Simulate OEM battery saver force-stopping/cancelling background work & PendingIntents
        val futureTarget = System.currentTimeMillis() + 12 * 60_000L
        WallpaperScheduler.cancelAll(context)
        assertFalse(WallpaperScheduler.isWorkScheduled(context, 30))
        assertFalse(WallpaperScheduler.isAlarmScheduled(context))
        assertEquals(0, shadowAlarmManager.scheduledAlarms.size)

        val restoredTarget = WallpaperScheduler.ensureScheduled(
            context = context,
            intervalMinutes = 30,
            desiredNextRunMillis = futureTarget
        )
        assertEquals(futureTarget, restoredTarget)
        assertTrue(
            "ensureScheduled on app open must re-enqueue cancelled periodic work automatically",
            WallpaperScheduler.isWorkScheduled(context, 30)
        )
        assertTrue(
            "ensureScheduled on app open must re-arm cancelled AlarmManager PendingIntent automatically",
            WallpaperScheduler.isAlarmScheduled(context)
        )
        assertEquals(1, shadowAlarmManager.scheduledAlarms.size)
        @Suppress("DEPRECATION")
        val restoredAlarmTrigger = shadowAlarmManager.scheduledAlarms.single().triggerAtTime
        assertEquals(futureTarget, restoredAlarmTrigger)

        WallpaperScheduler.cancelAll(context)
        repository.updateConfig { com.example.model.GeneratorConfig() }
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun autoWallpaperSet_bothSeparateTarget_generatesTwoDistinctWallpapersForHomeAndLock() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val repository = WallpaperPreferencesRepository(context)

        try {
            repository.updateConfig {
                it.copy(
                    autoRotateEnabled = true,
                    patternsRandomized = true,
                    autoTarget = com.example.model.WallpaperTarget.BOTH_SEPARATE,
                    pitchBlackMode = PitchBlackMode.SINGLE,
                    pitchBlackRatio = 0.68f
                )
            }

            val result = WallpaperScheduler.executeAutoRotationCycle(
                context = context,
                wasScheduled = true,
                force = true
            )
            assertTrue(result is WallpaperApplyResult.Success)
            val success = result as WallpaperApplyResult.Success
            assertTrue(
                "Expected ~68% average black across both separate wallpapers, got ${success.measuredBlackPercent}%",
                abs(success.measuredBlackPercent - 68) <= 8
            )

            val history = repository.getSnapshot().history
            assertTrue("Expected at least 2 history entries (home + lockscreen)", history.size >= 2)
            val homeEntry = history[0]
            val lockEntry = history[1]
            assertNotEquals("Home and lockscreen seeds must be completely different", homeEntry.seed, lockEntry.seed)
            assertNotEquals("Home and lockscreen patterns must differ when randomized", homeEntry.pattern, lockEntry.pattern)
            assertEquals(0.68f, homeEntry.pitchBlackRatio, 0.001f)
            assertEquals(0.68f, lockEntry.pitchBlackRatio, 0.001f)
            assertTrue(homeEntry.intricacy in 0.40f..0.60f)
            assertTrue(lockEntry.intricacy in 0.40f..0.60f)
        } finally {
            WallpaperScheduler.cancelAll(context)
            repository.updateConfig { com.example.model.GeneratorConfig() }
        }
    }

    private fun awaitMainIdleUntil(timeoutMs: Long = 6_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(15L)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun autoChange_whenAppNotForceClosed_runsInForegroundAndBackgroundAndSyncsUiState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val repository = WallpaperPreferencesRepository(context)
        val wallpaperManager = WallpaperManager.getInstance(context)
        val viewModelStore = ViewModelStore()

        try {
            WallpaperScheduler.cancelAll(context)
            repository.updateConfig { GeneratorConfig() }

            // 1. App OPEN (not force-closed): initialize RimaluxViewModel and enable 30m auto-change
            val viewModel = ViewModelProvider(
                viewModelStore,
                RimaluxViewModel.Factory(context)
            )[RimaluxViewModel::class.java]

            awaitMainIdleUntil {
                viewModel.uiState.value.preview != null && !viewModel.uiState.value.isGeneratingPreview
            }
            val initialPreviewSeed = requireNotNull(viewModel.uiState.value.preview).seed

            viewModel.updateIntervalMinutes(30)
            awaitMainIdleUntil {
                viewModel.uiState.value.config.autoRotateEnabled &&
                    viewModel.uiState.value.config.intervalMinutes == 30 &&
                    viewModel.uiState.value.config.nextRunEpochMillis > System.currentTimeMillis()
            }

            assertTrue(WallpaperScheduler.isWorkScheduled(context, 30))
            assertTrue(WallpaperScheduler.isAlarmScheduled(context))
            val scheduledTargetBeforeTick = repository.getSnapshot().config.nextRunEpochMillis

            // Backdate lastAutoExecMillis (if any) so a scheduled alarm tick is due now
            val preAlarmSnapshot = repository.getSnapshot()
            if (preAlarmSnapshot.history.isNotEmpty()) {
                repository.recordGeneration(
                    preAlarmSnapshot.history.first().copy(
                        timestampMillis = System.currentTimeMillis() - 35 * 60_000L
                    ),
                    isAutoTick = true
                )
            }

            // Trigger WallpaperAlarmReceiver while app & RimaluxViewModel are alive (NOT force-closed)
            val alarmIntent = Intent(context, WallpaperAlarmReceiver::class.java).apply {
                action = WallpaperScheduler.ACTION_AUTO_GENERATE_WALLPAPER
            }
            val alarmResult = WallpaperAlarmReceiver().handleAlarm(context, alarmIntent)
            assertTrue(alarmResult is WallpaperApplyResult.Success)
            assertFalse((alarmResult as WallpaperApplyResult.Success).skippedAsDuplicate)

            // Verify system wallpaper was applied via WallpaperManager
            assertTrue(alarmResult.widthPx > 0 && alarmResult.heightPx > 0)
            val shadowWallpaperManager = Shadows.shadowOf(wallpaperManager)
            assertNotNull(
                "Expected system home wallpaper bitmap applied on alarm rotation",
                shadowWallpaperManager.getBitmap(WallpaperManager.FLAG_SYSTEM)
            )
            assertNotNull(
                "Expected system lock wallpaper bitmap applied on alarm rotation",
                shadowWallpaperManager.getBitmap(WallpaperManager.FLAG_LOCK)
            )

            val afterAlarmSnapshot = repository.getSnapshot()
            val firstRotatedSeed = afterAlarmSnapshot.currentSeed
            assertNotEquals("Alarm rotation must generate a fresh seed", initialPreviewSeed, firstRotatedSeed)
            assertTrue("Next run target must advance to next 30m window", afterAlarmSnapshot.config.nextRunEpochMillis >= scheduledTargetBeforeTick)
            assertEquals(1, afterAlarmSnapshot.history.count { it.wasAutoScheduled && it.seed == firstRotatedSeed })

            // Verify active ViewModel automatically observed background rotation and synced its live preview
            awaitMainIdleUntil {
                viewModel.uiState.value.preview?.seed == firstRotatedSeed
            }
            assertEquals(firstRotatedSeed, viewModel.uiState.value.preview?.seed)

            // 1b. Foreground schedule monitor while app remains open and target timestamp becomes due
            val afterAlarmHistoryFirst = afterAlarmSnapshot.history.first()
            repository.recordAutoRotationAtomically(
                primaryItem = afterAlarmHistoryFirst.copy(timestampMillis = System.currentTimeMillis() - 31 * 60_000L),
                nextRunEpochMillis = System.currentTimeMillis() - 1_000L
            )
            awaitMainIdleUntil {
                val nextRun = viewModel.uiState.value.config.nextRunEpochMillis
                nextRun in 1L..System.currentTimeMillis()
            }
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(2_500L))
            awaitMainIdleUntil {
                val seed = viewModel.uiState.value.preview?.seed
                seed != null && seed != firstRotatedSeed
            }
            val foregroundRotatedSeed = requireNotNull(viewModel.uiState.value.preview).seed
            assertNotEquals("Foreground monitor must rotate wallpaper when due", firstRotatedSeed, foregroundRotatedSeed)
            assertTrue(
                "Foreground monitor must advance nextRunEpochMillis into the future",
                repository.getSnapshot().config.nextRunEpochMillis > System.currentTimeMillis() + 25 * 60_000L
            )

            // 2. App in BACKGROUND / UI closed (ViewModel cleared, NOT force-closed): WorkManager executes next interval
            viewModelStore.clear()
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            // Simulate 31 minutes elapsed since foreground rotation
            val foregroundHistoryItem = repository.getSnapshot().history.first()
            repository.recordGeneration(
                foregroundHistoryItem.copy(timestampMillis = System.currentTimeMillis() - 31 * 60_000L),
                isAutoTick = true
            )
            val scheduledCountBeforeWorker = repository.getSnapshot().history.count { it.wasAutoScheduled }

            val worker = TestListenableWorkerBuilder<WallpaperRotationWorker>(context).build()
            val workerResult = worker.doWork()
            assertEquals(ListenableWorker.Result.success(), workerResult)

            val afterWorkerSnapshot = repository.getSnapshot()
            val thirdRotatedSeed = afterWorkerSnapshot.currentSeed
            assertNotEquals("Background worker must generate a new wallpaper seed", foregroundRotatedSeed, thirdRotatedSeed)
            assertEquals(
                "Scheduled history count must increase by 1",
                scheduledCountBeforeWorker + 1,
                afterWorkerSnapshot.history.count { it.wasAutoScheduled }
            )
            assertTrue(WallpaperScheduler.isWorkScheduled(context, 30))
            assertTrue(WallpaperScheduler.isAlarmScheduled(context))
        } finally {
            viewModelStore.clear()
            WallpaperScheduler.cancelAll(context)
            repository.updateConfig { GeneratorConfig() }
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun autoChange_afterForceClose_resumesProperlyOnReopenAndBootAndExecutesNextCycle() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val repository = WallpaperPreferencesRepository(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarmManager = Shadows.shadowOf(alarmManager)
        var activeStore: ViewModelStore? = null

        try {
            WallpaperScheduler.cancelAll(context)
            val initialFutureTarget = WallpaperScheduler.scheduleNext(context, 30)
            repository.updateConfig {
                GeneratorConfig(
                    autoRotateEnabled = true,
                    intervalMinutes = 30,
                    nextRunEpochMillis = initialFutureTarget
                )
            }
            assertTrue(WallpaperScheduler.isWorkScheduled(context, 30))
            assertTrue(WallpaperScheduler.isAlarmScheduled(context))
            assertEquals(1, shadowAlarmManager.scheduledAlarms.size)

            // --- CASE A: Force-Closed BEFORE scheduled interval elapsed, then reopened ---
            WallpaperScheduler.cancelAll(context)
            assertFalse("Force-close simulation must clear WorkManager jobs", WallpaperScheduler.isWorkScheduled(context, 30))
            assertFalse("Force-close simulation must clear AlarmManager PendingIntent", WallpaperScheduler.isAlarmScheduled(context))
            assertEquals(0, shadowAlarmManager.scheduledAlarms.size)

            val scheduledHistoryBeforeReopen = repository.getSnapshot().history.count { it.wasAutoScheduled }

            // Reopen app (RimaluxViewModel cold init + ON_START ensureAutoScheduleIfEnabled)
            val reopenStoreA = ViewModelStore().also { activeStore = it }
            val reopenedVmA = ViewModelProvider(
                reopenStoreA,
                RimaluxViewModel.Factory(context)
            )[RimaluxViewModel::class.java]

            awaitMainIdleUntil {
                reopenedVmA.uiState.value.preview != null &&
                    WallpaperScheduler.isWorkScheduled(context, 30) &&
                    WallpaperScheduler.isAlarmScheduled(context)
            }
            reopenedVmA.ensureAutoScheduleIfEnabled()
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            assertTrue("Reopening after Force-Close must restore WorkManager schedule", WallpaperScheduler.isWorkScheduled(context, 30))
            assertTrue("Reopening after Force-Close must restore AlarmManager schedule", WallpaperScheduler.isAlarmScheduled(context))
            assertEquals(1, shadowAlarmManager.scheduledAlarms.size)
            @Suppress("DEPRECATION")
            assertEquals(
                "Remaining future target should be preserved when reopened before expiry",
                initialFutureTarget,
                shadowAlarmManager.scheduledAlarms.single().triggerAtTime
            )
            assertEquals(
                "Reopening app must NOT trigger an unexpected auto-rotation immediately",
                scheduledHistoryBeforeReopen,
                repository.getSnapshot().history.count { it.wasAutoScheduled }
            )

            // Execute the restored worker when interval arrives -> verify auto-change actually runs!
            reopenStoreA.clear()
            activeStore = null
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            val preExecSnapshotA = repository.getSnapshot()
            val historyItemToUse = preExecSnapshotA.history.firstOrNull()?.copy(
                timestampMillis = System.currentTimeMillis() - 35 * 60_000L
            ) ?: GeneratedHistoryItem(
                id = "gen_prev",
                seed = preExecSnapshotA.currentSeed.takeIf { it != 0L } ?: 0x1234L,
                pattern = ArtPattern.SILK_RIBBONS,
                colorFillMode = ColorFillMode.GRADIENT,
                paletteColors = listOf(0xFF00F5D4L),
                pitchBlackRatio = 0.85f,
                intricacy = 0.5f,
                timestampMillis = System.currentTimeMillis() - 35 * 60_000L,
                wasAutoScheduled = true
            )
            repository.recordGeneration(historyItemToUse, isAutoTick = true)
            val seedBeforeResumedExec = repository.getSnapshot().currentSeed
            val resumedWorkerResultA = TestListenableWorkerBuilder<WallpaperRotationWorker>(context).build().doWork()
            assertEquals(ListenableWorker.Result.success(), resumedWorkerResultA)
            val afterResumedExecA = repository.getSnapshot()
            assertNotEquals("Restored schedule must successfully rotate wallpaper", seedBeforeResumedExec, afterResumedExecA.currentSeed)
            assertTrue(WallpaperScheduler.isWorkScheduled(context, 30))
            assertTrue(WallpaperScheduler.isAlarmScheduled(context))

            // --- CASE B: Force-Closed overnight (scheduled target EXPIRED while dead), then reopened ---
            WallpaperScheduler.cancelAll(context)
            val expiredTarget = System.currentTimeMillis() - 2 * 60 * 60_000L // 2 hours ago
            repository.updateConfig {
                it.copy(
                    autoRotateEnabled = true,
                    intervalMinutes = 30,
                    nextRunEpochMillis = expiredTarget
                )
            }
            assertFalse(WallpaperScheduler.isWorkScheduled(context, 30))
            assertFalse(WallpaperScheduler.isAlarmScheduled(context))

            val reopenTimeMillis = System.currentTimeMillis()
            val reopenStoreB = ViewModelStore().also { activeStore = it }
            val reopenedVmB = ViewModelProvider(
                reopenStoreB,
                RimaluxViewModel.Factory(context)
            )[RimaluxViewModel::class.java]

            awaitMainIdleUntil {
                reopenedVmB.uiState.value.config.nextRunEpochMillis > reopenTimeMillis &&
                    WallpaperScheduler.isWorkScheduled(context, 30) &&
                    WallpaperScheduler.isAlarmScheduled(context)
            }

            val repairedNextRun = repository.getSnapshot().config.nextRunEpochMillis
            assertTrue(
                "Expired nextRunEpochMillis after Force-Close must be repaired to ~now + 30m",
                repairedNextRun >= reopenTimeMillis + 29 * 60_000L
            )
            assertEquals(1, shadowAlarmManager.scheduledAlarms.size)
            reopenStoreB.clear()
            activeStore = null
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            // --- CASE C: Device Reboot / App Update recovery via BootCompletedReceiver ---
            WallpaperScheduler.cancelAll(context)
            assertFalse(WallpaperScheduler.isWorkScheduled(context, 30))
            assertFalse(WallpaperScheduler.isAlarmScheduled(context))

            BootCompletedReceiver().handleBootOrUpdateIntent(
                context,
                Intent(Intent.ACTION_BOOT_COMPLETED)
            )
            assertTrue("BOOT_COMPLETED must restore WorkManager schedule", WallpaperScheduler.isWorkScheduled(context, 30))
            assertTrue("BOOT_COMPLETED must restore AlarmManager schedule", WallpaperScheduler.isAlarmScheduled(context))
            assertEquals(1, shadowAlarmManager.scheduledAlarms.size)
        } finally {
            activeStore?.clear()
            WallpaperScheduler.cancelAll(context)
            repository.updateConfig { GeneratorConfig() }
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun autoChange_producesNoDuplicateWallpaperChanges_acrossConcurrentDualTriggersDozeStaggerAndRepeatedOpens() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val repository = WallpaperPreferencesRepository(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarmManager = Shadows.shadowOf(alarmManager)
        val viewModelStore = ViewModelStore()

        try {
            WallpaperScheduler.cancelAll(context)
            val initialNextRun = WallpaperScheduler.scheduleNext(context, 30)
            repository.updateConfig {
                GeneratorConfig(
                    autoRotateEnabled = true,
                    intervalMinutes = 30,
                    nextRunEpochMillis = initialNextRun
                )
            }
            // Ensure any prior auto-exec timestamp is outside the 30m dedupe window
            val baselineSnapshot = repository.getSnapshot()
            if (baselineSnapshot.history.isNotEmpty()) {
                repository.recordGeneration(
                    baselineSnapshot.history.first().copy(timestampMillis = System.currentTimeMillis() - 60 * 60_000L),
                    isAutoTick = true
                )
            }
            val seedBeforeRace = repository.getSnapshot().currentSeed

            // 1. SIMULTANEOUS 3-WAY RACE: AlarmManager + WorkManager + Foreground Monitor firing together
            val alarmIntent = Intent(context, WallpaperAlarmReceiver::class.java).apply {
                action = WallpaperScheduler.ACTION_AUTO_GENERATE_WALLPAPER
            }
            val results = coroutineScope {
                listOf(
                    async(Dispatchers.Default) {
                        WallpaperAlarmReceiver().handleAlarm(context, alarmIntent)!!
                    },
                    async(Dispatchers.Default) {
                        WallpaperScheduler.executeAutoRotationCycle(
                            context = context,
                            wasScheduled = true,
                            force = false,
                            fromRunningWorker = true
                        )
                    },
                    async(Dispatchers.Default) {
                        WallpaperScheduler.executeAutoRotationCycle(
                            context = context,
                            wasScheduled = true,
                            force = false,
                            fromRunningWorker = false
                        )
                    }
                ).awaitAll()
            }

            val executedFreshCount = results.count { it is WallpaperApplyResult.Success && !it.skippedAsDuplicate }
            val skippedDuplicateCount = results.count { it is WallpaperApplyResult.Success && it.skippedAsDuplicate }
            assertEquals("Exactly 1 trigger in a 3-way simultaneous race may rotate the wallpaper", 1, executedFreshCount)
            assertEquals("The other 2 simultaneous triggers must be skipped as duplicates", 2, skippedDuplicateCount)

            val afterRaceSnapshot = repository.getSnapshot()
            val winnerSeed = afterRaceSnapshot.currentSeed
            assertNotEquals(seedBeforeRace, winnerSeed)
            val scheduledHistoryAfterRace = afterRaceSnapshot.history.count { it.wasAutoScheduled }

            // 2. DOZE-STAGGERED SECOND TRIGGER: 5 minutes later on a 30-minute interval (must NOT double-fire!)
            val winnerHistoryItem = afterRaceSnapshot.history.first()
            repository.recordGeneration(
                winnerHistoryItem.copy(timestampMillis = System.currentTimeMillis() - 5 * 60_000L),
                isAutoTick = true
            )

            val delayedWorkerResult = TestListenableWorkerBuilder<WallpaperRotationWorker>(context).build().doWork()
            assertEquals(ListenableWorker.Result.success(), delayedWorkerResult)
            val delayedAlarmResult = WallpaperAlarmReceiver().handleAlarm(context, alarmIntent)
            assertTrue(delayedAlarmResult is WallpaperApplyResult.Success)
            assertTrue(
                "A Doze-delayed second trigger 5m after the first on a 30m interval must be deduplicated",
                (delayedAlarmResult as WallpaperApplyResult.Success).skippedAsDuplicate
            )
            assertEquals(
                "Wallpaper seed must NOT change on Doze-staggered duplicate trigger",
                winnerSeed,
                repository.getSnapshot().currentSeed
            )
            assertEquals(
                "Scheduled history count must NOT increment on Doze-staggered duplicate trigger",
                scheduledHistoryAfterRace,
                repository.getSnapshot().history.count { it.wasAutoScheduled }
            )

            // 3. REPEATED APP OPENS (ON_START) & UI SLIDER CHANGES: strict no-op on schedules & zero extra rotations
            val viewModel = ViewModelProvider(
                viewModelStore,
                RimaluxViewModel.Factory(context)
            )[RimaluxViewModel::class.java]
            awaitMainIdleUntil {
                viewModel.uiState.value.preview != null && !viewModel.uiState.value.isGeneratingPreview
            }

            @Suppress("DEPRECATION")
            val alarmTriggerBeforeOpens = shadowAlarmManager.scheduledAlarms.single().triggerAtTime
            val workIdBeforeOpens = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(WallpaperScheduler.UNIQUE_PERIODIC_WORK_NAME)
                .get()
                .first { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
                .id
            val nextRunBeforeSlider = repository.getSnapshot().config.nextRunEpochMillis

            repeat(5) {
                viewModel.ensureAutoScheduleIfEnabled()
                Shadows.shadowOf(Looper.getMainLooper()).idle()
            }
            viewModel.updatePitchBlackRatio(0.65f)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(250L))
            val flushDeadline = System.currentTimeMillis() + 3_000L
            while (System.currentTimeMillis() < flushDeadline &&
                abs(repository.getSnapshot().config.pitchBlackRatio - 0.65f) >= 0.001f
            ) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(20L)
            }

            @Suppress("DEPRECATION")
            val alarmTriggerAfterOpens = shadowAlarmManager.scheduledAlarms.single().triggerAtTime
            val workIdAfterOpens = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(WallpaperScheduler.UNIQUE_PERIODIC_WORK_NAME)
                .get()
                .first { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
                .id

            assertEquals("Repeated app opens must not reset AlarmManager trigger time", alarmTriggerBeforeOpens, alarmTriggerAfterOpens)
            assertEquals("Repeated app opens must not recreate WorkManager job", workIdBeforeOpens, workIdAfterOpens)
            assertEquals(0.65f, repository.getSnapshot().config.pitchBlackRatio, 0.001f)
            assertEquals(
                "UI slider edits must not clobber persisted nextRunEpochMillis",
                nextRunBeforeSlider,
                repository.getSnapshot().config.nextRunEpochMillis
            )
            assertEquals("Repeated app opens must produce zero extra rotations", winnerSeed, repository.getSnapshot().currentSeed)

            // 4. GENUINE NEXT INTERVAL (31m elapsed on 30m interval): DOES rotate cleanly once!
            repository.recordGeneration(
                winnerHistoryItem.copy(timestampMillis = System.currentTimeMillis() - 31 * 60_000L),
                isAutoTick = true
            )
            val genuineNextResult = WallpaperAlarmReceiver().handleAlarm(context, alarmIntent)
            assertTrue(genuineNextResult is WallpaperApplyResult.Success)
            assertFalse(
                "Genuine next 30m interval trigger must execute normally",
                (genuineNextResult as WallpaperApplyResult.Success).skippedAsDuplicate
            )
            assertNotEquals("Genuine next interval must produce a new wallpaper seed", winnerSeed, repository.getSnapshot().currentSeed)
        } finally {
            viewModelStore.clear()
            WallpaperScheduler.cancelAll(context)
            repository.updateConfig { GeneratorConfig() }
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
