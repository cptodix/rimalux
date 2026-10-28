package com.example.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.sheet_create_palette_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = paletteName,
                onValueChange = { paletteName = it },
                label = { Text(stringResource(R.string.label_palette_name)) },
                placeholder = { Text(stringResource(R.string.placeholder_palette_name)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_custom_palette_name")
            )

            Text(
                text = stringResource(R.string.label_palette_swatches, swatches.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Interactive Swatch Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                swatches.forEachIndexed { index, argb ->
                    val isSelected = index == selectedSwatchIndex
                    Box(contentAlignment = Alignment.TopEnd) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(argb))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { selectedSwatchIndex = index }
                        )
                        if (swatches.size > 1 && isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.78f))
                                    .clickable {
                                        swatches.removeAt(index)
                                        selectedSwatchIndex = (selectedSwatchIndex - 1).coerceAtLeast(0)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.cd_delete_palette),
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }

                if (swatches.size < 6) {
                    FilledTonalIconButton(
                        onClick = {
                            val nextHue = (hue + 65f) % 360f
                            val nextInt = AndroidColor.HSVToColor(floatArrayOf(nextHue, 0.85f, 0.95f))
                            swatches.add(nextInt.toLong() and 0xFFFFFFFFL)
                            selectedSwatchIndex = swatches.lastIndex
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_add_swatch")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.btn_add_swatch)
                        )
                    }
                }
            }

            // HEX Input for precision color entry
            OutlinedTextField(
                value = hexInput,
                onValueChange = { text ->
                    hexInput = text.take(7)
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
                label = { Text(stringResource(R.string.label_hex_code)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Hue, Saturation, Brightness Sliders
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.label_hue), style = MaterialTheme.typography.labelLarge)
                    Text("${hue.roundToInt()}°", style = MaterialTheme.typography.labelMedium)
                }
                Slider(
                    value = hue,
                    onValueChange = {
                        hue = it
                        updateActiveFromHsv(hue, saturation, brightness)
                    },
                    valueRange = 0f..360f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.label_saturation), style = MaterialTheme.typography.labelLarge)
                    Text("${(saturation * 100f).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                }
                Slider(
                    value = saturation,
                    onValueChange = {
                        saturation = it
                        updateActiveFromHsv(hue, saturation, brightness)
                    },
                    valueRange = 0f..1f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.label_brightness), style = MaterialTheme.typography.labelLarge)
                    Text("${(brightness * 100f).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                }
                Slider(
                    value = brightness,
                    onValueChange = {
                        brightness = it
                        updateActiveFromHsv(hue, saturation, brightness)
                    },
                    valueRange = 0.15f..1f
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
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
                Button(
                    onClick = { onSavePalette(paletteName, swatches.toList()) },
                    modifier = Modifier
                        .weight(1.4f)
                        .minimumInteractiveComponentSize()
                        .testTag("btn_save_custom_palette")
                ) {
                    Text(stringResource(R.string.btn_save_palette))
                }
            }
        }
    }
}
