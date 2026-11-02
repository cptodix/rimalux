package com.cptodix.rimalux.oled.wallpaper.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.cptodix.rimalux.oled.wallpaper.R
import com.cptodix.rimalux.oled.wallpaper.model.AppLanguage
import com.cptodix.rimalux.oled.wallpaper.model.ColorFillMode
import com.cptodix.rimalux.oled.wallpaper.model.PatternWeightMode
import java.util.Locale

/**
 * Unified Material 3 modal dialog container backed by [BasicAlertDialog] so every popup across
 * Rimalux shares consistent window animations, focus trapping, TalkBack semantics, and medium-sharp
 * OLED surface styling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RimaluxModalDialog(
    dialogTestTag: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 360.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = modifier
            .padding(horizontal = 24.dp)
            .widthIn(max = maxWidth)
            .fillMaxWidth()
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(dialogTestTag)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = horizontalAlignment,
                verticalArrangement = verticalArrangement,
                content = content
            )
        }
    }
}

@Composable
fun PatternLayoutDialog(
    selectedMode: PatternWeightMode,
    onSelectMode: (PatternWeightMode) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        Triple(PatternWeightMode.NORMAL, R.string.weight_mode_normal, "layout_option_normal"),
        Triple(PatternWeightMode.CENTER_WEIGHTED, R.string.weight_mode_center, "layout_option_center")
    )

    RimaluxModalDialog(
        dialogTestTag = "dialog_pattern_layout",
        onDismiss = onDismiss
    ) {
        Text(
            text = stringResource(R.string.label_pattern_layout),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            options.forEach { (mode, labelRes, tag) ->
                val isSelected = selectedMode == mode
                DialogSingleChoiceRow(
                    label = stringResource(labelRes),
                    isSelected = isSelected,
                    onClick = { onSelectMode(mode) },
                    testTag = tag
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onDismiss,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("btn_cancel_pattern_layout")
            ) {
                Text(
                    text = stringResource(R.string.btn_cancel),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun ColorFillStyleDialog(
    selectedMode: ColorFillMode,
    onSelectMode: (ColorFillMode) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        Triple(ColorFillMode.SOLID, R.string.fill_mode_solid, "fill_option_solid"),
        Triple(ColorFillMode.GRADIENT, R.string.fill_mode_gradient, "fill_option_gradient")
    )

    RimaluxModalDialog(
        dialogTestTag = "dialog_color_fill",
        onDismiss = onDismiss
    ) {
        Text(
            text = stringResource(R.string.label_color_shading),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            options.forEach { (mode, labelRes, tag) ->
                val isSelected = selectedMode == mode
                DialogSingleChoiceRow(
                    label = stringResource(labelRes),
                    isSelected = isSelected,
                    onClick = { onSelectMode(mode) },
                    testTag = tag
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onDismiss,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("btn_cancel_color_fill")
            ) {
                Text(
                    text = stringResource(R.string.btn_cancel),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun LanguageSelectorDialog(
    selectedLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    RimaluxModalDialog(
        dialogTestTag = "dialog_language_selector",
        onDismiss = onDismiss
    ) {
        Text(
            text = stringResource(R.string.dialog_language_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppLanguage.entries.forEach { language ->
                val isSelected = selectedLanguage == language
                val optionTag = "language_option_${language.languageTag.lowercase(Locale.ROOT)}"
                DialogSingleChoiceRow(
                    label = language.nativeName,
                    isSelected = isSelected,
                    onClick = { onSelectLanguage(language) },
                    testTag = optionTag
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onDismiss,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("btn_cancel_language")
            ) {
                Text(
                    text = stringResource(R.string.btn_cancel),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun DialogSingleChoiceRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        selected = isSelected,
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        border = if (isSelected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
        } else {
            BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
