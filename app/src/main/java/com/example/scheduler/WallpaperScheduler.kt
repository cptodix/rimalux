package com.example.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.WallpaperPreferencesRepository
import com.example.engine.MathPatternGenerator
import com.example.engine.WallpaperApplyResult
import com.example.engine.WallpaperRenderer
import com.example.model.GeneratedHistoryItem
import com.example.model.PaletteMode
import com.example.model.RandomPaletteGenerator
import com.example.model.StarterPalettes
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object WallpaperScheduler {

    private const val ALARM_REQUEST_CODE = 48120
    const val ACTION_AUTO_GENERATE_WALLPAPER = "com.example.action.AUTO_GENERATE_WALLPAPER"
    const val UNIQUE_PERIODIC_WORK_NAME = "vectra_periodic_wallpaper_rotation"
    const val UNIQUE_CHAINED_WORK_NAME = "vectra_chained_wallpaper_rotation"

    private val rotationMutex = Mutex()

    fun scheduleNext(context: Context, intervalMinutes: Int): Long {
        val appContext = context.applicationContext
        val safeMinutes = intervalMinutes.coerceIn(1, 10080)
        val intervalMillis = safeMinutes * 60_000L
        val triggerAtMillis = System.currentTimeMillis() + intervalMillis

        runCatching {
            val workManager = WorkManager.getInstance(appContext)
            if (safeMinutes >= 15) {
                workManager.cancelUniqueWork(UNIQUE_CHAINED_WORK_NAME)
                val periodicRequest = PeriodicWorkRequestBuilder<WallpaperRotationWorker>(
                    safeMinutes.toLong(),
                    TimeUnit.MINUTES
                )
                    .setInitialDelay(safeMinutes.toLong(), TimeUnit.MINUTES)
                    .addTag(UNIQUE_PERIODIC_WORK_NAME)
                    .build()

                workManager.enqueueUniquePeriodicWork(
                    UNIQUE_PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    periodicRequest
                )
            } else {
                workManager.cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
                val chainedRequest = OneTimeWorkRequestBuilder<WallpaperRotationWorker>()
                    .setInitialDelay(safeMinutes.toLong(), TimeUnit.MINUTES)
                    .addTag(UNIQUE_CHAINED_WORK_NAME)
                    .build()

                workManager.enqueueUniqueWork(
                    UNIQUE_CHAINED_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
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

    fun cancelAll(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            val workManager = WorkManager.getInstance(appContext)
            workManager.cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
            workManager.cancelUniqueWork(UNIQUE_CHAINED_WORK_NAME)
        }
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        alarmManager?.cancel(buildPendingIntent(appContext))
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context.applicationContext, WallpaperAlarmReceiver::class.java).apply {
            action = ACTION_AUTO_GENERATE_WALLPAPER
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(
            context.applicationContext,
            ALARM_REQUEST_CODE,
            intent,
            flags
        )
    }

    suspend fun executeAutoRotationCycle(
        context: Context,
        wasScheduled: Boolean,
        force: Boolean = false
    ): WallpaperApplyResult = rotationMutex.withLock {
        val appContext = context.applicationContext
        val repository = WallpaperPreferencesRepository(appContext)
        val snapshot = repository.getSnapshot()
        val config = snapshot.config

        if (wasScheduled && !force) {
            if (!config.autoRotateEnabled) {
                cancelAll(appContext)
                return@withLock WallpaperApplyResult.Error("Automatic interval is turned off")
            }
            val now = System.currentTimeMillis()
            val dedupeWindowMillis = (config.intervalMinutes * 60_000L / 2L).coerceIn(20_000L, 120_000L)
            val elapsed = now - snapshot.lastAutoExecMillis
            if (snapshot.lastAutoExecMillis > 0L && elapsed in 0 until dedupeWindowMillis) {
                val spec = WallpaperRenderer.getTargetWallpaperSpec(appContext)
                return@withLock WallpaperApplyResult.Success(
                    widthPx = spec.widthPx,
                    heightPx = spec.heightPx,
                    densityDpi = spec.densityDpi,
                    measuredBlackPercent = (config.pitchBlackRatio * 100f).toInt()
                )
            }
        }

        val rng = Random(System.nanoTime() xor System.currentTimeMillis() xor snapshot.currentSeed)
        val seed = (rng.nextLong() and 0x7FFFFFFFFFFFL).takeIf { it != 0L } ?: 0x4A91B23DL

        val styleToUse = MathPatternGenerator.resolveStyleForSeed(
            seed = seed,
            stylesRandomized = config.stylesRandomized,
            selectedStyles = config.selectedStyles
        )

        val colorsToUse = when (config.paletteMode) {
            PaletteMode.RANDOMIZED -> RandomPaletteGenerator.generate(seed)
            PaletteMode.PALETTE_LIST -> {
                val activePalettes = snapshot.userPalettes.filter { it.id in config.selectedPaletteIds }
                val chosen = if (activePalettes.isNotEmpty()) {
                    activePalettes[rng.nextInt(activePalettes.size)]
                } else {
                    snapshot.userPalettes.firstOrNull() ?: StarterPalettes.defaults.first()
                }
                chosen.colors
            }
        }

        val (w, h) = WallpaperRenderer.getTargetWallpaperDimensions(appContext)
        val aspect = (h.toFloat() / w.toFloat()).coerceIn(0.5f, 3.0f)

        val scene = MathPatternGenerator.generate(
            seed = seed,
            style = styleToUse,
            intricacy = config.intricacy,
            aspectRatio = aspect
        )

        val applyResult = WallpaperRenderer.applyWallpaperToSystem(
            context = appContext,
            scene = scene,
            paletteColors = colorsToUse,
            pitchBlackRatio = config.pitchBlackRatio,
            colorFillMode = config.colorFillMode,
            target = config.autoTarget
        )

        val nowMillis = System.currentTimeMillis()
        val historyItem = GeneratedHistoryItem(
            id = "gen_${nowMillis}_${seed.toString(16)}",
            seed = seed,
            style = styleToUse,
            colorFillMode = config.colorFillMode,
            paletteColors = colorsToUse,
            pitchBlackRatio = config.pitchBlackRatio,
            intricacy = config.intricacy,
            timestampMillis = nowMillis,
            wasAutoScheduled = wasScheduled
        )
        repository.recordGeneration(historyItem, isAutoTick = true)

        if (config.autoRotateEnabled) {
            val nextMillis = scheduleNext(appContext, config.intervalMinutes)
            repository.updateConfig { it.copy(nextRunEpochMillis = nextMillis) }
        }

        applyResult
    }
}
