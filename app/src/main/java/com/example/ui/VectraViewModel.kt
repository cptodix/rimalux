package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.WallpaperPreferencesRepository
import com.example.engine.MathPatternGenerator
import com.example.engine.SceneRatioStats
import com.example.engine.VectorScene
import com.example.engine.WallpaperApplyResult
import com.example.engine.WallpaperRenderer
import com.example.model.ArtStyle
import com.example.model.ColorFillMode
import com.example.model.ColorPalette
import com.example.model.GeneratedHistoryItem
import com.example.model.GeneratorConfig
import com.example.model.IntervalFormatter
import com.example.model.MockupOverlayMode
import com.example.model.PaletteMode
import com.example.model.RandomPaletteGenerator
import com.example.model.StarterPalettes
import com.example.model.WallpaperTarget
import com.example.scheduler.WallpaperScheduler
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class VectraTab {
    HOME,
    STYLES,
    PALETTES,
    AUTO
}

data class VectraUiState(
    val selectedTab: VectraTab = VectraTab.HOME,
    val config: GeneratorConfig = GeneratorConfig(),
    val userPalettes: List<ColorPalette> = StarterPalettes.defaults,
    val currentSeed: Long = 0x5F3759DFL,
    val activeColors: List<Long> = StarterPalettes.defaults.first().colors,
    val currentScene: VectorScene = MathPatternGenerator.generate(
        seed = 0x5F3759DFL,
        style = ArtStyle.SILK_RIBBONS,
        intricacy = 0.60f,
        aspectRatio = 2.15f
    ),
    val ratioStats: SceneRatioStats = SceneRatioStats(65, 35),
    val mockupMode: MockupOverlayMode = MockupOverlayMode.NONE,
    val isFullScreenPreview: Boolean = false,
    val isApplyingWallpaper: Boolean = false,
    val statusMessage: String? = null,
    val countdownSeconds: Long = 0L,
    val history: List<GeneratedHistoryItem> = emptyList(),
    val isCreatePaletteSheetOpen: Boolean = false,
    val isCustomIntervalDialogOpen: Boolean = false
)

class VectraViewModel(
    private val appContext: Context,
    private val repository: WallpaperPreferencesRepository = WallpaperPreferencesRepository(appContext)
) : ViewModel() {

    private val screenAspect: Float by lazy {
        val (w, h) = WallpaperRenderer.getTargetWallpaperDimensions(appContext)
        (h.toFloat() / w.toFloat()).coerceIn(0.55f, 2.8f)
    }

    private val _uiState = MutableStateFlow(VectraUiState())
    val uiState: StateFlow<VectraUiState> = _uiState.asStateFlow()

    private var hasInitializedFirstSeed = false
    private var isExecutingScheduledTick = false

    init {
        observeRepository()
        startCountdownTicker()
    }

    private fun observeRepository() {
        viewModelScope.launch {
            repository.stateFlow.collect { stored ->
                if (!hasInitializedFirstSeed && stored.currentSeed == 0L) {
                    hasInitializedFirstSeed = true
                    val initialSeed = (Random(System.nanoTime()).nextLong() and 0x7FFFFFFFFFFFL)
                        .takeIf { it != 0L } ?: 0x7A4E92B1L
                    synthesizeNewScene(
                        seed = initialSeed,
                        config = stored.config,
                        userPalettes = stored.userPalettes,
                        applyTarget = if (stored.config.autoApplyOnGenerate) stored.config.manualTarget else null
                    )
                    return@collect
                }
                hasInitializedFirstSeed = true

                val prev = _uiState.value
                val seedToUse = if (stored.currentSeed != 0L) stored.currentSeed else prev.currentSeed
                val styleToUse = stored.currentStyle?.takeIf { style ->
                    stored.config.stylesRandomized || style in stored.config.selectedStyles
                } ?: MathPatternGenerator.resolveStyleForSeed(
                    seed = seedToUse,
                    stylesRandomized = stored.config.stylesRandomized,
                    selectedStyles = stored.config.selectedStyles
                )

                val colorsToUse = if (stored.currentColors.isNotEmpty()) {
                    stored.currentColors
                } else {
                    resolveColorsForConfig(seedToUse, stored.config, stored.userPalettes)
                }

                val needsRebuild = prev.currentSeed != seedToUse ||
                    prev.currentScene.style != styleToUse ||
                    prev.config.intricacy != stored.config.intricacy

                val scene = if (needsRebuild) {
                    MathPatternGenerator.generate(
                        seed = seedToUse,
                        style = styleToUse,
                        intricacy = stored.config.intricacy,
                        aspectRatio = screenAspect
                    )
                } else {
                    prev.currentScene
                }

                val remainingSec = if (stored.config.autoRotateEnabled && stored.config.nextRunEpochMillis > 0L) {
                    ((stored.config.nextRunEpochMillis - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
                } else {
                    0L
                }

                _uiState.update { state ->
                    state.copy(
                        config = stored.config,
                        userPalettes = stored.userPalettes,
                        currentSeed = seedToUse,
                        activeColors = colorsToUse,
                        currentScene = scene,
                        ratioStats = scene.computeStats(stored.config.pitchBlackRatio),
                        history = stored.history,
                        countdownSeconds = remainingSec
                    )
                }
            }
        }
    }

    private fun startCountdownTicker() {
        viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                val current = _uiState.value
                if (current.config.autoRotateEnabled && current.config.nextRunEpochMillis > 0L) {
                    val remaining = (current.config.nextRunEpochMillis - System.currentTimeMillis()) / 1000L
                    if (remaining <= 0L && !isExecutingScheduledTick) {
                        isExecutingScheduledTick = true
                        try {
                            val result = WallpaperScheduler.executeAutoRotationCycle(
                                context = appContext,
                                wasScheduled = true,
                                force = false
                            )
                            if (result is WallpaperApplyResult.Success) {
                                postStatus(
                                    appContext.getString(
                                        R.string.msg_wallpaper_applied,
                                        appContext.getString(current.config.autoTarget.labelRes),
                                        result.measuredBlackPercent
                                    )
                                )
                            }
                        } finally {
                            isExecutingScheduledTick = false
                        }
                    } else {
                        _uiState.update { it.copy(countdownSeconds = remaining.coerceAtLeast(0L)) }
                    }
                }
            }
        }
    }

    private fun resolveColorsForConfig(
        seed: Long,
        config: GeneratorConfig,
        userPalettes: List<ColorPalette>
    ): List<Long> {
        return when (config.paletteMode) {
            PaletteMode.RANDOMIZED -> RandomPaletteGenerator.generate(seed)
            PaletteMode.PALETTE_LIST -> {
                val active = userPalettes.filter { it.id in config.selectedPaletteIds }
                val rng = Random(seed xor 0x6A09E667F3BCC908L)
                val chosen = if (active.isNotEmpty()) {
                    active[rng.nextInt(active.size)]
                } else {
                    userPalettes.firstOrNull() ?: StarterPalettes.defaults.first()
                }
                chosen.colors
            }
        }
    }

    fun selectTab(tab: VectraTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun cycleMockupOverlay() {
        _uiState.update { state ->
            val next = when (state.mockupMode) {
                MockupOverlayMode.NONE -> MockupOverlayMode.LOCK_SCREEN
                MockupOverlayMode.LOCK_SCREEN -> MockupOverlayMode.HOME_GRID
                MockupOverlayMode.HOME_GRID -> MockupOverlayMode.NONE
            }
            state.copy(mockupMode = next)
        }
    }

    fun setFullScreenPreview(enabled: Boolean) {
        _uiState.update { it.copy(isFullScreenPreview = enabled) }
    }

    fun dismissStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun generateNewWallpaper() {
        val snapshot = _uiState.value
        val newSeed = (Random(System.nanoTime() xor snapshot.currentSeed).nextLong() and 0x7FFFFFFFFFFFL)
            .takeIf { it != 0L && it != snapshot.currentSeed } ?: (snapshot.currentSeed + 9973L)
        viewModelScope.launch {
            synthesizeNewScene(
                seed = newSeed,
                config = snapshot.config,
                userPalettes = snapshot.userPalettes,
                applyTarget = if (snapshot.config.autoApplyOnGenerate) snapshot.config.manualTarget else null
            )
        }
    }

    private suspend fun synthesizeNewScene(
        seed: Long,
        config: GeneratorConfig,
        userPalettes: List<ColorPalette>,
        applyTarget: WallpaperTarget?
    ) {
        val style = MathPatternGenerator.resolveStyleForSeed(
            seed = seed,
            stylesRandomized = config.stylesRandomized,
            selectedStyles = config.selectedStyles
        )
        val colors = resolveColorsForConfig(seed, config, userPalettes)
        val scene = MathPatternGenerator.generate(
            seed = seed,
            style = style,
            intricacy = config.intricacy,
            aspectRatio = screenAspect
        )
        val stats = scene.computeStats(config.pitchBlackRatio)

        _uiState.update {
            it.copy(
                currentSeed = seed,
                activeColors = colors,
                currentScene = scene,
                ratioStats = stats,
                isApplyingWallpaper = applyTarget != null
            )
        }

        val historyItem = GeneratedHistoryItem(
            id = "gen_${System.currentTimeMillis()}_${seed.toString(16)}",
            seed = seed,
            style = style,
            colorFillMode = config.colorFillMode,
            paletteColors = colors,
            pitchBlackRatio = config.pitchBlackRatio,
            intricacy = config.intricacy,
            timestampMillis = System.currentTimeMillis(),
            wasAutoScheduled = false
        )
        repository.recordGeneration(historyItem, isAutoTick = false)

        if (applyTarget != null) {
            val result = WallpaperRenderer.applyWallpaperToSystem(
                context = appContext,
                scene = scene,
                paletteColors = colors,
                pitchBlackRatio = config.pitchBlackRatio,
                colorFillMode = config.colorFillMode,
                target = applyTarget
            )
            _uiState.update { it.copy(isApplyingWallpaper = false) }
            handleApplyResult(result, applyTarget)
        }
    }

    fun applyCurrentWallpaperNow() {
        val current = _uiState.value
        if (current.isApplyingWallpaper) return
        val target = current.config.manualTarget
        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingWallpaper = true) }
            val result = WallpaperRenderer.applyWallpaperToSystem(
                context = appContext,
                scene = current.currentScene,
                paletteColors = current.activeColors,
                pitchBlackRatio = current.config.pitchBlackRatio,
                colorFillMode = current.config.colorFillMode,
                target = target
            )
            _uiState.update { it.copy(isApplyingWallpaper = false) }
            handleApplyResult(result, target)
        }
    }

    fun updatePitchBlackRatio(ratio: Float) {
        val clamped = ratio.coerceIn(0f, 0.95f)
        _uiState.update { state ->
            state.copy(
                config = state.config.copy(pitchBlackRatio = clamped),
                ratioStats = state.currentScene.computeStats(clamped)
            )
        }
        viewModelScope.launch {
            repository.updateConfig { it.copy(pitchBlackRatio = clamped) }
        }
    }

    fun updateManualTarget(target: WallpaperTarget) {
        _uiState.update { it.copy(config = it.config.copy(manualTarget = target)) }
        viewModelScope.launch {
            repository.updateConfig { it.copy(manualTarget = target) }
        }
    }

    fun updateAutoTarget(target: WallpaperTarget) {
        _uiState.update { it.copy(config = it.config.copy(autoTarget = target)) }
        viewModelScope.launch {
            repository.updateConfig { it.copy(autoTarget = target) }
        }
    }

    fun updateAutoApplyOnGenerate(enabled: Boolean) {
        _uiState.update { it.copy(config = it.config.copy(autoApplyOnGenerate = enabled)) }
        viewModelScope.launch {
            repository.updateConfig { it.copy(autoApplyOnGenerate = enabled) }
        }
    }

    // --- Math Art Style Controls ---
    fun setStylesRandomized(randomized: Boolean) {
        val current = _uiState.value
        val styleToRender = MathPatternGenerator.resolveStyleForSeed(
            seed = current.currentSeed,
            stylesRandomized = randomized,
            selectedStyles = current.config.selectedStyles
        )
        val rebuilt = MathPatternGenerator.generate(
            seed = current.currentSeed,
            style = styleToRender,
            intricacy = current.config.intricacy,
            aspectRatio = screenAspect
        )
        _uiState.update { state ->
            state.copy(
                config = state.config.copy(stylesRandomized = randomized),
                currentScene = rebuilt,
                ratioStats = rebuilt.computeStats(state.config.pitchBlackRatio)
            )
        }
        viewModelScope.launch {
            repository.setCurrentStyle(styleToRender)
            repository.updateConfig { it.copy(stylesRandomized = randomized) }
        }
    }

    fun toggleStyleSelection(style: ArtStyle) {
        val current = _uiState.value
        val updatedSet = if (current.config.stylesRandomized) {
            setOf(style)
        } else {
            val existing = current.config.selectedStyles
            if (style in existing && existing.size > 1) {
                existing - style
            } else {
                existing + style
            }
        }

        val previewStyle = if (style in updatedSet) style else updatedSet.first()
        val rebuilt = MathPatternGenerator.generate(
            seed = current.currentSeed,
            style = previewStyle,
            intricacy = current.config.intricacy,
            aspectRatio = screenAspect
        )
        _uiState.update { state ->
            state.copy(
                config = state.config.copy(
                    stylesRandomized = false,
                    selectedStyles = updatedSet
                ),
                currentScene = rebuilt,
                ratioStats = rebuilt.computeStats(state.config.pitchBlackRatio)
            )
        }
        viewModelScope.launch {
            repository.setCurrentStyle(previewStyle)
            repository.updateConfig {
                it.copy(stylesRandomized = false, selectedStyles = updatedSet)
            }
        }
    }

    fun updateIntricacy(intricacy: Float) {
        val clamped = intricacy.coerceIn(0.15f, 1.0f)
        val current = _uiState.value
        val rebuilt = MathPatternGenerator.generate(
            seed = current.currentSeed,
            style = current.currentScene.style,
            intricacy = clamped,
            aspectRatio = screenAspect
        )
        _uiState.update { state ->
            state.copy(
                config = state.config.copy(intricacy = clamped),
                currentScene = rebuilt,
                ratioStats = rebuilt.computeStats(state.config.pitchBlackRatio)
            )
        }
        viewModelScope.launch {
            repository.updateConfig { it.copy(intricacy = clamped) }
        }
    }

    // --- Solid vs Gradient & Palette Controls ---
    fun setColorFillMode(fillMode: ColorFillMode) {
        _uiState.update { state ->
            state.copy(config = state.config.copy(colorFillMode = fillMode))
        }
        viewModelScope.launch {
            repository.updateConfig { it.copy(colorFillMode = fillMode) }
        }
    }

    fun setPaletteMode(mode: PaletteMode) {
        val current = _uiState.value
        val nextColors = when (mode) {
            PaletteMode.RANDOMIZED -> RandomPaletteGenerator.generate(System.nanoTime())
            PaletteMode.PALETTE_LIST -> {
                val active = current.userPalettes.firstOrNull { it.id in current.config.selectedPaletteIds }
                    ?: current.userPalettes.firstOrNull()
                    ?: StarterPalettes.defaults.first()
                active.colors
            }
        }
        _uiState.update { state ->
            state.copy(
                config = state.config.copy(paletteMode = mode),
                activeColors = nextColors
            )
        }
        viewModelScope.launch {
            repository.updateConfig { it.copy(paletteMode = mode) }
            repository.setCurrentColors(nextColors)
        }
    }

    fun shuffleRandomColors() {
        val freshColors = RandomPaletteGenerator.generate(System.nanoTime() xor _uiState.value.currentSeed)
        _uiState.update {
            it.copy(
                config = it.config.copy(paletteMode = PaletteMode.RANDOMIZED),
                activeColors = freshColors
            )
        }
        viewModelScope.launch {
            repository.updateConfig { it.copy(paletteMode = PaletteMode.RANDOMIZED) }
            repository.setCurrentColors(freshColors)
        }
    }

    fun saveCurrentRandomColorsToList() {
        val colors = _uiState.value.activeColors
        val nextIndex = _uiState.value.userPalettes.size + 1
        val name = "Harmony #$nextIndex"
        val palette = ColorPalette(
            id = "pal_${System.currentTimeMillis()}",
            name = name,
            colors = colors
        )
        viewModelScope.launch {
            repository.addPaletteToList(palette, selectAndSwitchToListMode = false)
            postStatus(appContext.getString(R.string.msg_palette_saved, name))
        }
    }

    fun togglePaletteInList(palette: ColorPalette) {
        val current = _uiState.value
        val existingIds = current.config.selectedPaletteIds
        val updatedIds = if (palette.id in existingIds && existingIds.size > 1) {
            existingIds - palette.id
        } else {
            existingIds + palette.id
        }
        val previewColors = if (palette.id in updatedIds) {
            palette.colors
        } else {
            current.userPalettes.firstOrNull { it.id in updatedIds }?.colors ?: palette.colors
        }

        _uiState.update { state ->
            state.copy(
                config = state.config.copy(
                    paletteMode = PaletteMode.PALETTE_LIST,
                    selectedPaletteIds = updatedIds
                ),
                activeColors = previewColors
            )
        }
        viewModelScope.launch {
            repository.updateConfig {
                it.copy(paletteMode = PaletteMode.PALETTE_LIST, selectedPaletteIds = updatedIds)
            }
            repository.setCurrentColors(previewColors)
        }
    }

    fun setCreatePaletteSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isCreatePaletteSheetOpen = open) }
    }

    fun addCustomPalette(name: String, colors: List<Long>) {
        val cleanName = name.trim().ifEmpty { "Palette #${_uiState.value.userPalettes.size + 1}" }
        val validColors = colors.ifEmpty { listOf(0xFF00F5D4L, 0xFF7B2CBFL, 0xFFF15BB5L) }
        val palette = ColorPalette(
            id = "custom_${System.currentTimeMillis()}",
            name = cleanName,
            colors = validColors
        )
        _uiState.update { it.copy(isCreatePaletteSheetOpen = false, activeColors = validColors) }
        viewModelScope.launch {
            repository.addPaletteToList(palette, selectAndSwitchToListMode = true)
            postStatus(appContext.getString(R.string.msg_palette_saved, cleanName))
        }
    }

    fun deletePaletteFromList(paletteId: String) {
        viewModelScope.launch {
            repository.deletePaletteFromList(paletteId)
        }
    }

    // --- Persistent Background Interval Controls ---
    fun setAutoRotateEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                val cfg = _uiState.value.config
                val nextRun = WallpaperScheduler.scheduleNext(appContext, cfg.intervalMinutes)
                repository.updateConfig {
                    it.copy(autoRotateEnabled = true, nextRunEpochMillis = nextRun)
                }
                postStatus(
                    appContext.getString(
                        R.string.msg_schedule_updated,
                        IntervalFormatter.formatMinutesShort(cfg.intervalMinutes),
                        appContext.getString(cfg.autoTarget.labelRes)
                    )
                )
            } else {
                WallpaperScheduler.cancelAll(appContext)
                repository.updateConfig {
                    it.copy(autoRotateEnabled = false, nextRunEpochMillis = 0L)
                }
            }
        }
    }

    fun updateIntervalMinutes(minutes: Int) {
        val safeMinutes = minutes.coerceIn(1, 10080)
        viewModelScope.launch {
            val autoTarget = _uiState.value.config.autoTarget
            val nextRun = WallpaperScheduler.scheduleNext(appContext, safeMinutes)
            repository.updateConfig {
                it.copy(
                    intervalMinutes = safeMinutes,
                    autoRotateEnabled = true,
                    nextRunEpochMillis = nextRun
                )
            }
            _uiState.update { it.copy(isCustomIntervalDialogOpen = false) }
            postStatus(
                appContext.getString(
                    R.string.msg_schedule_updated,
                    IntervalFormatter.formatMinutesShort(safeMinutes),
                    appContext.getString(autoTarget.labelRes)
                )
            )
        }
    }

    fun setCustomIntervalDialogOpen(open: Boolean) {
        _uiState.update { it.copy(isCustomIntervalDialogOpen = open) }
    }

    fun triggerAutoCycleNow() {
        if (_uiState.value.isApplyingWallpaper) return
        val autoTarget = _uiState.value.config.autoTarget
        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingWallpaper = true) }
            val result = WallpaperScheduler.executeAutoRotationCycle(
                context = appContext,
                wasScheduled = true,
                force = true
            )
            _uiState.update { it.copy(isApplyingWallpaper = false) }
            handleApplyResult(result, autoTarget)
        }
    }

    fun restoreFromHistory(item: GeneratedHistoryItem) {
        val rebuilt = MathPatternGenerator.generate(
            seed = item.seed,
            style = item.style,
            intricacy = item.intricacy,
            aspectRatio = screenAspect
        )
        _uiState.update { state ->
            state.copy(
                selectedTab = VectraTab.HOME,
                currentSeed = item.seed,
                activeColors = item.paletteColors,
                currentScene = rebuilt,
                config = state.config.copy(
                    colorFillMode = item.colorFillMode,
                    pitchBlackRatio = item.pitchBlackRatio,
                    intricacy = item.intricacy
                ),
                ratioStats = rebuilt.computeStats(item.pitchBlackRatio)
            )
        }
        viewModelScope.launch {
            repository.recordGeneration(item.copy(wasAutoScheduled = false), isAutoTick = false)
            repository.updateConfig {
                it.copy(
                    colorFillMode = item.colorFillMode,
                    pitchBlackRatio = item.pitchBlackRatio,
                    intricacy = item.intricacy
                )
            }
        }
    }

    private fun handleApplyResult(result: WallpaperApplyResult, target: WallpaperTarget) {
        when (result) {
            is WallpaperApplyResult.Success -> {
                postStatus(
                    appContext.getString(
                        R.string.msg_wallpaper_applied,
                        appContext.getString(target.labelRes),
                        result.measuredBlackPercent
                    )
                )
            }
            is WallpaperApplyResult.Error -> {
                postStatus(appContext.getString(R.string.msg_wallpaper_error, result.reason))
            }
        }
    }

    private fun postStatus(message: String) {
        _uiState.update { it.copy(statusMessage = message) }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VectraViewModel(context.applicationContext) as T
        }
    }
}
