package com.cptodix.rimalux.oled.wallpaper.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cptodix.rimalux.oled.wallpaper.R
import com.cptodix.rimalux.oled.wallpaper.data.WallpaperPreferencesRepository
import com.cptodix.rimalux.oled.wallpaper.engine.ColorHarmonyEngine
import com.cptodix.rimalux.oled.wallpaper.engine.MathPatternGenerator
import com.cptodix.rimalux.oled.wallpaper.engine.SceneRatioStats
import com.cptodix.rimalux.oled.wallpaper.engine.VectorScene
import com.cptodix.rimalux.oled.wallpaper.engine.WallpaperGeneratorEngine
import com.cptodix.rimalux.oled.wallpaper.model.AppLanguage
import com.cptodix.rimalux.oled.wallpaper.model.ArtPattern
import com.cptodix.rimalux.oled.wallpaper.model.ColorFillMode
import com.cptodix.rimalux.oled.wallpaper.model.ColorPalette
import com.cptodix.rimalux.oled.wallpaper.model.GeneratedHistoryItem
import com.cptodix.rimalux.oled.wallpaper.model.GeneratorConfig
import com.cptodix.rimalux.oled.wallpaper.model.IntervalFormatter
import com.cptodix.rimalux.oled.wallpaper.model.MockupOverlayMode
import com.cptodix.rimalux.oled.wallpaper.model.PaletteMode
import com.cptodix.rimalux.oled.wallpaper.model.PatternWeightMode
import com.cptodix.rimalux.oled.wallpaper.model.PitchBlackMode
import androidx.annotation.StringRes
import com.cptodix.rimalux.oled.wallpaper.model.RandomPaletteGenerator
import com.cptodix.rimalux.oled.wallpaper.model.StarterPalettes
import com.cptodix.rimalux.oled.wallpaper.model.StudioGestureAction
import com.cptodix.rimalux.oled.wallpaper.model.WallpaperTarget
import com.cptodix.rimalux.oled.wallpaper.model.titleRes
import com.cptodix.rimalux.oled.wallpaper.platform.AppLocaleManager
import com.cptodix.rimalux.oled.wallpaper.platform.WallpaperApplyResult
import com.cptodix.rimalux.oled.wallpaper.platform.WallpaperExporter
import com.cptodix.rimalux.oled.wallpaper.platform.WallpaperRenderer
import com.cptodix.rimalux.oled.wallpaper.platform.WallpaperSaveResult
import com.cptodix.rimalux.oled.wallpaper.platform.WallpaperShareResult
import com.cptodix.rimalux.oled.wallpaper.scheduler.WallpaperScheduler
import java.util.EnumMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RimaluxScreen {
    HOME,
    PATTERNS,
    PALETTES,
    AUTO_CHANGE_HELP
}

@Immutable
data class RenderedPreviewSnapshot(
    val seed: Long,
    val pattern: ArtPattern,
    val colors: List<Long>,
    val pitchBlackRatio: Float,
    val colorFillMode: ColorFillMode,
    val patternWeightMode: PatternWeightMode = PatternWeightMode.NORMAL,
    val intricacy: Float,
    val ratioStats: SceneRatioStats,
    val scene: VectorScene,
    val imageBitmap: ImageBitmap
) {
    fun toHistoryItem(wasAutoScheduled: Boolean = false): GeneratedHistoryItem {
        return GeneratedHistoryItem(
            id = "gen_${System.currentTimeMillis()}_${seed.toString(16)}",
            seed = seed,
            pattern = pattern,
            patternWeightMode = patternWeightMode,
            colorFillMode = colorFillMode,
            paletteColors = colors,
            pitchBlackRatio = pitchBlackRatio,
            intricacy = intricacy,
            timestampMillis = System.currentTimeMillis(),
            wasAutoScheduled = wasAutoScheduled
        )
    }
}

@Immutable
data class StudioMiniHintState(
    val action: StudioGestureAction,
    val variationIndex: Int,
    @param:StringRes val messageRes: Int
)

@Immutable
data class RimaluxUiState(
    val currentScreen: RimaluxScreen = RimaluxScreen.HOME,
    val config: GeneratorConfig = GeneratorConfig(),
    val userPalettes: List<ColorPalette> = StarterPalettes.defaults,
    val preview: RenderedPreviewSnapshot? = null,
    val previousPreview: RenderedPreviewSnapshot? = null,
    val nextPreview: RenderedPreviewSnapshot? = null,
    val canSwipeRight: Boolean = false,
    val aheadReadyCount: Int = 0,
    val rightEdgeBounceCount: Int = 0,
    val stagedColors: List<Long> = StarterPalettes.defaults.first().colors,
    val hasUnappliedChanges: Boolean = false,
    val isGeneratingPreview: Boolean = true,
    val mockupMode: MockupOverlayMode = MockupOverlayMode.NONE,
    val isApplyingWallpaper: Boolean = false,
    val isExportingWallpaper: Boolean = false,
    val pendingShareIntent: Intent? = null,
    val statusMessage: String? = null,
    val history: List<GeneratedHistoryItem> = emptyList(),
    val isSetWallpaperDialogOpen: Boolean = false,
    val isCreatePaletteSheetOpen: Boolean = false,
    val isAutoTargetDialogOpen: Boolean = false,
    val isIntervalDialogOpen: Boolean = false,
    val isCustomIntervalDialogOpen: Boolean = false,
    val isBottomSheetOpen: Boolean = false,
    val isLikeAppDialogOpen: Boolean = false,
    val isSendThanksDialogOpen: Boolean = false,
    val isPatternLayoutDialogOpen: Boolean = false,
    val isColorFillDialogOpen: Boolean = false,
    val isLanguageDialogOpen: Boolean = false,
    val appLanguage: AppLanguage = AppLanguage.DEFAULT,
    val pendingLocaleRefreshToken: Long = 0L,
    val isGestureTutorialVisible: Boolean = false,
    val activeMiniHint: StudioMiniHintState? = null,
    val lastGestureTimestamps: Map<StudioGestureAction, Long> = emptyMap(),
    val previewActivityEpoch: Long = 0L
)

class RimaluxViewModel(
    private val appContext: Context,
    private val repository: WallpaperPreferencesRepository = WallpaperPreferencesRepository(appContext),
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    companion object {
        const val PREFETCH_AHEAD_TARGET = 3
        const val MINI_HINT_IDLE_DELAY_MS = 4_000L
        const val MINI_HINT_ROTATE_INTERVAL_MS = 6_000L
        const val GESTURE_STALE_THRESHOLD_MS = 7L * 24L * 60L * 60L * 1_000L
        private const val MAX_PAST_STACK = 24
        private const val MAX_RENDERED_CACHE = 8
    }

    private val screenAspect: Float by lazy {
        val (w, h) = WallpaperRenderer.getTargetWallpaperDimensions(appContext)
        (h.toFloat() / w.toFloat()).coerceIn(0.55f, 2.8f)
    }

    private val _uiState = MutableStateFlow(
        RimaluxUiState(
            appLanguage = AppLocaleManager.resolveOrInitializeLanguage(appContext)
        )
    )
    val uiState: StateFlow<RimaluxUiState> = _uiState.asStateFlow()

    private val localizedContext: Context
        get() = AppLocaleManager.createLocalizedContext(appContext, _uiState.value.appLanguage)

    // Deck state (mutated on Main dispatcher inside viewModelScope / UI callbacks)
    private val pastStack = ArrayDeque<GeneratedHistoryItem>()
    private val aheadQueue = ArrayDeque<RenderedPreviewSnapshot>()
    private val renderedCache = object : LinkedHashMap<Long, RenderedPreviewSnapshot>(12, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, RenderedPreviewSnapshot>?): Boolean {
            return size > MAX_RENDERED_CACHE
        }
    }

    private var hasInitializedFromStorage = false
    private var hasVerifiedInitialSchedule = false
    private var hasDismissedTutorialThisSession = false
    private var miniHintActionCursor = 0
    private val miniHintVariationCursors = EnumMap<StudioGestureAction, Int>(StudioGestureAction::class.java)
    private val pendingGestureWrites = EnumMap<StudioGestureAction, Long>(StudioGestureAction::class.java)
    private var lastObservedAutoExecMillis = 0L
    private var isExecutingScheduledTick = false
    private var pendingRandomColors: List<Long>? = null
    private var prefetchEpoch = 0L
    private var seedCounter = 1L
    private var isWaitingPrefetchDebounce = false
    private var prefetchJob: Job? = null
    private var previousRenderJob: Job? = null
    private var configSaveJob: Job? = null

    init {
        observeRepository()
        startForegroundScheduleMonitor()
    }

    private fun observeRepository() {
        viewModelScope.launch {
            repository.stateFlow.collect { stored ->
                if (!hasInitializedFromStorage) {
                    hasInitializedFromStorage = true
                    lastObservedAutoExecMillis = stored.lastAutoExecMillis

                    pastStack.clear()
                    if (stored.currentSeed != 0L && stored.history.isNotEmpty()) {
                        stored.history
                            .filterNot { it.seed == stored.currentSeed }
                            .take(MAX_PAST_STACK)
                            .asReversed()
                            .forEach { pastStack.addLast(it) }
                    }

                    val showInitialTutorial = !stored.hasSeenGestureTutorial && !hasDismissedTutorialThisSession
                    val initialTimestamps = StudioGestureAction.entries.associateWith { action ->
                        pendingGestureWrites[action] ?: (stored.lastGestureTimestamps[action] ?: 0L)
                    }
                    val resolvedLanguage = if (stored.appLanguageTag.isNullOrBlank()) {
                        val initial = AppLocaleManager.resolveOrInitializeLanguage(appContext)
                        viewModelScope.launch {
                            repository.setAppLanguageTag(initial.languageTag)
                        }
                        initial
                    } else {
                        val storedLang = AppLanguage.fromTagOrDefault(stored.appLanguageTag)
                        AppLocaleManager.setSelectedLanguage(appContext, storedLang)
                        storedLang
                    }

                    _uiState.update { state ->
                        state.copy(
                            config = stored.config,
                            userPalettes = stored.userPalettes,
                            history = stored.history,
                            canSwipeRight = pastStack.isNotEmpty(),
                            appLanguage = resolvedLanguage,
                            isGestureTutorialVisible = showInitialTutorial,
                            lastGestureTimestamps = initialTimestamps
                        )
                    }

                    // If auto-change is enabled, verify OS AlarmManager & WorkManager schedules exist
                    // and only re-arm if missing (e.g., cleared by an OEM battery saver Force-Stop).
                    try {
                        ensureAutoScheduleInternal(stored.config)
                    } finally {
                        hasVerifiedInitialSchedule = true
                    }

                    if (stored.currentSeed == 0L) {
                        ensureDeckPrefetch(debounceMs = 0L)
                    } else {
                        val patternToUse = stored.currentPattern ?: MathPatternGenerator.resolvePatternForSeed(
                            seed = stored.currentSeed,
                            patternsRandomized = stored.config.patternsRandomized,
                            selectedPatterns = stored.config.selectedPatterns
                        )
                        val colorsToUse = stored.currentColors.ifEmpty {
                            resolveColorsForConfig(stored.currentSeed, stored.config, stored.userPalettes)
                        }
                        val effectiveBlackRatio = when (stored.config.pitchBlackMode) {
                            PitchBlackMode.SINGLE -> stored.currentPitchBlackRatio ?: stored.config.pitchBlackRatio
                            PitchBlackMode.RANGE -> (stored.currentPitchBlackRatio ?: stored.config.pitchBlackRatio)
                                .coerceIn(stored.config.pitchBlackRangeMin, stored.config.pitchBlackRangeMax)
                        }
                        val effectiveIntricacy = stored.currentIntricacy
                            ?: GeneratorConfig.sampleIntricacyForSeed(stored.currentSeed)
                        val effectiveWeightMode = stored.currentPatternWeightMode
                            ?: stored.config.patternWeightMode
                        val rendered = buildRenderedPreview(
                            seed = stored.currentSeed,
                            pattern = patternToUse,
                            colors = colorsToUse,
                            pitchBlackRatio = effectiveBlackRatio,
                            colorFillMode = stored.config.colorFillMode,
                            patternWeightMode = effectiveWeightMode,
                            intricacy = effectiveIntricacy
                        )
                        renderedCache[rendered.seed] = rendered
                        _uiState.update { state ->
                            state.copy(
                                preview = rendered,
                                stagedColors = colorsToUse,
                                canSwipeRight = pastStack.isNotEmpty(),
                                isGeneratingPreview = false,
                                hasUnappliedChanges = false
                            )
                        }
                        ensureDeckPrefetch(debounceMs = 0L)
                    }
                    return@collect
                }

                val autoTickOccurred = stored.lastAutoExecMillis > 0L &&
                    stored.lastAutoExecMillis != lastObservedAutoExecMillis

                if (autoTickOccurred) {
                    lastObservedAutoExecMillis = stored.lastAutoExecMillis
                    val previousActive = _uiState.value.preview
                    if (previousActive != null && pastStack.lastOrNull()?.seed != previousActive.seed) {
                        pushPastItem(previousActive.toHistoryItem())
                    }
                    val seedToUse = stored.currentSeed.takeIf { it != 0L }
                        ?: (_uiState.value.preview?.seed ?: 0x7A4E92B1L)
                    val patternToUse = stored.currentPattern ?: MathPatternGenerator.resolvePatternForSeed(
                        seed = seedToUse,
                        patternsRandomized = stored.config.patternsRandomized,
                        selectedPatterns = stored.config.selectedPatterns
                    )
                    val colorsToUse = stored.currentColors.ifEmpty {
                        resolveColorsForConfig(seedToUse, stored.config, stored.userPalettes)
                    }
                    val effectiveBlackRatio = stored.currentPitchBlackRatio ?: stored.config.pitchBlackRatio
                    val effectiveIntricacy = stored.currentIntricacy
                        ?: GeneratorConfig.sampleIntricacyForSeed(seedToUse)
                    val effectiveWeightMode = stored.currentPatternWeightMode
                        ?: stored.config.patternWeightMode
                    val rendered = buildRenderedPreview(
                        seed = seedToUse,
                        pattern = patternToUse,
                        colors = colorsToUse,
                        pitchBlackRatio = effectiveBlackRatio,
                        colorFillMode = stored.config.colorFillMode,
                        patternWeightMode = effectiveWeightMode,
                        intricacy = effectiveIntricacy
                    )
                    renderedCache[rendered.seed] = rendered
                    val resolvedTimestamps = StudioGestureAction.entries.associateWith { action ->
                        pendingGestureWrites[action] ?: (stored.lastGestureTimestamps[action] ?: 0L)
                    }
                    _uiState.update { state ->
                        state.copy(
                            preview = rendered,
                            previousPreview = pastStack.lastOrNull()?.let { renderedCache[it.seed] },
                            canSwipeRight = pastStack.isNotEmpty(),
                            stagedColors = colorsToUse,
                            userPalettes = stored.userPalettes,
                            history = stored.history,
                            isGeneratingPreview = false,
                            hasUnappliedChanges = false,
                            lastGestureTimestamps = resolvedTimestamps,
                            config = state.config.copy(
                                autoRotateEnabled = stored.config.autoRotateEnabled,
                                intervalMinutes = stored.config.intervalMinutes,
                                nextRunEpochMillis = stored.config.nextRunEpochMillis
                            )
                        )
                    }
                    ensureDeckPrefetchIfIdle()
                } else {
                    val resolvedTimestamps = StudioGestureAction.entries.associateWith { action ->
                        pendingGestureWrites[action] ?: (stored.lastGestureTimestamps[action] ?: 0L)
                    }
                    _uiState.update { state ->
                        val activeAction = state.activeMiniHint?.action
                        val keepActiveHint = if (activeAction != null) {
                            val lastMs = resolvedTimestamps[activeAction] ?: 0L
                            lastMs <= 0L || (clockMillis() - lastMs) > GESTURE_STALE_THRESHOLD_MS
                        } else {
                            false
                        }
                        state.copy(
                            userPalettes = stored.userPalettes,
                            lastGestureTimestamps = resolvedTimestamps,
                            activeMiniHint = if (keepActiveHint) state.activeMiniHint else null,
                            config = state.config.copy(
                                autoRotateEnabled = stored.config.autoRotateEnabled,
                                intervalMinutes = stored.config.intervalMinutes,
                                nextRunEpochMillis = stored.config.nextRunEpochMillis
                            )
                        )
                    }
                }
            }
        }
    }

    private fun pushPastItem(item: GeneratedHistoryItem) {
        pastStack.removeAll { it.seed == item.seed }
        pastStack.addLast(item)
        while (pastStack.size > MAX_PAST_STACK) {
            pastStack.removeFirst()
        }
    }

    private fun buildCurrentHistoryList(active: RenderedPreviewSnapshot?): List<GeneratedHistoryItem> {
        val pastReversed = pastStack.asReversed()
        return if (active != null) {
            (listOf(active.toHistoryItem()) + pastReversed.filterNot { it.seed == active.seed }).take(MAX_PAST_STACK)
        } else {
            pastReversed.take(MAX_PAST_STACK)
        }
    }

    private fun persistActiveDeck(active: RenderedPreviewSnapshot) {
        val currentItem = active.toHistoryItem(wasAutoScheduled = false)
        val pastNewestFirst = pastStack.asReversed().toList()
        viewModelScope.launch {
            repository.saveTimelineState(
                current = currentItem,
                pastNewestFirst = pastNewestFirst
            )
        }
    }

    private fun nextUniqueSeed(): Long {
        val usedSeeds = HashSet<Long>().apply {
            _uiState.value.preview?.seed?.let { add(it) }
            pastStack.forEach { add(it.seed) }
            aheadQueue.forEach { add(it.seed) }
        }
        repeat(16) {
            seedCounter += 1L
            val candidate = (Random(System.nanoTime() xor (seedCounter * 0x9E3779B97F4A7C15UL.toLong()))
                .nextLong() and 0x7FFFFFFFFFFFL)
                .takeIf { it != 0L } ?: (0x7A4E92B1L + seedCounter)
            if (candidate !in usedSeeds) return candidate
        }
        return (System.nanoTime() and 0x7FFFFFFFFFFFL).coerceAtLeast(1L)
    }

    private suspend fun synthesizeForConfig(
        seed: Long,
        config: GeneratorConfig,
        userPalettes: List<ColorPalette>,
        explicitRandomColors: List<Long>?
    ): RenderedPreviewSnapshot = withContext(Dispatchers.Default) {
        val synthesized = WallpaperGeneratorEngine.synthesize(
            seed = seed,
            spec = config.toCoreSpec(),
            availablePalettes = userPalettes,
            aspectRatio = screenAspect,
            explicitColors = if (config.paletteMode == PaletteMode.RANDOMIZED) explicitRandomColors else null
        )
        rasterizeSynthesizedPreview(synthesized)
    }

    private fun invalidateAheadAndPrefetch(debounceMs: Long = 0L) {
        prefetchEpoch += 1L
        aheadQueue.clear()
        _uiState.update {
            it.copy(
                nextPreview = null,
                aheadReadyCount = 0
            )
        }
        ensureDeckPrefetch(debounceMs = debounceMs)
    }

    private fun ensureDeckPrefetchIfIdle(bypassDebounce: Boolean = false) {
        if (prefetchJob?.isActive == true && !(bypassDebounce && isWaitingPrefetchDebounce)) {
            return
        }
        ensureDeckPrefetch(debounceMs = 0L)
    }

    private fun ensureDeckPrefetch(debounceMs: Long = 0L) {
        prefetchJob?.cancel()
        isWaitingPrefetchDebounce = false
        val capturedEpoch = prefetchEpoch
        prefetchJob = viewModelScope.launch {
            if (debounceMs > 0L) {
                isWaitingPrefetchDebounce = true
                try {
                    delay(debounceMs)
                } finally {
                    isWaitingPrefetchDebounce = false
                }
            }
            if (capturedEpoch != prefetchEpoch) return@launch

            // 1. If active preview slot is empty (initial launch or user swiped ahead of buffer), synthesize immediately
            if (_uiState.value.preview == null) {
                val snap = _uiState.value
                val seed = nextUniqueSeed()
                val explicitRandom = pendingRandomColors
                pendingRandomColors = null
                val rendered = synthesizeForConfig(
                    seed = seed,
                    config = snap.config,
                    userPalettes = snap.userPalettes,
                    explicitRandomColors = explicitRandom
                )
                if (capturedEpoch != prefetchEpoch) return@launch
                renderedCache[rendered.seed] = rendered
                if (_uiState.value.preview == null) {
                    val updatedHistory = buildCurrentHistoryList(rendered)
                    _uiState.update { state ->
                        state.copy(
                            preview = rendered,
                            stagedColors = rendered.colors,
                            previousPreview = pastStack.lastOrNull()?.let { renderedCache[it.seed] },
                            canSwipeRight = pastStack.isNotEmpty(),
                            history = updatedHistory,
                            isGeneratingPreview = false
                        )
                    }
                    persistActiveDeck(rendered)
                } else {
                    aheadQueue.addLast(rendered)
                    _uiState.update { state ->
                        state.copy(
                            nextPreview = aheadQueue.firstOrNull(),
                            aheadReadyCount = aheadQueue.size
                        )
                    }
                }
            }

            // 2. Pre-warm immediate previous wallpaper in RAM if needed so swipe-right is instant
            val immediatePrev = pastStack.lastOrNull()
            if (immediatePrev != null && renderedCache[immediatePrev.seed] == null) {
                val renderedPrev = buildRenderedPreview(
                    seed = immediatePrev.seed,
                    pattern = immediatePrev.pattern,
                    colors = immediatePrev.paletteColors,
                    pitchBlackRatio = immediatePrev.pitchBlackRatio,
                    colorFillMode = immediatePrev.colorFillMode,
                    patternWeightMode = immediatePrev.patternWeightMode,
                    intricacy = immediatePrev.intricacy
                )
                renderedCache[immediatePrev.seed] = renderedPrev
                if (pastStack.lastOrNull()?.seed == immediatePrev.seed) {
                    _uiState.update { it.copy(previousPreview = renderedPrev) }
                }
            }

            // 3. Generate ahead buffer up to PREFETCH_AHEAD_TARGET
            while (isActive && capturedEpoch == prefetchEpoch && aheadQueue.size < PREFETCH_AHEAD_TARGET) {
                val snap = _uiState.value
                val seed = nextUniqueSeed()
                val explicitRandom = pendingRandomColors
                pendingRandomColors = null
                val renderedNext = synthesizeForConfig(
                    seed = seed,
                    config = snap.config,
                    userPalettes = snap.userPalettes,
                    explicitRandomColors = explicitRandom
                )
                if (capturedEpoch != prefetchEpoch) return@launch
                renderedCache[renderedNext.seed] = renderedNext

                // If user swiped left onto loading slot while we were rendering, promote directly to active preview
                if (_uiState.value.preview == null) {
                    val updatedHistory = buildCurrentHistoryList(renderedNext)
                    _uiState.update { state ->
                        state.copy(
                            preview = renderedNext,
                            stagedColors = renderedNext.colors,
                            previousPreview = pastStack.lastOrNull()?.let { renderedCache[it.seed] },
                            nextPreview = aheadQueue.firstOrNull(),
                            canSwipeRight = pastStack.isNotEmpty(),
                            aheadReadyCount = aheadQueue.size,
                            history = updatedHistory,
                            isGeneratingPreview = false
                        )
                    }
                    persistActiveDeck(renderedNext)
                } else {
                    aheadQueue.addLast(renderedNext)
                    _uiState.update { state ->
                        state.copy(
                            nextPreview = aheadQueue.firstOrNull(),
                            aheadReadyCount = aheadQueue.size
                        )
                    }
                }
            }
        }
    }

    /**
     * Commits a swipe-left gesture: advances to the next pre-generated wallpaper
     * (or switches to the pitch-black loading slot if the user swiped faster than background synthesis).
     */
    fun swipeLeftToNextWallpaper(): Boolean {
        onPreviewUserActivity()
        val currentSnapshot = _uiState.value
        val active = currentSnapshot.preview
        if (active == null && currentSnapshot.isGeneratingPreview) {
            return false
        }

        recordSuccessfulGesture(StudioGestureAction.SWIPE_LEFT)

        if (active != null) {
            renderedCache[active.seed] = active
            pushPastItem(active.toHistoryItem())
        }

        configSaveJob?.cancel()
        viewModelScope.launch {
            val latestConfig = _uiState.value.config
            repository.updateConfig { storedConfig ->
                latestConfig.copy(
                    autoRotateEnabled = storedConfig.autoRotateEnabled,
                    intervalMinutes = storedConfig.intervalMinutes,
                    nextRunEpochMillis = storedConfig.nextRunEpochMillis
                )
            }
        }

        val readyNext = aheadQueue.removeFirstOrNull()
        if (readyNext != null) {
            renderedCache[readyNext.seed] = readyNext
            val updatedHistory = buildCurrentHistoryList(readyNext)
            _uiState.update { state ->
                state.copy(
                    preview = readyNext,
                    stagedColors = readyNext.colors,
                    previousPreview = pastStack.lastOrNull()?.let { renderedCache[it.seed] },
                    nextPreview = aheadQueue.firstOrNull(),
                    canSwipeRight = pastStack.isNotEmpty(),
                    aheadReadyCount = aheadQueue.size,
                    history = updatedHistory,
                    hasUnappliedChanges = false,
                    isGeneratingPreview = false
                )
            }
            persistActiveDeck(readyNext)
            ensureDeckPrefetchIfIdle()
        } else {
            // User outran ahead queue: show centered OLED loading state while prefetch synthesizes
            val updatedHistory = buildCurrentHistoryList(null)
            _uiState.update { state ->
                state.copy(
                    preview = null,
                    previousPreview = pastStack.lastOrNull()?.let { renderedCache[it.seed] },
                    nextPreview = null,
                    canSwipeRight = pastStack.isNotEmpty(),
                    aheadReadyCount = 0,
                    history = updatedHistory,
                    hasUnappliedChanges = false,
                    isGeneratingPreview = true
                )
            }
            ensureDeckPrefetchIfIdle(bypassDebounce = true)
        }
        return true
    }

    /**
     * Commits a swipe-right gesture: restores the immediate previous wallpaper from recent history.
     * Returns false (and records a bounce without crediting SWIPE_RIGHT) if there are no more recent wallpapers.
     */
    fun swipeRightToPreviousWallpaper(): Boolean {
        val prevItem = pastStack.removeLastOrNull()
        if (prevItem == null) {
            recordRightEdgeBounce()
            return false
        }

        recordSuccessfulGesture(StudioGestureAction.SWIPE_RIGHT)

        val oldActive = _uiState.value.preview
        if (oldActive != null && !_uiState.value.hasUnappliedChanges) {
            renderedCache[oldActive.seed] = oldActive
            aheadQueue.removeAll { it.seed == oldActive.seed }
            aheadQueue.addFirst(oldActive)
            while (aheadQueue.size > PREFETCH_AHEAD_TARGET + 2) {
                aheadQueue.removeLast()
            }
        }

        val cachedPrev = renderedCache[prevItem.seed]
        val nextPastCached = pastStack.lastOrNull()?.let { renderedCache[it.seed] }

        if (cachedPrev != null) {
            val updatedHistory = buildCurrentHistoryList(cachedPrev)
            _uiState.update { state ->
                state.copy(
                    preview = cachedPrev,
                    stagedColors = cachedPrev.colors,
                    previousPreview = nextPastCached,
                    nextPreview = aheadQueue.firstOrNull(),
                    canSwipeRight = pastStack.isNotEmpty(),
                    aheadReadyCount = aheadQueue.size,
                    history = updatedHistory,
                    isGeneratingPreview = false
                )
            }
            persistActiveDeck(cachedPrev)
            if (nextPastCached == null && pastStack.isNotEmpty()) {
                prewarmImmediatePast()
            }
        } else {
            previousRenderJob?.cancel()
            _uiState.update { state ->
                state.copy(
                    preview = null,
                    previousPreview = nextPastCached,
                    nextPreview = aheadQueue.firstOrNull(),
                    canSwipeRight = pastStack.isNotEmpty(),
                    aheadReadyCount = aheadQueue.size,
                    isGeneratingPreview = true
                )
            }
            previousRenderJob = viewModelScope.launch {
                val rendered = buildRenderedPreview(
                    seed = prevItem.seed,
                    pattern = prevItem.pattern,
                    colors = prevItem.paletteColors,
                    pitchBlackRatio = prevItem.pitchBlackRatio,
                    colorFillMode = prevItem.colorFillMode,
                    patternWeightMode = prevItem.patternWeightMode,
                    intricacy = prevItem.intricacy
                )
                renderedCache[rendered.seed] = rendered
                val updatedHistory = buildCurrentHistoryList(rendered)
                _uiState.update { state ->
                    state.copy(
                        preview = rendered,
                        stagedColors = rendered.colors,
                        previousPreview = pastStack.lastOrNull()?.let { renderedCache[it.seed] },
                        canSwipeRight = pastStack.isNotEmpty(),
                        history = updatedHistory,
                        isGeneratingPreview = false
                    )
                }
                persistActiveDeck(rendered)
                prewarmImmediatePast()
            }
        }
        return true
    }

    private fun prewarmImmediatePast() {
        val target = pastStack.lastOrNull() ?: return
        if (renderedCache[target.seed] != null) return
        viewModelScope.launch {
            val rendered = buildRenderedPreview(
                seed = target.seed,
                pattern = target.pattern,
                colors = target.paletteColors,
                pitchBlackRatio = target.pitchBlackRatio,
                colorFillMode = target.colorFillMode,
                patternWeightMode = target.patternWeightMode,
                intricacy = target.intricacy
            )
            renderedCache[target.seed] = rendered
            if (pastStack.lastOrNull()?.seed == target.seed) {
                _uiState.update { it.copy(previousPreview = rendered) }
            }
        }
    }

    fun recordRightEdgeBounce() {
        onPreviewUserActivity()
        _uiState.update { it.copy(rightEdgeBounceCount = it.rightEdgeBounceCount + 1) }
    }

    private fun startForegroundScheduleMonitor() {
        viewModelScope.launch {
            while (isActive) {
                delay(2_000L)
                if (!hasVerifiedInitialSchedule) continue
                val current = _uiState.value
                if (current.config.autoRotateEnabled && current.config.nextRunEpochMillis > 0L) {
                    val remaining = current.config.nextRunEpochMillis - System.currentTimeMillis()
                    if (remaining <= 0L && !isExecutingScheduledTick) {
                        isExecutingScheduledTick = true
                        try {
                            val result = WallpaperScheduler.executeAutoRotationCycle(
                                context = appContext,
                                wasScheduled = true,
                                force = false,
                                fromRunningWorker = false
                            )
                            if (result is WallpaperApplyResult.Success && !result.skippedAsDuplicate) {
                                val locCtx = localizedContext
                                postStatus(
                                    locCtx.getString(
                                        R.string.msg_wallpaper_applied,
                                        locCtx.getString(current.config.autoTarget.labelRes),
                                        result.measuredBlackPercent
                                    )
                                )
                            }
                        } finally {
                            isExecutingScheduledTick = false
                        }
                    }
                }
            }
        }
    }

    private suspend fun buildRenderedPreview(
        seed: Long,
        pattern: ArtPattern,
        colors: List<Long>,
        pitchBlackRatio: Float,
        colorFillMode: ColorFillMode,
        patternWeightMode: PatternWeightMode = PatternWeightMode.NORMAL,
        intricacy: Float
    ): RenderedPreviewSnapshot = withContext(Dispatchers.Default) {
        val synthesized = WallpaperGeneratorEngine.synthesizeFromParameters(
            seed = seed,
            pattern = pattern,
            paletteColors = colors,
            colorFillMode = colorFillMode,
            pitchBlackRatio = pitchBlackRatio,
            intricacy = intricacy,
            aspectRatio = screenAspect,
            patternWeightMode = patternWeightMode
        )
        rasterizeSynthesizedPreview(synthesized)
    }

    private fun rasterizeSynthesizedPreview(
        synthesized: com.cptodix.rimalux.oled.wallpaper.engine.SynthesizedWallpaper
    ): RenderedPreviewSnapshot {
        val previewWidth = 640
        val previewHeight = (previewWidth * screenAspect).roundToInt().coerceIn(640, 1480)
        val bitmap = WallpaperRenderer.renderToBitmap(
            scene = synthesized.scene,
            paletteColors = synthesized.paletteColors,
            pitchBlackRatio = synthesized.pitchBlackRatio,
            widthPx = previewWidth,
            heightPx = previewHeight,
            colorFillMode = synthesized.colorFillMode
        )
        return RenderedPreviewSnapshot(
            seed = synthesized.seed,
            pattern = synthesized.pattern,
            colors = synthesized.paletteColors,
            pitchBlackRatio = synthesized.pitchBlackRatio,
            colorFillMode = synthesized.colorFillMode,
            patternWeightMode = synthesized.patternWeightMode,
            intricacy = synthesized.intricacy,
            ratioStats = synthesized.ratioStats,
            scene = synthesized.scene,
            imageBitmap = bitmap.asImageBitmap()
        )
    }

    private fun resolveColorsForConfig(
        seed: Long,
        config: GeneratorConfig,
        userPalettes: List<ColorPalette>,
        excludeColors: List<Long>? = null
    ): List<Long> = ColorHarmonyEngine.selectColorsForSeed(
        seed = seed,
        paletteMode = config.paletteMode,
        selectedPaletteIds = config.selectedPaletteIds,
        availablePalettes = userPalettes,
        excludeColors = excludeColors
    )

    private fun persistConfigChanges(debounceMs: Long = 0L) {
        configSaveJob?.cancel()
        configSaveJob = viewModelScope.launch {
            if (debounceMs > 0L) {
                delay(debounceMs)
            }
            val latestConfig = _uiState.value.config
            repository.updateConfig { storedConfig ->
                latestConfig.copy(
                    autoRotateEnabled = storedConfig.autoRotateEnabled,
                    intervalMinutes = storedConfig.intervalMinutes,
                    nextRunEpochMillis = storedConfig.nextRunEpochMillis
                )
            }
        }
    }

    fun navigateTo(screen: RimaluxScreen) {
        _uiState.update {
            it.copy(
                currentScreen = screen,
                activeMiniHint = null,
                previewActivityEpoch = it.previewActivityEpoch + 1L
            )
        }
    }

    fun setBottomSheetOpen(isOpen: Boolean) {
        onPreviewUserActivity()
        _uiState.update { it.copy(isBottomSheetOpen = isOpen) }
    }

    fun onSwipeUpOpenedSheet() {
        recordSuccessfulGesture(StudioGestureAction.SWIPE_UP)
        _uiState.update { it.copy(isBottomSheetOpen = true) }
    }

    fun onPreviewUserActivity() {
        _uiState.update { state ->
            state.copy(
                activeMiniHint = null,
                previewActivityEpoch = state.previewActivityEpoch + 1L
            )
        }
    }

    private fun recordSuccessfulGesture(action: StudioGestureAction) {
        val now = clockMillis()
        pendingGestureWrites[action] = now
        _uiState.update { state ->
            state.copy(
                activeMiniHint = null,
                previewActivityEpoch = state.previewActivityEpoch + 1L,
                lastGestureTimestamps = state.lastGestureTimestamps + (action to now)
            )
        }
        viewModelScope.launch {
            try {
                repository.recordGesturePerformed(action, now)
            } finally {
                if (pendingGestureWrites[action] == now) {
                    pendingGestureWrites.remove(action)
                }
            }
        }
    }

    fun eligibleStaleGestures(nowMillis: Long = clockMillis()): List<StudioGestureAction> {
        val timestamps = _uiState.value.lastGestureTimestamps
        return StudioGestureAction.entries.filter { action ->
            val lastMs = timestamps[action] ?: 0L
            lastMs <= 0L || (nowMillis - lastMs) > GESTURE_STALE_THRESHOLD_MS
        }
    }

    private fun canShowMiniHintNow(state: RimaluxUiState): Boolean {
        if (!hasInitializedFromStorage) return false
        if (state.currentScreen != RimaluxScreen.HOME) return false
        if (state.preview == null || state.isGeneratingPreview) return false
        if (state.isBottomSheetOpen || state.isGestureTutorialVisible) return false
        if (state.isSetWallpaperDialogOpen ||
            state.isCreatePaletteSheetOpen ||
            state.isAutoTargetDialogOpen ||
            state.isIntervalDialogOpen ||
            state.isCustomIntervalDialogOpen ||
            state.isLikeAppDialogOpen ||
            state.isSendThanksDialogOpen ||
            state.isPatternLayoutDialogOpen ||
            state.isColorFillDialogOpen
        ) {
            return false
        }
        return true
    }

    private fun selectNextMiniHint(currentAction: StudioGestureAction?): StudioMiniHintState? {
        val current = _uiState.value
        if (!canShowMiniHintNow(current)) return null
        val eligible = eligibleStaleGestures(clockMillis())
        if (eligible.isEmpty()) return null

        val chosenAction = if (eligible.size == 1) {
            eligible.first()
        } else {
            val startIdx = miniHintActionCursor.mod(eligible.size)
            val candidate = eligible[startIdx]
            if (currentAction != null && candidate == currentAction) {
                val nextIdx = (startIdx + 1) % eligible.size
                miniHintActionCursor = (nextIdx + 1) % eligible.size
                eligible[nextIdx]
            } else {
                miniHintActionCursor = (startIdx + 1) % eligible.size
                candidate
            }
        }

        val variations = chosenAction.variationResIds
        val nextVarIndex = (miniHintVariationCursors[chosenAction] ?: 0).mod(variations.size)
        miniHintVariationCursors[chosenAction] = (nextVarIndex + 1) % variations.size

        return StudioMiniHintState(
            action = chosenAction,
            variationIndex = nextVarIndex,
            messageRes = variations[nextVarIndex]
        )
    }

    fun showMiniHintAfterIdle(): Boolean {
        val nextHint = selectNextMiniHint(currentAction = _uiState.value.activeMiniHint?.action)
        _uiState.update { it.copy(activeMiniHint = nextHint) }
        return nextHint != null
    }

    fun rotateMiniHint(): Boolean {
        val nextHint = selectNextMiniHint(currentAction = _uiState.value.activeMiniHint?.action)
        _uiState.update { it.copy(activeMiniHint = nextHint) }
        return nextHint != null
    }

    fun hideMiniHint() {
        if (_uiState.value.activeMiniHint != null) {
            _uiState.update { it.copy(activeMiniHint = null) }
        }
    }

    fun dismissGestureTutorial() {
        val shouldPersist = !hasDismissedTutorialThisSession || _uiState.value.isGestureTutorialVisible
        hasDismissedTutorialThisSession = true
        _uiState.update {
            it.copy(
                isGestureTutorialVisible = false,
                activeMiniHint = null,
                previewActivityEpoch = it.previewActivityEpoch + 1L
            )
        }
        if (shouldPersist) {
            viewModelScope.launch {
                repository.setGestureTutorialSeen(true)
            }
        }
    }

    fun onCanvasSingleTap() {
        // Single-tap never opens or dismisses the Hint Window; any preview interaction closes the mini hint.
        onPreviewUserActivity()
    }

    fun openHintWindowFromMiniHint() {
        reopenGestureTutorial()
    }

    fun reopenGestureTutorial() {
        hasDismissedTutorialThisSession = true
        _uiState.update {
            it.copy(
                isBottomSheetOpen = false,
                activeMiniHint = null,
                isGestureTutorialVisible = true,
                previewActivityEpoch = it.previewActivityEpoch + 1L
            )
        }
        viewModelScope.launch {
            repository.setGestureTutorialSeen(true)
        }
    }

    fun cycleMockupOverlay() {
        onPreviewUserActivity()
        if (_uiState.value.preview == null) return
        recordSuccessfulGesture(StudioGestureAction.SWIPE_DOWN)
        _uiState.update { state ->
            val nextMode = when (state.mockupMode) {
                MockupOverlayMode.NONE -> MockupOverlayMode.LOCK_SCREEN
                MockupOverlayMode.LOCK_SCREEN -> MockupOverlayMode.HOME_GRID
                MockupOverlayMode.HOME_GRID -> MockupOverlayMode.NONE
            }
            state.copy(mockupMode = nextMode)
        }
    }

    fun setMockupOverlayMode(mode: MockupOverlayMode) {
        onPreviewUserActivity()
        _uiState.update { it.copy(mockupMode = mode) }
    }

    fun dismissStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun openSetWallpaperDialog() {
        onPreviewUserActivity()
        val current = _uiState.value
        if (current.preview == null || current.isApplyingWallpaper || current.isExportingWallpaper) return
        recordSuccessfulGesture(StudioGestureAction.DOUBLE_TAP)
        _uiState.update { it.copy(isSetWallpaperDialogOpen = true) }
    }

    fun dismissSetWallpaperDialog() {
        _uiState.update { it.copy(isSetWallpaperDialogOpen = false) }
    }

    fun applyWallpaperForTarget(target: WallpaperTarget) {
        val current = _uiState.value
        val preview = current.preview ?: return
        if (current.isApplyingWallpaper || current.isExportingWallpaper) return

        _uiState.update {
            it.copy(
                isSetWallpaperDialogOpen = false,
                isApplyingWallpaper = true,
                config = it.config.copy(manualTarget = target)
            )
        }
        persistConfigChanges(debounceMs = 0L)

        viewModelScope.launch {
            val result = if (target == WallpaperTarget.BOTH_SEPARATE) {
                val cfg = _uiState.value.config
                val palettes = _uiState.value.userPalettes
                val rng = Random(System.nanoTime() xor preview.seed xor 0x3C6EF372FE94F82AL)
                val lockSeed = WallpaperGeneratorEngine.nextDistinctSeed(
                    rng = rng,
                    excludedSeeds = setOf(0L, preview.seed)
                )
                val lockGen = withContext(Dispatchers.Default) {
                    WallpaperGeneratorEngine.synthesize(
                        seed = lockSeed,
                        spec = cfg.toCoreSpec(),
                        availablePalettes = palettes,
                        aspectRatio = screenAspect,
                        excludePattern = preview.pattern,
                        excludeColors = preview.colors
                    )
                }

                val applyRes = WallpaperRenderer.applySeparateWallpapersToSystem(
                    context = appContext,
                    homeScene = preview.scene,
                    homePaletteColors = preview.colors,
                    homePitchBlackRatio = preview.pitchBlackRatio,
                    lockScene = lockGen.scene,
                    lockPaletteColors = lockGen.paletteColors,
                    lockPitchBlackRatio = lockGen.pitchBlackRatio,
                    colorFillMode = preview.colorFillMode
                )
                if (applyRes is WallpaperApplyResult.Success) {
                    val lockHistoryItem = GeneratedHistoryItem(
                        id = "gen_${System.currentTimeMillis()}_lock_${lockGen.seed.toString(16)}",
                        seed = lockGen.seed,
                        pattern = lockGen.pattern,
                        patternWeightMode = lockGen.patternWeightMode,
                        colorFillMode = cfg.colorFillMode,
                        paletteColors = lockGen.paletteColors,
                        pitchBlackRatio = lockGen.pitchBlackRatio,
                        intricacy = lockGen.intricacy,
                        timestampMillis = System.currentTimeMillis(),
                        wasAutoScheduled = false
                    )
                    pushPastItem(lockHistoryItem)
                    val updatedHistory = listOf(lockHistoryItem) +
                        buildCurrentHistoryList(preview).filterNot { it.seed == lockHistoryItem.seed }
                    _uiState.update { state ->
                        state.copy(
                            canSwipeRight = pastStack.isNotEmpty(),
                            history = updatedHistory.take(MAX_PAST_STACK)
                        )
                    }
                    repository.appendHistoryOnly(lockHistoryItem)
                    prewarmImmediatePast()
                }
                applyRes
            } else {
                WallpaperRenderer.applyWallpaperToSystem(
                    context = appContext,
                    scene = preview.scene,
                    paletteColors = preview.colors,
                    pitchBlackRatio = preview.pitchBlackRatio,
                    colorFillMode = preview.colorFillMode,
                    target = target
                )
            }
            _uiState.update { it.copy(isApplyingWallpaper = false) }
            handleApplyResult(result, target)
        }
    }

    fun saveCurrentWallpaperToDevice() {
        val current = _uiState.value
        val preview = current.preview ?: return
        if (current.isApplyingWallpaper || current.isExportingWallpaper) return

        _uiState.update {
            it.copy(
                isSetWallpaperDialogOpen = false,
                isExportingWallpaper = true
            )
        }

        viewModelScope.launch {
            val result = WallpaperExporter.saveWallpaperToDevice(
                context = appContext,
                scene = preview.scene,
                patternId = preview.pattern.id,
                seed = preview.seed,
                paletteColors = preview.colors,
                pitchBlackRatio = preview.pitchBlackRatio,
                colorFillMode = preview.colorFillMode
            )
            _uiState.update { it.copy(isExportingWallpaper = false) }
            when (result) {
                is WallpaperSaveResult.Success -> {
                    postStatus(
                        localizedContext.getString(
                            R.string.msg_wallpaper_saved,
                            result.widthPx,
                            result.heightPx
                        )
                    )
                }
                is WallpaperSaveResult.Error -> {
                    postStatus(localizedContext.getString(R.string.msg_wallpaper_save_error, result.reason))
                }
            }
        }
    }

    fun shareCurrentWallpaper() {
        val current = _uiState.value
        val preview = current.preview ?: return
        if (current.isApplyingWallpaper || current.isExportingWallpaper) return

        _uiState.update {
            it.copy(
                isSetWallpaperDialogOpen = false,
                isExportingWallpaper = true
            )
        }

        viewModelScope.launch {
            val locCtx = localizedContext
            val patternTitle = locCtx.getString(preview.pattern.titleRes)
            val result = WallpaperExporter.prepareShareWallpaperIntent(
                context = locCtx,
                scene = preview.scene,
                patternId = preview.pattern.id,
                patternTitle = patternTitle,
                seed = preview.seed,
                paletteColors = preview.colors,
                pitchBlackRatio = preview.pitchBlackRatio,
                colorFillMode = preview.colorFillMode
            )
            when (result) {
                is WallpaperShareResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isExportingWallpaper = false,
                            pendingShareIntent = result.chooserIntent
                        )
                    }
                }
                is WallpaperShareResult.Error -> {
                    _uiState.update { it.copy(isExportingWallpaper = false) }
                    postStatus(locCtx.getString(R.string.msg_wallpaper_share_error, result.reason))
                }
            }
        }
    }

    fun consumePendingShareIntent() {
        _uiState.update { it.copy(pendingShareIntent = null) }
    }

    fun reportStoragePermissionDenied() {
        _uiState.update { it.copy(isSetWallpaperDialogOpen = false) }
        postStatus(localizedContext.getString(R.string.msg_storage_permission_denied))
    }

    fun reportShareLaunchError(reason: String) {
        postStatus(localizedContext.getString(R.string.msg_wallpaper_share_error, reason))
    }

    // --- Pitch Black Controls (Rebuilds ahead queue for next swipe-left) ---
    fun setPitchBlackMode(mode: PitchBlackMode) {
        if (_uiState.value.config.pitchBlackMode == mode) return
        _uiState.update { state ->
            state.copy(
                config = state.config.copy(pitchBlackMode = mode),
                hasUnappliedChanges = true
            )
        }
        persistConfigChanges(debounceMs = 0L)
        invalidateAheadAndPrefetch(debounceMs = 0L)
    }

    fun updatePitchBlackRatio(ratio: Float) {
        val clamped = ratio.coerceIn(0f, 0.95f)
        _uiState.update { state ->
            state.copy(
                config = state.config.copy(
                    pitchBlackMode = PitchBlackMode.SINGLE,
                    pitchBlackRatio = clamped
                ),
                hasUnappliedChanges = true
            )
        }
        persistConfigChanges(debounceMs = 180L)
        invalidateAheadAndPrefetch(debounceMs = 180L)
    }

    fun updatePitchBlackRange(minRatio: Float, maxRatio: Float) {
        val lo = min(minRatio, maxRatio).coerceIn(0f, 0.95f)
        val hi = max(minRatio, maxRatio).coerceIn(lo, 0.95f)
        _uiState.update { state ->
            state.copy(
                config = state.config.copy(
                    pitchBlackMode = PitchBlackMode.RANGE,
                    pitchBlackRangeMin = lo,
                    pitchBlackRangeMax = hi
                ),
                hasUnappliedChanges = true
            )
        }
        persistConfigChanges(debounceMs = 180L)
        invalidateAheadAndPrefetch(debounceMs = 180L)
    }

    fun updateManualTarget(target: WallpaperTarget) {
        _uiState.update { it.copy(config = it.config.copy(manualTarget = target)) }
        persistConfigChanges(debounceMs = 0L)
    }

    fun updateAutoTarget(target: WallpaperTarget) {
        _uiState.update {
            it.copy(
                isAutoTargetDialogOpen = false,
                config = it.config.copy(autoTarget = target)
            )
        }
        persistConfigChanges(debounceMs = 0L)
    }

    // --- Procedural Pattern Controls (Rebuilds ahead queue for next swipe-left) ---
    fun setPatternWeightMode(weightMode: PatternWeightMode) {
        if (_uiState.value.config.patternWeightMode == weightMode) {
            _uiState.update { it.copy(isPatternLayoutDialogOpen = false) }
            return
        }
        _uiState.update { state ->
            state.copy(
                isPatternLayoutDialogOpen = false,
                config = state.config.copy(patternWeightMode = weightMode),
                hasUnappliedChanges = true
            )
        }
        persistConfigChanges(debounceMs = 0L)
        invalidateAheadAndPrefetch(debounceMs = 0L)
    }

    fun setPatternLayoutDialogOpen(open: Boolean) {
        _uiState.update { it.copy(isPatternLayoutDialogOpen = open) }
    }

    fun setPatternsRandomized(randomized: Boolean) {
        if (_uiState.value.config.patternsRandomized == randomized) return
        _uiState.update { state ->
            state.copy(
                config = state.config.copy(patternsRandomized = randomized),
                hasUnappliedChanges = true
            )
        }
        persistConfigChanges(debounceMs = 0L)
        invalidateAheadAndPrefetch(debounceMs = 0L)
    }

    fun togglePatternSelection(pattern: ArtPattern) {
        val current = _uiState.value
        val updatedSet = if (current.config.patternsRandomized) {
            setOf(pattern)
        } else {
            val existing = current.config.selectedPatterns
            if (pattern in existing && existing.size > 1) {
                existing - pattern
            } else {
                existing + pattern
            }
        }

        _uiState.update { state ->
            state.copy(
                config = state.config.copy(
                    patternsRandomized = false,
                    selectedPatterns = updatedSet
                ),
                hasUnappliedChanges = true
            )
        }
        persistConfigChanges(debounceMs = 0L)
        invalidateAheadAndPrefetch(debounceMs = 0L)
    }

    fun selectAllPatterns() {
        _uiState.update { state ->
            state.copy(
                config = state.config.copy(
                    patternsRandomized = false,
                    selectedPatterns = ArtPattern.entries.toSet()
                ),
                hasUnappliedChanges = true
            )
        }
        persistConfigChanges(debounceMs = 0L)
        invalidateAheadAndPrefetch(debounceMs = 0L)
    }

    fun selectNonePatterns() {
        _uiState.update { state ->
            state.copy(
                config = state.config.copy(
                    patternsRandomized = false,
                    selectedPatterns = emptySet()
                ),
                hasUnappliedChanges = true
            )
        }
        persistConfigChanges(debounceMs = 0L)
        invalidateAheadAndPrefetch(debounceMs = 0L)
    }

    // --- Solid vs Gradient & Palette Controls (Rebuilds ahead queue for next swipe-left) ---
    fun setColorFillMode(fillMode: ColorFillMode) {
        if (_uiState.value.config.colorFillMode == fillMode) {
            _uiState.update { it.copy(isColorFillDialogOpen = false) }
            return
        }
        _uiState.update { state ->
            state.copy(
                isColorFillDialogOpen = false,
                config = state.config.copy(colorFillMode = fillMode),
                hasUnappliedChanges = true
            )
        }
        persistConfigChanges(debounceMs = 0L)
        invalidateAheadAndPrefetch(debounceMs = 0L)
    }

    fun setColorFillDialogOpen(open: Boolean) {
        _uiState.update { it.copy(isColorFillDialogOpen = open) }
    }

    fun setPaletteMode(mode: PaletteMode) {
        val current = _uiState.value
        if (current.config.paletteMode == mode) return
        val nextStagedColors = when (mode) {
            PaletteMode.RANDOMIZED -> {
                val fresh = RandomPaletteGenerator.generate(System.nanoTime())
                pendingRandomColors = fresh
                fresh
            }
            PaletteMode.PALETTE_LIST -> {
                pendingRandomColors = null
                val active = current.userPalettes.firstOrNull { it.id in current.config.selectedPaletteIds }
                    ?: current.userPalettes.firstOrNull()
                    ?: StarterPalettes.defaults.first()
                active.colors
            }
        }
        _uiState.update { state ->
            state.copy(
                config = state.config.copy(paletteMode = mode),
                stagedColors = nextStagedColors,
                hasUnappliedChanges = true
            )
        }
        persistConfigChanges(debounceMs = 0L)
        invalidateAheadAndPrefetch(debounceMs = 0L)
    }

    fun shuffleRandomColors() {
        val baseSeed = _uiState.value.preview?.seed ?: 0x5F3759DFL
        val freshColors = RandomPaletteGenerator.generate(System.nanoTime() xor baseSeed)
        pendingRandomColors = freshColors
        _uiState.update {
            it.copy(
                config = it.config.copy(paletteMode = PaletteMode.RANDOMIZED),
                stagedColors = freshColors,
                hasUnappliedChanges = true
            )
        }
        persistConfigChanges(debounceMs = 0L)
        invalidateAheadAndPrefetch(debounceMs = 0L)
    }

    fun saveCurrentRandomColorsToList() {
        val locCtx = localizedContext
        val colors = _uiState.value.stagedColors
        val nextIndex = _uiState.value.userPalettes.size + 1
        val name = locCtx.getString(R.string.default_harmony_palette_name, nextIndex)
        val palette = ColorPalette(
            id = "pal_${System.currentTimeMillis()}",
            name = name,
            colors = colors
        )
        viewModelScope.launch {
            repository.addPaletteToList(palette, selectAndSwitchToListMode = false)
            postStatus(locCtx.getString(R.string.msg_palette_saved, name))
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
        val nextStagedColors = if (palette.id in updatedIds) {
            palette.colors
        } else {
            current.userPalettes.firstOrNull { it.id in updatedIds }?.colors ?: palette.colors
        }
        pendingRandomColors = null

        _uiState.update { state ->
            state.copy(
                config = state.config.copy(
                    paletteMode = PaletteMode.PALETTE_LIST,
                    selectedPaletteIds = updatedIds
                ),
                stagedColors = nextStagedColors,
                hasUnappliedChanges = true
            )
        }
        persistConfigChanges(debounceMs = 0L)
        invalidateAheadAndPrefetch(debounceMs = 0L)
    }

    fun selectAllPalettes() {
        val allIds = _uiState.value.userPalettes.map { it.id }.toSet()
        val firstColors = _uiState.value.userPalettes.firstOrNull()?.colors
            ?: StarterPalettes.defaults.first().colors
        pendingRandomColors = null
        _uiState.update { state ->
            state.copy(
                config = state.config.copy(
                    paletteMode = PaletteMode.PALETTE_LIST,
                    selectedPaletteIds = allIds
                ),
                stagedColors = firstColors,
                hasUnappliedChanges = true
            )
        }
        persistConfigChanges(debounceMs = 0L)
        invalidateAheadAndPrefetch(debounceMs = 0L)
    }

    fun selectNonePalettes() {
        val fallbackColors = _uiState.value.userPalettes.firstOrNull()?.colors
            ?: StarterPalettes.defaults.first().colors
        pendingRandomColors = null
        _uiState.update { state ->
            state.copy(
                config = state.config.copy(
                    paletteMode = PaletteMode.PALETTE_LIST,
                    selectedPaletteIds = emptySet()
                ),
                stagedColors = fallbackColors,
                hasUnappliedChanges = true
            )
        }
        persistConfigChanges(debounceMs = 0L)
        invalidateAheadAndPrefetch(debounceMs = 0L)
    }

    fun setCreatePaletteSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isCreatePaletteSheetOpen = open) }
    }

    fun addCustomPalette(name: String, colors: List<Long>) {
        val locCtx = localizedContext
        val cleanName = name.trim().ifEmpty {
            locCtx.getString(R.string.default_custom_palette_name, _uiState.value.userPalettes.size + 1)
        }
        val validColors = colors.ifEmpty { listOf(0xFF00F5D4L, 0xFF7B2CBFL, 0xFFF15BB5L) }
        val palette = ColorPalette(
            id = "custom_${System.currentTimeMillis()}",
            name = cleanName,
            colors = validColors
        )
        pendingRandomColors = null
        _uiState.update { state ->
            state.copy(
                isCreatePaletteSheetOpen = false,
                stagedColors = validColors,
                config = state.config.copy(
                    paletteMode = PaletteMode.PALETTE_LIST,
                    selectedPaletteIds = state.config.selectedPaletteIds + palette.id
                ),
                hasUnappliedChanges = true
            )
        }
        invalidateAheadAndPrefetch(debounceMs = 0L)
        viewModelScope.launch {
            repository.addPaletteToList(palette, selectAndSwitchToListMode = true)
            postStatus(locCtx.getString(R.string.msg_palette_saved, cleanName))
        }
    }

    fun deletePaletteFromList(paletteId: String) {
        val current = _uiState.value
        if (current.userPalettes.size <= 1) return
        val wasSelectedInListMode = current.config.paletteMode == PaletteMode.PALETTE_LIST &&
            paletteId in current.config.selectedPaletteIds
        val remainingPalettes = current.userPalettes.filterNot { it.id == paletteId }
        val remainingIds = (current.config.selectedPaletteIds - paletteId)
            .ifEmpty { setOf(remainingPalettes.first().id) }

        _uiState.update { state ->
            state.copy(
                userPalettes = remainingPalettes,
                config = state.config.copy(selectedPaletteIds = remainingIds),
                hasUnappliedChanges = state.hasUnappliedChanges || wasSelectedInListMode
            )
        }
        if (wasSelectedInListMode) {
            invalidateAheadAndPrefetch(debounceMs = 0L)
        }
        viewModelScope.launch {
            repository.deletePaletteFromList(paletteId)
        }
    }

    // --- Persistent Background Interval Controls ---
    fun ensureAutoScheduleIfEnabled() {
        if (!hasInitializedFromStorage) return
        viewModelScope.launch {
            ensureAutoScheduleInternal(_uiState.value.config)
        }
    }

    private suspend fun ensureAutoScheduleInternal(cfg: GeneratorConfig) {
        if (!cfg.autoRotateEnabled) return
        val ensuredNextRun = WallpaperScheduler.ensureScheduled(
            context = appContext,
            intervalMinutes = cfg.intervalMinutes,
            desiredNextRunMillis = cfg.nextRunEpochMillis
        )
        if (ensuredNextRun != cfg.nextRunEpochMillis) {
            _uiState.update {
                it.copy(config = it.config.copy(nextRunEpochMillis = ensuredNextRun))
            }
            repository.updateConfig { it.copy(nextRunEpochMillis = ensuredNextRun) }
        }
    }

    fun setAutoRotateEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                val cfg = _uiState.value.config
                val nextRun = WallpaperScheduler.scheduleNext(appContext, cfg.intervalMinutes)
                _uiState.update {
                    it.copy(config = it.config.copy(autoRotateEnabled = true, nextRunEpochMillis = nextRun))
                }
                repository.updateConfig {
                    it.copy(autoRotateEnabled = true, nextRunEpochMillis = nextRun)
                }
                val locCtx = localizedContext
                postStatus(
                    locCtx.getString(
                        R.string.msg_schedule_updated,
                        IntervalFormatter.formatMinutesShort(locCtx, cfg.intervalMinutes),
                        locCtx.getString(cfg.autoTarget.labelRes)
                    )
                )
            } else {
                WallpaperScheduler.cancelAll(appContext)
                _uiState.update {
                    it.copy(
                        isAutoTargetDialogOpen = false,
                        isIntervalDialogOpen = false,
                        isCustomIntervalDialogOpen = false,
                        config = it.config.copy(autoRotateEnabled = false, nextRunEpochMillis = 0L)
                    )
                }
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
            _uiState.update {
                it.copy(
                    isIntervalDialogOpen = false,
                    isCustomIntervalDialogOpen = false,
                    config = it.config.copy(
                        intervalMinutes = safeMinutes,
                        autoRotateEnabled = true,
                        nextRunEpochMillis = nextRun
                    )
                )
            }
            repository.updateConfig {
                it.copy(
                    intervalMinutes = safeMinutes,
                    autoRotateEnabled = true,
                    nextRunEpochMillis = nextRun
                )
            }
            val locCtx = localizedContext
            postStatus(
                locCtx.getString(
                    R.string.msg_schedule_updated,
                    IntervalFormatter.formatMinutesShort(locCtx, safeMinutes),
                    locCtx.getString(autoTarget.labelRes)
                )
            )
        }
    }

    fun postExternalStatusMessage(message: String) {
        postStatus(message)
    }

    fun setAutoTargetDialogOpen(open: Boolean) {
        if (open && !_uiState.value.config.autoRotateEnabled) return
        _uiState.update { it.copy(isAutoTargetDialogOpen = open) }
    }

    fun setIntervalDialogOpen(open: Boolean) {
        if (open && !_uiState.value.config.autoRotateEnabled) return
        _uiState.update { it.copy(isIntervalDialogOpen = open) }
    }

    fun openCustomIntervalFromPicker() {
        _uiState.update {
            it.copy(
                isIntervalDialogOpen = false,
                isCustomIntervalDialogOpen = true
            )
        }
    }

    fun setCustomIntervalDialogOpen(open: Boolean) {
        _uiState.update { it.copy(isCustomIntervalDialogOpen = open) }
    }

    private fun handleApplyResult(result: WallpaperApplyResult, target: WallpaperTarget) {
        val locCtx = localizedContext
        when (result) {
            is WallpaperApplyResult.Success -> {
                postStatus(
                    locCtx.getString(
                        R.string.msg_wallpaper_applied,
                        locCtx.getString(target.labelRes),
                        result.measuredBlackPercent
                    )
                )
            }
            is WallpaperApplyResult.Error -> {
                postStatus(locCtx.getString(R.string.msg_wallpaper_error, result.reason))
            }
        }
    }

    private fun postStatus(message: String) {
        _uiState.update { it.copy(statusMessage = message) }
    }

    fun setLanguageDialogOpen(open: Boolean) {
        _uiState.update { it.copy(isLanguageDialogOpen = open) }
    }

    fun selectAppLanguage(language: AppLanguage) {
        val changed = AppLocaleManager.setSelectedLanguage(appContext, language)
        _uiState.update { state ->
            state.copy(
                isLanguageDialogOpen = false,
                appLanguage = language,
                pendingLocaleRefreshToken = if (changed) {
                    state.pendingLocaleRefreshToken + 1L
                } else {
                    state.pendingLocaleRefreshToken
                }
            )
        }
        viewModelScope.launch {
            repository.setAppLanguageTag(language.languageTag)
        }
    }

    fun consumePendingLocaleRefresh() {
        if (_uiState.value.pendingLocaleRefreshToken == 0L) return
        _uiState.update { it.copy(pendingLocaleRefreshToken = 0L) }
    }

    fun openLikeAppDialog() {
        _uiState.update { it.copy(isLikeAppDialogOpen = true) }
    }

    fun closeLikeAppDialog() {
        _uiState.update { it.copy(isLikeAppDialogOpen = false) }
    }

    fun openSendThanksDialog() {
        _uiState.update { it.copy(isLikeAppDialogOpen = false, isSendThanksDialogOpen = true) }
    }

    fun closeSendThanksDialog() {
        _uiState.update { it.copy(isSendThanksDialogOpen = false) }
    }

    fun rateApp() {
        closeLikeAppDialog()
        val packageName = appContext.packageName
        val rateIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching {
            appContext.startActivity(rateIntent)
            true
        }.getOrElse {
            runCatching {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(webIntent)
                true
            }.getOrDefault(false)
        }
        postStatus(localizedContext.getString(R.string.msg_rating_launched))
    }

    fun sendThanksTip(amountLabel: String) {
        closeSendThanksDialog()
        postStatus(localizedContext.getString(R.string.msg_thanks_sent, amountLabel))
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RimaluxViewModel(context.applicationContext) as T
        }
    }
}
