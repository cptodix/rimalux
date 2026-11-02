package com.cptodix.rimalux.oled.wallpaper.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cptodix.rimalux.oled.wallpaper.R
import com.cptodix.rimalux.oled.wallpaper.ui.components.ColorFillStyleDialog
import com.cptodix.rimalux.oled.wallpaper.ui.components.LanguageSelectorDialog
import com.cptodix.rimalux.oled.wallpaper.ui.components.LikeAppDialog
import com.cptodix.rimalux.oled.wallpaper.ui.components.PatternLayoutDialog
import com.cptodix.rimalux.oled.wallpaper.ui.components.SendThanksDialog
import com.cptodix.rimalux.oled.wallpaper.ui.components.SetWallpaperTargetDialog
import com.cptodix.rimalux.oled.wallpaper.ui.screens.AutoChangeHelpScreen
import com.cptodix.rimalux.oled.wallpaper.ui.screens.AutoTargetDialog
import com.cptodix.rimalux.oled.wallpaper.ui.screens.CustomPaletteSheet
import com.cptodix.rimalux.oled.wallpaper.ui.screens.IntervalPickerDialog
import com.cptodix.rimalux.oled.wallpaper.ui.screens.PalettesScreen
import com.cptodix.rimalux.oled.wallpaper.ui.screens.PatternsScreen
import com.cptodix.rimalux.oled.wallpaper.ui.screens.StudioScreen
import com.cptodix.rimalux.oled.wallpaper.ui.theme.RimaluxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RimaluxApp(
    viewModel: RimaluxViewModel = viewModel(factory = RimaluxViewModel.Factory(LocalContext.current))
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle(
        context = Dispatchers.Main.immediate
    )

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.ensureAutoScheduleIfEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.saveCurrentWallpaperToDevice()
        } else {
            viewModel.reportStoragePermissionDenied()
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        if (uiState.statusMessage == null) return@LaunchedEffect
        delay(2_600L)
        viewModel.dismissStatusMessage()
    }

    LaunchedEffect(uiState.pendingShareIntent) {
        val shareIntent = uiState.pendingShareIntent ?: return@LaunchedEffect
        viewModel.consumePendingShareIntent()
        runCatching {
            if (context !is Activity) {
                shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(shareIntent)
        }.onFailure { error ->
            viewModel.reportShareLaunchError(
                error.localizedMessage ?: context.getString(R.string.share_chooser_title)
            )
        }
    }

    LaunchedEffect(uiState.pendingLocaleRefreshToken) {
        if (uiState.pendingLocaleRefreshToken == 0L) return@LaunchedEffect
        viewModel.consumePendingLocaleRefresh()
        (context as? Activity)?.recreate()
    }

    val activePaletteColors = uiState.preview?.colors ?: uiState.stagedColors

    RimaluxTheme(paletteColors = activePaletteColors) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { innerPadding ->
                Crossfade(
                    targetState = uiState.currentScreen,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    label = "rimalux_screen_crossfade",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) { screen ->
                    when (screen) {
                        RimaluxScreen.HOME -> {
                            StudioScreen(
                                uiState = uiState,
                                onSwipeLeftNext = viewModel::swipeLeftToNextWallpaper,
                                onSwipeRightPrevious = viewModel::swipeRightToPreviousWallpaper,
                                onSwipeDownCycleMockup = viewModel::cycleMockupOverlay,
                                onBottomSheetOpenChange = viewModel::setBottomSheetOpen,
                                onRightEdgeBounce = viewModel::recordRightEdgeBounce,
                                onOpenSetWallpaperDialog = viewModel::openSetWallpaperDialog,
                                onSetMockupMode = viewModel::setMockupOverlayMode,
                                onSetPitchBlackMode = viewModel::setPitchBlackMode,
                                onPitchBlackRatioChange = viewModel::updatePitchBlackRatio,
                                onPitchBlackRangeChange = viewModel::updatePitchBlackRange,
                                onOpenPatternsPage = { viewModel.navigateTo(RimaluxScreen.PATTERNS) },
                                onOpenPatternLayoutDialog = { viewModel.setPatternLayoutDialogOpen(true) },
                                onOpenPalettesPage = { viewModel.navigateTo(RimaluxScreen.PALETTES) },
                                onOpenColorFillDialog = { viewModel.setColorFillDialogOpen(true) },
                                onToggleAutoRotate = viewModel::setAutoRotateEnabled,
                                onOpenAutoTargetDialog = { viewModel.setAutoTargetDialogOpen(true) },
                                onOpenIntervalDialog = { viewModel.setIntervalDialogOpen(true) },
                                onOpenAutoChangeHelp = { viewModel.navigateTo(RimaluxScreen.AUTO_CHANGE_HELP) },
                                onOpenLikeApp = viewModel::openLikeAppDialog,
                                onOpenLanguageDialog = { viewModel.setLanguageDialogOpen(true) },
                                onCanvasSingleTap = viewModel::onCanvasSingleTap,
                                onDismissTutorial = viewModel::dismissGestureTutorial,
                                onReopenGestureTutorial = viewModel::reopenGestureTutorial,
                                onSwipeUpOpenedSheet = viewModel::onSwipeUpOpenedSheet,
                                onPreviewUserActivity = viewModel::onPreviewUserActivity,
                                onShowMiniHintAfterIdle = viewModel::showMiniHintAfterIdle,
                                onRotateMiniHint = viewModel::rotateMiniHint,
                                onHideMiniHint = viewModel::hideMiniHint,
                                onOpenHintWindowFromMiniHint = viewModel::openHintWindowFromMiniHint,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        RimaluxScreen.PATTERNS -> {
                            PatternsScreen(
                                uiState = uiState,
                                onSetPatternWeightMode = viewModel::setPatternWeightMode,
                                onSetRandomized = viewModel::setPatternsRandomized,
                                onTogglePattern = viewModel::togglePatternSelection,
                                onSelectAllPatterns = viewModel::selectAllPatterns,
                                onSelectNonePatterns = viewModel::selectNonePatterns,
                                onBackToHome = { viewModel.navigateTo(RimaluxScreen.HOME) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        RimaluxScreen.PALETTES -> {
                            PalettesScreen(
                                uiState = uiState,
                                onSetColorFillMode = viewModel::setColorFillMode,
                                onSetPaletteMode = viewModel::setPaletteMode,
                                onSelectAllPalettes = viewModel::selectAllPalettes,
                                onSelectNonePalettes = viewModel::selectNonePalettes,
                                onTogglePaletteInList = viewModel::togglePaletteInList,
                                onOpenCreatePalette = { viewModel.setCreatePaletteSheetOpen(true) },
                                onDeletePalette = viewModel::deletePaletteFromList,
                                onBackToHome = { viewModel.navigateTo(RimaluxScreen.HOME) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        RimaluxScreen.AUTO_CHANGE_HELP -> {
                            AutoChangeHelpScreen(
                                onPostStatusMessage = viewModel::postExternalStatusMessage,
                                onBackToHome = { viewModel.navigateTo(RimaluxScreen.HOME) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // Touch-passthrough status toast banner so notifications never block controls or swipes
            AnimatedVisibility(
                visible = uiState.statusMessage != null,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = 16.dp, end = 16.dp, bottom = 60.dp)
            ) {
                val activeMessage = uiState.statusMessage.orEmpty()
                val toastShape = MaterialTheme.shapes.medium
                Box(
                    modifier = Modifier
                        .widthIn(max = 460.dp)
                        .shadow(elevation = 4.dp, shape = toastShape)
                        .clip(toastShape)
                        .background(MaterialTheme.colorScheme.inverseSurface)
                ) {
                    Text(
                        text = activeMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                    )
                }
            }

            if (uiState.isAutoTargetDialogOpen) {
                AutoTargetDialog(
                    selectedTarget = uiState.config.autoTarget,
                    onSelectTarget = viewModel::updateAutoTarget,
                    onDismiss = { viewModel.setAutoTargetDialogOpen(false) }
                )
            }

            if (uiState.isIntervalDialogOpen || uiState.isCustomIntervalDialogOpen) {
                IntervalPickerDialog(
                    selectedMinutes = uiState.config.intervalMinutes,
                    isCustomEntryMode = uiState.isCustomIntervalDialogOpen,
                    onSelectMinutes = viewModel::updateIntervalMinutes,
                    onOpenCustomInterval = viewModel::openCustomIntervalFromPicker,
                    onDismiss = {
                        viewModel.setIntervalDialogOpen(false)
                        viewModel.setCustomIntervalDialogOpen(false)
                    }
                )
            }
        }

        if (uiState.isSetWallpaperDialogOpen) {
            SetWallpaperTargetDialog(
                selectedTarget = null,
                onSelectTargetAndApply = viewModel::applyWallpaperForTarget,
                onSaveToDevice = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        viewModel.saveCurrentWallpaperToDevice()
                    } else {
                        viewModel.dismissSetWallpaperDialog()
                        legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                },
                onShareWallpaper = viewModel::shareCurrentWallpaper,
                onDismiss = viewModel::dismissSetWallpaperDialog
            )
        }

        if (uiState.isCreatePaletteSheetOpen) {
            CustomPaletteSheet(
                onSavePalette = viewModel::addCustomPalette,
                onDismiss = { viewModel.setCreatePaletteSheetOpen(false) }
            )
        }

        if (uiState.isPatternLayoutDialogOpen) {
            PatternLayoutDialog(
                selectedMode = uiState.config.patternWeightMode,
                onSelectMode = viewModel::setPatternWeightMode,
                onDismiss = { viewModel.setPatternLayoutDialogOpen(false) }
            )
        }

        if (uiState.isColorFillDialogOpen) {
            ColorFillStyleDialog(
                selectedMode = uiState.config.colorFillMode,
                onSelectMode = viewModel::setColorFillMode,
                onDismiss = { viewModel.setColorFillDialogOpen(false) }
            )
        }

        if (uiState.isLikeAppDialogOpen) {
            LikeAppDialog(
                onRateApp = viewModel::rateApp,
                onOpenSendThanks = viewModel::openSendThanksDialog,
                onDismiss = viewModel::closeLikeAppDialog
            )
        }

        if (uiState.isSendThanksDialogOpen) {
            SendThanksDialog(
                onSendTip = viewModel::sendThanksTip,
                onDismiss = viewModel::closeSendThanksDialog
            )
        }

        if (uiState.isLanguageDialogOpen) {
            LanguageSelectorDialog(
                selectedLanguage = uiState.appLanguage,
                onSelectLanguage = viewModel::selectAppLanguage,
                onDismiss = { viewModel.setLanguageDialogOpen(false) }
            )
        }
    }
}
