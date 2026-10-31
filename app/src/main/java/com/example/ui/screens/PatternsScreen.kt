package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ArtPattern
import com.example.model.PatternCategory
import com.example.model.labelRes
import com.example.model.previewRes
import com.example.model.subtitleRes
import com.example.model.titleRes
import com.example.ui.RimaluxUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PatternsScreen(
    uiState: RimaluxUiState,
    onSetRandomized: (Boolean) -> Unit,
    onTogglePattern: (ArtPattern) -> Unit,
    onSelectAllPatterns: () -> Unit,
    onSelectNonePatterns: () -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToHome)

    var selectedCategory by remember { mutableStateOf<PatternCategory?>(null) }

    val filteredPatterns = remember(selectedCategory, uiState.config.patternsRandomized) {
        if (uiState.config.patternsRandomized || selectedCategory == null) {
            ArtPattern.entries.toList()
        } else {
            ArtPattern.entries.filter { it.category == selectedCategory }
        }
    }
    val patternRows = remember(filteredPatterns) { filteredPatterns.chunked(2) }
    val activePreviewPattern = uiState.preview?.pattern
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val segmentedColors = SegmentedButtonDefaults.colors(
        activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val segmentedBorder = SegmentedButtonDefaults.borderStroke(
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    )
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
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
                        text = stringResource(R.string.patterns_title),
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
                    .testTag("patterns_screen_list"),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 4.dp,
                    bottom = innerPadding.calculateBottomPadding() + 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item(key = "patterns_subtitle") {
                    Text(
                        text = stringResource(R.string.patterns_subtitle, ArtPattern.entries.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Randomized vs Multi-Select Segmented Bar
                item(key = "patterns_mode_bar") {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = uiState.config.patternsRandomized,
                            onClick = { onSetRandomized(true) },
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
                                .testTag("btn_patterns_random")
                        ) {
                            Text(
                                text = stringResource(R.string.mode_randomized),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        SegmentedButton(
                            selected = !uiState.config.patternsRandomized,
                            onClick = { onSetRandomized(false) },
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
                                .testTag("btn_patterns_multiselect")
                        ) {
                            Text(
                                text = stringResource(R.string.mode_multiselect),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                // Category Filter FlowRow shown ONLY in Select Custom mode
                if (!uiState.config.patternsRandomized) {
                    item(key = "patterns_category_filters") {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isAll = selectedCategory == null
                            FilterChip(
                                selected = isAll,
                                onClick = { selectedCategory = null },
                                shape = CircleShape,
                                colors = chipColors,
                                border = null,
                                label = {
                                    Text(
                                        text = stringResource(R.string.cat_all, ArtPattern.entries.size),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("chip_pattern_cat_all")
                            )

                            PatternCategory.entries.forEach { category ->
                                val isPicked = selectedCategory == category
                                FilterChip(
                                    selected = isPicked,
                                    onClick = {
                                        selectedCategory = if (isPicked) null else category
                                    },
                                    shape = CircleShape,
                                    colors = chipColors,
                                    border = null,
                                    label = {
                                        Text(
                                            text = stringResource(category.labelRes),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("chip_pattern_cat_${category.name.lowercase()}")
                                )
                            }
                        }
                    }
                }

                if (!uiState.config.patternsRandomized) {
                    item(key = "patterns_hint") {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.patterns_multiselect_hint,
                                    uiState.config.selectedPatterns.size,
                                    ArtPattern.entries.size
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
                                    onClick = onSelectAllPatterns,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("btn_select_all_patterns")
                                ) {
                                    Text(
                                        text = stringResource(R.string.btn_select_all),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                TextButton(
                                    onClick = onSelectNonePatterns,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("btn_select_none_patterns")
                                ) {
                                    Text(
                                        text = stringResource(R.string.btn_select_none),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 2-Column Gallery of all 64 Procedural Patterns using pre-rendered static drawables
                items(
                    items = patternRows,
                    key = { row -> row.joinToString("_") { it.id } }
                ) { rowPatterns ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        rowPatterns.forEach { pattern ->
                            val isSelected = !uiState.config.patternsRandomized &&
                                pattern in uiState.config.selectedPatterns
                            val isCurrentlyRendered = activePreviewPattern == pattern
                            PatternGalleryCard(
                                pattern = pattern,
                                isSelected = isSelected,
                                isCurrentlyRendered = isCurrentlyRendered,
                                onClick = { onTogglePattern(pattern) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowPatterns.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PatternGalleryCard(
    pattern: ArtPattern,
    isSelected: Boolean,
    isCurrentlyRendered: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        selected = isSelected,
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = when {
            isSelected -> MaterialTheme.colorScheme.surfaceContainerHigh
            isCurrentlyRendered -> MaterialTheme.colorScheme.surfaceContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = when {
            isSelected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            isCurrentlyRendered -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            else -> null
        },
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("pattern_card_${pattern.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(id = pattern.previewRes),
                    contentDescription = stringResource(pattern.titleRes),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(Color.Black)
                )

                if (isSelected) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(22.dp)
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
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(pattern.titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(pattern.subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
