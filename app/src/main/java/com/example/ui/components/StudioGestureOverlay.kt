package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.StarterPalettes
import com.example.model.StudioGestureAction
import com.example.ui.StudioMiniHintState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val DefaultMiniHintColors = StarterPalettes.defaults.first().colors

/**
 * Pure zero-allocation vertical swipe dot motion curves (`SWIPE_DOWN` & `SWIPE_UP`):
 * 1. [0.00..0.22]: At starting vertical offset (`-dir * maxOffsetPx`), dots grow `o o o o -> O O O O`.
 * 2. [0.22..0.60]: While bold `O O O O`, dots glide smoothly across vertical offset to `+dir * maxOffsetPx`.
 * 3. [0.60..0.84]: At ending vertical offset (`+dir * maxOffsetPx`), dots settle `O O O O -> o o o o`.
 * 4. [0.84..1.00]: Softly dissolves out at ending offset and re-emerges at starting offset with zero jump.
 */
internal fun computeVerticalSwipeDotTranslationY(
    progress: Float,
    goingDown: Boolean,
    maxOffsetPx: Float
): Float {
    val t = progress.coerceIn(0f, 1f)
    val dir = if (goingDown) 1f else -1f
    val startY = -dir * maxOffsetPx
    val endY = dir * maxOffsetPx
    return when {
        t < 0.22f -> startY
        t < 0.60f -> {
            val p = (t - 0.22f) / 0.38f
            val ease = 0.5f * (1f - cos(p * PI.toFloat()))
            startY + (endY - startY) * ease
        }
        t < 0.92f -> endY
        else -> startY
    }
}

internal fun computeVerticalSwipeDotScale(progress: Float): Float {
    val t = progress.coerceIn(0f, 1f)
    return when {
        t < 0.22f -> {
            val p = t / 0.22f
            val ease = 0.5f * (1f - cos(p * PI.toFloat()))
            0.78f + (1.30f - 0.78f) * ease
        }
        t < 0.60f -> 1.30f
        t < 0.84f -> {
            val p = (t - 0.60f) / 0.24f
            val ease = 0.5f * (1f - cos(p * PI.toFloat()))
            1.30f - (1.30f - 0.78f) * ease
        }
        t < 0.92f -> {
            val p = (t - 0.84f) / 0.08f
            0.78f - 0.10f * p
        }
        else -> {
            val p = (t - 0.92f) / 0.08f
            0.68f + 0.10f * p
        }
    }
}

internal fun computeVerticalSwipeDotAlpha(progress: Float): Float {
    val t = progress.coerceIn(0f, 1f)
    return when {
        t < 0.22f -> {
            val p = t / 0.22f
            val ease = 0.5f * (1f - cos(p * PI.toFloat()))
            0.45f + (1.00f - 0.45f) * ease
        }
        t < 0.60f -> 1.00f
        t < 0.84f -> {
            val p = (t - 0.60f) / 0.24f
            val ease = 0.5f * (1f - cos(p * PI.toFloat()))
            1.00f - (1.00f - 0.42f) * ease
        }
        t < 0.92f -> {
            val p = (t - 0.84f) / 0.08f
            (0.42f * (1f - p)).coerceAtLeast(0f)
        }
        else -> {
            val p = (t - 0.92f) / 0.08f
            (0.45f * p).coerceIn(0f, 0.45f)
        }
    }
}

/**
 * Compact top mini hint displayed after 4 seconds of idle time on the Studio wallpaper preview
 * for gestures the user has not performed in over a week.
 *
 * Left-aligned full playful question + CTA (never cut off), right-aligned 4-dot palette-colored
 * gesture animation. Tapping the mini hint opens the full Hint Window.
 */
@Composable
fun StudioMiniHintBar(
    miniHint: StudioMiniHintState?,
    paletteColors: List<Long>,
    onOpenHintWindow: () -> Unit,
    modifier: Modifier = Modifier
) {
    var lastHint by remember { mutableStateOf(miniHint) }
    if (miniHint != null) {
        lastHint = miniHint
    }
    val openHintsCd = stringResource(R.string.cd_open_gesture_hints)

    AnimatedVisibility(
        visible = miniHint != null,
        enter = fadeIn(animationSpec = tween(durationMillis = 200)),
        exit = fadeOut(animationSpec = tween(durationMillis = 160)),
        modifier = modifier
    ) {
        val displayedHint = lastHint ?: return@AnimatedVisibility
        val pillShape = MaterialTheme.shapes.medium

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .clip(pillShape)
                .clickable(
                    role = Role.Button,
                    onClick = onOpenHintWindow
                )
                .semantics {
                    contentDescription = openHintsCd
                }
                .testTag("studio_mini_hint")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(pillShape)
                    .background(Color(0xFF0A0E17).copy(alpha = 0.84f))
                    .border(0.75.dp, Color.White.copy(alpha = 0.20f), pillShape)
                    .padding(horizontal = 13.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(displayedHint.messageRes),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        letterSpacing = 0.15.sp
                    ),
                    color = Color.White.copy(alpha = 0.94f),
                    textAlign = TextAlign.Start,
                    softWrap = true,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp)
                        .testTag("studio_mini_hint_text")
                )

                StudioMiniHintAnimatedIcons(
                    action = displayedHint.action,
                    paletteColors = paletteColors,
                    modifier = Modifier.testTag("studio_mini_hint_icons")
                )
            }
        }
    }
}

@Composable
private fun StudioMiniHintAnimatedIcons(
    action: StudioGestureAction,
    paletteColors: List<Long>,
    modifier: Modifier = Modifier
) {
    val safeColors = paletteColors.ifEmpty { DefaultMiniHintColors }
    val infiniteTransition = rememberInfiniteTransition(label = "mini_hint_anim")

    // Keep animation states unread in composition; read inside graphicsLayer for zero recompositions
    val waveOneWayState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mini_hint_wave_oneway"
    )

    val verticalGlideState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mini_hint_vertical_glide"
    )

    val doubleTapHeartState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mini_hint_double_tap_ms"
    )

    Row(
        modifier = modifier.heightIn(min = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(4) { index ->
            val swatchArgb = safeColors[index % safeColors.size]

            Icon(
                imageVector = Icons.Rounded.FiberManualRecord,
                contentDescription = null,
                tint = Color(swatchArgb),
                modifier = Modifier
                    .size(10.5.dp)
                    .graphicsLayer {
                        when (action) {
                            // Swipe Left: 4 dots wave right -> left (index 3 to 0)
                            StudioGestureAction.SWIPE_LEFT -> {
                                val activeSlot = 3.5f - waveOneWayState.value
                                val focus = (1f - abs(index.toFloat() - activeSlot)).coerceIn(0f, 1f)
                                val s = 0.85f + 0.45f * focus
                                scaleX = s
                                scaleY = s
                                translationY = 0f
                                alpha = 0.58f + 0.42f * focus
                            }

                            // Swipe Right: 4 dots wave left -> right (index 0 to 3)
                            StudioGestureAction.SWIPE_RIGHT -> {
                                val activeSlot = waveOneWayState.value - 0.5f
                                val focus = (1f - abs(index.toFloat() - activeSlot)).coerceIn(0f, 1f)
                                val s = 0.85f + 0.45f * focus
                                scaleX = s
                                scaleY = s
                                translationY = 0f
                                alpha = 0.58f + 0.42f * focus
                            }

                            // Swipe Down: 4 dots swell o->O at upper offset, glide down, settle O->o at lower offset
                            StudioGestureAction.SWIPE_DOWN -> {
                                val t = verticalGlideState.value
                                val s = computeVerticalSwipeDotScale(t)
                                scaleX = s
                                scaleY = s
                                translationY = computeVerticalSwipeDotTranslationY(
                                    progress = t,
                                    goingDown = true,
                                    maxOffsetPx = 3.2.dp.toPx()
                                )
                                alpha = computeVerticalSwipeDotAlpha(t)
                            }

                            // Swipe Up: 4 dots swell o->O at lower offset, glide up, settle O->o at upper offset
                            StudioGestureAction.SWIPE_UP -> {
                                val t = verticalGlideState.value
                                val s = computeVerticalSwipeDotScale(t)
                                scaleX = s
                                scaleY = s
                                translationY = computeVerticalSwipeDotTranslationY(
                                    progress = t,
                                    goingDown = false,
                                    maxOffsetPx = 3.2.dp.toPx()
                                )
                                alpha = computeVerticalSwipeDotAlpha(t)
                            }

                            // Double Tap: 4 dots size-heartbeat / double-beat pulse (tap tap . . . . tap tap . . . .)
                            StudioGestureAction.DOUBLE_TAP -> {
                                val ms = doubleTapHeartState.value
                                val beat1 = if (ms in 0f..150f) {
                                    sin(ms / 150f * PI.toFloat())
                                } else 0f
                                val beat2 = if (ms in 220f..370f) {
                                    sin((ms - 220f) / 150f * PI.toFloat())
                                } else 0f
                                val focus = maxOf(beat1, beat2)
                                val s = 0.85f + 0.48f * focus
                                scaleX = s
                                scaleY = s
                                translationY = 0f
                                alpha = 0.58f + 0.42f * focus
                            }
                        }
                    }
                    .testTag("studio_mini_hint_icon_$index")
            )
        }
    }
}

/**
 * Minimalist, darker translucent Hint Window without outer background darkening.
 *
 * Tapping anywhere outside or inside the card does not dismiss it; the user explicitly dismisses
 * it via the simple "Got It" TextButton at the bottom of the window.
 */
@Composable
fun StudioGestureOverlay(
    visible: Boolean,
    bottomClearance: Dp,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusBarTop = maxOf(WindowInsets.statusBars.asPaddingValues().calculateTopPadding(), 12.dp)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 220)),
        exit = fadeOut(animationSpec = tween(durationMillis = 180)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("gesture_tutorial_overlay")
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = statusBarTop + 40.dp,
                        bottom = bottomClearance,
                        start = 24.dp,
                        end = 24.dp
                    )
            ) {
                val cardShape = MaterialTheme.shapes.extraLarge
                Column(
                    modifier = Modifier
                        .widthIn(max = 316.dp)
                        .fillMaxWidth()
                        .clip(cardShape)
                        .background(Color(0xFF090D14).copy(alpha = 0.78f))
                        .border(0.75.dp, Color.White.copy(alpha = 0.18f), cardShape)
                        .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)
                        .testTag("gesture_tutorial_card"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GestureHintItemRow(
                        icon = Icons.Rounded.SwapHoriz,
                        title = stringResource(R.string.tutorial_swipe_horiz_title),
                        subtitle = stringResource(R.string.tutorial_swipe_horiz_desc)
                    )

                    GestureHintItemRow(
                        icon = Icons.Rounded.TouchApp,
                        title = stringResource(R.string.tutorial_double_tap_title),
                        subtitle = stringResource(R.string.tutorial_double_tap_desc)
                    )

                    GestureHintItemRow(
                        icon = Icons.Rounded.KeyboardArrowUp,
                        title = stringResource(R.string.tutorial_swipe_up_title),
                        subtitle = stringResource(R.string.tutorial_swipe_up_desc)
                    )

                    GestureHintItemRow(
                        icon = Icons.Rounded.KeyboardArrowDown,
                        title = stringResource(R.string.tutorial_swipe_down_title),
                        subtitle = stringResource(R.string.tutorial_swipe_down_desc)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("btn_dismiss_tutorial")
                        ) {
                            Text(
                                text = stringResource(R.string.btn_dismiss_tutorial),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GestureHintItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.95f)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f)
            )
        }
    }
}
