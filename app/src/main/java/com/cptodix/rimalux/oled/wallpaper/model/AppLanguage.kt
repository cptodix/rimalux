package com.cptodix.rimalux.oled.wallpaper.model

import java.util.Locale

/**
 * Single-source registry of supported application languages.
 *
 * HOW TO ADD A NEW LANGUAGE IN THE FUTURE:
 * 1. Add translated resources in `app/src/main/res/values-<lang>/strings.xml`
 *    (note: Android uses `values-in` for Indonesian `id-ID`).
 * 2. Add `<locale android:name="<bcp47>" />` in `app/src/main/res/xml/locales_config.xml`.
 * 3. Add an enum entry below with its BCP-47 [languageTag] and [nativeName] (endonym).
 *
 * First-launch system language detection, `en-US` fallback, DataStore persistence,
 * and the Studio language selector popup automatically adapt to all entries here.
 */
enum class AppLanguage(
    val languageTag: String,
    val nativeName: String
) {
    EN_US(
        languageTag = "en-US",
        nativeName = "English (US)"
    ),
    ID_ID(
        languageTag = "id-ID",
        nativeName = "Bahasa Indonesia"
    );

    fun toLocale(): Locale = Locale.forLanguageTag(languageTag)

    companion object {
        val DEFAULT: AppLanguage = EN_US

        fun fromTagOrNull(tag: String?): AppLanguage? {
            if (tag.isNullOrBlank()) return null
            val normalizedInput = normalizeCanonicalTag(Locale.forLanguageTag(tag.replace('_', '-')))
            return entries.firstOrNull { entry ->
                entry.languageTag.equals(tag, ignoreCase = true) ||
                    normalizeCanonicalTag(entry.toLocale()) == normalizedInput
            }
        }

        fun fromTagOrDefault(tag: String?): AppLanguage = fromTagOrNull(tag) ?: DEFAULT

        /**
         * Picks the best supported [AppLanguage] for first launch after install by inspecting
         * the user's ordered [systemLocales], falling back to [DEFAULT] (`en-US`) when no
         * supported translation matches.
         */
        fun resolveInitialLanguage(
            systemLocales: List<Locale>,
            supported: List<AppLanguage> = entries
        ): AppLanguage {
            if (supported.isEmpty()) return DEFAULT
            val fallback = supported.firstOrNull { it == DEFAULT } ?: supported.first()
            val matchedTag = resolveBestSupportedTag(
                systemLocales = systemLocales,
                supportedTags = supported.map { it.languageTag },
                fallbackTag = fallback.languageTag
            )
            return supported.firstOrNull { it.languageTag.equals(matchedTag, ignoreCase = true) } ?: fallback
        }

        /**
         * Pure BCP-47 language negotiation across ordered [systemLocales] against [supportedTags]
         * with 3-tier specificity (exact language+script+region -> language+script -> primary language)
         * and deterministic fallback to [fallbackTag].
         */
        internal fun resolveBestSupportedTag(
            systemLocales: List<Locale>,
            supportedTags: List<String>,
            fallbackTag: String = DEFAULT.languageTag
        ): String {
            if (supportedTags.isEmpty()) return fallbackTag
            val parsedSupported = supportedTags.map { rawTag ->
                rawTag to parseLocaleComponents(Locale.forLanguageTag(rawTag.replace('_', '-')))
            }

            for (sysLocale in systemLocales) {
                val sys = parseLocaleComponents(sysLocale)
                if (sys.language.isEmpty()) continue

                // Tier 1: Exact language + script + region match (e.g. en-US == en-US, pt-BR == pt-BR)
                parsedSupported.firstOrNull { (_, cand) ->
                    cand.language == sys.language &&
                        cand.script == sys.script &&
                        cand.region.isNotEmpty() &&
                        cand.region == sys.region
                }?.let { return it.first }

                // Tier 2: Language + script match (e.g. zh-CN / zh-SG -> zh-Hans; zh-TW / zh-HK -> zh-Hant)
                if (sys.script.isNotEmpty()) {
                    parsedSupported.firstOrNull { (_, cand) ->
                        cand.language == sys.language && cand.script == sys.script
                    }?.let { return it.first }
                }

                // Tier 3: Primary ISO-639 language match (e.g. en-GB -> en-US, es-MX -> es, in-ID -> id)
                parsedSupported.firstOrNull { (_, cand) ->
                    cand.language == sys.language &&
                        (cand.script.isEmpty() || sys.script.isEmpty() || cand.script == sys.script)
                }?.let { return it.first }
            }

            return fallbackTag
        }

        private data class LocaleComponents(
            val language: String,
            val script: String,
            val region: String
        )

        private fun normalizeCanonicalTag(locale: Locale): String {
            val c = parseLocaleComponents(locale)
            return buildString {
                append(c.language)
                if (c.script.isNotEmpty()) append('-').append(c.script)
                if (c.region.isNotEmpty()) append('-').append(c.region)
            }
        }

        private fun parseLocaleComponents(locale: Locale): LocaleComponents {
            val rawLang = locale.language.lowercase(Locale.ROOT)
            val normalizedLang = when (rawLang) {
                "in" -> "id" // Legacy Android Indonesian code -> modern BCP-47 "id"
                "iw" -> "he" // Legacy Android Hebrew code -> modern BCP-47 "he"
                "ji" -> "yi" // Legacy Android Yiddish code -> modern BCP-47 "yi"
                else -> rawLang
            }
            val region = locale.country.uppercase(Locale.ROOT)
            val explicitScript = locale.script.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
            }
            val inferredScript = when {
                explicitScript.isNotEmpty() -> explicitScript
                normalizedLang == "zh" && (region == "TW" || region == "HK" || region == "MO") -> "Hant"
                normalizedLang == "zh" && (region == "CN" || region == "SG") -> "Hans"
                else -> ""
            }
            return LocaleComponents(
                language = normalizedLang,
                script = inferredScript,
                region = region
            )
        }
    }
}
