package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ArtPattern
import com.example.model.ColorFillMode
import com.example.model.MockupOverlayMode
import com.example.model.PaletteMode
import com.example.model.PitchBlackMode
import com.example.model.titleRes
import com.example.ui.RimaluxUiState
import com.example.ui.components.SwipeableWallpaperDeck
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val SheetOpenEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)

@Composable
private fun FloatingBottomSheetHandle(
    isSheetOpen: Boolean,
    onClick: () -> Unit,
    onVerticalDragDelta: (Float) -> Unit,
    onVerticalDragEnd: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val handleCd = stringResource(R.string.cd_bottom_sheet_handle)
    val draggableState = rememberDraggableState { deltaY ->
        onVerticalDragDelta(-deltaY)
    }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isSheetOpen) 180f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "sheet_handle_chevron"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(88.dp)
            .height(48.dp)
            .minimumInteractiveComponentSize()
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocityY -> onVerticalDragEnd(velocityY) }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .semantics { contentDescription = handleCd }
            .testTag("home_bottom_sheet_handle")
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.72f),
            border = BorderStroke(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
            ),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            rotationZ = chevronRotation
                        }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    uiState: RimaluxUiState,
    onSwipeLeftNext: () -> Boolean,
    onSwipeRightPrevious: () -> Boolean,
    onRightEdgeBounce: () -> Unit,
    onOpenSetWallpaperDialog: () -> Unit,
    onSwipeDownCycleMockup: () -> Unit = {},
    onBottomSheetOpenChange: (Boolean) -> Unit = {},
    onSetMockupMode: (MockupOverlayMode) -> Unit = {},
    onSetPitchBlackMode: (PitchBlackMode) -> Unit,
    onPitchBlackRatioChange: (Float) -> Unit,
    onPitchBlackRangeChange: (Float, Float) -> Unit,
    onOpenPatternsPage: () -> Unit,
    onOpenPalettesPage: () -> Unit,
    onToggleAutoRotate: (Boolean) -> Unit,
    onOpenAutoTargetDialog: () -> Unit,
    onOpenIntervalDialog: () -> Unit,
    onOpenAutoChangeHelp: () -> Unit = {},
    onOpenLikeApp: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val patternsSummary = if (uiState.config.patternsRandomized) {
        stringResource(R.string.summary_patterns_random, ArtPattern.entries.size)
    } else {
        val names = uiState.config.selectedPatterns.take(2)
            .map { stringResource(it.titleRes).substringBefore(" ") }
            .joinToString(", ")
        val suffix = if (uiState.config.selectedPatterns.size > 2) {
            " +${uiState.config.selectedPatterns.size - 2}"
        } else ""
        stringResource(R.string.summary_patterns_selected, "$names$suffix")
    }

    val fillShortLabel = when (uiState.config.colorFillMode) {
        ColorFillMode.SOLID -> stringResource(R.string.fill_short_solid)
        ColorFillMode.GRADIENT -> stringResource(R.string.fill_short_gradient)
    }

    val paletteSummary = when (uiState.config.paletteMode) {
        PaletteMode.RANDOMIZED -> stringResource(R.string.summary_palette_random, fillShortLabel)
        PaletteMode.PALETTE_LIST -> {
            val activeList = uiState.userPalettes.filter { it.id in uiState.config.selectedPaletteIds }
            if (activeList.size == 1) {
                stringResource(R.string.summary_palette_single, activeList.first().name, fillShortLabel)
            } else {
                stringResource(R.string.summary_palette_list, activeList.size.coerceAtLeast(1), fillShortLabel)
            }
        }
    }

    val previewBlackRatio = uiState.preview?.pitchBlackRatio ?: uiState.config.pitchBlackRatio
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Initialize with open state if preserved from previous navigation
    val sheetProgress = remember { Animatable(if (uiState.isBottomSheetOpen) 1f else 0f) }
    var isDraggingSheet by remember { mutableStateOf(false) }
    var sheetAnimJob by remember { mutableStateOf<Job?>(null) }
    var dragTargetProgress by remember {
        mutableFloatStateOf(if (uiState.isBottomSheetOpen) 1f else 0f)
    }
    var dragStartedFromOpen by remember { mutableStateOf(uiState.isBottomSheetOpen) }
    var dragAccumulatedUpwardPx by remember { mutableFloatStateOf(0f) }
    var dragPeakUpwardPx by remember { mutableFloatStateOf(0f) }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    fun animateSheetOpen() {
        onBottomSheetOpenChange(true)
        isDraggingSheet = false
        dragTargetProgress = 1f
        dragStartedFromOpen = true
        dragAccumulatedUpwardPx = 0f
        dragPeakUpwardPx = 0f
        sheetAnimJob?.cancel()
        sheetAnimJob = coroutineScope.launch {
            sheetProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 280, easing = SheetOpenEasing)
            )
        }
    }

    fun animateSheetClosed() {
        onBottomSheetOpenChange(false)
        isDraggingSheet = false
        dragTargetProgress = 0f
        dragStartedFromOpen = false
        dragAccumulatedUpwardPx = 0f
        dragPeakUpwardPx = 0f
        sheetAnimJob?.cancel()
        sheetAnimJob = coroutineScope.launch {
            sheetProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
            )
        }
    }

    BackHandler(enabled = sheetProgress.value > 0.02f || dragTargetProgress > 0.02f) {
        animateSheetClosed()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxSheetHeightDp = (maxHeight - statusBarTop - 64.dp).coerceAtLeast(280.dp)
        val fallbackSheetHeightPx = with(density) { maxSheetHeightDp.roundToPx().coerceAtLeast(1) }
        var measuredSheetHeightPx by remember { mutableIntStateOf(fallbackSheetHeightPx) }
        val effectiveSheetHeightPx = measuredSheetHeightPx.coerceAtLeast(1)

        fun applyUpwardPullPx(upwardDeltaPx: Float) {
            if (!isDraggingSheet) {
                sheetAnimJob?.cancel()
                val startProgress = sheetProgress.value
                dragStartedFromOpen = startProgress >= 0.5f
                dragTargetProgress = startProgress
                dragAccumulatedUpwardPx = 0f
                dragPeakUpwardPx = 0f
                isDraggingSheet = true
            }
            dragAccumulatedUpwardPx += upwardDeltaPx
            if (dragAccumulatedUpwardPx > dragPeakUpwardPx) {
                dragPeakUpwardPx = dragAccumulatedUpwardPx
            }
            val next = (dragTargetProgress + (upwardDeltaPx / effectiveSheetHeightPx)).coerceIn(0f, 1f)
            dragTargetProgress = next
            sheetAnimJob?.cancel()
            sheetAnimJob = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                sheetProgress.snapTo(next)
            }
        }

        fun settleVerticalPull(velocityY: Float) {
            if (!isDraggingSheet && sheetProgress.value <= 0.001f && velocityY >= -40f) {
                return
            }
            val velocityDpPerSec = with(density) { velocityY.toDp().value }
            val netUpwardDp = with(density) { dragAccumulatedUpwardPx.toDp().value }
            val peakUpwardDp = with(density) { dragPeakUpwardPx.toDp().value }

            val shouldOpen = if (!dragStartedFromOpen) {
                val isUpwardFlick = velocityDpPerSec <= -35f
                val isDeliberateDownwardCancel = velocityDpPerSec >= 320f
                val pulledEnoughToOpen = (
                    netUpwardDp >= 12f ||
                        dragTargetProgress >= 0.02f ||
                        (peakUpwardDp >= 18f && netUpwardDp >= peakUpwardDp - 12f)
                    ) && netUpwardDp > 3f
                isUpwardFlick || (pulledEnoughToOpen && !isDeliberateDownwardCancel)
            } else {
                val isDownwardFlick = velocityDpPerSec >= 110f
                val isUpwardReopenFlick = velocityDpPerSec <= -150f
                val netDownwardDp = -netUpwardDp
                val pulledEnoughToClose = netDownwardDp >= 48f || dragTargetProgress <= 0.88f
                isUpwardReopenFlick || (!isDownwardFlick && !pulledEnoughToClose)
            }

            if (shouldOpen) animateSheetOpen() else animateSheetClosed()
        }

        val sheetNestedScroll = remember(effectiveSheetHeightPx) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    // Dragging downward when scroll is at top pulls the sheet closed
                    if (source == NestedScrollSource.UserInput && available.y > 0f && scrollState.value == 0) {
                        applyUpwardPullPx(-available.y)
                        return Offset(0f, available.y)
                    }
                    // If sheet is mid-drag between 0 and 1, dragging upward finishes opening sheet first
                    if (source == NestedScrollSource.UserInput && available.y < 0f &&
                        ((isDraggingSheet && dragTargetProgress < 0.999f) || sheetProgress.value < 0.999f)
                    ) {
                        applyUpwardPullPx(-available.y)
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (isDraggingSheet || (sheetProgress.value > 0f && sheetProgress.value < 0.999f)) {
                        settleVerticalPull(available.y)
                        return available
                    }
                    return Velocity.Zero
                }
            }
        }

        val isSheetComposed = sheetProgress.value > 0.002f || dragTargetProgress > 0.002f || isDraggingSheet

        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Full Edge-to-Edge Interactive Swipe Deck (Left = Next/Generate, Right = Previous/Bounce, Double-Tap = Apply Popup, Swipe Down = Cycle Overlay, Swipe Up = Open Sheet)
            SwipeableWallpaperDeck(
                currentPreview = uiState.preview,
                previousPreview = uiState.previousPreview,
                nextPreview = uiState.nextPreview,
                canSwipeRight = uiState.canSwipeRight,
                isGeneratingCurrent = uiState.isGeneratingPreview,
                mockupMode = uiState.mockupMode,
                bottomOverlayClearance = navBarBottom + 56.dp,
                gesturesEnabled = !isSheetComposed,
                onSwipeLeftCommitted = onSwipeLeftNext,
                onSwipeRightCommitted = onSwipeRightPrevious,
                onRightEdgeBounce = onRightEdgeBounce,
                onDoubleTap = onOpenSetWallpaperDialog,
                onSwipeDown = onSwipeDownCycleMockup,
                onVerticalSwipeUpDelta = ::applyUpwardPullPx,
                onVerticalSwipeUpEnd = ::settleVerticalPull
            )

            // 2. Small text overlay on main preview showing pattern, 4 color dots, and true black ratio (touch-passthrough)
            if (uiState.preview != null && !isSheetComposed) {
                val previewSnapshot = uiState.preview
                val patternTitle = stringResource(previewSnapshot.pattern.titleRes)
                val blackPercent = (previewBlackRatio * 100f).roundToInt()
                val bottomOffsetDp = if (uiState.hasUnappliedChanges) 98.dp else 56.dp

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = 20.dp,
                            end = 20.dp,
                            bottom = navBarBottom + bottomOffsetDp
                        )
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.72f))
                        .border(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            shape = CircleShape
                        )
                        .testTag("preview_info_overlay")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = patternTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            previewSnapshot.colors.take(4).forEach { argb ->
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(argb))
                                        .border(
                                            0.5.dp,
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f),
                                            CircleShape
                                        )
                                )
                            }
                        }

                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )

                        Text(
                            text = stringResource(R.string.preview_true_black_percent, blackPercent),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 3. Subtle floating pending-settings pill when sheet is hidden (touch-passthrough)
            if (uiState.hasUnappliedChanges && !isSheetComposed) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = 24.dp,
                            end = 24.dp,
                            bottom = navBarBottom + 56.dp
                        )
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f))
                        .testTag("preview_pending_notice")
                ) {
                    Text(
                        text = stringResource(R.string.preview_pending_changes_notice),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // 3. Backdrop Scrim + Completely Hidden-When-Closed Settings Bottom Sheet
            if (isSheetComposed) {
                val scrimDragState = rememberDraggableState { deltaY ->
                    applyUpwardPullPx(-deltaY)
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.scrim.copy(
                                alpha = (0.48f * sheetProgress.value).coerceIn(0f, 0.48f)
                            )
                        )
                        .draggable(
                            state = scrimDragState,
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocityY -> settleVerticalPull(velocityY) }
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { animateSheetClosed() }
                        )
                        .testTag("sheet_expanded_scrim")
                )

                Surface(
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxSheetHeightDp)
                        .onSizeChanged { size ->
                            if (size.height > 0) {
                                measuredSheetHeightPx = size.height
                            }
                        }
                        .graphicsLayer {
                            translationY = (1f - sheetProgress.value) * effectiveSheetHeightPx
                        }
                        .nestedScroll(sheetNestedScroll)
                        .testTag("home_settings_sheet")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(
                                start = 20.dp,
                                end = 20.dp,
                                top = 24.dp,
                                bottom = navBarBottom + 32.dp
                            )
                            .testTag("home_screen_scroll"),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Joined Tonal Control Card (True Black Ratio, Patterns, Colors)
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // True Black Ratio Controls
                                Column(
                                    modifier = Modifier.padding(
                                        start = 20.dp,
                                        end = 20.dp,
                                        top = 20.dp,
                                        bottom = 16.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.label_pitch_black_ratio),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        if (uiState.config.pitchBlackMode == PitchBlackMode.SINGLE) {
                                            Text(
                                                text = "${(uiState.config.pitchBlackRatio * 100f).roundToInt()}%",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Text(
                                                        text = stringResource(
                                                            R.string.pitch_black_active_now,
                                                            (previewBlackRatio * 100f).roundToInt()
                                                        ),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                    )
                                                }
                                                Text(
                                                    text = stringResource(
                                                        R.string.pitch_black_range_value,
                                                        (uiState.config.pitchBlackRangeMin * 100f).roundToInt(),
                                                        (uiState.config.pitchBlackRangeMax * 100f).roundToInt()
                                                    ),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }

                                    val segmentedColors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val segmentedBorder = SegmentedButtonDefaults.borderStroke(
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                                    )

                                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                        SegmentedButton(
                                            selected = uiState.config.pitchBlackMode == PitchBlackMode.SINGLE,
                                            onClick = { onSetPitchBlackMode(PitchBlackMode.SINGLE) },
                                            icon = {},
                                            colors = segmentedColors,
                                            border = segmentedBorder,
                                            shape = SegmentedButtonDefaults.itemShape(
                                                index = 0,
                                                count = 2,
                                                baseShape = CircleShape
                                            ),
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("btn_black_mode_single")
                                        ) {
                                            Text(
                                                text = stringResource(R.string.black_mode_single),
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                        SegmentedButton(
                                            selected = uiState.config.pitchBlackMode == PitchBlackMode.RANGE,
                                            onClick = { onSetPitchBlackMode(PitchBlackMode.RANGE) },
                                            icon = {},
                                            colors = segmentedColors,
                                            border = segmentedBorder,
                                            shape = SegmentedButtonDefaults.itemShape(
                                                index = 1,
                                                count = 2,
                                                baseShape = CircleShape
                                            ),
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("btn_black_mode_range")
                                        ) {
                                            Text(
                                                text = stringResource(R.string.black_mode_range),
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                    }

                                    val sliderColors = SliderDefaults.colors(
                                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    )

                                    if (uiState.config.pitchBlackMode == PitchBlackMode.SINGLE) {
                                        Slider(
                                            value = uiState.config.pitchBlackRatio,
                                            onValueChange = onPitchBlackRatioChange,
                                            valueRange = 0.0f..0.95f,
                                            colors = sliderColors,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("slider_pitch_black_ratio")
                                        )
                                    } else {
                                        val safeMin = uiState.config.pitchBlackRangeMin.coerceIn(0f, 0.95f)
                                        val safeMax = uiState.config.pitchBlackRangeMax.coerceIn(safeMin, 0.95f)
                                        RangeSlider(
                                            value = safeMin..safeMax,
                                            onValueChange = { range ->
                                                onPitchBlackRangeChange(range.start, range.endInclusive)
                                            },
                                            valueRange = 0.0f..0.95f,
                                            colors = sliderColors,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("range_slider_pitch_black")
                                        )
                                    }
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
                                )

                                // Patterns Navigation Row (1 full row, single column under True Black Ratio)
                                Surface(
                                    onClick = onOpenPatternsPage,
                                    color = Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 64.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag("pill_open_patterns")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.nav_patterns),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = patternsSummary,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
                                )

                                // Colors Navigation Row (1 full row, single column under Patterns)
                                Surface(
                                    onClick = onOpenPalettesPage,
                                    color = Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 64.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag("pill_open_palettes")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.nav_palettes),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = paletteSummary,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                uiState.stagedColors.take(4).forEach { argb ->
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(argb))
                                                    )
                                                }
                                            }

                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Auto-Change Wallpaper Section
                        AutoChangeSection(
                            config = uiState.config,
                            onToggleAutoRotate = onToggleAutoRotate,
                            onOpenAutoTargetDialog = onOpenAutoTargetDialog,
                            onOpenIntervalDialog = onOpenIntervalDialog,
                            onOpenAutoChangeHelp = onOpenAutoChangeHelp
                        )

                        // "Like the App?" Button Card at bottom part of bottom sheet
                        LikeAppButtonCard(onOpenLikeApp = onOpenLikeApp)

                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            // 4. Floating Pill Handle Positioned Above the Bottom Sheet (rests near bottom edge when sheet is completely hidden)
            val visibleSheetDp = with(density) { (sheetProgress.value * effectiveSheetHeightPx).toDp() }
            val handleBottomPadding = maxOf(navBarBottom + 8.dp, visibleSheetDp + 8.dp)

            FloatingBottomSheetHandle(
                isSheetOpen = sheetProgress.value >= 0.5f,
                onClick = {
                    if (sheetProgress.value >= 0.5f) {
                        animateSheetClosed()
                    } else {
                        animateSheetOpen()
                    }
                },
                onVerticalDragDelta = ::applyUpwardPullPx,
                onVerticalDragEnd = ::settleVerticalPull,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = handleBottomPadding)
            )
        }
    }
}

@Composable
private fun LikeAppButtonCard(
    onOpenLikeApp: () -> Unit
) {
    Surface(
        onClick = onOpenLikeApp,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("btn_like_the_app")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.btn_like_the_app),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
