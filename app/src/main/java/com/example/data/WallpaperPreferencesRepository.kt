package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.model.ArtPattern
import com.example.model.ColorFillMode
import com.example.model.ColorPalette
import com.example.model.GeneratedHistoryItem
import com.example.model.GeneratorConfig
import com.example.model.PaletteMode
import com.example.model.PatternWeightMode
import com.example.model.PitchBlackMode
import com.example.model.StarterPalettes
import com.example.model.WallpaperTarget
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val RIMALUX_DATASTORE_NAME = "rimalux_wallpaper_v3_prefs"
private val rimaluxDataStoreRegistry = ConcurrentHashMap<String, DataStore<Preferences>>()

private val Context.rimaluxDataStore: DataStore<Preferences>
    get() {
        val appCtx = applicationContext
        val storageRootKey = runCatching { appCtx.filesDir.canonicalPath }
            .getOrElse { appCtx.filesDir.absolutePath }
        return rimaluxDataStoreRegistry.computeIfAbsent(storageRootKey) {
            PreferenceDataStoreFactory.create(
                produceFile = { appCtx.preferencesDataStoreFile(RIMALUX_DATASTORE_NAME) }
            )
        }
    }

data class StoredWallpaperState(
    val config: GeneratorConfig = GeneratorConfig(),
    val currentSeed: Long = 0L,
    val currentPattern: ArtPattern? = null,
    val currentPatternWeightMode: PatternWeightMode? = null,
    val currentColors: List<Long> = emptyList(),
    val currentPitchBlackRatio: Float? = null,
    val currentIntricacy: Float? = null,
    val lastAutoExecMillis: Long = 0L,
    val userPalettes: List<ColorPalette> = StarterPalettes.defaults,
    val history: List<GeneratedHistoryItem> = emptyList(),
    val hasSeenGestureTutorial: Boolean = false
)

class WallpaperPreferencesRepository(private val context: Context) {

    private object Keys {
        val PATTERNS_RANDOMIZED = booleanPreferencesKey("patterns_randomized")
        val SELECTED_PATTERNS = stringPreferencesKey("selected_patterns")
        val PATTERN_WEIGHT_MODE = stringPreferencesKey("pattern_weight_mode")
        val COLOR_FILL_MODE = stringPreferencesKey("color_fill_mode")
        val PALETTE_MODE = stringPreferencesKey("palette_mode")
        val SELECTED_PALETTE_IDS = stringPreferencesKey("selected_palette_ids")
        val PITCH_BLACK_MODE = stringPreferencesKey("pitch_black_mode")
        val PITCH_BLACK_RATIO = floatPreferencesKey("pitch_black_ratio_v2")
        val PITCH_BLACK_RANGE_MIN = floatPreferencesKey("pitch_black_range_min_v2")
        val PITCH_BLACK_RANGE_MAX = floatPreferencesKey("pitch_black_range_max_v2")
        val CURRENT_BLACK_RATIO = floatPreferencesKey("current_black_ratio")
        val CURRENT_INTRICACY = floatPreferencesKey("current_intricacy")
        val CURRENT_WEIGHT_MODE = stringPreferencesKey("current_weight_mode")
        val MANUAL_TARGET = stringPreferencesKey("manual_target")
        val AUTO_ROTATE_ENABLED = booleanPreferencesKey("auto_rotate_enabled")
        val INTERVAL_MINUTES = intPreferencesKey("interval_minutes")
        val AUTO_TARGET = stringPreferencesKey("auto_target")
        val NEXT_RUN_MILLIS = longPreferencesKey("next_run_millis")
        val LAST_AUTO_EXEC_MILLIS = longPreferencesKey("last_auto_exec_millis")
        val CURRENT_SEED = longPreferencesKey("current_seed")
        val CURRENT_PATTERN = stringPreferencesKey("current_pattern")
        val CURRENT_COLORS_JSON = stringPreferencesKey("current_colors_json")
        val USER_PALETTES_JSON = stringPreferencesKey("user_palettes_json")
        val DELETED_PALETTE_IDS = stringPreferencesKey("deleted_palette_ids")
        val HISTORY_JSON = stringPreferencesKey("history_json")
        val HAS_SEEN_GESTURE_TUTORIAL = booleanPreferencesKey("has_seen_gesture_tutorial")
    }

    val stateFlow: Flow<StoredWallpaperState> = context.applicationContext.rimaluxDataStore.data.map { prefs ->
        val palettes = resolveUserPalettes(prefs)
        val config = parseConfigFromPrefs(prefs, palettes)

        StoredWallpaperState(
            config = config,
            currentSeed = prefs[Keys.CURRENT_SEED] ?: 0L,
            currentPattern = prefs[Keys.CURRENT_PATTERN]?.let { ArtPattern.fromId(it) },
            currentPatternWeightMode = prefs[Keys.CURRENT_WEIGHT_MODE]?.let { PatternWeightMode.fromId(it) },
            currentColors = decodeLongList(prefs[Keys.CURRENT_COLORS_JSON]),
            currentPitchBlackRatio = prefs[Keys.CURRENT_BLACK_RATIO]?.coerceIn(0f, 0.95f),
            currentIntricacy = prefs[Keys.CURRENT_INTRICACY]?.coerceIn(0.15f, 1.0f),
            lastAutoExecMillis = prefs[Keys.LAST_AUTO_EXEC_MILLIS] ?: 0L,
            userPalettes = palettes,
            history = decodeHistory(prefs[Keys.HISTORY_JSON]),
            hasSeenGestureTutorial = prefs[Keys.HAS_SEEN_GESTURE_TUTORIAL] ?: false
        )
    }.flowOn(Dispatchers.IO)

    suspend fun getSnapshot(): StoredWallpaperState = withContext(Dispatchers.IO) {
        stateFlow.first()
    }

    suspend fun setGestureTutorialSeen(seen: Boolean = true) = withContext(Dispatchers.IO) {
        context.applicationContext.rimaluxDataStore.edit { prefs ->
            prefs[Keys.HAS_SEEN_GESTURE_TUTORIAL] = seen
        }
        Unit
    }

    private fun resolveUserPalettes(prefs: Preferences): List<ColorPalette> {
        val decoded = decodePalettes(prefs[Keys.USER_PALETTES_JSON])
        val deletedIds = decodeStringSet(prefs[Keys.DELETED_PALETTE_IDS])
        if (decoded == null) {
            return StarterPalettes.defaults.filterNot { it.id in deletedIds }.ifEmpty { StarterPalettes.defaults }
        }
        val existingIds = decoded.map { it.id }.toSet()
        val missingStarters = StarterPalettes.defaults.filter {
            it.id !in existingIds && it.id !in deletedIds
        }
        return (decoded + missingStarters).ifEmpty { StarterPalettes.defaults }
    }

    private fun parseConfigFromPrefs(prefs: Preferences, palettes: List<ColorPalette>): GeneratorConfig {
        val parsedPatterns = decodePatternSet(prefs[Keys.SELECTED_PATTERNS])
        val parsedPaletteIds = decodeStringSet(prefs[Keys.SELECTED_PALETTE_IDS])
            .filter { id -> palettes.any { it.id == id } }
            .toSet()
            .ifEmpty { setOf(palettes.firstOrNull()?.id ?: "obsidian_aurora") }

        val rawRangeMin = (prefs[Keys.PITCH_BLACK_RANGE_MIN] ?: 0.75f).coerceIn(0f, 0.95f)
        val rawRangeMax = (prefs[Keys.PITCH_BLACK_RANGE_MAX] ?: 0.90f).coerceIn(0f, 0.95f)
        val rangeLo = min(rawRangeMin, rawRangeMax)
        val rangeHi = max(rawRangeMin, rawRangeMax)

        return GeneratorConfig(
            patternsRandomized = prefs[Keys.PATTERNS_RANDOMIZED] ?: true,
            selectedPatterns = parsedPatterns.ifEmpty {
                setOf(
                    ArtPattern.SILK_RIBBONS,
                    ArtPattern.CRYSTAL_VORONOI,
                    ArtPattern.SACRED_MANDALA,
                    ArtPattern.PENROSE_QUASICRYSTAL
                )
            },
            patternWeightMode = prefs[Keys.PATTERN_WEIGHT_MODE]?.let { PatternWeightMode.fromId(it) } ?: PatternWeightMode.NORMAL,
            colorFillMode = prefs[Keys.COLOR_FILL_MODE]?.let { ColorFillMode.fromId(it) } ?: ColorFillMode.GRADIENT,
            paletteMode = prefs[Keys.PALETTE_MODE]?.let { PaletteMode.fromId(it) } ?: PaletteMode.RANDOMIZED,
            selectedPaletteIds = parsedPaletteIds,
            pitchBlackMode = prefs[Keys.PITCH_BLACK_MODE]?.let { PitchBlackMode.fromId(it) } ?: PitchBlackMode.SINGLE,
            pitchBlackRatio = (prefs[Keys.PITCH_BLACK_RATIO] ?: GeneratorConfig.DEFAULT_PITCH_BLACK_RATIO).coerceIn(0f, 0.95f),
            pitchBlackRangeMin = rangeLo,
            pitchBlackRangeMax = rangeHi,
            manualTarget = prefs[Keys.MANUAL_TARGET]?.let { WallpaperTarget.fromId(it) } ?: WallpaperTarget.BOTH,
            autoRotateEnabled = prefs[Keys.AUTO_ROTATE_ENABLED] ?: false,
            intervalMinutes = (prefs[Keys.INTERVAL_MINUTES] ?: 60).coerceIn(1, 10080),
            autoTarget = prefs[Keys.AUTO_TARGET]?.let { WallpaperTarget.fromId(it) } ?: WallpaperTarget.BOTH,
            nextRunEpochMillis = prefs[Keys.NEXT_RUN_MILLIS] ?: 0L
        )
    }

    suspend fun updateConfig(
        activeBlackRatioOverride: Float? = null,
        currentPatternOverride: ArtPattern? = null,
        currentColorsOverride: List<Long>? = null,
        transform: (GeneratorConfig) -> GeneratorConfig
    ) = withContext(Dispatchers.IO) {
        context.applicationContext.rimaluxDataStore.edit { prefs ->
            val palettes = resolveUserPalettes(prefs)
            val currentConfig = parseConfigFromPrefs(prefs, palettes)
            val updated = transform(currentConfig)
            val rangeLo = min(updated.pitchBlackRangeMin, updated.pitchBlackRangeMax).coerceIn(0f, 0.95f)
            val rangeHi = max(updated.pitchBlackRangeMin, updated.pitchBlackRangeMax).coerceIn(rangeLo, 0.95f)

            prefs[Keys.PATTERNS_RANDOMIZED] = updated.patternsRandomized
            prefs[Keys.SELECTED_PATTERNS] = encodePatternSet(updated.selectedPatterns)
            prefs[Keys.PATTERN_WEIGHT_MODE] = updated.patternWeightMode.id
            prefs[Keys.COLOR_FILL_MODE] = updated.colorFillMode.id
            prefs[Keys.PALETTE_MODE] = updated.paletteMode.id
            prefs[Keys.SELECTED_PALETTE_IDS] = encodeStringSet(updated.selectedPaletteIds)
            prefs[Keys.PITCH_BLACK_MODE] = updated.pitchBlackMode.id
            prefs[Keys.PITCH_BLACK_RATIO] = updated.pitchBlackRatio.coerceIn(0f, 0.95f)
            prefs[Keys.PITCH_BLACK_RANGE_MIN] = rangeLo
            prefs[Keys.PITCH_BLACK_RANGE_MAX] = rangeHi
            prefs[Keys.MANUAL_TARGET] = updated.manualTarget.id
            prefs[Keys.AUTO_ROTATE_ENABLED] = updated.autoRotateEnabled
            prefs[Keys.INTERVAL_MINUTES] = updated.intervalMinutes.coerceIn(1, 10080)
            prefs[Keys.AUTO_TARGET] = updated.autoTarget.id
            prefs[Keys.NEXT_RUN_MILLIS] = updated.nextRunEpochMillis

            if (activeBlackRatioOverride != null) {
                prefs[Keys.CURRENT_BLACK_RATIO] = activeBlackRatioOverride.coerceIn(0f, 0.95f)
            }
            if (currentPatternOverride != null) {
                prefs[Keys.CURRENT_PATTERN] = currentPatternOverride.id
            }
            if (currentColorsOverride != null) {
                prefs[Keys.CURRENT_COLORS_JSON] = encodeLongList(currentColorsOverride)
            }
        }
        Unit
    }

    suspend fun recordGeneration(item: GeneratedHistoryItem, isAutoTick: Boolean = false) = withContext(Dispatchers.IO) {
        context.applicationContext.rimaluxDataStore.edit { prefs ->
            prefs[Keys.CURRENT_SEED] = item.seed
            prefs[Keys.CURRENT_PATTERN] = item.pattern.id
            prefs[Keys.CURRENT_WEIGHT_MODE] = item.patternWeightMode.id
            prefs[Keys.CURRENT_COLORS_JSON] = encodeLongList(item.paletteColors)
            prefs[Keys.CURRENT_BLACK_RATIO] = item.pitchBlackRatio.coerceIn(0f, 0.95f)
            prefs[Keys.CURRENT_INTRICACY] = item.intricacy.coerceIn(0.15f, 1.0f)
            if (isAutoTick) {
                prefs[Keys.LAST_AUTO_EXEC_MILLIS] = item.timestampMillis
            }
            val existing = decodeHistory(prefs[Keys.HISTORY_JSON])
            val updated = (listOf(item) + existing.filterNot { it.seed == item.seed }).take(24)
            prefs[Keys.HISTORY_JSON] = encodeHistory(updated)
        }
        Unit
    }

    suspend fun recordAutoRotationAtomically(
        primaryItem: GeneratedHistoryItem,
        secondaryHistoryItem: GeneratedHistoryItem? = null,
        nextRunEpochMillis: Long? = null
    ) = withContext(Dispatchers.IO) {
        context.applicationContext.rimaluxDataStore.edit { prefs ->
            prefs[Keys.CURRENT_SEED] = primaryItem.seed
            prefs[Keys.CURRENT_PATTERN] = primaryItem.pattern.id
            prefs[Keys.CURRENT_WEIGHT_MODE] = primaryItem.patternWeightMode.id
            prefs[Keys.CURRENT_COLORS_JSON] = encodeLongList(primaryItem.paletteColors)
            prefs[Keys.CURRENT_BLACK_RATIO] = primaryItem.pitchBlackRatio.coerceIn(0f, 0.95f)
            prefs[Keys.CURRENT_INTRICACY] = primaryItem.intricacy.coerceIn(0.15f, 1.0f)
            prefs[Keys.LAST_AUTO_EXEC_MILLIS] = primaryItem.timestampMillis
            if (nextRunEpochMillis != null) {
                prefs[Keys.NEXT_RUN_MILLIS] = nextRunEpochMillis
            }
            val existing = decodeHistory(prefs[Keys.HISTORY_JSON])
            val prepended = if (secondaryHistoryItem != null && secondaryHistoryItem.seed != primaryItem.seed) {
                listOf(primaryItem, secondaryHistoryItem)
            } else {
                listOf(primaryItem)
            }
            val prependedSeeds = prepended.map { it.seed }.toSet()
            val updated = (prepended + existing.filterNot { it.seed in prependedSeeds }).take(24)
            prefs[Keys.HISTORY_JSON] = encodeHistory(updated)
        }
        Unit
    }

    suspend fun saveTimelineState(
        current: GeneratedHistoryItem,
        pastNewestFirst: List<GeneratedHistoryItem>
    ) = withContext(Dispatchers.IO) {
        context.applicationContext.rimaluxDataStore.edit { prefs ->
            prefs[Keys.CURRENT_SEED] = current.seed
            prefs[Keys.CURRENT_PATTERN] = current.pattern.id
            prefs[Keys.CURRENT_WEIGHT_MODE] = current.patternWeightMode.id
            prefs[Keys.CURRENT_COLORS_JSON] = encodeLongList(current.paletteColors)
            prefs[Keys.CURRENT_BLACK_RATIO] = current.pitchBlackRatio.coerceIn(0f, 0.95f)
            prefs[Keys.CURRENT_INTRICACY] = current.intricacy.coerceIn(0.15f, 1.0f)
            val combined = (listOf(current) + pastNewestFirst.filterNot { it.seed == current.seed }).take(24)
            prefs[Keys.HISTORY_JSON] = encodeHistory(combined)
        }
        Unit
    }

    suspend fun appendHistoryOnly(item: GeneratedHistoryItem) = withContext(Dispatchers.IO) {
        context.applicationContext.rimaluxDataStore.edit { prefs ->
            val existing = decodeHistory(prefs[Keys.HISTORY_JSON])
            val updated = (listOf(item) + existing.filterNot { it.seed == item.seed }).take(24)
            prefs[Keys.HISTORY_JSON] = encodeHistory(updated)
        }
        Unit
    }

    suspend fun addPaletteToList(palette: ColorPalette, selectAndSwitchToListMode: Boolean = true) = withContext(Dispatchers.IO) {
        val snapshot = getSnapshot()
        val updatedPalettes = listOf(palette) + snapshot.userPalettes.filterNot { it.id == palette.id }
        context.applicationContext.rimaluxDataStore.edit { prefs ->
            val deleted = decodeStringSet(prefs[Keys.DELETED_PALETTE_IDS]) - palette.id
            prefs[Keys.DELETED_PALETTE_IDS] = encodeStringSet(deleted)
            prefs[Keys.USER_PALETTES_JSON] = encodePalettes(updatedPalettes)
            if (selectAndSwitchToListMode) {
                prefs[Keys.PALETTE_MODE] = PaletteMode.PALETTE_LIST.id
                val updatedSelected = snapshot.config.selectedPaletteIds + palette.id
                prefs[Keys.SELECTED_PALETTE_IDS] = encodeStringSet(updatedSelected)
                prefs[Keys.CURRENT_COLORS_JSON] = encodeLongList(palette.colors)
            }
        }
        Unit
    }

    suspend fun deletePaletteFromList(paletteId: String) = withContext(Dispatchers.IO) {
        val snapshot = getSnapshot()
        if (snapshot.userPalettes.size <= 1) return@withContext
        val updatedPalettes = snapshot.userPalettes.filterNot { it.id == paletteId }
        val remainingIds = (snapshot.config.selectedPaletteIds - paletteId)
            .ifEmpty { setOf(updatedPalettes.first().id) }
        context.applicationContext.rimaluxDataStore.edit { prefs ->
            val deleted = decodeStringSet(prefs[Keys.DELETED_PALETTE_IDS]) + paletteId
            prefs[Keys.DELETED_PALETTE_IDS] = encodeStringSet(deleted)
            prefs[Keys.USER_PALETTES_JSON] = encodePalettes(updatedPalettes)
            prefs[Keys.SELECTED_PALETTE_IDS] = encodeStringSet(remainingIds)
        }
        Unit
    }

    private fun encodePatternSet(patterns: Set<ArtPattern>): String {
        val arr = JSONArray()
        patterns.forEach { arr.put(it.id) }
        return arr.toString()
    }

    private fun decodePatternSet(raw: String?): Set<ArtPattern> {
        if (raw.isNullOrBlank()) return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            val set = LinkedHashSet<ArtPattern>()
            for (i in 0 until arr.length()) {
                ArtPattern.fromId(arr.getString(i))?.let { set.add(it) }
            }
            set
        }.getOrDefault(emptySet())
    }

    private fun encodeStringSet(values: Set<String>): String {
        val arr = JSONArray()
        values.forEach { arr.put(it) }
        return arr.toString()
    }

    private fun decodeStringSet(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            val set = LinkedHashSet<String>()
            for (i in 0 until arr.length()) {
                set.add(arr.getString(i))
            }
            set
        }.getOrDefault(emptySet())
    }

    private fun encodeLongList(values: List<Long>): String {
        val arr = JSONArray()
        values.forEach { arr.put(it) }
        return arr.toString()
    }

    private fun decodeLongList(raw: String?): List<Long> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.getLong(it) }
        }.getOrDefault(emptyList())
    }

    private fun encodePalettes(palettes: List<ColorPalette>): String {
        val arr = JSONArray()
        for (p in palettes) {
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            val cArr = JSONArray()
            p.colors.forEach { cArr.put(it) }
            obj.put("colors", cArr)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodePalettes(raw: String?): List<ColorPalette>? {
        if (raw == null) return null
        return runCatching {
            val arr = JSONArray(raw)
            val result = ArrayList<ColorPalette>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val cArr = obj.getJSONArray("colors")
                val colors = List(cArr.length()) { cArr.getLong(it) }
                if (colors.isNotEmpty()) {
                    val id = obj.getString("id")
                    val rawName = obj.getString("name")
                    val starterMatch = StarterPalettes.defaults.find { it.id == id }
                    val resolvedName = if (starterMatch != null && rawName.equals(starterMatch.name, ignoreCase = true)) {
                        starterMatch.name
                    } else {
                        rawName
                    }
                    result.add(
                        ColorPalette(
                            id = id,
                            name = resolvedName,
                            colors = colors
                        )
                    )
                }
            }
            result.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun encodeHistory(items: List<GeneratedHistoryItem>): String {
        val arr = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("seed", item.seed)
            obj.put("pattern", item.pattern.id)
            obj.put("weightMode", item.patternWeightMode.id)
            obj.put("fillMode", item.colorFillMode.id)
            obj.put("colors", encodeLongList(item.paletteColors))
            obj.put("blackRatio", item.pitchBlackRatio.toDouble())
            obj.put("intricacy", item.intricacy.toDouble())
            obj.put("ts", item.timestampMillis)
            obj.put("scheduled", item.wasAutoScheduled)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodeHistory(raw: String?): List<GeneratedHistoryItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val result = ArrayList<GeneratedHistoryItem>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val pattern = ArtPattern.fromId(obj.optString("pattern")) ?: ArtPattern.SILK_RIBBONS
                val weightMode = PatternWeightMode.fromId(obj.optString("weightMode", PatternWeightMode.NORMAL.id))
                val fillMode = ColorFillMode.fromId(obj.optString("fillMode", ColorFillMode.GRADIENT.id))
                val colors = decodeLongList(obj.optString("colors"))
                if (colors.isNotEmpty()) {
                    result.add(
                        GeneratedHistoryItem(
                            id = obj.getString("id"),
                            seed = obj.getLong("seed"),
                            pattern = pattern,
                            colorFillMode = fillMode,
                            paletteColors = colors,
                            pitchBlackRatio = obj.getDouble("blackRatio").toFloat(),
                            intricacy = obj.optDouble("intricacy", 0.50).toFloat(),
                            timestampMillis = obj.getLong("ts"),
                            wasAutoScheduled = obj.optBoolean("scheduled", false),
                            patternWeightMode = weightMode
                        )
                    )
                }
            }
            result
        }.getOrDefault(emptyList())
    }
}
