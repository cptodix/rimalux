package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.engine.VectorScene
import com.example.engine.WallpaperRenderer.drawVectorScene
import com.example.model.ColorFillMode
import com.example.model.MockupOverlayMode
import com.example.model.WallpaperTarget
import com.example.ui.theme.SpaceGroteskFontFamily

@Composable
fun VectorWallpaperPreviewCard(
    scene: VectorScene,
    paletteColors: List<Long>,
    pitchBlackRatio: Float,
    colorFillMode: ColorFillMode,
    mockupMode: MockupOverlayMode,
    onCycleMockup: () -> Unit,
    onOpenFullScreen: () -> Unit,
    modifier: Modifier = Modifier,
    previewAspectRatio: Float = 0.92f
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(previewAspectRatio)
            .clip(RoundedCornerShape(26.dp))
            .background(Color.Black)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                shape = RoundedCornerShape(26.dp)
            )
            .clickable(onClick = onOpenFullScreen)
            .testTag("vector_preview_canvas")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawVectorScene(
                scene = scene,
                paletteColors = paletteColors,
                pitchBlackRatio = pitchBlackRatio,
                colorFillMode = colorFillMode
            )
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = mockupMode == MockupOverlayMode.LOCK_SCREEN,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            LockScreenOverlayMockup()
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = mockupMode == MockupOverlayMode.HOME_GRID,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            HomeGridOverlayMockup()
        }

        // Subtle active style tag at bottom-left
        Surface(
            color = Color.Black.copy(alpha = 0.58f),
            shape = CircleShape,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Text(
                text = stringResource(scene.style.titleRes),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.88f),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }

        // Minimalist corner icons
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = onCycleMockup,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.62f),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("btn_mockup_cycle")
            ) {
                Icon(
                    imageVector = when (mockupMode) {
                        MockupOverlayMode.NONE -> Icons.Outlined.Smartphone
                        MockupOverlayMode.LOCK_SCREEN -> Icons.Outlined.Lock
                        MockupOverlayMode.HOME_GRID -> Icons.Outlined.GridView
                    },
                    contentDescription = stringResource(R.string.cd_mockup_toggle),
                    modifier = Modifier.size(18.dp)
                )
            }

            FilledTonalIconButton(
                onClick = onOpenFullScreen,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.62f),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("btn_fullscreen_preview")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Fullscreen,
                    contentDescription = stringResource(R.string.cd_fullscreen_preview),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetSegmentedBar(
    selectedTarget: WallpaperTarget,
    onTargetSelected: (WallpaperTarget) -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier
) {
    val targets = WallpaperTarget.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        targets.forEachIndexed { index, target ->
            SegmentedButton(
                selected = selectedTarget == target,
                onClick = { onTargetSelected(target) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = targets.size),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("${tagPrefix}_${target.id}")
            ) {
                Text(
                    text = stringResource(target.labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun LockScreenOverlayMockup() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.38f),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "09:41",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 38.sp,
                    color = Color.White,
                    letterSpacing = (-1).sp
                )
                Text(
                    text = "WED · OCT 28",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun HomeGridOverlayMockup() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 34.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        repeat(2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.20f))
                            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
fun FullScreenOledPreviewDialog(
    scene: VectorScene,
    paletteColors: List<Long>,
    pitchBlackRatio: Float,
    colorFillMode: ColorFillMode,
    isApplying: Boolean,
    onRandomize: () -> Unit,
    onApplyNow: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler(onBack = onDismiss)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("fullscreen_oled_dialog")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawVectorScene(
                    scene = scene,
                    paletteColors = paletteColors,
                    pitchBlackRatio = pitchBlackRatio,
                    colorFillMode = colorFillMode
                )
            }

            FilledTonalIconButton(
                onClick = onDismiss,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.70f),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("btn_close_fullscreen")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_close_fullscreen)
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.80f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = onRandomize,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Casino,
                            contentDescription = stringResource(R.string.btn_generate)
                        )
                    }
                    Button(
                        onClick = onApplyNow,
                        enabled = !isApplying,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wallpaper,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isApplying) {
                                stringResource(R.string.btn_applying)
                            } else {
                                stringResource(R.string.btn_set_wallpaper)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MiniVectorThumbnail(
    scene: VectorScene,
    paletteColors: List<Long>,
    pitchBlackRatio: Float,
    colorFillMode: ColorFillMode,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
    ) {
        drawVectorScene(
            scene = scene,
            paletteColors = paletteColors,
            pitchBlackRatio = pitchBlackRatio,
            colorFillMode = colorFillMode
        )
    }
}
