package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.MathPatternGenerator
import com.example.model.GeneratedHistoryItem
import com.example.model.IntervalFormatter
import com.example.model.WallpaperTarget
import com.example.ui.VectraUiState
import com.example.ui.components.MiniVectorThumbnail
import com.example.ui.components.TargetSegmentedBar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleScreen(
    uiState: VectraUiState,
    onToggleAutoRotate: (Boolean) -> Unit,
    onSelectIntervalMinutes: (Int) -> Unit,
    onOpenCustomIntervalDialog: () -> Unit,
    onTriggerRotationNow: () -> Unit,
    onAutoTargetChange: (WallpaperTarget) -> Unit,
    onRestoreHistoryItem: (GeneratedHistoryItem) -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToHome)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("schedule_screen_list"),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Persistent Background Hero Switch Card
        item(key = "auto_hero_card") {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(116.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_automation_banner),
                            contentDescription = stringResource(R.string.auto_title),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.88f))
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.auto_title),
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.auto_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onToggleAutoRotate(!uiState.config.autoRotateEnabled) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.auto_master_toggle),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (uiState.config.autoRotateEnabled) {
                                        stringResource(
                                            R.string.auto_next_in,
                                            IntervalFormatter.formatCountdown(uiState.countdownSeconds)
                                        )
                                    } else {
                                        stringResource(R.string.auto_off_desc)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (uiState.config.autoRotateEnabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Switch(
                                checked = uiState.config.autoRotateEnabled,
                                onCheckedChange = onToggleAutoRotate,
                                modifier = Modifier.testTag("switch_auto_rotate")
                            )
                        }

                        Button(
                            onClick = onTriggerRotationNow,
                            enabled = !uiState.isApplyingWallpaper,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("btn_trigger_rotation_now")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (uiState.isApplyingWallpaper) {
                                    stringResource(R.string.btn_applying)
                                } else {
                                    stringResource(R.string.btn_run_now)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Dedicated Automatic Target Selector (Wallpaper / Lock Screen / Both)
        item(key = "auto_target_card") {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.label_auto_target),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TargetSegmentedBar(
                        selectedTarget = uiState.config.autoTarget,
                        onTargetSelected = onAutoTargetChange,
                        tagPrefix = "auto_target"
                    )
                }
            }
        }

        // Interval Picker Card
        item(key = "interval_card") {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.label_interval),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = IntervalFormatter.formatMinutesShort(uiState.config.intervalMinutes),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IntervalFormatter.presetMinutes.forEach { mins ->
                            val isSelected = uiState.config.intervalMinutes == mins
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectIntervalMinutes(mins) },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                        )
                                    }
                                } else null,
                                label = {
                                    Text(
                                        text = IntervalFormatter.formatMinutesShort(mins),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("chip_interval_$mins")
                            )
                        }

                        val isCustom = uiState.config.intervalMinutes !in IntervalFormatter.presetMinutes
                        FilterChip(
                            selected = isCustom,
                            onClick = onOpenCustomIntervalDialog,
                            label = {
                                Text(
                                    text = if (isCustom) {
                                        "${uiState.config.intervalMinutes}m"
                                    } else {
                                        stringResource(R.string.btn_custom_interval)
                                    },
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("btn_custom_interval")
                        )
                    }
                }
            }
        }

        // Compact Recent Thumbnails Strip
        if (uiState.history.isNotEmpty()) {
            item(key = "recent_strip") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.section_recent),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(
                            items = uiState.history,
                            key = { it.id }
                        ) { item ->
                            val thumbScene = remember(item.seed, item.style, item.intricacy) {
                                MathPatternGenerator.generate(
                                    seed = item.seed,
                                    style = item.style,
                                    intricacy = item.intricacy,
                                    aspectRatio = 1.45f
                                )
                            }
                            MiniVectorThumbnail(
                                scene = thumbScene,
                                paletteColors = item.paletteColors,
                                pitchBlackRatio = item.pitchBlackRatio,
                                colorFillMode = item.colorFillMode,
                                modifier = Modifier
                                    .size(width = 76.dp, height = 110.dp)
                                    .clickable { onRestoreHistoryItem(item) }
                                    .testTag("history_card_${item.id}")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomIntervalDialog(
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var minutesText by remember { mutableStateOf(initialMinutes.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.dialog_custom_interval_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            OutlinedTextField(
                value = minutesText,
                onValueChange = { input ->
                    minutesText = input.filter { it.isDigit() }.take(5)
                },
                label = { Text(stringResource(R.string.label_minutes_input)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_custom_interval_minutes")
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = minutesText.toIntOrNull()?.coerceIn(1, 10080) ?: 60
                    onConfirm(parsed)
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("btn_confirm_custom_interval")
            ) {
                Text(stringResource(R.string.btn_confirm_interval))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
