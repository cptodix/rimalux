package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ColorFillMode
import com.example.model.PaletteMode
import com.example.model.WallpaperTarget
import com.example.ui.VectraUiState
import com.example.ui.components.TargetSegmentedBar
import com.example.ui.components.VectorWallpaperPreviewCard
import kotlin.math.roundToInt

@Composable
fun StudioScreen(
    uiState: VectraUiState,
    showInlinePreview: Boolean,
    onGenerateNew: () -> Unit,
    onApplyNow: () -> Unit,
    onCycleMockup: () -> Unit,
    onOpenFullScreen: () -> Unit,
    onPitchBlackRatioChange: (Float) -> Unit,
    onManualTargetChange: (WallpaperTarget) -> Unit,
    onAutoApplyChange: (Boolean) -> Unit,
    onOpenStylesTab: () -> Unit,
    onOpenPalettesTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stylesSummary = if (uiState.config.stylesRandomized) {
        stringResource(R.string.summary_styles_random)
    } else {
        val names = uiState.config.selectedStyles.take(2)
            .map { stringResource(it.titleRes).substringBefore(" ") }
            .joinToString(", ")
        val suffix = if (uiState.config.selectedStyles.size > 2) {
            " +${uiState.config.selectedStyles.size - 2}"
        } else ""
        stringResource(R.string.summary_styles_selected, "$names$suffix")
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .testTag("home_screen_scroll"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (showInlinePreview) {
            VectorWallpaperPreviewCard(
                scene = uiState.currentScene,
                paletteColors = uiState.activeColors,
                pitchBlackRatio = uiState.config.pitchBlackRatio,
                colorFillMode = uiState.config.colorFillMode,
                mockupMode = uiState.mockupMode,
                onCycleMockup = onCycleMockup,
                onOpenFullScreen = onOpenFullScreen,
                previewAspectRatio = 0.98f
            )
        }

        // Quick Minimalist Summary Pills (Styles & Colors/Shading)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenStylesTab)
                    .testTag("pill_open_styles")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stylesSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenPalettesTab)
                    .testTag("pill_open_palettes")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        uiState.activeColors.take(3).forEach { argb ->
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                            )
                        }
                    }
                    Text(
                        text = paletteSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Clean Pitch-Black Ratio Slider
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color.Black)
                                .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                        )
                        Text(
                            text = stringResource(R.string.label_pitch_black_ratio),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "${(uiState.config.pitchBlackRatio * 100f).roundToInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = uiState.config.pitchBlackRatio,
                    onValueChange = onPitchBlackRatioChange,
                    valueRange = 0.0f..0.95f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("slider_pitch_black_ratio")
                )
            }
        }

        // Manual Target (Wallpaper / Lock Screen / Both) + Primary Action Buttons
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TargetSegmentedBar(
                    selectedTarget = uiState.config.manualTarget,
                    onTargetSelected = onManualTargetChange,
                    tagPrefix = "manual_target"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onGenerateNew,
                        enabled = !uiState.isApplyingWallpaper,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("btn_generate_wallpaper")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Casino,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_generate),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Button(
                        onClick = onApplyNow,
                        enabled = !uiState.isApplyingWallpaper,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                        modifier = Modifier
                            .weight(1.15f)
                            .minimumInteractiveComponentSize()
                            .testTag("btn_apply_wallpaper")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wallpaper,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.isApplyingWallpaper) {
                                stringResource(R.string.btn_applying)
                            } else {
                                stringResource(R.string.btn_set_wallpaper)
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onAutoApplyChange(!uiState.config.autoApplyOnGenerate) }
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.toggle_auto_apply_on_generate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = uiState.config.autoApplyOnGenerate,
                        onCheckedChange = onAutoApplyChange,
                        modifier = Modifier.testTag("switch_auto_apply")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}
