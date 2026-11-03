package com.cptodix.rimalux.oled.wallpaper.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cptodix.rimalux.oled.wallpaper.R
import com.cptodix.rimalux.oled.wallpaper.model.TipCatalogItem
import com.cptodix.rimalux.oled.wallpaper.model.TipProduct

@Composable
fun LikeAppDialog(
    onRateApp: () -> Unit,
    onOpenSendThanks: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes

    RimaluxModalDialog(
        dialogTestTag = "dialog_like_app",
        onDismiss = onDismiss,
        modifier = modifier,
        maxWidth = 380.dp,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                    color = colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.dialog_like_app_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = colorScheme.onSurface
                )

                Text(
                    text = stringResource(R.string.dialog_like_app_sub),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onRateApp,
                        shape = shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("btn_dialog_rate_app")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_rate_app),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    FilledTonalButton(
                        onClick = onOpenSendThanks,
                        shape = shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("btn_dialog_send_thanks")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_send_thanks),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    TextButton(
                        onClick = onDismiss,
                        shape = shapes.small,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("btn_dialog_dismiss_like")
                    ) {
                        Text(
                            text = stringResource(R.string.btn_cancel),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
    }
}

@Composable
fun SendThanksDialog(
    onSelectTip: (TipProduct) -> Unit,
    onDismiss: () -> Unit,
    tipCatalog: List<TipCatalogItem> = TipCatalogItem.defaultCatalog,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes

    RimaluxModalDialog(
        dialogTestTag = "dialog_send_thanks",
        onDismiss = onDismiss,
        modifier = modifier,
        maxWidth = 380.dp,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                    color = colorScheme.tertiaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.dialog_send_thanks_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = colorScheme.onSurface
                )

                Text(
                    text = stringResource(R.string.dialog_send_thanks_sub),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // Single-column direct-action tip buttons backed by Google Play Billing INAPP products
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tip_options_column"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tipCatalog.forEach { item ->
                        val tipLabel = item.formattedPrice?.takeIf { it.isNotBlank() }
                            ?: stringResource(item.product.fallbackLabelRes)
                        Surface(
                            onClick = { onSelectTip(item.product) },
                            shape = shapes.medium,
                            color = colorScheme.surfaceContainerHighest,
                            border = BorderStroke(
                                width = 0.75.dp,
                                color = colorScheme.outlineVariant.copy(alpha = 0.42f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("btn_tip_${item.product.amountKey}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tipLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    shape = shapes.small,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("btn_dialog_dismiss_thanks")
                ) {
                    Text(
                        text = stringResource(R.string.btn_cancel),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
    }
}
