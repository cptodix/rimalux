package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.MathPatternGenerator
import com.example.model.ArtStyle
import com.example.model.ColorFillMode
import com.example.ui.VectraUiState
import com.example.ui.components.MiniVectorThumbnail
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StylesScreen(
    uiState: VectraUiState,
    onSetRandomized: (Boolean) -> Unit,
    onToggleStyle: (ArtStyle) -> Unit,
    onIntricacyChange: (Float) -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToHome)

    val styleRows = remember { ArtStyle.entries.chunked(2) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("styles_screen_list"),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "styles_header") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.styles_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.styles_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Randomized vs Multi-Select Segmented Bar
        item(key = "styles_mode_bar") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = uiState.config.stylesRandomized,
                    onClick = { onSetRandomized(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("btn_styles_random")
                ) {
                    Text(stringResource(R.string.mode_randomized))
                }
                SegmentedButton(
                    selected = !uiState.config.stylesRandomized,
                    onClick = { onSetRandomized(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("btn_styles_multiselect")
                ) {
                    Text(stringResource(R.string.mode_multiselect))
                }
            }
        }

        // Detail & Intricacy Slider
        item(key = "intricacy_card") {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.label_intricacy),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${(uiState.config.intricacy * 100f).roundToInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = uiState.config.intricacy,
                        onValueChange = onIntricacyChange,
                        valueRange = 0.15f..1.0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("slider_intricacy")
                    )
                }
            }
        }

        item(key = "styles_hint") {
            Text(
                text = if (uiState.config.stylesRandomized) {
                    stringResource(R.string.styles_random_active_hint)
                } else {
                    stringResource(R.string.styles_multiselect_hint, uiState.config.selectedStyles.size)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 2-Column Gallery of all 16 Math Art Styles
        items(
            items = styleRows,
            key = { row -> row.joinToString("_") { it.id } }
        ) { rowStyles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowStyles.forEach { style ->
                    val isSelected = !uiState.config.stylesRandomized &&
                        style in uiState.config.selectedStyles
                    val isCurrentlyRendered = uiState.currentScene.style == style
                    StyleGalleryCard(
                        style = style,
                        isSelected = isSelected,
                        isCurrentlyRendered = isCurrentlyRendered,
                        paletteColors = uiState.activeColors,
                        colorFillMode = uiState.config.colorFillMode,
                        onClick = { onToggleStyle(style) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowStyles.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StyleGalleryCard(
    style: ArtStyle,
    isSelected: Boolean,
    isCurrentlyRendered: Boolean,
    paletteColors: List<Long>,
    colorFillMode: ColorFillMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val previewScene = remember(style) {
        val showcaseSeed = 0x243F6A8885A3L xor (style.ordinal.toLong() * 0x9E3779B9L)
        MathPatternGenerator.generate(
            seed = showcaseSeed,
            style = style,
            intricacy = 0.56f,
            aspectRatio = 0.65f
        )
    }

    val highlighted = isSelected || isCurrentlyRendered
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (isSelected) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            width = if (highlighted) 2.dp else 1.dp,
            color = when {
                isSelected -> MaterialTheme.colorScheme.primary
                isCurrentlyRendered -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f)
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            }
        ),
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .testTag("style_card_${style.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                MiniVectorThumbnail(
                    scene = previewScene,
                    paletteColors = paletteColors,
                    pitchBlackRatio = 0.52f,
                    colorFillMode = colorFillMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                )

                if (isSelected) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.72f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(2.dp)
                                .size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(style.titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(style.subtitleRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
