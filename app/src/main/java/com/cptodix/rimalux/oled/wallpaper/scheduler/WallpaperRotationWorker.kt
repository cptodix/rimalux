package com.cptodix.rimalux.oled.wallpaper.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cptodix.rimalux.oled.wallpaper.data.WallpaperPreferencesRepository
import com.cptodix.rimalux.oled.wallpaper.platform.WallpaperApplyResult

/**
 * Persistent OS-backed background worker (JobScheduler + WorkManager SQLite store)
 * that generates and applies a new mathematical vector wallpaper even when the app
 * is closed, swiped from Recents, or its process was killed by the system.
 */
class WallpaperRotationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val result = WallpaperScheduler.executeAutoRotationCycle(
            context = applicationContext,
            wasScheduled = true,
            force = false,
            fromRunningWorker = true
        )
        return when (result) {
            is WallpaperApplyResult.Success -> Result.success()
            is WallpaperApplyResult.Error -> {
                val isStillEnabled = runCatching {
                    WallpaperPreferencesRepository(applicationContext)
                        .getSnapshot()
                        .config
                        .autoRotateEnabled
                }.getOrDefault(false)
                if (isStillEnabled && runAttemptCount < 2) {
                    Result.retry()
                } else {
                    Result.success()
                }
            }
        }
    }
}
