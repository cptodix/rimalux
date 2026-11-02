package com.cptodix.rimalux.oled.wallpaper.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.cptodix.rimalux.oled.wallpaper.data.WallpaperPreferencesRepository
import com.cptodix.rimalux.oled.wallpaper.engine.WallpaperGeneratorEngine
import com.cptodix.rimalux.oled.wallpaper.model.GeneratedHistoryItem
import com.cptodix.rimalux.oled.wallpaper.model.WallpaperTarget
import com.cptodix.rimalux.oled.wallpaper.platform.WallpaperApplyResult
import com.cptodix.rimalux.oled.wallpaper.platform.WallpaperRenderer
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object WallpaperScheduler {

    private const val ALARM_REQUEST_CODE = 48120
    const val ACTION_AUTO_GENERATE_WALLPAPER = "com.cptodix.rimalux.oled.wallpaper.action.AUTO_GENERATE_WALLPAPER"
    const val UNIQUE_PERIODIC_WORK_NAME = "rimalux_periodic_wallpaper_rotation"
    const val UNIQUE_CHAINED_WORK_NAME = "rimalux_chained_wallpaper_rotation"

    private val rotationMutex = Mutex()
    private val scheduleMutex = Mutex()

    fun isAlarmScheduled(context: Context): Boolean {
        val appContext = context.applicationContext
        val existingPendingIntent = PendingIntent.getBroadcast(
            appContext,
            ALARM_REQUEST_CODE,
            buildAlarmIntent(appContext),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        return existingPendingIntent != null
    }

    fun isWorkScheduled(context: Context, intervalMinutes: Int): Boolean {
        val appContext = context.applicationContext
        val safeMinutes = intervalMinutes.coerceIn(1, 10080)
        val targetWorkName = if (safeMinutes >= 15) {
            UNIQUE_PERIODIC_WORK_NAME
        } else {
            UNIQUE_CHAINED_WORK_NAME
        }
        return runCatching {
            val infos = WorkManager.getInstance(appContext)
                .getWorkInfosForUniqueWork(targetWorkName)
                .get()
            infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
        }.getOrDefault(false)
    }

    fun scheduleNext(
        context: Context,
        intervalMinutes: Int,
        fromRunningWorker: Boolean = false
    ): Long {
        val appContext = context.applicationContext
        val safeMinutes = intervalMinutes.coerceIn(1, 10080)
        val intervalMillis = safeMinutes * 60_000L
        val triggerAtMillis = System.currentTimeMillis() + intervalMillis

        runCatching {
            val workManager = WorkManager.getInstance(appContext)
            if (safeMinutes >= 15) {
                workManager.cancelUniqueWork(UNIQUE_CHAINED_WORK_NAME)
                val periodicCurrentlyRunning = fromRunningWorker && runCatching {
                    workManager.getWorkInfosForUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
                        .get()
                        .any { it.state == WorkInfo.State.RUNNING }
                }.getOrDefault(false)

                if (!periodicCurrentlyRunning) {
                    val periodicRequest = PeriodicWorkRequestBuilder<WallpaperRotationWorker>(
                        safeMinutes.toLong(),
                        TimeUnit.MINUTES
                    )
                        .setInitialDelay(safeMinutes.toLong(), TimeUnit.MINUTES)
                        .addTag(UNIQUE_PERIODIC_WORK_NAME)
                        .build()

                    workManager.enqueueUniquePeriodicWork(
                        UNIQUE_PERIODIC_WORK_NAME,
                        ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                        periodicRequest
                    )
                }
            } else {
                workManager.cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
                val chainedCurrentlyRunning = fromRunningWorker && runCatching {
                    workManager.getWorkInfosForUniqueWork(UNIQUE_CHAINED_WORK_NAME)
                        .get()
                        .any { it.state == WorkInfo.State.RUNNING }
                }.getOrDefault(false)

                val chainedRequest = OneTimeWorkRequestBuilder<WallpaperRotationWorker>()
                    .setInitialDelay(safeMinutes.toLong(), TimeUnit.MINUTES)
                    .addTag(UNIQUE_CHAINED_WORK_NAME)
                    .build()

                val workPolicy = if (chainedCurrentlyRunning) {
                    ExistingWorkPolicy.APPEND_OR_REPLACE
                } else {
                    ExistingWorkPolicy.REPLACE
                }

                workManager.enqueueUniqueWork(
                    UNIQUE_CHAINED_WORK_NAME,
                    workPolicy,
                    chainedRequest
                )
            }
        }

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val pendingIntent = buildPendingIntent(appContext)
        alarmManager?.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )

        return triggerAtMillis
    }

    /**
     * Verifies whether OS AlarmManager and WorkManager schedules already exist before doing any work.
     * - If both the active WorkManager job and AlarmManager PendingIntent already exist and
     *   [desiredNextRunMillis] is still valid in the future, this returns immediately as a no-op.
     * - Re-enqueues or re-arms whichever schedule was cleared (e.g., by an OEM battery saver
     *   Force-Stop or device reboot) or expired while Force-Stopped.
     */
    suspend fun ensureScheduled(
        context: Context,
        intervalMinutes: Int,
        desiredNextRunMillis: Long = 0L
    ): Long = withContext(Dispatchers.IO) {
        scheduleMutex.withLock {
            val appContext = context.applicationContext
            val safeMinutes = intervalMinutes.coerceIn(1, 10080)
            val intervalMillis = safeMinutes * 60_000L
            val now = System.currentTimeMillis()

            val hasValidFutureTarget = desiredNextRunMillis > now + 5_000L &&
                desiredNextRunMillis <= now + intervalMillis + 60_000L

            val workAlreadyScheduled = isWorkScheduled(appContext, safeMinutes)
            val alarmAlreadyScheduled = hasValidFutureTarget && isAlarmScheduled(appContext)

            if (workAlreadyScheduled && alarmAlreadyScheduled) {
                return@withLock desiredNextRunMillis
            }

            val triggerAtMillis = if (hasValidFutureTarget) {
                desiredNextRunMillis
            } else {
                now + intervalMillis
            }
            val initialDelayMillis = (triggerAtMillis - now).coerceAtLeast(5_000L)

            if (!workAlreadyScheduled || !hasValidFutureTarget) {
                runCatching {
                    val workManager = WorkManager.getInstance(appContext)
                    if (safeMinutes >= 15) {
                        workManager.cancelUniqueWork(UNIQUE_CHAINED_WORK_NAME)
                        val periodicRequest = PeriodicWorkRequestBuilder<WallpaperRotationWorker>(
                            safeMinutes.toLong(),
                            TimeUnit.MINUTES
                        )
                            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                            .addTag(UNIQUE_PERIODIC_WORK_NAME)
                            .build()

                        val policy = if (workAlreadyScheduled) {
                            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
                        } else {
                            ExistingPeriodicWorkPolicy.KEEP
                        }

                        workManager.enqueueUniquePeriodicWork(
                            UNIQUE_PERIODIC_WORK_NAME,
                            policy,
                            periodicRequest
                        )
                    } else {
                        workManager.cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
                        val chainedRequest = OneTimeWorkRequestBuilder<WallpaperRotationWorker>()
                            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                            .addTag(UNIQUE_CHAINED_WORK_NAME)
                            .build()

                        val policy = if (workAlreadyScheduled) {
                            ExistingWorkPolicy.REPLACE
                        } else {
                            ExistingWorkPolicy.KEEP
                        }

                        workManager.enqueueUniqueWork(
                            UNIQUE_CHAINED_WORK_NAME,
                            policy,
                            chainedRequest
                        )
                    }
                }
            }

            if (!alarmAlreadyScheduled) {
                val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                val pendingIntent = buildPendingIntent(appContext)
                alarmManager?.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }

            triggerAtMillis
        }
    }

    fun cancelAll(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            val workManager = WorkManager.getInstance(appContext)
            workManager.cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
            workManager.cancelUniqueWork(UNIQUE_CHAINED_WORK_NAME)
        }
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val existingPendingIntent = PendingIntent.getBroadcast(
            appContext,
            ALARM_REQUEST_CODE,
            buildAlarmIntent(appContext),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (existingPendingIntent != null) {
            alarmManager?.cancel(existingPendingIntent)
            existingPendingIntent.cancel()
        }
    }

    private fun buildAlarmIntent(context: Context): Intent {
        return Intent(context.applicationContext, WallpaperAlarmReceiver::class.java).apply {
            action = ACTION_AUTO_GENERATE_WALLPAPER
        }
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val appContext = context.applicationContext
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(
            appContext,
            ALARM_REQUEST_CODE,
            buildAlarmIntent(appContext),
            flags
        )
    }


    suspend fun executeAutoRotationCycle(
        context: Context,
        wasScheduled: Boolean,
        force: Boolean = false,
        fromRunningWorker: Boolean = false
    ): WallpaperApplyResult = withContext(Dispatchers.IO + NonCancellable) {
        rotationMutex.withLock {
            val appContext = context.applicationContext
            val repository = WallpaperPreferencesRepository(appContext)
            val snapshot = repository.getSnapshot()
            val config = snapshot.config
            val safeMinutes = config.intervalMinutes.coerceIn(1, 10080)
            val intervalMillis = safeMinutes * 60_000L

            if (wasScheduled && !force) {
                if (!config.autoRotateEnabled) {
                    cancelAll(appContext)
                    return@withLock WallpaperApplyResult.Error("Automatic interval is turned off")
                }
                val now = System.currentTimeMillis()
                val dedupeWindowMillis = (intervalMillis * 3L / 4L).coerceAtLeast(30_000L)
                val elapsed = now - snapshot.lastAutoExecMillis
                if (snapshot.lastAutoExecMillis > 0L && elapsed in -60_000L until dedupeWindowMillis) {
                    val validFutureTarget = if (config.nextRunEpochMillis > now + 5_000L) {
                        config.nextRunEpochMillis
                    } else {
                        (snapshot.lastAutoExecMillis + intervalMillis).coerceAtLeast(now + 10_000L)
                    }
                    val ensuredNext = ensureScheduled(
                        context = appContext,
                        intervalMinutes = safeMinutes,
                        desiredNextRunMillis = validFutureTarget
                    )
                    if (ensuredNext != config.nextRunEpochMillis) {
                        repository.updateConfig { it.copy(nextRunEpochMillis = ensuredNext) }
                    }
                    val spec = WallpaperRenderer.getTargetWallpaperSpec(appContext)
                    val cachedRatio = snapshot.currentPitchBlackRatio ?: config.pitchBlackRatio
                    return@withLock WallpaperApplyResult.Success(
                        widthPx = spec.widthPx,
                        heightPx = spec.heightPx,
                        densityDpi = spec.densityDpi,
                        measuredBlackPercent = (cachedRatio * 100f).toInt(),
                        skippedAsDuplicate = true
                    )
                }
            }

            val rng = Random(System.nanoTime() xor System.currentTimeMillis() xor snapshot.currentSeed)
            val homeSeed = WallpaperGeneratorEngine.nextDistinctSeed(
                rng = rng,
                excludedSeeds = setOf(0L, snapshot.currentSeed)
            )

            val (w, h) = WallpaperRenderer.getTargetWallpaperDimensions(appContext)
            val aspect = (h.toFloat() / w.toFloat()).coerceIn(0.5f, 3.0f)
            val coreSpec = config.toCoreSpec()

            val nowMillis = maxOf(System.currentTimeMillis(), snapshot.lastAutoExecMillis + 1L)
            val homeSynthesized: com.cptodix.rimalux.oled.wallpaper.engine.SynthesizedWallpaper
            var lockHistoryItem: GeneratedHistoryItem? = null

            val applyResult = if (config.autoTarget == WallpaperTarget.BOTH_SEPARATE) {
                val lockSeed = WallpaperGeneratorEngine.nextDistinctSeed(
                    rng = rng,
                    excludedSeeds = setOf(0L, snapshot.currentSeed, homeSeed)
                )
                val (homeGen, lockGen) = WallpaperGeneratorEngine.synthesizeDistinctPair(
                    primarySeed = homeSeed,
                    secondarySeed = lockSeed,
                    spec = coreSpec,
                    availablePalettes = snapshot.userPalettes,
                    aspectRatio = aspect,
                    rng = rng
                )
                homeSynthesized = homeGen

                val res = WallpaperRenderer.applySeparateWallpapersToSystem(
                    context = appContext,
                    homeScene = homeGen.scene,
                    homePaletteColors = homeGen.paletteColors,
                    homePitchBlackRatio = homeGen.pitchBlackRatio,
                    lockScene = lockGen.scene,
                    lockPaletteColors = lockGen.paletteColors,
                    lockPitchBlackRatio = lockGen.pitchBlackRatio,
                    colorFillMode = config.colorFillMode
                )

                lockHistoryItem = GeneratedHistoryItem(
                    id = "gen_${nowMillis}_lock_${lockGen.seed.toString(16)}",
                    seed = lockGen.seed,
                    pattern = lockGen.pattern,
                    colorFillMode = lockGen.colorFillMode,
                    paletteColors = lockGen.paletteColors,
                    pitchBlackRatio = lockGen.pitchBlackRatio,
                    intricacy = lockGen.intricacy,
                    timestampMillis = nowMillis,
                    wasAutoScheduled = wasScheduled,
                    patternWeightMode = lockGen.patternWeightMode
                )
                res
            } else {
                homeSynthesized = WallpaperGeneratorEngine.synthesize(
                    seed = homeSeed,
                    spec = coreSpec,
                    availablePalettes = snapshot.userPalettes,
                    aspectRatio = aspect,
                    paletteRng = rng,
                    ratioRng = rng,
                    intricacyOverride = WallpaperGeneratorEngine.sampleIntricacy(rng)
                )
                WallpaperRenderer.applyWallpaperToSystem(
                    context = appContext,
                    scene = homeSynthesized.scene,
                    paletteColors = homeSynthesized.paletteColors,
                    pitchBlackRatio = homeSynthesized.pitchBlackRatio,
                    colorFillMode = config.colorFillMode,
                    target = config.autoTarget
                )
            }

            val nextMillis = if (config.autoRotateEnabled) {
                scheduleNext(
                    context = appContext,
                    intervalMinutes = safeMinutes,
                    fromRunningWorker = fromRunningWorker
                )
            } else {
                null
            }

            val homeHistoryItem = GeneratedHistoryItem(
                id = "gen_${nowMillis}_${homeSynthesized.seed.toString(16)}",
                seed = homeSynthesized.seed,
                pattern = homeSynthesized.pattern,
                colorFillMode = homeSynthesized.colorFillMode,
                paletteColors = homeSynthesized.paletteColors,
                pitchBlackRatio = homeSynthesized.pitchBlackRatio,
                intricacy = homeSynthesized.intricacy,
                timestampMillis = nowMillis,
                wasAutoScheduled = wasScheduled,
                patternWeightMode = homeSynthesized.patternWeightMode
            )
            repository.recordAutoRotationAtomically(
                primaryItem = homeHistoryItem,
                secondaryHistoryItem = lockHistoryItem,
                nextRunEpochMillis = nextMillis
            )

            applyResult
        }
    }
}
