package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ColorFillMode
import com.example.model.ColorPalette
import com.example.model.PaletteMode
import com.example.ui.VectraUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PalettesScreen(
    uiState: VectraUiState,
    onSetColorFillMode: (ColorFillMode) -> Unit,
    onSetPaletteMode: (PaletteMode) -> Unit,
    onShuffleRandomColors: () -> Unit,
    onSaveRandomToList: () -> Unit,
    onTogglePaletteInList: (ColorPalette) -> Unit,
    onOpenCreatePalette: () -> Unit,
    onDeletePalette: (String) -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToHome)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("palettes_screen_list"),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "palettes_header") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.palettes_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.palettes_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Solid Colors vs Gradients Selector Card
        item(key = "color_shading_card") {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.label_color_shading),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = uiState.config.colorFillMode == ColorFillMode.SOLID,
                            onClick = { onSetColorFillMode(ColorFillMode.SOLID) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("btn_fill_mode_solid")
                        ) {
                            Text(stringResource(R.string.fill_mode_solid))
                        }
                        SegmentedButton(
                            selected = uiState.config.colorFillMode == ColorFillMode.GRADIENT,
                            onClick = { onSetColorFillMode(ColorFillMode.GRADIENT) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("btn_fill_mode_gradient")
                        ) {
                            Text(stringResource(R.string.fill_mode_gradient))
                        }
                    }
                }
            }
        }

        // Randomized vs Palette List Segmented Mode Switch
        item(key = "palette_mode_selector") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = uiState.config.paletteMode == PaletteMode.RANDOMIZED,
                    onClick = { onSetPaletteMode(PaletteMode.RANDOMIZED) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("btn_palette_mode_random")
                ) {
                    Text(stringResource(R.string.palette_mode_random))
                }
                SegmentedButton(
                    selected = uiState.config.paletteMode == PaletteMode.PALETTE_LIST,
                    onClick = { onSetPaletteMode(PaletteMode.PALETTE_LIST) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("btn_palette_mode_list")
                ) {
                    Text(stringResource(R.string.palette_mode_list))
                }
            }
        }

        if (uiState.config.paletteMode == PaletteMode.RANDOMIZED) {
            item(key = "randomized_palette_card") {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.random_palette_card_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.random_palette_card_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        SwatchBarWithVoid(
                            colors = uiState.activeColors,
                            pitchBlackRatio = uiState.config.pitchBlackRatio,
                            colorFillMode = uiState.config.colorFillMode
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onShuffleRandomColors,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_shuffle_colors")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Casino,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.btn_shuffle_colors))
                            }

                            FilledTonalButton(
                                onClick = onSaveRandomToList,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_save_random_to_list")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BookmarkAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.btn_save_random_to_list))
                            }
                        }
                    }
                }
            }
        }

        // User's Palette List Section Header
        item(key = "palette_list_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.palette_list_hint,
                        if (uiState.config.paletteMode == PaletteMode.PALETTE_LIST) {
                            uiState.config.selectedPaletteIds.size
                        } else 0
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(12.dp))

                FilledTonalButton(
                    onClick = onOpenCreatePalette,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("btn_open_create_palette")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_add_palette))
                }
            }
        }

        items(
            items = uiState.userPalettes,
            key = { it.id }
        ) { palette ->
            val isSelected = uiState.config.paletteMode == PaletteMode.PALETTE_LIST &&
                palette.id in uiState.config.selectedPaletteIds
            PaletteListItemCard(
                palette = palette,
                isSelected = isSelected,
                pitchBlackRatio = uiState.config.pitchBlackRatio,
                colorFillMode = uiState.config.colorFillMode,
                canDelete = uiState.userPalettes.size > 1,
                onClick = { onTogglePaletteInList(palette) },
                onDelete = { onDeletePalette(palette.id) }
            )
        }
    }
}

@Composable
private fun PaletteListItemCard(
    palette: ColorPalette,
    isSelected: Boolean,
    pitchBlackRatio: Float,
    colorFillMode: ColorFillMode,
    canDelete: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (isSelected) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .testTag("palette_card_${palette.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = palette.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (canDelete) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("delete_palette_${palette.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.cd_delete_palette),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SwatchBarWithVoid(
                colors = palette.colors,
                pitchBlackRatio = pitchBlackRatio,
                colorFillMode = colorFillMode
            )
        }
    }
}

@Composable
private fun SwatchBarWithVoid(
    colors: List<Long>,
    pitchBlackRatio: Float,
    colorFillMode: ColorFillMode
) {
    val blackShare = pitchBlackRatio.coerceIn(0.14f, 0.70f)
    val safeColors = colors.ifEmpty { listOf(0xFF00F5D4L) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
    ) {
        Box(
            modifier = Modifier
                .weight(blackShare)
                .fillMaxHeight()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "#000",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.65f)
            )
        }

        if (colorFillMode == ColorFillMode.GRADIENT && safeColors.size >= 2) {
            Box(
                modifier = Modifier
                    .weight(1f - blackShare)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            colors = safeColors.map { Color(it) }
                        )
                    )
            )
        } else {
            val colorShare = ((1f - blackShare) / safeColors.size).coerceAtLeast(0.04f)
            safeColors.forEach { argb ->
                Box(
                    modifier = Modifier
                        .weight(colorShare)
                        .fillMaxHeight()
                        .background(Color(argb))
                )
            }
        }
    }
}
