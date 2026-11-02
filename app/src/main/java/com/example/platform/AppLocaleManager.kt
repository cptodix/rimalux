package com.example.platform

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import com.example.model.AppLanguage
import java.util.Locale

/**
 * Platform helper that resolves the initial app language on first launch from the device's
 * system locales (falling back to `en-US`), persists user selection for synchronous startup
 * wrapping in [android.app.Activity.attachBaseContext], and creates localized [Context] instances
 * with proper RTL layout direction across Android 7.0 (API 24) through Android 16 (API 36).
 */
object AppLocaleManager {

    private const val PREFS_NAME = "rimalux_locale_prefs"
    private const val KEY_APP_LANGUAGE_TAG = "app_language_tag"

    /**
     * Returns the user's ordered OS system locales independent of any active app-locale override.
     */
    fun getDeviceSystemLocales(context: Context): List<Locale> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = runCatching {
                context.getSystemService(LocaleManager::class.java)
            }.getOrNull()
            val sysList = localeManager?.systemLocales
            if (sysList != null && !sysList.isEmpty) {
                return (0 until sysList.size()).map { sysList[it] }
            }
        }
        val sysConfigLocales = Resources.getSystem().configuration.locales
        if (!sysConfigLocales.isEmpty) {
            return (0 until sysConfigLocales.size()).map { sysConfigLocales[it] }
        }
        return listOf(Locale.getDefault())
    }

    /**
     * Resolves the active [AppLanguage]. On the very first launch after installation (when no
     * language tag has been persisted yet), matches [getDeviceSystemLocales] against supported
     * [AppLanguage] entries with fallback to `en-US`, and records that initial choice.
     */
    fun resolveOrInitializeLanguage(
        context: Context,
        systemLocalesOverride: List<Locale>? = null
    ): AppLanguage {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedTag = prefs.getString(KEY_APP_LANGUAGE_TAG, null)
        if (!savedTag.isNullOrBlank()) {
            val resolved = AppLanguage.fromTagOrDefault(savedTag)
            if (resolved.languageTag != savedTag) {
                prefs.edit().putString(KEY_APP_LANGUAGE_TAG, resolved.languageTag).apply()
            }
            return resolved
        }

        // Check if Android 13+ system per-app language settings already specify a supported locale
        if (systemLocalesOverride == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val appLocales = runCatching {
                context.getSystemService(LocaleManager::class.java)?.applicationLocales
            }.getOrNull()
            if (appLocales != null && !appLocales.isEmpty) {
                val osOverrideList = (0 until appLocales.size()).map { appLocales[it] }
                val fromOsSetting = AppLanguage.resolveInitialLanguage(osOverrideList)
                prefs.edit().putString(KEY_APP_LANGUAGE_TAG, fromOsSetting.languageTag).apply()
                return fromOsSetting
            }
        }

        val systemLocales = systemLocalesOverride ?: getDeviceSystemLocales(context)
        val initialLanguage = AppLanguage.resolveInitialLanguage(systemLocales)
        prefs.edit().putString(KEY_APP_LANGUAGE_TAG, initialLanguage.languageTag).apply()
        return initialLanguage
    }

    /**
     * Persists a user-selected [language] synchronously and returns `true` if the active language changed.
     */
    fun setSelectedLanguage(context: Context, language: AppLanguage): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previousTag = prefs.getString(KEY_APP_LANGUAGE_TAG, null)
        val previous = previousTag?.let { AppLanguage.fromTagOrDefault(it) }
        prefs.edit().putString(KEY_APP_LANGUAGE_TAG, language.languageTag).commit()
        Locale.setDefault(language.toLocale())
        return previous != language
    }

    /**
     * Wraps an Activity or Service base [Context] with the resolved [AppLanguage] configuration
     * and layout direction.
     */
    fun wrapContext(base: Context): Context {
        val language = resolveOrInitializeLanguage(base)
        return createLocalizedContext(base, language)
    }

    /**
     * Produces a configuration-overridden [Context] whose `Resources` and layout direction
     * match [language].
     */
    fun createLocalizedContext(base: Context, language: AppLanguage): Context {
        val targetLocale = language.toLocale()
        Locale.setDefault(targetLocale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(targetLocale))
        config.setLayoutDirection(targetLocale)
        return base.createConfigurationContext(config)
    }

    internal fun clearStoredLanguageForTesting(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
