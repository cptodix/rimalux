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
        if (!isSupportedAction(intent?.action)) {
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handleBootOrUpdateIntent(context, intent)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    internal suspend fun handleBootOrUpdateIntent(context: Context, intent: Intent?) {
        if (!isSupportedAction(intent?.action)) {
            return
        }
        val appContext = context.applicationContext
        val repository = WallpaperPreferencesRepository(appContext)
        val snapshot = repository.getSnapshot()
        if (snapshot.config.autoRotateEnabled) {
            val nextMillis = WallpaperScheduler.ensureScheduled(
                context = appContext,
                intervalMinutes = snapshot.config.intervalMinutes,
                desiredNextRunMillis = snapshot.config.nextRunEpochMillis
            )
            if (nextMillis != snapshot.config.nextRunEpochMillis) {
                repository.updateConfig { it.copy(nextRunEpochMillis = nextMillis) }
            }
        }
    }

    private fun isSupportedAction(action: String?): Boolean {
        if (action == null) return false
        return action in SUPPORTED_ACTIONS
    }

    companion object {
        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
    }
}
