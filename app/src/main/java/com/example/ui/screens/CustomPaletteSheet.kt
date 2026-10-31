package com.example.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.RandomPaletteGenerator
import com.example.ui.theme.relativeLuminance
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPaletteSheet(
    onSavePalette: (name: String, colors: List<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var paletteName by remember { mutableStateOf("") }
    val swatches = remember {
        mutableStateListOf(0xFF00F0FFL, 0xFFFF4D79L, 0xFFC8FF2EL, 0xFF9B5DE5L)
    }
    var selectedSwatchIndex by remember { mutableIntStateOf(0) }

    val activeColor = swatches.getOrElse(selectedSwatchIndex) { 0xFF00F0FFL }
    val initialHsv = remember(selectedSwatchIndex) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(activeColor.toInt(), hsv)
        hsv
    }

    var hue by remember(selectedSwatchIndex) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(selectedSwatchIndex) { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember(selectedSwatchIndex) { mutableFloatStateOf(initialHsv[2].coerceAtLeast(0.2f)) }
    var hexInput by remember(selectedSwatchIndex) {
        mutableStateOf("#%06X".format(activeColor and 0xFFFFFFL))
    }

    fun updateActiveFromHsv(h: Float, s: Float, v: Float) {
        val intColor = AndroidColor.HSVToColor(floatArrayOf(h, s, v))
        val argbLong = intColor.toLong() and 0xFFFFFFFFL
        if (selectedSwatchIndex in swatches.indices) {
            swatches[selectedSwatchIndex] = argbLong
            hexInput = "#%06X".format(argbLong and 0xFFFFFFL)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = BottomSheetDefaults.ExpandedShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = stringResource(R.string.sheet_create_palette_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = paletteName,
                onValueChange = { paletteName = it },
                textStyle = MaterialTheme.typography.bodyLarge,
                label = {
                    Text(text = stringResource(R.string.label_palette_name))
                },
                placeholder = {
                    Text(text = stringResource(R.string.placeholder_palette_name))
                },
                shape = MaterialTheme.shapes.medium,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_custom_palette_name")
            )

            // Button to synthesize a random color harmony right inside the dialog
            FilledTonalButton(
                onClick = {
                    val freshHarmony = RandomPaletteGenerator.generate(System.nanoTime())
                    swatches.clear()
                    swatches.addAll(freshHarmony)
                    selectedSwatchIndex = 0
                    val firstColor = freshHarmony.firstOrNull() ?: 0xFF00F0FFL
                    val hsv = FloatArray(3)
                    AndroidColor.colorToHSV(firstColor.toInt(), hsv)
                    hue = hsv[0]
                    saturation = hsv[1]
                    brightness = hsv[2]
                    hexInput = "#%06X".format(firstColor and 0xFFFFFFL)
                },
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("btn_generate_random_harmony")
            ) {
                Text(
                    text = stringResource(R.string.btn_generate_random_harmony),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.label_palette_swatches, swatches.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (swatches.size > 1) {
                        TextButton(
                            onClick = {
                                if (selectedSwatchIndex in swatches.indices && swatches.size > 1) {
                                    swatches.removeAt(selectedSwatchIndex)
                                    selectedSwatchIndex = selectedSwatchIndex.coerceAtMost(swatches.lastIndex)
                                }
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("btn_remove_swatch")
                        ) {
                            Text(
                                text = stringResource(R.string.btn_remove_swatch),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (swatches.size < 6) {
                        FilledTonalButton(
                            onClick = {
                                val nextHue = (hue + 65f) % 360f
                                val nextInt = AndroidColor.HSVToColor(floatArrayOf(nextHue, 0.85f, 0.95f))
                                swatches.add(nextInt.toLong() and 0xFFFFFFFFL)
                                selectedSwatchIndex = swatches.lastIndex
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("btn_add_swatch")
                        ) {
                            Text(
                                text = stringResource(R.string.btn_add_swatch),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            // Interactive 48dp M3 Selectable Swatch Row with accessible contrast checkmark
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                swatches.forEachIndexed { index, argb ->
                    val isSelected = index == selectedSwatchIndex
                    val swatchColor = Color(argb)
                    val swatchCd = stringResource(R.string.cd_select_swatch, index + 1)
                    Surface(
                        selected = isSelected,
                        onClick = { selectedSwatchIndex = index },
                        shape = CircleShape,
                        color = swatchColor,
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 0.5.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                            }
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .minimumInteractiveComponentSize()
                            .semantics { contentDescription = swatchCd }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(48.dp)
                        ) {
                            if (isSelected) {
                                val checkTint = if (relativeLuminance(swatchColor) > 0.35f) {
                                    Color(0xFF101318)
                                } else {
                                    Color(0xFFF5F7FA)
                                }
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = checkTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // HEX Input for precision color entry
            OutlinedTextField(
                value = hexInput,
                onValueChange = { text ->
                    hexInput = text.uppercase().take(7)
                    val clean = text.removePrefix("#")
                    if (clean.length == 6) {
                        clean.toLongOrNull(16)?.let { rgb ->
                            val argb = 0xFF000000L or rgb
                            if (selectedSwatchIndex in swatches.indices) {
                                swatches[selectedSwatchIndex] = argb
                                val hsv = FloatArray(3)
                                AndroidColor.colorToHSV(argb.toInt(), hsv)
                                hue = hsv[0]
                                saturation = hsv[1]
                                brightness = hsv[2]
                            }
                        }
                    }
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                label = {
                    Text(text = stringResource(R.string.label_hex_code))
                },
                shape = MaterialTheme.shapes.medium,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Hue, Saturation, Brightness Sliders
            val sliderColors = SliderDefaults.colors(
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.label_hue),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${hue.roundToInt()}°",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = hue,
                    onValueChange = {
                        hue = it
                        updateActiveFromHsv(hue, saturation, brightness)
                    },
                    valueRange = 0f..360f,
                    colors = sliderColors
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.label_saturation),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${(saturation * 100f).roundToInt()}%",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = saturation,
                    onValueChange = {
                        saturation = it
                        updateActiveFromHsv(hue, saturation, brightness)
                    },
                    valueRange = 0f..1f,
                    colors = sliderColors
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.label_brightness),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${(brightness * 100f).roundToInt()}%",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = brightness,
                    onValueChange = {
                        brightness = it
                        updateActiveFromHsv(hue, saturation, brightness)
                    },
                    valueRange = 0.15f..1f,
                    colors = sliderColors
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .minimumInteractiveComponentSize()
                ) {
                    Text(
                        text = stringResource(R.string.btn_cancel),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Button(
                    onClick = { onSavePalette(paletteName, swatches.toList()) },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .heightIn(min = 48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("btn_save_custom_palette")
                ) {
                    Text(
                        text = stringResource(R.string.btn_save_palette),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
