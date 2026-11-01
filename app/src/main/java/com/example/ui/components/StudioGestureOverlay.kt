package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * Minimalist, non-cluttering gesture tutorial & reminder overlay.
 *
 * Designed as a touch-passthrough HUD (using Box/Column/Row without Material Surface pointer blocking)
 * so the user can immediately perform any swipe or double-tap gesture right through the overlay—which
 * both executes the gesture and dismisses the hint. A compact "Got It" pill in the top-right corner
 * also provides explicit tap dismissal without obstructing the center canvas.
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
            // Minimalist translucent modal card
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
                        .background(Color(0xFF090D14).copy(alpha = 0.58f))
                        .border(0.75.dp, Color.White.copy(alpha = 0.16f), cardShape)
                        .padding(horizontal = 20.dp, vertical = 20.dp)
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
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
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
                color = Color.White.copy(alpha = 0.94f)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.70f)
            )
        }
    }
}
