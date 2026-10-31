package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.WallpaperPreferencesRepository
import com.example.engine.ColorHarmonyEngine
import com.example.engine.MathPatternGenerator
import com.example.engine.SceneRatioStats
import com.example.engine.VectorScene
import com.example.engine.WallpaperGeneratorEngine
import com.example.model.ArtPattern
import com.example.model.ColorFillMode
import com.example.model.ColorPalette
import com.example.model.GeneratedHistoryItem
import com.example.model.GeneratorConfig
import com.example.model.IntervalFormatter
import com.example.model.MockupOverlayMode
import com.example.model.PaletteMode
import com.example.model.PitchBlackMode
import com.example.model.RandomPaletteGenerator
import com.example.model.StarterPalettes
import com.example.model.WallpaperTarget
import com.example.model.titleRes
import com.example.platform.WallpaperApplyResult
import com.example.platform.WallpaperExporter
import com.example.platform.WallpaperRenderer
import com.example.platform.WallpaperSaveResult
import com.example.platform.WallpaperShareResult
import com.example.scheduler.WallpaperScheduler
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
    val isSendThanksDialogOpen: Boolean = false
)

class RimaluxViewModel(
    private val appContext: Context,
    private val repository: WallpaperPreferencesRepository = WallpaperPreferencesRepository(appContext)
) : ViewModel() {

    companion object {
        const val PREFETCH_AHEAD_TARGET = 3
        private const val MAX_PAST_STACK = 24
        private const val MAX_RENDERED_CACHE = 8
    }

    private val screenAspect: Float by lazy {
        val (w, h) = WallpaperRenderer.getTargetWallpaperDimensions(appContext)
        (h.toFloat() / w.toFloat()).coerceIn(0.55f, 2.8f)
    }

    private val _uiState = MutableStateFlow(RimaluxUiState())
    val uiState: StateFlow<RimaluxUiState> = _uiState.asStateFlow()

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

                    _uiState.update { state ->
                        state.copy(
                            config = stored.config,
                            userPalettes = stored.userPalettes,
                            history = stored.history,
                            canSwipeRight = pastStack.isNotEmpty()
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
                        val rendered = buildRenderedPreview(
                            seed = stored.currentSeed,
                            pattern = patternToUse,
                            colors = colorsToUse,
                            pitchBlackRatio = effectiveBlackRatio,
                            colorFillMode = stored.config.colorFillMode,
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
                    val rendered = buildRenderedPreview(
                        seed = seedToUse,
                        pattern = patternToUse,
                        colors = colorsToUse,
                        pitchBlackRatio = effectiveBlackRatio,
                        colorFillMode = stored.config.colorFillMode,
                        intricacy = effectiveIntricacy
                    )
                    renderedCache[rendered.seed] = rendered
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
                            config = state.config.copy(
                                autoRotateEnabled = stored.config.autoRotateEnabled,
                                intervalMinutes = stored.config.intervalMinutes,
                                nextRunEpochMillis = stored.config.nextRunEpochMillis
                            )
                        )
                    }
                    ensureDeckPrefetchIfIdle()
                } else {
                    _uiState.update { state ->
                        state.copy(
                            userPalettes = stored.userPalettes,
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
        val currentSnapshot = _uiState.value
        val active = currentSnapshot.preview
        if (active == null && currentSnapshot.isGeneratingPreview) {
            return false
        }

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
     * Returns false (and records a bounce) if there are no more recent wallpapers.
     */
    fun swipeRightToPreviousWallpaper(): Boolean {
        val prevItem = pastStack.removeLastOrNull()
        if (prevItem == null) {
            recordRightEdgeBounce()
            return false
        }

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
                intricacy = target.intricacy
            )
            renderedCache[target.seed] = rendered
            if (pastStack.lastOrNull()?.seed == target.seed) {
                _uiState.update { it.copy(previousPreview = rendered) }
            }
        }
    }

    fun recordRightEdgeBounce() {
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
        intricacy: Float
    ): RenderedPreviewSnapshot = withContext(Dispatchers.Default) {
        val synthesized = WallpaperGeneratorEngine.synthesizeFromParameters(
            seed = seed,
            pattern = pattern,
            paletteColors = colors,
            colorFillMode = colorFillMode,
            pitchBlackRatio = pitchBlackRatio,
            intricacy = intricacy,
            aspectRatio = screenAspect
        )
        rasterizeSynthesizedPreview(synthesized)
    }

    private fun rasterizeSynthesizedPreview(
        synthesized: com.example.engine.SynthesizedWallpaper
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
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun setBottomSheetOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isBottomSheetOpen = isOpen) }
    }

    fun cycleMockupOverlay() {
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
        _uiState.update { it.copy(mockupMode = mode) }
    }

    fun dismissStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun openSetWallpaperDialog() {
        val current = _uiState.value
        if (current.preview == null || current.isApplyingWallpaper || current.isExportingWallpaper) return
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
                        appContext.getString(
                            R.string.msg_wallpaper_saved,
                            result.widthPx,
                            result.heightPx
                        )
                    )
                }
                is WallpaperSaveResult.Error -> {
                    postStatus(appContext.getString(R.string.msg_wallpaper_save_error, result.reason))
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
            val patternTitle = appContext.getString(preview.pattern.titleRes)
            val result = WallpaperExporter.prepareShareWallpaperIntent(
                context = appContext,
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
                    postStatus(appContext.getString(R.string.msg_wallpaper_share_error, result.reason))
                }
            }
        }
    }

    fun consumePendingShareIntent() {
        _uiState.update { it.copy(pendingShareIntent = null) }
    }

    fun reportStoragePermissionDenied() {
        _uiState.update { it.copy(isSetWallpaperDialogOpen = false) }
        postStatus(appContext.getString(R.string.msg_storage_permission_denied))
    }

    fun reportShareLaunchError(reason: String) {
        postStatus(appContext.getString(R.string.msg_wallpaper_share_error, reason))
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
        if (_uiState.value.config.colorFillMode == fillMode) return
        _uiState.update { state ->
            state.copy(
                config = state.config.copy(colorFillMode = fillMode),
                hasUnappliedChanges = true
            )
        }
        persistConfigChanges(debounceMs = 0L)
        invalidateAheadAndPrefetch(debounceMs = 0L)
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
        val colors = _uiState.value.stagedColors
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
        val cleanName = name.trim().ifEmpty { "Palette #${_uiState.value.userPalettes.size + 1}" }
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
            postStatus(appContext.getString(R.string.msg_palette_saved, cleanName))
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
                postStatus(
                    appContext.getString(
                        R.string.msg_schedule_updated,
                        IntervalFormatter.formatMinutesShort(cfg.intervalMinutes),
                        appContext.getString(cfg.autoTarget.labelRes)
                    )
                )
            } else {
                WallpaperScheduler.cancelAll(appContext)
                _uiState.update {
                    it.copy(config = it.config.copy(autoRotateEnabled = false, nextRunEpochMillis = 0L))
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
            postStatus(
                appContext.getString(
                    R.string.msg_schedule_updated,
                    IntervalFormatter.formatMinutesShort(safeMinutes),
                    appContext.getString(autoTarget.labelRes)
                )
            )
        }
    }

    fun postExternalStatusMessage(message: String) {
        postStatus(message)
    }

    fun setAutoTargetDialogOpen(open: Boolean) {
        _uiState.update { it.copy(isAutoTargetDialogOpen = open) }
    }

    fun setIntervalDialogOpen(open: Boolean) {
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
        postStatus(appContext.getString(R.string.msg_rating_launched))
    }

    fun sendThanksTip(amountLabel: String) {
        closeSendThanksDialog()
        postStatus(appContext.getString(R.string.msg_thanks_sent, amountLabel))
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RimaluxViewModel(context.applicationContext) as T
        }
    }
}
