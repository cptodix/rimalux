package com.example.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.WallpaperPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val repository = WallpaperPreferencesRepository(appContext)
                val snapshot = repository.getSnapshot()
                if (snapshot.config.autoRotateEnabled) {
                    val nextMillis = WallpaperScheduler.scheduleNext(
                        appContext,
                        snapshot.config.intervalMinutes
                    )
                    repository.updateConfig { it.copy(nextRunEpochMillis = nextMillis) }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
