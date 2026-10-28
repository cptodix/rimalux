package com.example.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.model.IntervalFormatter
import com.example.ui.components.FullScreenOledPreviewDialog
import com.example.ui.components.VectorWallpaperPreviewCard
import com.example.ui.screens.CustomIntervalDialog
import com.example.ui.screens.CustomPaletteSheet
import com.example.ui.screens.PalettesScreen
import com.example.ui.screens.ScheduleScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.screens.StylesScreen

private data class NavDestination(
    val tab: VectraTab,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val navDestinations = listOf(
    NavDestination(
        tab = VectraTab.HOME,
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_tab_home"
    ),
    NavDestination(
        tab = VectraTab.STYLES,
        labelRes = R.string.nav_styles,
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
        testTag = "nav_tab_styles"
    ),
    NavDestination(
        tab = VectraTab.PALETTES,
        labelRes = R.string.nav_palettes,
        selectedIcon = Icons.Filled.Palette,
        unselectedIcon = Icons.Outlined.Palette,
        testTag = "nav_tab_palettes"
    ),
    NavDestination(
        tab = VectraTab.AUTO,
        labelRes = R.string.nav_auto,
        selectedIcon = Icons.Filled.Autorenew,
        unselectedIcon = Icons.Outlined.Autorenew,
        testTag = "nav_tab_auto"
    )
)

@Composable
fun VectraApp(
    viewModel: VectraViewModel = viewModel(factory = VectraViewModel.Factory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.statusMessage) {
        val message = uiState.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissStatusMessage()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isWideScreen = maxWidth >= 640.dp

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                VectraMinimalTopBar(
                    isAutoRotateEnabled = uiState.config.autoRotateEnabled,
                    countdownSeconds = uiState.countdownSeconds,
                    onClickScheduleBadge = { viewModel.selectTab(VectraTab.AUTO) }
                )
            },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 4.dp
                    ) {
                        navDestinations.forEach { dest ->
                            val selected = uiState.selectedTab == dest.tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(dest.tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(dest.labelRes),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            if (isWideScreen) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        navDestinations.forEach { dest ->
                            val selected = uiState.selectedTab == dest.tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(dest.tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .testTag(dest.testTag)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Box(
                        modifier = Modifier
                            .weight(0.92f)
                            .fillMaxHeight()
                            .padding(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        VectorWallpaperPreviewCard(
                            scene = uiState.currentScene,
                            paletteColors = uiState.activeColors,
                            pitchBlackRatio = uiState.config.pitchBlackRatio,
                            colorFillMode = uiState.config.colorFillMode,
                            mockupMode = uiState.mockupMode,
                            onCycleMockup = viewModel::cycleMockupOverlay,
                            onOpenFullScreen = { viewModel.setFullScreenPreview(true) },
                            previewAspectRatio = 0.65f,
                            modifier = Modifier.widthIn(max = 400.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1.08f)
                            .fillMaxHeight()
                    ) {
                        ActiveTabContent(
                            uiState = uiState,
                            showInlinePreview = false,
                            viewModel = viewModel
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    ActiveTabContent(
                        uiState = uiState,
                        showInlinePreview = true,
                        viewModel = viewModel,
                        modifier = Modifier.widthIn(max = 580.dp)
                    )
                }
            }
        }
    }

    if (uiState.isFullScreenPreview) {
        FullScreenOledPreviewDialog(
            scene = uiState.currentScene,
            paletteColors = uiState.activeColors,
            pitchBlackRatio = uiState.config.pitchBlackRatio,
            colorFillMode = uiState.config.colorFillMode,
            isApplying = uiState.isApplyingWallpaper,
            onRandomize = viewModel::generateNewWallpaper,
            onApplyNow = viewModel::applyCurrentWallpaperNow,
            onDismiss = { viewModel.setFullScreenPreview(false) }
        )
    }

    if (uiState.isCreatePaletteSheetOpen) {
        CustomPaletteSheet(
            onSavePalette = viewModel::addCustomPalette,
            onDismiss = { viewModel.setCreatePaletteSheetOpen(false) }
        )
    }

    if (uiState.isCustomIntervalDialogOpen) {
        CustomIntervalDialog(
            initialMinutes = uiState.config.intervalMinutes,
            onConfirm = viewModel::updateIntervalMinutes,
            onDismiss = { viewModel.setCustomIntervalDialogOpen(false) }
        )
    }
}

@Composable
private fun ActiveTabContent(
    uiState: VectraUiState,
    showInlinePreview: Boolean,
    viewModel: VectraViewModel,
    modifier: Modifier = Modifier
) {
    Crossfade(
        targetState = uiState.selectedTab,
        animationSpec = tween(durationMillis = 200),
        label = "vectra_tab_crossfade",
        modifier = modifier.fillMaxSize()
    ) { tab ->
        when (tab) {
            VectraTab.HOME -> StudioScreen(
                uiState = uiState,
                showInlinePreview = showInlinePreview,
                onGenerateNew = viewModel::generateNewWallpaper,
                onApplyNow = viewModel::applyCurrentWallpaperNow,
                onCycleMockup = viewModel::cycleMockupOverlay,
                onOpenFullScreen = { viewModel.setFullScreenPreview(true) },
                onPitchBlackRatioChange = viewModel::updatePitchBlackRatio,
                onManualTargetChange = viewModel::updateManualTarget,
                onAutoApplyChange = viewModel::updateAutoApplyOnGenerate,
                onOpenStylesTab = { viewModel.selectTab(VectraTab.STYLES) },
                onOpenPalettesTab = { viewModel.selectTab(VectraTab.PALETTES) }
            )

            VectraTab.STYLES -> StylesScreen(
                uiState = uiState,
                onSetRandomized = viewModel::setStylesRandomized,
                onToggleStyle = viewModel::toggleStyleSelection,
                onIntricacyChange = viewModel::updateIntricacy,
                onBackToHome = { viewModel.selectTab(VectraTab.HOME) }
            )

            VectraTab.PALETTES -> PalettesScreen(
                uiState = uiState,
                onSetColorFillMode = viewModel::setColorFillMode,
                onSetPaletteMode = viewModel::setPaletteMode,
                onShuffleRandomColors = viewModel::shuffleRandomColors,
                onSaveRandomToList = viewModel::saveCurrentRandomColorsToList,
                onTogglePaletteInList = viewModel::togglePaletteInList,
                onOpenCreatePalette = { viewModel.setCreatePaletteSheetOpen(true) },
                onDeletePalette = viewModel::deletePaletteFromList,
                onBackToHome = { viewModel.selectTab(VectraTab.HOME) }
            )

            VectraTab.AUTO -> ScheduleScreen(
                uiState = uiState,
                onToggleAutoRotate = viewModel::setAutoRotateEnabled,
                onSelectIntervalMinutes = viewModel::updateIntervalMinutes,
                onOpenCustomIntervalDialog = { viewModel.setCustomIntervalDialogOpen(true) },
                onTriggerRotationNow = viewModel::triggerAutoCycleNow,
                onAutoTargetChange = viewModel::updateAutoTarget,
                onRestoreHistoryItem = viewModel::restoreFromHistory,
                onBackToHome = { viewModel.selectTab(VectraTab.HOME) }
            )
        }
    }
}

@Composable
private fun VectraMinimalTopBar(
    isAutoRotateEnabled: Boolean,
    countdownSeconds: Long,
    onClickScheduleBadge: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (isAutoRotateEnabled) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .clickable(onClick = onClickScheduleBadge)
                    .testTag("top_schedule_status_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary)
                    )
                    Text(
                        text = stringResource(
                            R.string.status_auto_short,
                            IntervalFormatter.formatCountdown(countdownSeconds)
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}
