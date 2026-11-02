package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.MockupOverlayMode
import com.example.model.WallpaperTarget
import com.example.model.titleRes
import com.example.ui.RenderedPreviewSnapshot
import kotlin.math.abs
import kotlin.math.tanh
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val SmoothDeckSwipeEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)

private enum class DeckGestureAxis {
    UNDECIDED,
    HORIZONTAL,
    VERTICAL_UP,
    VERTICAL_DOWN
}

private data class SettledDeckCommit(
    val previousSeed: Long?,
    val previousWasGenerating: Boolean,
    val targetPreview: RenderedPreviewSnapshot?,
    val targetIsGenerating: Boolean
)

@Composable
fun SwipeableWallpaperDeck(
    currentPreview: RenderedPreviewSnapshot?,
    previousPreview: RenderedPreviewSnapshot?,
    nextPreview: RenderedPreviewSnapshot?,
    canSwipeRight: Boolean,
    isGeneratingCurrent: Boolean,
    mockupMode: MockupOverlayMode,
    bottomOverlayClearance: Dp,
    topOverlayClearance: Dp = 0.dp,
    gesturesEnabled: Boolean,
    onSwipeLeftCommitted: () -> Boolean,
    onSwipeRightCommitted: () -> Boolean,
    onRightEdgeBounce: () -> Unit,
    onDoubleTap: () -> Unit,
    onSingleTap: () -> Unit = {},
    onSwipeDown: () -> Unit = {},
    onUserGestureStarted: () -> Unit = {},
    onVerticalSwipeUpDelta: (Float) -> Unit,
    onVerticalSwipeUpEnd: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val dragOffsetX = remember { Animatable(0f) }
    var containerWidthPx by remember { mutableIntStateOf(1080) }
    var isCommittingSlide by remember { mutableStateOf(false) }
    var slideAnimJob by remember { mutableStateOf<Job?>(null) }
    var settledCommit by remember { mutableStateOf<SettledDeckCommit?>(null) }

    val isHoldingCommittedTarget = settledCommit?.let { hold ->
        currentPreview?.seed == hold.previousSeed &&
            (currentPreview == null && isGeneratingCurrent) == hold.previousWasGenerating
    } == true

    val displayCurrentPreview = if (isHoldingCommittedTarget) {
        settledCommit?.targetPreview
    } else {
        currentPreview
    }
    val displayIsGeneratingCurrent = if (isHoldingCommittedTarget) {
        settledCommit?.targetIsGenerating == true
    } else {
        currentPreview == null && isGeneratingCurrent
    }

    LaunchedEffect(currentPreview?.seed, isGeneratingCurrent) {
        val hold = settledCommit
        if (hold != null) {
            val stillStale = currentPreview?.seed == hold.previousSeed &&
                (currentPreview == null && isGeneratingCurrent) == hold.previousWasGenerating
            if (!stillStale) {
                settledCommit = null
            }
        }
    }

    val latestCurrentPreview by rememberUpdatedState(currentPreview)
    val latestPreviousPreview by rememberUpdatedState(previousPreview)
    val latestNextPreview by rememberUpdatedState(nextPreview)
    val latestIsGeneratingCurrent by rememberUpdatedState(isGeneratingCurrent)
    val latestCanSwipeRight by rememberUpdatedState(canSwipeRight)
    val latestIsGeneratingEmpty by rememberUpdatedState(displayCurrentPreview == null && displayIsGeneratingCurrent)
    val latestGesturesEnabled by rememberUpdatedState(gesturesEnabled)
    val latestOnSwipeLeft by rememberUpdatedState(onSwipeLeftCommitted)
    val latestOnSwipeRight by rememberUpdatedState(onSwipeRightCommitted)
    val latestOnBounce by rememberUpdatedState(onRightEdgeBounce)
    val latestOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val latestOnSingleTap by rememberUpdatedState(onSingleTap)
    val latestOnSwipeDown by rememberUpdatedState(onSwipeDown)
    val latestOnGestureStarted by rememberUpdatedState(onUserGestureStarted)
    val latestOnVerticalDelta by rememberUpdatedState(onVerticalSwipeUpDelta)
    val latestOnVerticalEnd by rememberUpdatedState(onVerticalSwipeUpEnd)

    val safeWidthPx = containerWidthPx.coerceAtLeast(1).toFloat()
    val showPreviousSlide by remember(canSwipeRight) {
        derivedStateOf { canSwipeRight && dragOffsetX.value > 0.5f }
    }
    val showNextSlide by remember {
        derivedStateOf { dragOffsetX.value < -0.5f }
    }
    val showRightBounceRim by remember(canSwipeRight) {
        derivedStateOf { !canSwipeRight && dragOffsetX.value > 1f }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { size ->
                if (size.width > 0) {
                    containerWidthPx = size.width
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (latestGesturesEnabled && !isCommittingSlide) {
                            latestOnSingleTap()
                        }
                    },
                    onDoubleTap = {
                        if (latestGesturesEnabled && !isCommittingSlide) {
                            latestOnDoubleTap()
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!latestGesturesEnabled || isCommittingSlide) return@awaitEachGesture

                    slideAnimJob?.cancel()
                    val touchSlop = viewConfiguration.touchSlop
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)

                    var axis = DeckGestureAxis.UNDECIDED
                    var accumulatedDx = 0f
                    var accumulatedDy = 0f
                    var rawHorizontalDrag = dragOffsetX.value
                    var pointerId = down.id

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId }
                            ?: event.changes.firstOrNull()
                            ?: break
                        pointerId = change.id

                        if (!change.pressed) {
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            break
                        }

                        val delta = change.positionChange()
                        if (delta.x == 0f && delta.y == 0f) continue
                        velocityTracker.addPosition(change.uptimeMillis, change.position)

                        if (axis == DeckGestureAxis.UNDECIDED) {
                            accumulatedDx += delta.x
                            accumulatedDy += delta.y
                            val absX = abs(accumulatedDx)
                            val absY = abs(accumulatedDy)
                            val upwardSlopPx = (touchSlop * 0.45f).coerceIn(6f, 16f)

                            if (accumulatedDy < -upwardSlopPx && absY >= absX * 0.78f) {
                                axis = DeckGestureAxis.VERTICAL_UP
                                change.consume()
                                latestOnGestureStarted()
                                latestOnVerticalDelta(-accumulatedDy)
                                continue
                            } else if (absX > touchSlop && absX > absY) {
                                axis = DeckGestureAxis.HORIZONTAL
                                rawHorizontalDrag = dragOffsetX.value + accumulatedDx
                                change.consume()
                                latestOnGestureStarted()
                            } else if (accumulatedDy > touchSlop && absY > absX) {
                                axis = DeckGestureAxis.VERTICAL_DOWN
                                change.consume()
                                latestOnGestureStarted()
                                continue
                            } else {
                                continue
                            }
                        } else if (axis == DeckGestureAxis.HORIZONTAL) {
                            rawHorizontalDrag += delta.x
                            change.consume()
                        } else if (axis == DeckGestureAxis.VERTICAL_UP) {
                            change.consume()
                            latestOnVerticalDelta(-delta.y)
                            continue
                        } else if (axis == DeckGestureAxis.VERTICAL_DOWN) {
                            change.consume()
                            continue
                        }

                        if (axis == DeckGestureAxis.HORIZONTAL) {
                            val widthF = size.width.coerceAtLeast(1).toFloat()
                            val mappedX = when {
                                // Rubber-band resistance when swiping right with no recent wallpaper
                                rawHorizontalDrag > 0f && !latestCanSwipeRight -> {
                                    val maxPull = widthF * 0.22f
                                    maxPull * tanh(rawHorizontalDrag / (widthF * 0.36f))
                                }
                                // Gentle resistance if already waiting on an empty generating slot
                                rawHorizontalDrag < 0f && latestIsGeneratingEmpty -> {
                                    val maxPull = widthF * 0.16f
                                    -maxPull * tanh(-rawHorizontalDrag / (widthF * 0.36f))
                                }
                                else -> rawHorizontalDrag.coerceIn(-widthF, widthF)
                            }
                            coroutineScope.launch {
                                dragOffsetX.snapTo(mappedX)
                            }
                        }
                    }

                    val velocity = velocityTracker.calculateVelocity()
                    when (axis) {
                        DeckGestureAxis.VERTICAL_UP -> {
                            latestOnVerticalEnd(velocity.y)
                        }

                        DeckGestureAxis.VERTICAL_DOWN -> {
                            latestOnSwipeDown()
                        }

                        DeckGestureAxis.HORIZONTAL -> {
                            val widthF = size.width.coerceAtLeast(1).toFloat()
                            val commitDist = widthF * 0.20f
                            val commitVel = 560f
                            val shouldBounceRight =
                                (rawHorizontalDrag > touchSlop || velocity.x > commitVel) && !latestCanSwipeRight
                            val shouldCommitSwipeLeft = !shouldBounceRight && !latestIsGeneratingEmpty &&
                                (rawHorizontalDrag <= -commitDist || (rawHorizontalDrag < -touchSlop && velocity.x <= -commitVel))
                            val shouldCommitSwipeRight = !shouldBounceRight && latestCanSwipeRight &&
                                (rawHorizontalDrag >= commitDist || (rawHorizontalDrag > touchSlop && velocity.x >= commitVel))

                            if (shouldCommitSwipeLeft || shouldCommitSwipeRight) {
                                isCommittingSlide = true
                            }

                            slideAnimJob = coroutineScope.launch {
                                when {
                                    // Swipe Right when out of recent -> elastic bounce!
                                    shouldBounceRight -> {
                                        latestOnBounce()
                                        if (dragOffsetX.value < widthF * 0.08f && velocity.x > 360f) {
                                            dragOffsetX.animateTo(
                                                targetValue = widthF * 0.13f,
                                                animationSpec = tween(durationMillis = 95, easing = FastOutSlowInEasing)
                                            )
                                        }
                                        dragOffsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = 0.45f,
                                                stiffness = 380f
                                            )
                                        )
                                    }

                                    // Swipe Left -> commit to next wallpaper (smooth, controlled 360ms slide)
                                    shouldCommitSwipeLeft -> {
                                        try {
                                            dragOffsetX.animateTo(
                                                targetValue = -widthF,
                                                animationSpec = tween(
                                                    durationMillis = 360,
                                                    easing = SmoothDeckSwipeEasing
                                                )
                                            )
                                            val prevPreview = latestCurrentPreview
                                            val prevWasGenerating = prevPreview == null && latestIsGeneratingCurrent
                                            val targetSnapshot = latestNextPreview
                                            val committed = latestOnSwipeLeft()
                                            if (committed) {
                                                settledCommit = SettledDeckCommit(
                                                    previousSeed = prevPreview?.seed,
                                                    previousWasGenerating = prevWasGenerating,
                                                    targetPreview = targetSnapshot,
                                                    targetIsGenerating = targetSnapshot == null
                                                )
                                            }
                                            dragOffsetX.snapTo(0f)
                                        } finally {
                                            isCommittingSlide = false
                                        }
                                    }

                                    // Swipe Right -> commit to previous recent wallpaper (smooth 360ms slide)
                                    shouldCommitSwipeRight -> {
                                        try {
                                            dragOffsetX.animateTo(
                                                targetValue = widthF,
                                                animationSpec = tween(
                                                    durationMillis = 360,
                                                    easing = SmoothDeckSwipeEasing
                                                )
                                            )
                                            val prevPreview = latestCurrentPreview
                                            val prevWasGenerating = prevPreview == null && latestIsGeneratingCurrent
                                            val targetSnapshot = latestPreviousPreview
                                            val committed = latestOnSwipeRight()
                                            if (committed) {
                                                settledCommit = SettledDeckCommit(
                                                    previousSeed = prevPreview?.seed,
                                                    previousWasGenerating = prevWasGenerating,
                                                    targetPreview = targetSnapshot,
                                                    targetIsGenerating = targetSnapshot == null
                                                )
                                            }
                                            dragOffsetX.snapTo(0f)
                                        } finally {
                                            isCommittingSlide = false
                                        }
                                    }

                                    // Cancelled partial drag -> smooth settle back to center
                                    else -> {
                                        dragOffsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = 0.86f,
                                                stiffness = 460f
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        DeckGestureAxis.UNDECIDED -> Unit
                    }
                }
            }
            .testTag("vector_preview_canvas")
    ) {
        // Incoming Previous Page (underneath on left when dragging right with available history)
        if (showPreviousSlide) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val shift = dragOffsetX.value
                        val revealFraction = (shift / safeWidthPx).coerceIn(0f, 1f)
                        translationX = -safeWidthPx * 0.36f * (1f - revealFraction)
                        alpha = 0.62f + 0.38f * revealFraction
                    }
            ) {
                WallpaperSlideSurface(
                    preview = previousPreview,
                    isGenerating = previousPreview == null,
                    exposeLoadingTag = false
                )
            }
        }

        // Active Current Page
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val shift = dragOffsetX.value
                    translationX = if (shift < 0f) shift * 0.42f else shift
                    alpha = if (shift < 0f) {
                        val outFrac = (-shift / safeWidthPx).coerceIn(0f, 1f)
                        1f - 0.36f * outFrac
                    } else {
                        1f
                    }
                }
        ) {
            WallpaperSlideSurface(
                preview = displayCurrentPreview,
                isGenerating = displayIsGeneratingCurrent,
                exposeLoadingTag = true
            )
        }

        // Incoming Next Page (slides over from right edge when swiping left)
        if (showNextSlide) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = safeWidthPx + dragOffsetX.value
                    }
            ) {
                WallpaperSlideSurface(
                    preview = nextPreview,
                    isGenerating = nextPreview == null,
                    exposeLoadingTag = false
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(Color.White.copy(alpha = 0.10f))
                )
            }
        }

        // Subtle left-edge elastic rim glow during right-edge bounce
        if (showRightBounceRim) {
            val primaryColor = MaterialTheme.colorScheme.primary
            val bounceRimBrush = remember(primaryColor) {
                Brush.horizontalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.24f),
                        Color.Transparent
                    )
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(44.dp)
                    .graphicsLayer {
                        alpha = (dragOffsetX.value / (safeWidthPx * 0.22f)).coerceIn(0f, 1f)
                    }
                    .background(bounceRimBrush)
            )
        }

        // Optional screen mockup overlays
        AnimatedVisibility(
            visible = mockupMode == MockupOverlayMode.LOCK_SCREEN,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            LockScreenOverlayMockup(
                bottomClearance = bottomOverlayClearance,
                topClearance = topOverlayClearance
            )
        }

        AnimatedVisibility(
            visible = mockupMode == MockupOverlayMode.HOME_GRID,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            HomeGridOverlayMockup(
                bottomClearance = bottomOverlayClearance,
                topClearance = topOverlayClearance
            )
        }
    }
}

@Composable
private fun WallpaperSlideSurface(
    preview: RenderedPreviewSnapshot?,
    isGenerating: Boolean,
    exposeLoadingTag: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (preview != null) {
            Image(
                bitmap = preview.imageBitmap,
                contentDescription = stringResource(preview.pattern.titleRes),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (exposeLoadingTag) {
                            Modifier.testTag("active_wallpaper_seed_${preview.seed}")
                        } else {
                            Modifier
                        }
                    )
            )
        } else if (isGenerating) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .then(
                        if (exposeLoadingTag) {
                            Modifier.testTag("wallpaper_generating_indicator")
                        } else {
                            Modifier
                        }
                    )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = stringResource(R.string.status_loading_wallpaper),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }
            }
        }
    }
}

@Composable
fun SetWallpaperTargetDialog(
    selectedTarget: WallpaperTarget? = null,
    onSelectTargetAndApply: (WallpaperTarget) -> Unit,
    onSaveToDevice: () -> Unit = {},
    onShareWallpaper: () -> Unit = {},
    onDismiss: () -> Unit
) {
    RimaluxModalDialog(
        dialogTestTag = "dialog_set_wallpaper_target",
        onDismiss = onDismiss
    ) {
        Text(
            text = stringResource(R.string.dialog_set_wallpaper_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            WallpaperTarget.entries.forEach { target ->
                val isCurrent = selectedTarget != null && selectedTarget == target
                Surface(
                    selected = isCurrent,
                    onClick = { onSelectTargetAndApply(target) },
                    shape = MaterialTheme.shapes.medium,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    border = if (isCurrent) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
                    } else {
                        BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("manual_target_${target.id}")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = stringResource(target.labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )

            DialogExportActionButton(
                label = stringResource(R.string.btn_save_to_device),
                icon = Icons.Rounded.Download,
                onClick = onSaveToDevice,
                modifier = Modifier.testTag("btn_save_to_device")
            )

            DialogExportActionButton(
                label = stringResource(R.string.btn_share_wallpaper),
                icon = Icons.Rounded.Share,
                onClick = onShareWallpaper,
                modifier = Modifier.testTag("btn_share_wallpaper")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onDismiss,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("btn_cancel_set_wallpaper")
            ) {
                Text(
                    text = stringResource(R.string.btn_cancel),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun DialogExportActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = 0.75.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .minimumInteractiveComponentSize()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
