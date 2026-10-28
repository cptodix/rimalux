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
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ArtStyle
import com.example.model.ColorFillMode
import com.example.model.ColorPalette
import com.example.model.GeneratedHistoryItem
import com.example.model.GeneratorConfig
import com.example.model.PaletteMode
import com.example.model.StarterPalettes
import com.example.model.WallpaperTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.vectraDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "vectra_wallpaper_v3_prefs"
)

data class StoredWallpaperState(
    val config: GeneratorConfig = GeneratorConfig(),
    val currentSeed: Long = 0L,
    val currentStyle: ArtStyle? = null,
    val currentColors: List<Long> = emptyList(),
    val lastAutoExecMillis: Long = 0L,
    val userPalettes: List<ColorPalette> = StarterPalettes.defaults,
    val history: List<GeneratedHistoryItem> = emptyList()
)

class WallpaperPreferencesRepository(private val context: Context) {

    private object Keys {
        val STYLES_RANDOMIZED = booleanPreferencesKey("styles_randomized")
        val SELECTED_STYLES = stringPreferencesKey("selected_styles")
        val COLOR_FILL_MODE = stringPreferencesKey("color_fill_mode")
        val PALETTE_MODE = stringPreferencesKey("palette_mode")
        val SELECTED_PALETTE_IDS = stringPreferencesKey("selected_palette_ids")
        val PITCH_BLACK_RATIO = floatPreferencesKey("pitch_black_ratio")
        val INTRICACY = floatPreferencesKey("intricacy")
        val AUTO_APPLY_ON_GENERATE = booleanPreferencesKey("auto_apply_on_generate")
        val MANUAL_TARGET = stringPreferencesKey("manual_target")
        val AUTO_ROTATE_ENABLED = booleanPreferencesKey("auto_rotate_enabled")
        val INTERVAL_MINUTES = intPreferencesKey("interval_minutes")
        val AUTO_TARGET = stringPreferencesKey("auto_target")
        val NEXT_RUN_MILLIS = longPreferencesKey("next_run_millis")
        val LAST_AUTO_EXEC_MILLIS = longPreferencesKey("last_auto_exec_millis")
        val CURRENT_SEED = longPreferencesKey("current_seed")
        val CURRENT_STYLE = stringPreferencesKey("current_style")
        val CURRENT_COLORS_JSON = stringPreferencesKey("current_colors_json")
        val USER_PALETTES_JSON = stringPreferencesKey("user_palettes_json")
        val HISTORY_JSON = stringPreferencesKey("history_json")
    }

    val stateFlow: Flow<StoredWallpaperState> = context.applicationContext.vectraDataStore.data.map { prefs ->
        val palettes = decodePalettes(prefs[Keys.USER_PALETTES_JSON]) ?: StarterPalettes.defaults
        val parsedStyles = decodeStyleSet(prefs[Keys.SELECTED_STYLES])
        val parsedPaletteIds = decodeStringSet(prefs[Keys.SELECTED_PALETTE_IDS])
            .filter { id -> palettes.any { it.id == id } }
            .toSet()
            .ifEmpty { setOf(palettes.firstOrNull()?.id ?: "obsidian_aurora") }

        val config = GeneratorConfig(
            stylesRandomized = prefs[Keys.STYLES_RANDOMIZED] ?: true,
            selectedStyles = parsedStyles.ifEmpty {
                setOf(
                    ArtStyle.SILK_RIBBONS,
                    ArtStyle.CRYSTAL_VORONOI,
                    ArtStyle.SACRED_MANDALA,
                    ArtStyle.PENROSE_QUASICRYSTAL
                )
            },
            colorFillMode = prefs[Keys.COLOR_FILL_MODE]?.let { ColorFillMode.fromId(it) } ?: ColorFillMode.GRADIENT,
            paletteMode = prefs[Keys.PALETTE_MODE]?.let { PaletteMode.fromId(it) } ?: PaletteMode.RANDOMIZED,
            selectedPaletteIds = parsedPaletteIds,
            pitchBlackRatio = (prefs[Keys.PITCH_BLACK_RATIO] ?: 0.65f).coerceIn(0f, 0.95f),
            intricacy = (prefs[Keys.INTRICACY] ?: 0.60f).coerceIn(0.15f, 1.0f),
            autoApplyOnGenerate = prefs[Keys.AUTO_APPLY_ON_GENERATE] ?: true,
            manualTarget = prefs[Keys.MANUAL_TARGET]?.let { WallpaperTarget.fromId(it) } ?: WallpaperTarget.BOTH,
            autoRotateEnabled = prefs[Keys.AUTO_ROTATE_ENABLED] ?: false,
            intervalMinutes = (prefs[Keys.INTERVAL_MINUTES] ?: 60).coerceIn(1, 10080),
            autoTarget = prefs[Keys.AUTO_TARGET]?.let { WallpaperTarget.fromId(it) } ?: WallpaperTarget.BOTH,
            nextRunEpochMillis = prefs[Keys.NEXT_RUN_MILLIS] ?: 0L
        )

        StoredWallpaperState(
            config = config,
            currentSeed = prefs[Keys.CURRENT_SEED] ?: 0L,
            currentStyle = prefs[Keys.CURRENT_STYLE]?.let { ArtStyle.fromId(it) },
            currentColors = decodeLongList(prefs[Keys.CURRENT_COLORS_JSON]),
            lastAutoExecMillis = prefs[Keys.LAST_AUTO_EXEC_MILLIS] ?: 0L,
            userPalettes = palettes,
            history = decodeHistory(prefs[Keys.HISTORY_JSON])
        )
    }

    suspend fun getSnapshot(): StoredWallpaperState = stateFlow.first()

    private fun parseConfigFromPrefs(prefs: Preferences, palettes: List<ColorPalette>): GeneratorConfig {
        val parsedStyles = decodeStyleSet(prefs[Keys.SELECTED_STYLES])
        val parsedPaletteIds = decodeStringSet(prefs[Keys.SELECTED_PALETTE_IDS])
            .filter { id -> palettes.any { it.id == id } }
            .toSet()
            .ifEmpty { setOf(palettes.firstOrNull()?.id ?: "obsidian_aurora") }

        return GeneratorConfig(
            stylesRandomized = prefs[Keys.STYLES_RANDOMIZED] ?: true,
            selectedStyles = parsedStyles.ifEmpty {
                setOf(
                    ArtStyle.SILK_RIBBONS,
                    ArtStyle.CRYSTAL_VORONOI,
                    ArtStyle.SACRED_MANDALA,
                    ArtStyle.PENROSE_QUASICRYSTAL
                )
            },
            colorFillMode = prefs[Keys.COLOR_FILL_MODE]?.let { ColorFillMode.fromId(it) } ?: ColorFillMode.GRADIENT,
            paletteMode = prefs[Keys.PALETTE_MODE]?.let { PaletteMode.fromId(it) } ?: PaletteMode.RANDOMIZED,
            selectedPaletteIds = parsedPaletteIds,
            pitchBlackRatio = (prefs[Keys.PITCH_BLACK_RATIO] ?: 0.65f).coerceIn(0f, 0.95f),
            intricacy = (prefs[Keys.INTRICACY] ?: 0.60f).coerceIn(0.15f, 1.0f),
            autoApplyOnGenerate = prefs[Keys.AUTO_APPLY_ON_GENERATE] ?: true,
            manualTarget = prefs[Keys.MANUAL_TARGET]?.let { WallpaperTarget.fromId(it) } ?: WallpaperTarget.BOTH,
            autoRotateEnabled = prefs[Keys.AUTO_ROTATE_ENABLED] ?: false,
            intervalMinutes = (prefs[Keys.INTERVAL_MINUTES] ?: 60).coerceIn(1, 10080),
            autoTarget = prefs[Keys.AUTO_TARGET]?.let { WallpaperTarget.fromId(it) } ?: WallpaperTarget.BOTH,
            nextRunEpochMillis = prefs[Keys.NEXT_RUN_MILLIS] ?: 0L
        )
    }

    suspend fun updateConfig(transform: (GeneratorConfig) -> GeneratorConfig) {
        context.applicationContext.vectraDataStore.edit { prefs ->
            val palettes = decodePalettes(prefs[Keys.USER_PALETTES_JSON]) ?: StarterPalettes.defaults
            val currentConfig = parseConfigFromPrefs(prefs, palettes)
            val updated = transform(currentConfig)
            prefs[Keys.STYLES_RANDOMIZED] = updated.stylesRandomized
            prefs[Keys.SELECTED_STYLES] = encodeStyleSet(updated.selectedStyles)
            prefs[Keys.COLOR_FILL_MODE] = updated.colorFillMode.id
            prefs[Keys.PALETTE_MODE] = updated.paletteMode.id
            prefs[Keys.SELECTED_PALETTE_IDS] = encodeStringSet(updated.selectedPaletteIds)
            prefs[Keys.PITCH_BLACK_RATIO] = updated.pitchBlackRatio.coerceIn(0f, 0.95f)
            prefs[Keys.INTRICACY] = updated.intricacy.coerceIn(0.15f, 1.0f)
            prefs[Keys.AUTO_APPLY_ON_GENERATE] = updated.autoApplyOnGenerate
            prefs[Keys.MANUAL_TARGET] = updated.manualTarget.id
            prefs[Keys.AUTO_ROTATE_ENABLED] = updated.autoRotateEnabled
            prefs[Keys.INTERVAL_MINUTES] = updated.intervalMinutes.coerceIn(1, 10080)
            prefs[Keys.AUTO_TARGET] = updated.autoTarget.id
            prefs[Keys.NEXT_RUN_MILLIS] = updated.nextRunEpochMillis
        }
    }

    suspend fun setCurrentColors(colors: List<Long>) {
        context.applicationContext.vectraDataStore.edit { prefs ->
            prefs[Keys.CURRENT_COLORS_JSON] = encodeLongList(colors)
        }
    }

    suspend fun setCurrentStyle(style: ArtStyle) {
        context.applicationContext.vectraDataStore.edit { prefs ->
            prefs[Keys.CURRENT_STYLE] = style.id
        }
    }

    suspend fun recordGeneration(item: GeneratedHistoryItem, isAutoTick: Boolean = false) {
        context.applicationContext.vectraDataStore.edit { prefs ->
            prefs[Keys.CURRENT_SEED] = item.seed
            prefs[Keys.CURRENT_STYLE] = item.style.id
            prefs[Keys.CURRENT_COLORS_JSON] = encodeLongList(item.paletteColors)
            if (isAutoTick) {
                prefs[Keys.LAST_AUTO_EXEC_MILLIS] = item.timestampMillis
            }
            val existing = decodeHistory(prefs[Keys.HISTORY_JSON])
            val updated = (listOf(item) + existing.filterNot { it.seed == item.seed }).take(14)
            prefs[Keys.HISTORY_JSON] = encodeHistory(updated)
        }
    }

    suspend fun addPaletteToList(palette: ColorPalette, selectAndSwitchToListMode: Boolean = true) {
        val snapshot = getSnapshot()
        val updatedPalettes = listOf(palette) + snapshot.userPalettes.filterNot { it.id == palette.id }
        context.applicationContext.vectraDataStore.edit { prefs ->
            prefs[Keys.USER_PALETTES_JSON] = encodePalettes(updatedPalettes)
            if (selectAndSwitchToListMode) {
                prefs[Keys.PALETTE_MODE] = PaletteMode.PALETTE_LIST.id
                val updatedSelected = snapshot.config.selectedPaletteIds + palette.id
                prefs[Keys.SELECTED_PALETTE_IDS] = encodeStringSet(updatedSelected)
                prefs[Keys.CURRENT_COLORS_JSON] = encodeLongList(palette.colors)
            }
        }
    }

    suspend fun deletePaletteFromList(paletteId: String) {
        val snapshot = getSnapshot()
        if (snapshot.userPalettes.size <= 1) return
        val updatedPalettes = snapshot.userPalettes.filterNot { it.id == paletteId }
        val remainingIds = (snapshot.config.selectedPaletteIds - paletteId)
            .ifEmpty { setOf(updatedPalettes.first().id) }
        context.applicationContext.vectraDataStore.edit { prefs ->
            prefs[Keys.USER_PALETTES_JSON] = encodePalettes(updatedPalettes)
            prefs[Keys.SELECTED_PALETTE_IDS] = encodeStringSet(remainingIds)
        }
    }

    private fun encodeStyleSet(styles: Set<ArtStyle>): String {
        val arr = JSONArray()
        styles.forEach { arr.put(it.id) }
        return arr.toString()
    }

    private fun decodeStyleSet(raw: String?): Set<ArtStyle> {
        if (raw.isNullOrBlank()) return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            val set = LinkedHashSet<ArtStyle>()
            for (i in 0 until arr.length()) {
                ArtStyle.fromId(arr.getString(i))?.let { set.add(it) }
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
                    result.add(
                        ColorPalette(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
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
            obj.put("style", item.style.id)
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
                val style = ArtStyle.fromId(obj.optString("style")) ?: ArtStyle.SILK_RIBBONS
                val fillMode = ColorFillMode.fromId(obj.optString("fillMode", ColorFillMode.GRADIENT.id))
                val colors = decodeLongList(obj.optString("colors"))
                if (colors.isNotEmpty()) {
                    result.add(
                        GeneratedHistoryItem(
                            id = obj.getString("id"),
                            seed = obj.getLong("seed"),
                            style = style,
                            colorFillMode = fillMode,
                            paletteColors = colors,
                            pitchBlackRatio = obj.getDouble("blackRatio").toFloat(),
                            intricacy = obj.optDouble("intricacy", 0.60).toFloat(),
                            timestampMillis = obj.getLong("ts"),
                            wasAutoScheduled = obj.optBoolean("scheduled", false)
                        )
                    )
                }
            }
            result
        }.getOrDefault(emptyList())
    }
}
