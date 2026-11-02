package com.cptodix.rimalux.oled.wallpaper.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cptodix.rimalux.oled.wallpaper.R
import com.cptodix.rimalux.oled.wallpaper.model.ColorFillMode
import com.cptodix.rimalux.oled.wallpaper.model.ColorPalette
import com.cptodix.rimalux.oled.wallpaper.model.PaletteMode
import com.cptodix.rimalux.oled.wallpaper.model.resolveDisplayName
import com.cptodix.rimalux.oled.wallpaper.ui.RimaluxUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PalettesScreen(
    uiState: RimaluxUiState,
    onSetColorFillMode: (ColorFillMode) -> Unit,
    onSetPaletteMode: (PaletteMode) -> Unit,
    onSelectAllPalettes: () -> Unit,
    onSelectNonePalettes: () -> Unit,
    onTogglePaletteInList: (ColorPalette) -> Unit,
    onOpenCreatePalette: () -> Unit,
    onDeletePalette: (String) -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToHome)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val cardSegmentedColors = SegmentedButtonDefaults.colors(
        activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        activeBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
        inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f)
    )

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.palettes_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackToHome,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_back_to_home")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
                    .testTag("palettes_screen_list"),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 4.dp,
                    bottom = innerPadding.calculateBottomPadding() + 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item(key = "palettes_subtitle") {
                    Text(
                        text = stringResource(R.string.palettes_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Palette Mode Segmented Row (Randomized vs Select Palettes)
                item(key = "palette_mode_bar") {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = uiState.config.paletteMode == PaletteMode.RANDOMIZED,
                            onClick = { onSetPaletteMode(PaletteMode.RANDOMIZED) },
                            icon = {},
                            colors = cardSegmentedColors,
                            shape = SegmentedButtonDefaults.itemShape(
                                index = 0,
                                count = 2,
                                baseShape = MaterialTheme.shapes.medium
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("btn_palette_mode_random")
                        ) {
                            Text(
                                text = stringResource(R.string.palette_mode_random),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        SegmentedButton(
                            selected = uiState.config.paletteMode == PaletteMode.PALETTE_LIST,
                            onClick = { onSetPaletteMode(PaletteMode.PALETTE_LIST) },
                            icon = {},
                            colors = cardSegmentedColors,
                            shape = SegmentedButtonDefaults.itemShape(
                                index = 1,
                                count = 2,
                                baseShape = MaterialTheme.shapes.medium
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("btn_palette_mode_list")
                        ) {
                            Text(
                                text = stringResource(R.string.palette_mode_list),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                // User's Palette List Section Header
                item(key = "palette_list_header") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = onOpenCreatePalette,
                                shape = MaterialTheme.shapes.medium,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .heightIn(min = 44.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_open_create_palette")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.btn_add_palette),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        if (uiState.config.paletteMode == PaletteMode.PALETTE_LIST) {
                            Text(
                                text = stringResource(
                                    R.string.palette_list_hint,
                                    uiState.config.selectedPaletteIds.size,
                                    uiState.userPalettes.size
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = onSelectAllPalettes,
                                    shape = MaterialTheme.shapes.small,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("btn_select_all_palettes")
                                ) {
                                    Text(
                                        text = stringResource(R.string.btn_select_all),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                TextButton(
                                    onClick = onSelectNonePalettes,
                                    shape = MaterialTheme.shapes.small,
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("btn_select_none_palettes")
                                ) {
                                    Text(
                                        text = stringResource(R.string.btn_select_none),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }

                items(
                    items = uiState.userPalettes,
                    key = { it.id },
                    contentType = { "palette_item" }
                ) { palette ->
                    val isSelected = uiState.config.paletteMode == PaletteMode.PALETTE_LIST &&
                        palette.id in uiState.config.selectedPaletteIds
                    PaletteListItemCard(
                        palette = palette,
                        isSelected = isSelected,
                        canDelete = uiState.userPalettes.size > 1,
                        onClick = { onTogglePaletteInList(palette) },
                        onDelete = { onDeletePalette(palette.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PaletteListItemCard(
    palette: ColorPalette,
    isSelected: Boolean,
    canDelete: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        selected = isSelected,
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (isSelected) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = if (isSelected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f))
        },
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("palette_card_${palette.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = palette.resolveDisplayName(context),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isSelected) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    if (canDelete) {
                        TextButton(
                            onClick = onDelete,
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("delete_palette_${palette.id}")
                        ) {
                            Text(
                                text = stringResource(R.string.btn_remove_palette),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            PaletteSolidSwatchesRow(colors = palette.colors)
        }
    }
}

@Composable
private fun PaletteSolidSwatchesRow(colors: List<Long>) {
    val safeColors = colors.ifEmpty { listOf(0xFF00F5D4L) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        safeColors.forEach { argb ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .background(Color(argb))
            )
        }
    }
}
