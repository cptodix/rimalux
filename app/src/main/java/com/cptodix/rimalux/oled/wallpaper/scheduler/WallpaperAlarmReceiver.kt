package com.cptodix.rimalux.oled.wallpaper.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cptodix.rimalux.oled.wallpaper.platform.WallpaperApplyResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WallpaperAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != null && intent.action != WallpaperScheduler.ACTION_AUTO_GENERATE_WALLPAPER) {
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handleAlarm(context, intent)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    internal suspend fun handleAlarm(context: Context, intent: Intent?): WallpaperApplyResult? {
        if (intent?.action != null && intent.action != WallpaperScheduler.ACTION_AUTO_GENERATE_WALLPAPER) {
            return null
        }
        return WallpaperScheduler.executeAutoRotationCycle(
            context = context.applicationContext,
            wasScheduled = true,
            force = false,
            fromRunningWorker = false
        )
    }
}
