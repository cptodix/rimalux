package com.example

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.model.ArtPattern
import com.example.model.ColorFillMode
import com.example.model.MockupOverlayMode
import com.example.model.PatternWeightMode
import com.example.model.StarterPalettes
import com.example.model.nameResOrNull
import com.example.model.previewRes
import com.example.model.resolveDisplayName
import com.example.scheduler.OemBackgroundHelper
import com.example.scheduler.OemVendor
import com.example.ui.RimaluxScreen
import com.example.ui.RimaluxViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun awaitPreviewReady(
        viewModel: RimaluxViewModel,
        previousSeed: Long? = null,
        requireAheadBuffer: Boolean = false
    ) {
        val deadline = System.currentTimeMillis() + 8_000L
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeTestRule.waitForIdle()
            val state = viewModel.uiState.value
            val preview = state.preview
            val seedReady = preview != null && !state.isGeneratingPreview &&
                (previousSeed == null || preview.seed != previousSeed)
            val aheadReady = !requireAheadBuffer || state.aheadReadyCount >= 1
            if (seedReady && aheadReady) {
                return
            }
            Thread.sleep(15L)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
    }

    @Test
    fun appName_64PatternsWithStaticDrawables_andPalettesMatchRimaluxSpec() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Rimalux", context.getString(R.string.app_name))
        assertNotNull(androidx.core.content.res.ResourcesCompat.getFont(context, R.font.plus_jakarta_sans))
        val allTypeStyles = listOf(
            com.example.ui.theme.Typography.displayLarge,
            com.example.ui.theme.Typography.displayMedium,
            com.example.ui.theme.Typography.displaySmall,
            com.example.ui.theme.Typography.headlineLarge,
            com.example.ui.theme.Typography.headlineMedium,
            com.example.ui.theme.Typography.headlineSmall,
            com.example.ui.theme.Typography.titleLarge,
            com.example.ui.theme.Typography.titleMedium,
            com.example.ui.theme.Typography.titleSmall,
            com.example.ui.theme.Typography.bodyLarge,
            com.example.ui.theme.Typography.bodyMedium,
            com.example.ui.theme.Typography.bodySmall,
            com.example.ui.theme.Typography.labelLarge,
            com.example.ui.theme.Typography.labelMedium,
            com.example.ui.theme.Typography.labelSmall
        )
        allTypeStyles.forEach { style ->
            assertEquals(com.example.ui.theme.PlusJakartaSansFontFamily, style.fontFamily)
        }
        assertNotNull(context.getDrawable(R.drawable.ic_launcher_foreground))
        assertNotNull(context.getDrawable(R.drawable.ic_launcher_background))
        assertNotNull(context.getDrawable(R.mipmap.ic_launcher))
        assertNotNull(context.getDrawable(R.mipmap.ic_launcher_round))
        assertEquals(80, ArtPattern.entries.size)
        for (pattern in ArtPattern.entries) {
            val decoded = BitmapFactory.decodeResource(context.resources, pattern.previewRes)
            assertNotNull("Expected static preview drawable for ${pattern.id}", decoded)
            decoded?.recycle()
        }
        assertTrue("Expected at least 40 curated starter palettes", StarterPalettes.defaults.size >= 40)
        assertTrue(
            "Starter palettes must contain Monochromatic OLED palette",
            StarterPalettes.defaults.any { it.id == "monochromatic_oled" }
        )
        for (palette in StarterPalettes.defaults) {
            assertTrue(
                "Starter palette ${palette.id} should contain pure non-black colors",
                palette.colors.none { (it and 0xFFFFFFL) == 0L }
            )
        }
    }

    @Test
    fun uiFlow_swipeDeck_aheadPrefetch_rightBounce_doubleTapApply_andCompletelyHiddenSwipeUpSheet() {
        composeTestRule.waitForIdle()
        val viewModel = ViewModelProvider(
            composeTestRule.activity,
            RimaluxViewModel.Factory(composeTestRule.activity)
        )[RimaluxViewModel::class.java]

        awaitPreviewReady(viewModel, requireAheadBuffer = true)
        val initialPreview = requireNotNull(viewModel.uiState.value.preview)
        assertEquals(0.85f, com.example.model.GeneratorConfig().pitchBlackRatio, 0.0001f)
        assertEquals(0.85f, viewModel.uiState.value.config.pitchBlackRatio, 0.0001f)
        assertEquals(0.85f, initialPreview.pitchBlackRatio, 0.0001f)
        assertTrue("Expected pre-generated ahead wallpapers ready", viewModel.uiState.value.aheadReadyCount >= 1)

        // 1. Buttons & recent strip are removed; bottom sheet is completely hidden (not peeking)
        composeTestRule.onNodeWithTag("btn_generate_wallpaper").assertDoesNotExist()
        composeTestRule.onNodeWithTag("btn_apply_wallpaper").assertDoesNotExist()
        composeTestRule.onNodeWithText("recent").assertDoesNotExist()
        composeTestRule.onNodeWithTag("home_settings_sheet").assertDoesNotExist()
        composeTestRule.onNodeWithTag("home_screen_scroll").assertDoesNotExist()
        composeTestRule.onNodeWithTag("vector_preview_canvas").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_bottom_sheet_handle").assertIsDisplayed()

        val previewBounds = composeTestRule.onNodeWithTag("vector_preview_canvas").fetchSemanticsNode().boundsInRoot
        val restingHandleBounds = composeTestRule.onNodeWithTag("home_bottom_sheet_handle").fetchSemanticsNode().boundsInRoot
        assertEquals(0f, previewBounds.left, 1f)
        assertEquals(0f, previewBounds.top, 1f)
        assertTrue(previewBounds.bottom >= restingHandleBounds.bottom)

        // 2. At start with no previous recent wallpaper: Swipe Right bounces and keeps initial wallpaper!
        assertFalse(viewModel.uiState.value.canSwipeRight)
        val bounceBefore = viewModel.uiState.value.rightEdgeBounceCount
        composeTestRule.onNodeWithTag("vector_preview_canvas").performTouchInput {
            swipeRight(startX = width * 0.15f, endX = width * 0.85f, durationMillis = 180L)
        }
        composeTestRule.waitForIdle()
        assertTrue(
            "Swiping right with no recent history must trigger elastic bounce",
            viewModel.uiState.value.rightEdgeBounceCount > bounceBefore
        )
        assertEquals(initialPreview.seed, viewModel.uiState.value.preview?.seed)

        // 3. Swipe Left -> smoothly advances to pre-generated next wallpaper
        composeTestRule.onNodeWithTag("vector_preview_canvas").performTouchInput {
            swipeLeft(startX = width * 0.85f, endX = width * 0.15f, durationMillis = 180L)
        }
        awaitPreviewReady(viewModel, previousSeed = initialPreview.seed, requireAheadBuffer = true)
        val secondPreview = requireNotNull(viewModel.uiState.value.preview)
        assertNotEquals(initialPreview.seed, secondPreview.seed)
        assertTrue(viewModel.uiState.value.canSwipeRight)

        // 4. Swipe Right -> restores previous wallpaper from recent, then Swipe Right again -> bounces!
        composeTestRule.onNodeWithTag("vector_preview_canvas").performTouchInput {
            swipeRight(startX = width * 0.15f, endX = width * 0.85f, durationMillis = 180L)
        }
        composeTestRule.waitForIdle()
        assertEquals(
            "Swiping right must restore the previous wallpaper from recent",
            initialPreview.seed,
            viewModel.uiState.value.preview?.seed
        )
        assertFalse("Should now be at oldest recent item", viewModel.uiState.value.canSwipeRight)

        val bounceCountBeforeSecondEdge = viewModel.uiState.value.rightEdgeBounceCount
        composeTestRule.onNodeWithTag("vector_preview_canvas").performTouchInput {
            swipeRight(startX = width * 0.15f, endX = width * 0.85f, durationMillis = 180L)
        }
        composeTestRule.waitForIdle()
        assertTrue(
            "Swiping right after exhausting recent history must bounce",
            viewModel.uiState.value.rightEdgeBounceCount > bounceCountBeforeSecondEdge
        )
        assertEquals(initialPreview.seed, viewModel.uiState.value.preview?.seed)

        // 5. Double Tap -> opens "Apply Wallpaper To" popup dialog & verifies targets + Save/Share buttons
        composeTestRule.onNodeWithTag("vector_preview_canvas").performTouchInput {
            doubleClick(center)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_set_wallpaper_target").assertIsDisplayed()
        composeTestRule.onNodeWithText("Apply Wallpaper To").assertIsDisplayed()
        composeTestRule.onNodeWithTag("manual_target_home").assertIsDisplayed()
        composeTestRule.onNode(
            hasText("Home Screen") and hasAnyAncestor(hasTestTag("dialog_set_wallpaper_target"))
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag("manual_target_lock").assertIsDisplayed()
        composeTestRule.onNodeWithTag("manual_target_both").assertIsDisplayed()
        composeTestRule.onNodeWithTag("manual_target_both_separate").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_save_to_device").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_share_wallpaper").assertIsDisplayed()

        composeTestRule.onNodeWithTag("manual_target_both_separate").performClick()
        val applyDeadline = System.currentTimeMillis() + 6_000L
        while (System.currentTimeMillis() < applyDeadline && viewModel.uiState.value.isApplyingWallpaper) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeTestRule.waitForIdle()
            Thread.sleep(15L)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_set_wallpaper_target").assertDoesNotExist()
        assertEquals(
            com.example.model.WallpaperTarget.BOTH_SEPARATE,
            viewModel.uiState.value.config.manualTarget
        )

        // 6. Swipe Up anywhere on preview -> opens the hidden bottom sheet, with floating handle positioned above sheet!
        composeTestRule.onNodeWithTag("vector_preview_canvas").performTouchInput {
            swipeUp(startY = height * 0.78f, endY = height * 0.22f, durationMillis = 180L)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_settings_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_screen_scroll").assertIsDisplayed()

        val openSheetBounds = composeTestRule.onNodeWithTag("home_settings_sheet").fetchSemanticsNode().boundsInRoot
        val openHandleBounds = composeTestRule.onNodeWithTag("home_bottom_sheet_handle").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Floating bottom sheet handle (${openHandleBounds.bottom}) must sit above the open bottom sheet (${openSheetBounds.top})",
            openHandleBounds.bottom <= openSheetBounds.top + 4f
        )

        // Adjust True Black slider & verify joined card with Patterns and Colors
        val seedBeforeSliderChange = requireNotNull(viewModel.uiState.value.preview).seed
        composeTestRule.onNodeWithTag("home_screen_scroll")
            .performScrollToNode(hasTestTag("slider_pitch_black_ratio"))
        composeTestRule.onNodeWithTag("slider_pitch_black_ratio")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.68f) }
        composeTestRule.waitForIdle()
        assertEquals(seedBeforeSliderChange, viewModel.uiState.value.preview?.seed)

        // Verify standalone overlay button card is deleted
        composeTestRule.onNodeWithTag("overlay_mode_lock_screen").assertDoesNotExist()

        // Close sheet to test swipe down overlay cycling on full screen preview
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_settings_sheet").assertDoesNotExist()

        // Swipe down on preview canvas cycles overlay mode: NONE -> LOCK_SCREEN
        assertEquals(MockupOverlayMode.NONE, viewModel.uiState.value.mockupMode)
        composeTestRule.onNodeWithTag("vector_preview_canvas").performTouchInput {
            swipeDown(startY = height * 0.25f, endY = height * 0.75f, durationMillis = 180L)
        }
        composeTestRule.waitForIdle()
        assertEquals(MockupOverlayMode.LOCK_SCREEN, viewModel.uiState.value.mockupMode)

        // Swipe down again: LOCK_SCREEN -> HOME_GRID
        composeTestRule.onNodeWithTag("vector_preview_canvas").performTouchInput {
            swipeDown(startY = height * 0.25f, endY = height * 0.75f, durationMillis = 180L)
        }
        composeTestRule.waitForIdle()
        assertEquals(MockupOverlayMode.HOME_GRID, viewModel.uiState.value.mockupMode)

        // Swipe down again: HOME_GRID -> NONE
        composeTestRule.onNodeWithTag("vector_preview_canvas").performTouchInput {
            swipeDown(startY = height * 0.25f, endY = height * 0.75f, durationMillis = 180L)
        }
        composeTestRule.waitForIdle()
        assertEquals(MockupOverlayMode.NONE, viewModel.uiState.value.mockupMode)

        // Swipe Left -> applies new pitch black ratio on next wallpaper & clears pending notice
        composeTestRule.onNodeWithTag("vector_preview_canvas").performTouchInput {
            swipeLeft(startX = width * 0.85f, endX = width * 0.15f, durationMillis = 180L)
        }
        awaitPreviewReady(viewModel, previousSeed = seedBeforeSliderChange, requireAheadBuffer = true)
        composeTestRule.onNodeWithTag("preview_pending_notice").assertDoesNotExist()

        // 7. Open sheet via floating handle -> navigate to Patterns page -> pick pattern -> swipe left on Home
        val seedBeforePatternPick = requireNotNull(viewModel.uiState.value.preview).seed
        composeTestRule.onNodeWithTag("home_bottom_sheet_handle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pill_open_patterns").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Navigate back").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chip_pattern_cat_tessellation").assertDoesNotExist()
        composeTestRule.onNodeWithTag("patterns_screen_list")
            .performScrollToNode(hasTestTag("pattern_card_ammann_quasicrystal"))
        composeTestRule.onNodeWithTag("pattern_card_ammann_quasicrystal").performClick()
        composeTestRule.onNodeWithTag("patterns_screen_list")
            .performScrollToNode(hasTestTag("chip_pattern_cat_tessellation"))
        composeTestRule.onNodeWithTag("chip_pattern_cat_tessellation").assertIsDisplayed()

        composeTestRule.onNodeWithTag("btn_back_to_home").performClick()
        composeTestRule.waitForIdle()
        assertEquals(RimaluxScreen.HOME, viewModel.uiState.value.currentScreen)
        // Bottom sheet is preserved as UP (open) when returning from PATTERNS page
        composeTestRule.onNodeWithTag("home_settings_sheet").assertIsDisplayed()

        // Close sheet to view small preview info text overlay
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_settings_sheet").assertDoesNotExist()
        composeTestRule.onNodeWithTag("preview_info_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("preview_pending_notice").assertIsDisplayed()

        composeTestRule.onNodeWithTag("vector_preview_canvas").performTouchInput {
            swipeLeft(startX = width * 0.85f, endX = width * 0.15f, durationMillis = 180L)
        }
        awaitPreviewReady(viewModel, previousSeed = seedBeforePatternPick, requireAheadBuffer = true)
        assertEquals(ArtPattern.AMMANN_QUASICRYSTAL, viewModel.uiState.value.preview?.pattern)
        composeTestRule.onNodeWithTag("preview_pending_notice").assertDoesNotExist()

        // 8. Open sheet via swipe up -> verify Palettes & Auto-Change controls -> Back hides sheet completely
        composeTestRule.onNodeWithTag("vector_preview_canvas").performTouchInput {
            swipeUp(startY = height * 0.78f, endY = height * 0.22f, durationMillis = 180L)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pill_open_palettes").performClick()
        composeTestRule.waitForIdle()
        assertEquals(RimaluxScreen.PALETTES, viewModel.uiState.value.currentScreen)
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.waitForIdle()
        assertEquals(RimaluxScreen.HOME, viewModel.uiState.value.currentScreen)
        // Bottom sheet is preserved as UP (open) when returning from PALETTES page
        composeTestRule.onNodeWithTag("home_settings_sheet").assertIsDisplayed()

        // Verify quick popup toggles for Pattern Layout & Color Fill Style inside Studio bottom sheet
        composeTestRule.onNodeWithTag("home_screen_scroll")
            .performScrollToNode(hasTestTag("pill_open_pattern_layout"))
        composeTestRule.onNodeWithTag("pill_open_pattern_layout").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_pattern_layout").assertIsDisplayed()
        composeTestRule.onNodeWithTag("layout_option_center").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_pattern_layout").assertDoesNotExist()
        assertEquals(PatternWeightMode.CENTER_WEIGHTED, viewModel.uiState.value.config.patternWeightMode)

        composeTestRule.onNodeWithTag("home_screen_scroll")
            .performScrollToNode(hasTestTag("pill_open_color_fill"))
        composeTestRule.onNodeWithTag("pill_open_color_fill").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_color_fill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fill_option_gradient").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_color_fill").assertDoesNotExist()
        assertEquals(ColorFillMode.GRADIENT, viewModel.uiState.value.config.colorFillMode)

        // Verify Apply To & Interval rows are disabled while Auto-Change Wallpaper is OFF
        composeTestRule.onNodeWithText("automatic target").assertDoesNotExist()
        composeTestRule.onNodeWithTag("home_screen_scroll")
            .performScrollToNode(hasTestTag("pill_open_interval"))
        composeTestRule.onNodeWithText("Apply To").assertIsDisplayed()
        assertFalse(viewModel.uiState.value.config.autoRotateEnabled)
        composeTestRule.onNodeWithTag("pill_open_auto_target").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("pill_open_interval").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("pill_open_auto_target").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_auto_target").assertDoesNotExist()
        composeTestRule.onNodeWithTag("pill_open_interval").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_interval_picker").assertDoesNotExist()

        // Toggle Auto-Change Wallpaper ON -> Apply To & Interval become enabled and open unified popups
        composeTestRule.onNodeWithTag("home_screen_scroll")
            .performScrollToNode(hasTestTag("switch_auto_rotate"))
        composeTestRule.onNodeWithTag("switch_auto_rotate").performClick()
        val autoEnableDeadline = System.currentTimeMillis() + 5_000L
        while (System.currentTimeMillis() < autoEnableDeadline && !viewModel.uiState.value.config.autoRotateEnabled) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeTestRule.waitForIdle()
            Thread.sleep(15L)
        }
        composeTestRule.waitForIdle()
        assertTrue(viewModel.uiState.value.config.autoRotateEnabled)

        composeTestRule.onNodeWithTag("home_screen_scroll")
            .performScrollToNode(hasTestTag("pill_open_auto_target"))
        composeTestRule.onNodeWithTag("pill_open_auto_target").assertIsEnabled()
        composeTestRule.onNodeWithTag("pill_open_auto_target").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_auto_target").assertIsDisplayed()
        composeTestRule.onNodeWithTag("auto_target_both_separate").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_auto_target").assertDoesNotExist()
        assertEquals(
            com.example.model.WallpaperTarget.BOTH_SEPARATE,
            viewModel.uiState.value.config.autoTarget
        )

        composeTestRule.onNodeWithTag("home_screen_scroll")
            .performScrollToNode(hasTestTag("pill_open_interval"))
        composeTestRule.onNodeWithTag("pill_open_interval").assertIsEnabled()
        composeTestRule.onNodeWithTag("pill_open_interval").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_interval_picker").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chip_interval_15").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_interval_picker").assertDoesNotExist()
        assertEquals(15, viewModel.uiState.value.config.intervalMinutes)

        // Verify tapping "Custom…" inside interval popup transitions to custom minutes dialog
        composeTestRule.onNodeWithTag("pill_open_interval").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_custom_interval").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_interval_picker").assertDoesNotExist()
        composeTestRule.onNodeWithTag("dialog_custom_interval").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_custom_interval_minutes").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_cancel_interval_picker").performClick()
        composeTestRule.waitForIdle()

        // Clean up Auto-Change switch back to OFF
        viewModel.setAutoRotateEnabled(false)
        composeTestRule.waitForIdle()

        // System Back closes sheet back to completely hidden
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_settings_sheet").assertDoesNotExist()
    }

    @Test
    fun swipeLeftAndRight_neverFlashesPreviousWallpaperOnAnyFrame() {
        composeTestRule.waitForIdle()
        val viewModel = ViewModelProvider(
            composeTestRule.activity,
            RimaluxViewModel.Factory(composeTestRule.activity)
        )[RimaluxViewModel::class.java]

        awaitPreviewReady(viewModel, requireAheadBuffer = true)
        val seedA = requireNotNull(viewModel.uiState.value.preview).seed
        val seedB = requireNotNull(viewModel.uiState.value.nextPreview).seed
        assertNotEquals(seedA, seedB)

        composeTestRule.onNodeWithTag("active_wallpaper_seed_$seedA").assertIsDisplayed()

        composeTestRule.mainClock.autoAdvance = false
        try {
            // 1. Swipe Left (seedA -> seedB): inspect every single 16ms frame
            composeTestRule.onNodeWithTag("vector_preview_canvas").performTouchInput {
                swipeLeft(startX = width * 0.85f, endX = width * 0.15f, durationMillis = 120L)
            }

            var transitionedToB = false
            repeat(50) { frameIdx ->
                composeTestRule.mainClock.advanceTimeByFrame()
                Shadows.shadowOf(Looper.getMainLooper()).idle()

                val aVisible = composeTestRule
                    .onAllNodes(hasTestTag("active_wallpaper_seed_$seedA"))
                    .fetchSemanticsNodes()
                    .isNotEmpty()
                val bVisible = composeTestRule
                    .onAllNodes(hasTestTag("active_wallpaper_seed_$seedB"))
                    .fetchSemanticsNodes()
                    .isNotEmpty()

                assertFalse(
                    "Frame $frameIdx after swipe left: previous ($seedA) and current ($seedB) must never crossfade/overlap",
                    aVisible && bVisible
                )
                assertTrue(
                    "Frame $frameIdx after swipe left: active slot must never go blank",
                    aVisible || bVisible
                )
                if (bVisible) {
                    transitionedToB = true
                }
                if (transitionedToB) {
                    assertFalse(
                        "Frame $frameIdx after swipe left commit: previous wallpaper ($seedA) flashed back!",
                        aVisible
                    )
                    assertTrue(
                        "Frame $frameIdx after swipe left commit: current wallpaper ($seedB) must remain displayed",
                        bVisible
                    )
                }
            }
            assertTrue("Swipe left should have transitioned to next wallpaper ($seedB)", transitionedToB)

            // 2. Swipe Right (seedB -> seedA): inspect every single 16ms frame
            composeTestRule.onNodeWithTag("vector_preview_canvas").performTouchInput {
                swipeRight(startX = width * 0.15f, endX = width * 0.85f, durationMillis = 120L)
            }

            var transitionedBackToA = false
            repeat(50) { frameIdx ->
                composeTestRule.mainClock.advanceTimeByFrame()
                Shadows.shadowOf(Looper.getMainLooper()).idle()

                val aVisible = composeTestRule
                    .onAllNodes(hasTestTag("active_wallpaper_seed_$seedA"))
                    .fetchSemanticsNodes()
                    .isNotEmpty()
                val bVisible = composeTestRule
                    .onAllNodes(hasTestTag("active_wallpaper_seed_$seedB"))
                    .fetchSemanticsNodes()
                    .isNotEmpty()

                assertFalse(
                    "Frame $frameIdx after swipe right: previous ($seedB) and restored ($seedA) must never crossfade/overlap",
                    aVisible && bVisible
                )
                assertTrue(
                    "Frame $frameIdx after swipe right: active slot must never go blank",
                    aVisible || bVisible
                )
                if (aVisible) {
                    transitionedBackToA = true
                }
                if (transitionedBackToA) {
                    assertFalse(
                        "Frame $frameIdx after swipe right commit: previous wallpaper ($seedB) flashed back!",
                        bVisible
                    )
                    assertTrue(
                        "Frame $frameIdx after swipe right commit: restored wallpaper ($seedA) must remain displayed",
                        aVisible
                    )
                }
            }
            assertTrue("Swipe right should have restored wallpaper ($seedA)", transitionedBackToA)
        } finally {
            composeTestRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun dynamicThemeAndTypography_meetMaterial3ContrastAndHierarchyGuidelines() {
        // 1. Verify calm M3 typography weights (Normal for Display/Headline/TitleLarge/Body; Medium for TitleMedium/TitleSmall/Label)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Normal, com.example.ui.theme.Typography.headlineMedium.fontWeight)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Normal, com.example.ui.theme.Typography.headlineSmall.fontWeight)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Normal, com.example.ui.theme.Typography.titleLarge.fontWeight)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Medium, com.example.ui.theme.Typography.titleMedium.fontWeight)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Medium, com.example.ui.theme.Typography.titleSmall.fontWeight)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Medium, com.example.ui.theme.Typography.labelLarge.fontWeight)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Normal, com.example.ui.theme.Typography.bodyLarge.fontWeight)

        // 2. Verify OLED pitch-black base, WCAG contrast & monotonic M3 dark tonal container hierarchy across all palettes + dark edge cases
        val edgeCasePalettes = listOf(
            listOf(0xFF000000L, 0xFF050505L),               // Pure pitch-black / ultra-dark swatches
            listOf(0xFF04050CL, 0xFF090208L, 0xFF020906L),  // Near-black chromatic swatches
            listOf(0xFF121318L, 0xFF00F5D4L, 0xFF1A1C24L),  // Dark shadow first swatch + vibrant second swatch
            listOf(0xFF0A14FFL, 0xFF220066L, 0xFF001844L),  // Deep saturated blues/violets
            listOf(0xFF3A000CL, 0xFF1A0505L, 0xFF551100L),  // Very dark reds
            listOf(0xFFFAFCFFL, 0xFFEFF5FFL, 0xFFFFF8F0L),  // Near-white pastels
            listOf(0xFF444850L, 0xFF888C94L, 0xFF222428L)   // Low-chroma neutral grays
        )
        val allPalettesToAudit = StarterPalettes.defaults.map { it.colors } + edgeCasePalettes + listOf(null)

        allPalettesToAudit.forEachIndexed { idx, colors ->
            val scheme = com.example.ui.theme.createDynamicColorScheme(colors)

            assertEquals(
                "Palette #$idx background must be true OLED pitch black",
                androidx.compose.ui.graphics.Color.Black,
                scheme.background
            )
            assertEquals(
                "Palette #$idx surface must be true OLED pitch black",
                androidx.compose.ui.graphics.Color.Black,
                scheme.surface
            )
            assertTrue(
                "Palette #$idx primary/background contrast must exceed 7:1 so accent never gets too dark to read",
                com.example.ui.theme.contrastRatio(scheme.primary, scheme.background) >= 7.0f
            )
            assertTrue(
                "Palette #$idx primary/surfaceContainer contrast must exceed 7:1",
                com.example.ui.theme.contrastRatio(scheme.primary, scheme.surfaceContainer) >= 7.0f
            )
            assertTrue(
                "Palette #$idx primary/onPrimary contrast must meet WCAG AA (4.5:1)",
                com.example.ui.theme.contrastRatio(scheme.primary, scheme.onPrimary) >= 4.5f
            )
            assertTrue(
                "Palette #$idx onPrimaryContainer/primaryContainer contrast must meet WCAG AA (4.5:1)",
                com.example.ui.theme.contrastRatio(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.5f
            )
            assertTrue(
                "Palette #$idx onSecondaryContainer/secondaryContainer contrast must meet WCAG AA (4.5:1)",
                com.example.ui.theme.contrastRatio(scheme.onSecondaryContainer, scheme.secondaryContainer) >= 4.5f
            )
            assertTrue(
                "Palette #$idx onSurface/surfaceContainer contrast must exceed 7:1",
                com.example.ui.theme.contrastRatio(scheme.onSurface, scheme.surfaceContainer) >= 7.0f
            )
            assertTrue(
                "Palette #$idx onSurfaceVariant/surfaceContainer contrast must meet WCAG AA (4.5:1)",
                com.example.ui.theme.contrastRatio(scheme.onSurfaceVariant, scheme.surfaceContainer) >= 4.5f
            )

            // Verify OLED pitch-black base (0f) + strict monotonic M3 dark tonal container progression
            val lumLowest = com.example.ui.theme.relativeLuminance(scheme.surfaceContainerLowest)
            val lumBase = com.example.ui.theme.relativeLuminance(scheme.surface)
            val lumLow = com.example.ui.theme.relativeLuminance(scheme.surfaceContainerLow)
            val lumContainer = com.example.ui.theme.relativeLuminance(scheme.surfaceContainer)
            val lumHigh = com.example.ui.theme.relativeLuminance(scheme.surfaceContainerHigh)
            val lumHighest = com.example.ui.theme.relativeLuminance(scheme.surfaceContainerHighest)

            assertEquals("Palette #$idx lowest must be 0f OLED black", 0f, lumLowest, 0.0001f)
            assertEquals("Palette #$idx base must be 0f OLED black", 0f, lumBase, 0.0001f)
            assertTrue("Palette #$idx base < low", lumBase < lumLow)
            assertTrue("Palette #$idx low < container", lumLow < lumContainer)
            assertTrue("Palette #$idx container < high", lumContainer < lumHigh)
            assertTrue("Palette #$idx high < highest", lumHigh < lumHighest)
            assertTrue("Palette #$idx highest stays ultra-dark (< 0.03f)", lumHighest < 0.03f)
        }
    }

    @Test
    fun doubleTapDialog_saveToDeviceAndShareWallpaper_exportsLosslessFullResPng() {
        composeTestRule.waitForIdle()
        val activity = composeTestRule.activity
        val viewModel = ViewModelProvider(
            activity,
            RimaluxViewModel.Factory(activity)
        )[RimaluxViewModel::class.java]

        awaitPreviewReady(viewModel)
        val expectedSpec = com.example.platform.WallpaperRenderer.getTargetWallpaperSpec(activity)

        // 1. Double tap preview -> tap "Save to Device"
        composeTestRule.onNodeWithTag("vector_preview_canvas").performTouchInput {
            doubleClick(center)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_set_wallpaper_target").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_save_to_device").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_save_to_device").performClick()

        val saveDeadline = System.currentTimeMillis() + 6_000L
        while (
            System.currentTimeMillis() < saveDeadline &&
            (viewModel.uiState.value.isExportingWallpaper || viewModel.uiState.value.statusMessage == null)
        ) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeTestRule.waitForIdle()
            Thread.sleep(15L)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dialog_set_wallpaper_target").assertDoesNotExist()
        val savedStatus = viewModel.uiState.value.statusMessage.orEmpty()
        assertTrue(
            "Expected saved confirmation status message, got: '$savedStatus'",
            savedStatus.startsWith("Saved to Pictures")
        )
        assertTrue(
            "Expected full native resolution in status message: '$savedStatus'",
            savedStatus.contains("${expectedSpec.widthPx}×${expectedSpec.heightPx}")
        )

        // 2. Double tap preview -> tap "Share" -> verify FileProvider URI + ACTION_CHOOSER intent + valid PNG
        Shadows.shadowOf(activity).clearNextStartedActivities()
        composeTestRule.onNodeWithTag("vector_preview_canvas").performTouchInput {
            doubleClick(center)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_set_wallpaper_target").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_share_wallpaper").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_share_wallpaper").performClick()

        var startedChooser: Intent? = null
        val shareDeadline = System.currentTimeMillis() + 6_000L
        while (System.currentTimeMillis() < shareDeadline && startedChooser == null) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeTestRule.waitForIdle()
            startedChooser = Shadows.shadowOf(activity).nextStartedActivity
            if (startedChooser == null) {
                Thread.sleep(15L)
            }
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dialog_set_wallpaper_target").assertDoesNotExist()
        val chooserIntent = requireNotNull(startedChooser) { "Expected system share chooser intent to be launched" }
        assertEquals(Intent.ACTION_CHOOSER, chooserIntent.action)

        @Suppress("DEPRECATION")
        val sendIntent = requireNotNull(chooserIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
        assertEquals(Intent.ACTION_SEND, sendIntent.action)
        assertEquals("image/png", sendIntent.type)
        assertTrue((sendIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)

        @Suppress("DEPRECATION")
        val streamUri = requireNotNull(sendIntent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals("content", streamUri.scheme)
        assertEquals("${activity.packageName}.fileprovider", streamUri.authority)

        val sharedBitmap = activity.contentResolver.openInputStream(streamUri)?.use { input ->
            BitmapFactory.decodeStream(input)
        }
        assertNotNull("Expected FileProvider stream URI to decode a valid PNG bitmap", sharedBitmap)
        assertEquals(expectedSpec.widthPx, sharedBitmap!!.width)
        assertEquals(expectedSpec.heightPx, sharedBitmap.height)

        // Verify shared PNG contains subtle watermark strictly inside social safe-zone while raw render does not
        val activePreview = requireNotNull(viewModel.uiState.value.preview)
        val cleanBitmap = com.example.platform.WallpaperRenderer.renderToBitmap(
            scene = activePreview.scene,
            paletteColors = activePreview.colors,
            pitchBlackRatio = activePreview.pitchBlackRatio,
            widthPx = expectedSpec.widthPx,
            heightPx = expectedSpec.heightPx,
            colorFillMode = activePreview.colorFillMode,
            densityDpi = expectedSpec.densityDpi
        )
        try {
            val safeZone = com.example.engine.WatermarkPlacementSolver.computeSocialSafeZone(
                expectedSpec.widthPx,
                expectedSpec.heightPx
            )
            var watermarkPixelCount = 0
            var outsideSafeZoneDiffCount = 0
            var maxDeltaOnPureBlack = 0
            val cleanRow = IntArray(expectedSpec.widthPx)
            val sharedRow = IntArray(expectedSpec.widthPx)
            for (y in 0 until expectedSpec.heightPx) {
                cleanBitmap.getPixels(cleanRow, 0, expectedSpec.widthPx, 0, y, expectedSpec.widthPx, 1)
                sharedBitmap.getPixels(sharedRow, 0, expectedSpec.widthPx, 0, y, expectedSpec.widthPx, 1)
                val inSafeY = y in safeZone.topPx..safeZone.bottomPx
                for (x in 0 until expectedSpec.widthPx) {
                    val c = cleanRow[x] and 0x00FFFFFF
                    val s = sharedRow[x] and 0x00FFFFFF
                    if (c != s) {
                        watermarkPixelCount += 1
                        if (!inSafeY || x !in safeZone.leftPx..safeZone.rightPx) {
                            outsideSafeZoneDiffCount += 1
                        }
                        if (c == 0) {
                            val dr = kotlin.math.abs(((s ushr 16) and 0xFF))
                            val dg = kotlin.math.abs(((s ushr 8) and 0xFF))
                            val db = kotlin.math.abs((s and 0xFF))
                            val peak = maxOf(dr, dg, db)
                            if (peak > maxDeltaOnPureBlack) {
                                maxDeltaOnPureBlack = peak
                            }
                        }
                    }
                }
            }
            assertTrue(
                "Expected shared PNG to render watermark glyphs (>0 modified pixels), got $watermarkPixelCount",
                watermarkPixelCount > 25
            )
            assertEquals(
                "Watermark pixels must never spill outside social media safe zone ($safeZone)",
                0,
                outsideSafeZoneDiffCount
            )
            assertTrue(
                "Watermark on #000000 must stay barely noticeable (peak RGB <= 60), got $maxDeltaOnPureBlack",
                maxDeltaOnPureBlack <= 60
            )
        } finally {
            cleanBitmap.recycle()
            sharedBitmap.recycle()
        }
    }

    @Test
    fun shortFlickUpAndShortPullWithReleaseJitter_reliablyOpenBottomSheetWithoutDropping() {
        composeTestRule.waitForIdle()
        val viewModel = ViewModelProvider(
            composeTestRule.activity,
            RimaluxViewModel.Factory(composeTestRule.activity)
        )[RimaluxViewModel::class.java]

        awaitPreviewReady(viewModel)
        composeTestRule.onNodeWithTag("home_settings_sheet").assertDoesNotExist()

        // 1. Short casual upward flick (only ~7% screen height) opens bottom sheet cleanly
        composeTestRule.onNodeWithTag("vector_preview_canvas").performTouchInput {
            swipeUp(startY = height * 0.85f, endY = height * 0.78f, durationMillis = 90L)
        }
        composeTestRule.waitForIdle()
        assertTrue(viewModel.uiState.value.isBottomSheetOpen)
        composeTestRule.onNodeWithTag("home_settings_sheet").assertIsDisplayed()

        // Close sheet via Back
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.waitForIdle()
        assertFalse(viewModel.uiState.value.isBottomSheetOpen)
        composeTestRule.onNodeWithTag("home_settings_sheet").assertDoesNotExist()

        // 2. Short upward pull + pause + tiny downward thumb lift-off jitter must NOT peek and drop back down
        composeTestRule.onNodeWithTag("vector_preview_canvas").performTouchInput {
            val start = Offset(width * 0.52f, height * 0.84f)
            down(start)
            moveBy(Offset(4f, -32f), delayMillis = 16L)
            moveBy(Offset(3f, -44f), delayMillis = 16L)
            moveBy(Offset(0f, -24f), delayMillis = 16L)
            // Finger pauses at top of short pull, then has slight downward release micro-jitter
            advanceEventTime(140L)
            moveBy(Offset(0f, 6f), delayMillis = 16L)
            up()
        }
        composeTestRule.waitForIdle()
        assertTrue(
            "Short upward pull with slight release jitter must open the bottom sheet instead of dropping back down",
            viewModel.uiState.value.isBottomSheetOpen
        )
        composeTestRule.onNodeWithTag("home_settings_sheet").assertIsDisplayed()
    }

    @Test
    fun autoChangeTroubleshootingButtonUnderInterval_navigatesAndLaunchesVendorShortcuts() {
        // Verify OEM manufacturer detection across major brands
        assertEquals(OemVendor.XIAOMI, OemBackgroundHelper.detectDeviceVendor("Xiaomi", "POCOPHONE"))
        assertEquals(OemVendor.XIAOMI, OemBackgroundHelper.detectDeviceVendor("Redmi", "Redmi"))
        assertEquals(OemVendor.SAMSUNG, OemBackgroundHelper.detectDeviceVendor(" samsung ", "galaxy"))
        assertEquals(OemVendor.OPPO_ONEPLUS, OemBackgroundHelper.detectDeviceVendor("OnePlus", "OnePlus"))
        assertEquals(OemVendor.OPPO_ONEPLUS, OemBackgroundHelper.detectDeviceVendor("realme", "RMX"))
        assertEquals(OemVendor.VIVO, OemBackgroundHelper.detectDeviceVendor("vivo", "iQOO"))
        assertEquals(OemVendor.HUAWEI, OemBackgroundHelper.detectDeviceVendor("HUAWEI", "HONOR"))
        assertEquals(OemVendor.ASUS, OemBackgroundHelper.detectDeviceVendor("asus", "ROG"))
        assertEquals(OemVendor.STOCK_ANDROID, OemBackgroundHelper.detectDeviceVendor("Google", "Pixel"))

        composeTestRule.waitForIdle()
        val activity = composeTestRule.activity
        val viewModel = ViewModelProvider(
            activity,
            RimaluxViewModel.Factory(activity)
        )[RimaluxViewModel::class.java]

        awaitPreviewReady(viewModel)

        // Open settings bottom sheet
        composeTestRule.onNodeWithTag("home_bottom_sheet_handle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_settings_sheet").assertIsDisplayed()

        // Toggle Auto-Change ON -> verify static description remains and NO countdown timer is shown
        composeTestRule.onNodeWithTag("home_screen_scroll")
            .performScrollToNode(hasTestTag("switch_auto_rotate"))
        composeTestRule.onNodeWithTag("switch_auto_rotate").performClick()
        val enableDeadline = System.currentTimeMillis() + 4_000L
        while (System.currentTimeMillis() < enableDeadline && !viewModel.uiState.value.config.autoRotateEnabled) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeTestRule.waitForIdle()
            Thread.sleep(15L)
        }
        assertTrue(viewModel.uiState.value.config.autoRotateEnabled)
        composeTestRule.onNodeWithText("Automatically updates your wallpaper at set intervals").assertIsDisplayed()
        composeTestRule.onNodeWithText("Next change in", substring = true).assertDoesNotExist()

        // Scroll to troubleshooting pill button and verify shortened text + placement under Interval
        composeTestRule.onNodeWithTag("home_screen_scroll")
            .performScrollToNode(hasTestTag("btn_auto_change_help"))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Auto-change stopped working?").assertIsDisplayed()
        val intervalBounds = composeTestRule.onNodeWithTag("pill_open_interval").fetchSemanticsNode().boundsInRoot
        val helpBtnBounds = composeTestRule.onNodeWithTag("btn_auto_change_help").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Troubleshooting pill (${helpBtnBounds.top}) must be positioned under Interval (${intervalBounds.bottom})",
            helpBtnBounds.top >= intervalBounds.bottom - 4f
        )

        // Tap troubleshooting button -> opens simplified Auto-Change Help screen
        composeTestRule.onNodeWithTag("btn_auto_change_help").performClick()
        composeTestRule.waitForIdle()
        assertEquals(RimaluxScreen.AUTO_CHANGE_HELP, viewModel.uiState.value.currentScreen)
        composeTestRule.onNodeWithTag("auto_change_help_screen").assertIsDisplayed()

        // Live Android battery status card & manual re-sync button must NOT exist
        composeTestRule.onNodeWithTag("card_battery_optimization_status").assertDoesNotExist()
        composeTestRule.onNodeWithTag("badge_battery_status").assertDoesNotExist()
        composeTestRule.onNodeWithTag("btn_resync_auto_schedule").assertDoesNotExist()

        // Verify updated explanation copy and absence of "Select a brand below" text & inline chips
        composeTestRule.onNodeWithText(
            "Many phone brands include aggressive battery savers that force-stop background apps which pause auto-change when your screen is off. Setting Rimalux to Unrestricted lets auto-change run reliably with virtually zero battery drain."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Select a brand below", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("chip_vendor_xiaomi").assertDoesNotExist()

        // Tap "Not your device?" -> opens brand select popup -> select Xiaomi
        composeTestRule.onNodeWithTag("auto_change_help_list")
            .performScrollToNode(hasTestTag("btn_not_your_device"))
        composeTestRule.onNodeWithTag("btn_not_your_device").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_not_your_device").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dialog_select_brand").assertIsDisplayed()
        composeTestRule.onNodeWithTag("brand_select_list")
            .performScrollToNode(hasTestTag("chip_vendor_xiaomi"))
        composeTestRule.onNodeWithTag("chip_vendor_xiaomi").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_select_brand").assertDoesNotExist()

        composeTestRule.onNodeWithTag("auto_change_help_list")
            .performScrollToNode(hasTestTag("btn_open_app_battery_settings"))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Xiaomi · Redmi · POCO").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_open_vendor_shortcut").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_open_app_battery_settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open App Info").assertIsDisplayed()

        val vendorBtnBounds = composeTestRule.onNodeWithTag("btn_open_vendor_shortcut").fetchSemanticsNode().boundsInRoot
        val appInfoBtnBounds = composeTestRule.onNodeWithTag("btn_open_app_battery_settings").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "App Info shortcut (${appInfoBtnBounds.top}) must sit directly under vendor shortcut (${vendorBtnBounds.bottom})",
            appInfoBtnBounds.top >= vendorBtnBounds.bottom - 2f
        )

        // Tap vendor shortcut -> launches vendor settings or App Info fallback
        Shadows.shadowOf(activity).clearNextStartedActivities()
        composeTestRule.onNodeWithTag("btn_open_vendor_shortcut").performClick()
        composeTestRule.waitForIdle()
        val vendorOrFallbackIntent = requireNotNull(Shadows.shadowOf(activity).nextStartedActivity)
        assertNotNull(vendorOrFallbackIntent)

        // Tap "Open App Info" underneath -> launches ACTION_APPLICATION_DETAILS_SETTINGS for package
        Shadows.shadowOf(activity).clearNextStartedActivities()
        composeTestRule.onNodeWithTag("btn_open_app_battery_settings").performClick()
        composeTestRule.waitForIdle()
        val appDetailsIntent = requireNotNull(Shadows.shadowOf(activity).nextStartedActivity)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appDetailsIntent.action)
        assertEquals("package:${activity.packageName}", appDetailsIntent.dataString)

        // Verify updated Quick Recovery Tip description
        composeTestRule.onNodeWithTag("auto_change_help_list")
            .performScrollToNode(hasTestTag("card_recovery_tip"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(
            "If auto-change ever stops, simply opening Rimalux once will automatically resume wallpaper auto-change."
        ).assertIsDisplayed()

        // Navigate back -> returns to Home with bottom sheet still open
        composeTestRule.onNodeWithTag("btn_back_to_home").performClick()
        composeTestRule.waitForIdle()
        assertEquals(RimaluxScreen.HOME, viewModel.uiState.value.currentScreen)
        composeTestRule.onNodeWithTag("home_settings_sheet").assertIsDisplayed()

        // Clean up auto-rotate toggle
        viewModel.setAutoRotateEnabled(false)
        composeTestRule.waitForIdle()
    }

    @Test
    fun likeTheAppButton_opensLikeAppDialog_andLaunchesRatingOrSendThanksDialog() {
        composeTestRule.waitForIdle()
        val activity = composeTestRule.activity
        val viewModel = ViewModelProvider(
            activity,
            RimaluxViewModel.Factory(activity)
        )[RimaluxViewModel::class.java]

        awaitPreviewReady(viewModel)

        // Open settings bottom sheet
        composeTestRule.onNodeWithTag("home_bottom_sheet_handle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_settings_sheet").assertIsDisplayed()

        // Scroll to "Enjoying Rimalux?" button
        composeTestRule.onNodeWithTag("home_screen_scroll")
            .performScrollToNode(hasTestTag("btn_like_the_app"))
        composeTestRule.onNodeWithTag("btn_like_the_app").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enjoying Rimalux?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_like_the_app").performClick()
        composeTestRule.waitForIdle()

        // Verify Enjoying Rimalux? popup dialog opens with consistent title & updated copy
        composeTestRule.onNodeWithTag("dialog_like_app").assertIsDisplayed()
        composeTestRule.onNode(
            hasText("Enjoying Rimalux?") and hasAnyAncestor(hasTestTag("dialog_like_app"))
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Rimalux is 100% free with no ads or tracking. If it made your setup look nice, leaving a review or sending a tip is always appreciated."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_dialog_rate_app").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_dialog_send_thanks").assertIsDisplayed()

        // Tap "Send a Thanks" -> opens Send Thanks tip dialog with single-column direct actions including $3
        composeTestRule.onNodeWithTag("btn_dialog_send_thanks").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_like_app").assertDoesNotExist()
        composeTestRule.onNodeWithTag("dialog_send_thanks").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "No perks, no unlocked features, and no roadmap promises—just a simple tip jar if you feel like dropping a quick thank you."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_confirm_send_tip").assertDoesNotExist()

        val tipTags = listOf("btn_tip_1", "btn_tip_3", "btn_tip_5", "btn_tip_10", "btn_tip_20")
        val tipBounds = tipTags.map { tag ->
            composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
            composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        }
        for (i in 1 until tipBounds.size) {
            assertEquals("Tip options must align in a single column", tipBounds[0].left, tipBounds[i].left, 1f)
            assertTrue(
                "Tip option $i must sit vertically below option ${i - 1}",
                tipBounds[i].top >= tipBounds[i - 1].bottom - 1f
            )
        }

        // Direct action tap on $3 immediately sends thanks and closes dialog
        composeTestRule.onNodeWithTag("btn_tip_3").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dialog_send_thanks").assertDoesNotExist()
        val status = viewModel.uiState.value.statusMessage.orEmpty()
        assertEquals("Thank you so much for the $3! Your tip means the world.", status)

        // Reopen "Enjoying Rimalux?" -> tap "Rate the App"
        composeTestRule.onNodeWithTag("btn_like_the_app").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_like_app").assertIsDisplayed()

        Shadows.shadowOf(activity).clearNextStartedActivities()
        composeTestRule.onNodeWithTag("btn_dialog_rate_app").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dialog_like_app").assertDoesNotExist()
        val rateIntent = requireNotNull(Shadows.shadowOf(activity).nextStartedActivity)
        assertEquals(Intent.ACTION_VIEW, rateIntent.action)
    }

    @Test
    fun patternsScreen_patternLayoutNormalAndCenterWeighted_updatesMainRowAndGeneratesCenterWeightedPreview() {
        composeTestRule.waitForIdle()
        val activity = composeTestRule.activity
        val viewModel = ViewModelProvider(
            activity,
            RimaluxViewModel.Factory(activity)
        )[RimaluxViewModel::class.java]

        awaitPreviewReady(viewModel, requireAheadBuffer = true)
        assertEquals(PatternWeightMode.NORMAL, viewModel.uiState.value.config.patternWeightMode)

        // 1. Open bottom sheet on main page -> verify Patterns row shows "Random (80)" alongside selection mode
        composeTestRule.onNodeWithTag("home_bottom_sheet_handle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_settings_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Random (80)").assertIsDisplayed()

        // 2. Open Pattern Layout popup from Studio bottom sheet -> select "Center Weighted"
        composeTestRule.onNodeWithTag("home_screen_scroll")
            .performScrollToNode(hasTestTag("pill_open_pattern_layout"))
        composeTestRule.onNodeWithTag("pill_open_pattern_layout").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_pattern_layout").assertIsDisplayed()
        composeTestRule.onNodeWithTag("layout_option_center").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_pattern_layout").assertDoesNotExist()
        assertEquals(PatternWeightMode.CENTER_WEIGHTED, viewModel.uiState.value.config.patternWeightMode)
        composeTestRule.onNodeWithText("Random (80)").assertIsDisplayed()

        // Re-open Patterns -> verify LazyVerticalGrid 2-column phone layout, category FilterChips, and custom pattern pick
        composeTestRule.onNodeWithTag("pill_open_patterns").performClick()
        composeTestRule.waitForIdle()
        val firstPatternId = ArtPattern.entries[0].id
        val secondPatternId = ArtPattern.entries[1].id
        composeTestRule.onNodeWithTag("patterns_screen_list")
            .performScrollToNode(hasTestTag("pattern_card_$firstPatternId"))
        val card0Bounds = composeTestRule.onNodeWithTag("pattern_card_$firstPatternId").fetchSemanticsNode().boundsInRoot
        val card1Bounds = composeTestRule.onNodeWithTag("pattern_card_$secondPatternId").fetchSemanticsNode().boundsInRoot
        assertEquals("Adaptive grid must place first two pattern cards on the same row on phone width", card0Bounds.top, card1Bounds.top, 2f)
        assertTrue("Second pattern card must sit to the right of first card", card1Bounds.left >= card0Bounds.right)

        composeTestRule.onNodeWithTag("patterns_screen_list")
            .performScrollToNode(hasTestTag("pattern_card_laves_floret"))
        composeTestRule.onNodeWithTag("pattern_card_laves_floret").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("patterns_screen_list")
            .performScrollToNode(hasTestTag("chip_pattern_cat_all"))
        composeTestRule.onNodeWithTag("chip_pattern_cat_all").assertIsDisplayed()

        composeTestRule.onNodeWithTag("btn_back_to_home").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Floret").assertIsDisplayed()

        // 4. Close sheet and swipe left -> verify next wallpaper uses LAVES_FLORET + CENTER_WEIGHTED
        val seedBeforeSwipe = requireNotNull(viewModel.uiState.value.preview).seed
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vector_preview_canvas").performTouchInput {
            swipeLeft(startX = width * 0.85f, endX = width * 0.15f, durationMillis = 180L)
        }
        awaitPreviewReady(viewModel, previousSeed = seedBeforeSwipe, requireAheadBuffer = true)
        val activePreview = requireNotNull(viewModel.uiState.value.preview)
        assertEquals(ArtPattern.LAVES_FLORET, activePreview.pattern)
        assertEquals(PatternWeightMode.CENTER_WEIGHTED, activePreview.patternWeightMode)
    }

    @Test
    fun realisticPhoneMockupOverlays_removeStatusBars_andNeverHitMiniHintOrPreviewInfoOverlay() {
        composeTestRule.waitForIdle()
        val activity = composeTestRule.activity
        val viewModel = ViewModelProvider(
            activity,
            RimaluxViewModel.Factory(activity)
        )[RimaluxViewModel::class.java]

        awaitPreviewReady(viewModel)
        // Dismiss initial Hint Window so idle mini hint can appear over mockups
        if (viewModel.uiState.value.isGestureTutorialVisible) {
            composeTestRule.onNodeWithTag("btn_dismiss_tutorial").performClick()
            composeTestRule.waitForIdle()
        }

        // 1. Swipe down -> Lock Screen overlay (status bar removed)
        composeTestRule.onNodeWithTag("vector_preview_canvas").performTouchInput {
            swipeDown(startY = height * 0.25f, endY = height * 0.75f, durationMillis = 180L)
        }
        composeTestRule.waitForIdle()
        assertEquals(MockupOverlayMode.LOCK_SCREEN, viewModel.uiState.value.mockupMode)
        composeTestRule.onNodeWithTag("mockup_lock_screen_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mockup_lock_status_bar").assertDoesNotExist()
        composeTestRule.onNodeWithText("09:41").assertIsDisplayed()
        composeTestRule.onNodeWithText("Design Sync").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alex Rivera").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mockup_lock_bottom_shortcuts").assertIsDisplayed()
        composeTestRule.onNodeWithTag("preview_info_overlay").assertIsDisplayed()

        // Wait 4s idle so top mini hint shows over Lock Screen overlay -> verify zero overlap
        composeTestRule.mainClock.advanceTimeBy(4_200L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("studio_mini_hint").assertIsDisplayed()
        val miniHintOnLockBounds = composeTestRule.onNodeWithTag("studio_mini_hint").fetchSemanticsNode().boundsInRoot
        val lockTopHeaderBounds = composeTestRule.onNodeWithTag("mockup_lock_top_header").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Mini hint (${miniHintOnLockBounds.bottom}) must never hit Lock Screen top header (${lockTopHeaderBounds.top})",
            miniHintOnLockBounds.bottom <= lockTopHeaderBounds.top - 4f
        )

        val lockShortcutsBounds = composeTestRule.onNodeWithTag("mockup_lock_bottom_shortcuts").fetchSemanticsNode().boundsInRoot
        val infoBoundsOnLock = composeTestRule.onNodeWithTag("preview_info_overlay").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Lock Screen bottom shortcuts (${lockShortcutsBounds.bottom}) must stay cleanly above preview_info_overlay (${infoBoundsOnLock.top})",
            lockShortcutsBounds.bottom <= infoBoundsOnLock.top - 4f
        )

        // 2. Swipe down -> Home Screen overlay (status bar removed, realistic app icons + labels)
        composeTestRule.onNodeWithTag("vector_preview_canvas").performTouchInput {
            swipeDown(startY = height * 0.25f, endY = height * 0.75f, durationMillis = 180L)
        }
        composeTestRule.waitForIdle()
        assertEquals(MockupOverlayMode.HOME_GRID, viewModel.uiState.value.mockupMode)
        composeTestRule.onNodeWithTag("mockup_home_screen_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mockup_home_status_bar").assertDoesNotExist()
        composeTestRule.onNodeWithTag("mockup_home_at_a_glance").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mockup_home_search_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mockup_home_bottom_cluster").assertIsDisplayed()

        // Wait 4s idle so top mini hint shows over Home Screen overlay -> verify zero overlap
        composeTestRule.mainClock.advanceTimeBy(4_200L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("studio_mini_hint").assertIsDisplayed()
        val miniHintOnHomeBounds = composeTestRule.onNodeWithTag("studio_mini_hint").fetchSemanticsNode().boundsInRoot
        val homeAtAGlanceBounds = composeTestRule.onNodeWithTag("mockup_home_at_a_glance").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Mini hint (${miniHintOnHomeBounds.bottom}) must never hit Home Screen At-a-Glance (${homeAtAGlanceBounds.top})",
            miniHintOnHomeBounds.bottom <= homeAtAGlanceBounds.top - 4f
        )

        val expectedApps = listOf(
            "calendar" to "Calendar",
            "photos" to "Photos",
            "maps" to "Maps",
            "clock" to "Clock",
            "mail" to "Mail",
            "music" to "Music",
            "files" to "Files",
            "settings" to "Settings",
            "phone" to "Phone",
            "messages" to "Messages",
            "browser" to "Browser",
            "camera" to "Camera"
        )
        val infoBoundsNormal = composeTestRule.onNodeWithTag("preview_info_overlay").fetchSemanticsNode().boundsInRoot

        for ((key, appName) in expectedApps) {
            composeTestRule.onNodeWithTag("mockup_app_icon_$key").assertIsDisplayed()
            composeTestRule.onNodeWithTag("mockup_app_label_$key").assertIsDisplayed()
            composeTestRule.onNodeWithText(appName).assertIsDisplayed()

            val iconBounds = composeTestRule.onNodeWithTag("mockup_app_icon_$key").fetchSemanticsNode().boundsInRoot
            val labelBounds = composeTestRule.onNodeWithTag("mockup_app_label_$key").fetchSemanticsNode().boundsInRoot
            assertTrue(
                "App name '$appName' (${labelBounds.top}) must sit underneath its app icon (${iconBounds.bottom})",
                labelBounds.top >= iconBounds.bottom - 1f
            )
            assertTrue(
                "App name '$appName' (${labelBounds.bottom}) must never hit or overlap preview_info_overlay (${infoBoundsNormal.top})",
                labelBounds.bottom <= infoBoundsNormal.top - 4f
            )
        }

        // 3. Trigger hasUnappliedChanges (pushes preview_info_overlay higher up) -> verify Home Screen overlay STILL clears it
        viewModel.updatePitchBlackRatio(0.72f)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("preview_pending_notice").assertIsDisplayed()
        composeTestRule.onNodeWithTag("preview_info_overlay").assertIsDisplayed()

        val infoBoundsElevated = composeTestRule.onNodeWithTag("preview_info_overlay").fetchSemanticsNode().boundsInRoot
        val homeBottomBoundsElevated = composeTestRule.onNodeWithTag("mockup_home_bottom_cluster").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Home Screen apps & dock (${homeBottomBoundsElevated.bottom}) must stay cleanly above elevated preview_info_overlay (${infoBoundsElevated.top})",
            homeBottomBoundsElevated.bottom <= infoBoundsElevated.top - 4f
        )
    }

    @Test
    fun hintWindowAndIdleMiniHint_trackSuccessfulGestures_rotateStaleHints_dismissOnAnyAction_andOpenHintWindowOnClick() {
        composeTestRule.waitForIdle()
        val activity = composeTestRule.activity
        val viewModel = ViewModelProvider(
            activity,
            RimaluxViewModel.Factory(activity)
        )[RimaluxViewModel::class.java]

        awaitPreviewReady(viewModel, requireAheadBuffer = true)

        // 1. Hint Window auto-opens on first launch with darker card & simple "Got It" TextButton (no title)
        assertTrue(viewModel.uiState.value.isGestureTutorialVisible)
        composeTestRule.onNodeWithTag("gesture_tutorial_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gesture_tutorial_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Studio Gestures").assertDoesNotExist()
        composeTestRule.onNodeWithText("Swipe Left or Right").assertIsDisplayed()
        composeTestRule.onNodeWithText("Double-Tap").assertIsDisplayed()
        composeTestRule.onNodeWithText("Swipe Up").assertIsDisplayed()
        composeTestRule.onNodeWithText("Swipe Down").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_dismiss_tutorial").assertIsDisplayed()
        composeTestRule.onNodeWithText("Got It").assertIsDisplayed()

        // Tapping anywhere on canvas must NOT dismiss the Hint Window
        composeTestRule.onNodeWithTag("vector_preview_canvas").performClick()
        composeTestRule.mainClock.advanceTimeBy(400L)
        composeTestRule.waitForIdle()
        assertTrue("Tap anywhere must not dismiss Hint Window", viewModel.uiState.value.isGestureTutorialVisible)
        composeTestRule.onNodeWithTag("gesture_tutorial_overlay").assertIsDisplayed()

        // Clicking "Got It" TextButton dismisses Hint Window and persists hasSeenGestureTutorial = true
        composeTestRule.onNodeWithTag("btn_dismiss_tutorial").performClick()
        composeTestRule.waitForIdle()
        assertFalse(viewModel.uiState.value.isGestureTutorialVisible)
        composeTestRule.onNodeWithTag("gesture_tutorial_overlay").assertDoesNotExist()

        val repo = com.example.data.WallpaperPreferencesRepository(activity)
        val storedAfterDismiss = kotlinx.coroutines.runBlocking { repo.getSnapshot() }
        assertTrue("Dismissing Hint Window must persist hasSeenGestureTutorial = true", storedAfterDismiss.hasSeenGestureTutorial)

        // Single-tapping canvas must NO LONGER show Hint Window
        composeTestRule.onNodeWithTag("vector_preview_canvas").performClick()
        composeTestRule.mainClock.advanceTimeBy(400L)
        composeTestRule.waitForIdle()
        assertFalse("Single-tap must not open Hint Window", viewModel.uiState.value.isGestureTutorialVisible)
        composeTestRule.onNodeWithTag("gesture_tutorial_overlay").assertDoesNotExist()

        // 2. Idle < 4 seconds -> mini hint must stay hidden; >= 4 seconds -> mini hint shows on top!
        composeTestRule.mainClock.advanceTimeBy(3_500L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("studio_mini_hint").assertDoesNotExist()

        composeTestRule.mainClock.advanceTimeBy(700L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("studio_mini_hint").assertIsDisplayed()
        composeTestRule.onNodeWithTag("studio_mini_hint_text", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("studio_mini_hint_icons", useUnmergedTree = true).assertIsDisplayed()
        repeat(4) { idx ->
            composeTestRule.onNodeWithTag("studio_mini_hint_icon_$idx", useUnmergedTree = true).assertIsDisplayed()
        }

        // Verify hint text is left-aligned and animated palette icons are right-aligned
        val textBounds = composeTestRule.onNodeWithTag("studio_mini_hint_text", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val iconsBounds = composeTestRule.onNodeWithTag("studio_mini_hint_icons", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Mini hint text (${textBounds.right}) must sit cleanly to the left of right-aligned icons (${iconsBounds.left})",
            textBounds.left < iconsBounds.left && textBounds.right <= iconsBounds.left + 1f
        )

        val firstMiniHint = requireNotNull(viewModel.uiState.value.activeMiniHint)

        // 3. Rotate mini hint every 6 seconds
        composeTestRule.mainClock.advanceTimeBy(6_100L)
        composeTestRule.waitForIdle()
        val secondMiniHint = requireNotNull(viewModel.uiState.value.activeMiniHint)
        assertNotEquals(
            "Mini hint must rotate to another stale gesture after 3 seconds",
            firstMiniHint.action,
            secondMiniHint.action
        )

        // 4. Right-edge bounce (unsuccessful swipe right when history is empty) closes mini hint,
        //    but does NOT mark SWIPE_RIGHT as performed!
        assertFalse(viewModel.uiState.value.canSwipeRight)
        composeTestRule.onNodeWithTag("vector_preview_canvas").performTouchInput {
            swipeRight(startX = width * 0.15f, endX = width * 0.85f, durationMillis = 180L)
        }
        composeTestRule.waitForIdle()
        assertEquals(null, viewModel.uiState.value.activeMiniHint)
        composeTestRule.onNodeWithTag("studio_mini_hint").assertDoesNotExist()
        assertEquals(
            "Unsuccessful right-edge bounce must not record SWIPE_RIGHT timestamp",
            0L,
            viewModel.uiState.value.lastGestureTimestamps[com.example.model.StudioGestureAction.SWIPE_RIGHT] ?: 0L
        )

        // 5. Clicking mini hint opens Hint Window
        composeTestRule.mainClock.advanceTimeBy(4_200L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("studio_mini_hint").assertIsDisplayed()
        composeTestRule.onNodeWithTag("studio_mini_hint").performClick()
        composeTestRule.waitForIdle()
        assertTrue("Clicking mini hint must open Hint Window", viewModel.uiState.value.isGestureTutorialVisible)
        composeTestRule.onNodeWithTag("gesture_tutorial_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("studio_mini_hint").assertDoesNotExist()

        // Close Hint Window via Got It TextButton
        composeTestRule.onNodeWithTag("btn_dismiss_tutorial").performClick()
        composeTestRule.waitForIdle()
        assertFalse(viewModel.uiState.value.isGestureTutorialVisible)

        // 6. Perform all 5 gestures successfully -> verify each records timestamp & suppresses mini hint (< 1 week)
        val seedStart = requireNotNull(viewModel.uiState.value.preview).seed
        // (a) Swipe Left
        composeTestRule.onNodeWithTag("vector_preview_canvas").performTouchInput {
            swipeLeft(startX = width * 0.85f, endX = width * 0.15f, durationMillis = 180L)
        }
        awaitPreviewReady(viewModel, previousSeed = seedStart)
        assertTrue((viewModel.uiState.value.lastGestureTimestamps[com.example.model.StudioGestureAction.SWIPE_LEFT] ?: 0L) > 0L)

        // (b) Swipe Right (now succeeds because pastStack has seedStart!)
        composeTestRule.onNodeWithTag("vector_preview_canvas").performTouchInput {
            swipeRight(startX = width * 0.15f, endX = width * 0.85f, durationMillis = 180L)
        }
        composeTestRule.waitForIdle()
        assertTrue((viewModel.uiState.value.lastGestureTimestamps[com.example.model.StudioGestureAction.SWIPE_RIGHT] ?: 0L) > 0L)

        // (c) Double-Tap
        composeTestRule.onNodeWithTag("vector_preview_canvas").performTouchInput {
            doubleClick(center)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_set_wallpaper_target").assertIsDisplayed()
        assertTrue((viewModel.uiState.value.lastGestureTimestamps[com.example.model.StudioGestureAction.DOUBLE_TAP] ?: 0L) > 0L)
        composeTestRule.onNodeWithTag("btn_cancel_set_wallpaper").performClick()
        composeTestRule.waitForIdle()

        // (d) Swipe Up
        composeTestRule.onNodeWithTag("vector_preview_canvas").performTouchInput {
            swipeUp(startY = height * 0.78f, endY = height * 0.22f, durationMillis = 180L)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_settings_sheet").assertIsDisplayed()
        assertTrue((viewModel.uiState.value.lastGestureTimestamps[com.example.model.StudioGestureAction.SWIPE_UP] ?: 0L) > 0L)
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.mainClock.advanceTimeBy(300L)
        composeTestRule.waitForIdle()

        // (e) Swipe Down
        composeTestRule.onNodeWithTag("vector_preview_canvas").performTouchInput {
            swipeDown(startY = height * 0.25f, endY = height * 0.75f, durationMillis = 180L)
        }
        composeTestRule.waitForIdle()
        assertTrue((viewModel.uiState.value.lastGestureTimestamps[com.example.model.StudioGestureAction.SWIPE_DOWN] ?: 0L) > 0L)

        // Since all 5 gestures were performed within the last week, mini hint must NOT appear after 2s or 5s idle!
        assertTrue(viewModel.eligibleStaleGestures().isEmpty())
        composeTestRule.mainClock.advanceTimeBy(5_500L)
        composeTestRule.waitForIdle()
        assertEquals(null, viewModel.uiState.value.activeMiniHint)
        composeTestRule.onNodeWithTag("studio_mini_hint").assertDoesNotExist()

        // 7. Verify > 1 week (> 7 days) staleness filter & multiple variations per gesture across sessions
        val fakeNow = 1_800_000_000_000L
        val twoDaysAgo = fakeNow - (2L * 24L * 60L * 60L * 1_000L)
        val eightDaysAgo = fakeNow - (8L * 24L * 60L * 60L * 1_000L)
        kotlinx.coroutines.runBlocking {
            repo.setGestureTimestamps(
                mapOf(
                    com.example.model.StudioGestureAction.SWIPE_LEFT to twoDaysAgo,
                    com.example.model.StudioGestureAction.SWIPE_RIGHT to eightDaysAgo,
                    com.example.model.StudioGestureAction.DOUBLE_TAP to twoDaysAgo,
                    com.example.model.StudioGestureAction.SWIPE_UP to eightDaysAgo,
                    com.example.model.StudioGestureAction.SWIPE_DOWN to twoDaysAgo
                )
            )
        }

        val weekLaterVm = RimaluxViewModel(activity, repo, clockMillis = { fakeNow })
        val reloadDeadline = System.currentTimeMillis() + 5_000L
        while (System.currentTimeMillis() < reloadDeadline && weekLaterVm.uiState.value.preview == null) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(15L)
        }
        val staleActions = weekLaterVm.eligibleStaleGestures(fakeNow)
        assertEquals(
            listOf(
                com.example.model.StudioGestureAction.SWIPE_RIGHT,
                com.example.model.StudioGestureAction.SWIPE_UP
            ),
            staleActions
        )
        assertTrue(weekLaterVm.showMiniHintAfterIdle())
        val firstStale = requireNotNull(weekLaterVm.uiState.value.activeMiniHint)
        assertEquals(com.example.model.StudioGestureAction.SWIPE_RIGHT, firstStale.action)

        assertTrue(weekLaterVm.rotateMiniHint())
        val secondStale = requireNotNull(weekLaterVm.uiState.value.activeMiniHint)
        assertEquals(com.example.model.StudioGestureAction.SWIPE_UP, secondStale.action)

        assertTrue(weekLaterVm.rotateMiniHint())
        val thirdStale = requireNotNull(weekLaterVm.uiState.value.activeMiniHint)
        assertEquals(com.example.model.StudioGestureAction.SWIPE_RIGHT, thirdStale.action)
        assertNotEquals(
            "Subsequent rotation back to SWIPE_RIGHT should cycle to its next playful variation",
            firstStale.messageRes,
            thirdStale.messageRes
        )

        // 8. Verify 4-dot vertical glide offset & o->O->O->o scale trajectory for Swipe Down vs Swipe Up
        val maxOff = 10f
        // Swipe Down: starts above (-maxOff) small 'o', swells to 'O', glides down to (+maxOff), settles to 'o'
        assertEquals(-maxOff, com.example.ui.components.computeVerticalSwipeDotTranslationY(0.02f, goingDown = true, maxOffsetPx = maxOff), 0.01f)
        assertEquals(-maxOff, com.example.ui.components.computeVerticalSwipeDotTranslationY(0.21f, goingDown = true, maxOffsetPx = maxOff), 0.01f)
        assertTrue(com.example.ui.components.computeVerticalSwipeDotScale(0.21f) > com.example.ui.components.computeVerticalSwipeDotScale(0.02f) + 0.4f)
        assertEquals(0f, com.example.ui.components.computeVerticalSwipeDotTranslationY(0.41f, goingDown = true, maxOffsetPx = maxOff), 0.1f)
        assertEquals(maxOff, com.example.ui.components.computeVerticalSwipeDotTranslationY(0.62f, goingDown = true, maxOffsetPx = maxOff), 0.01f)
        assertTrue(com.example.ui.components.computeVerticalSwipeDotScale(0.62f) > com.example.ui.components.computeVerticalSwipeDotScale(0.83f) + 0.4f)

        // Swipe Up: starts below (+maxOff) small 'o', swells to 'O', glides up to (-maxOff), settles to 'o'
        assertEquals(maxOff, com.example.ui.components.computeVerticalSwipeDotTranslationY(0.02f, goingDown = false, maxOffsetPx = maxOff), 0.01f)
        assertEquals(-maxOff, com.example.ui.components.computeVerticalSwipeDotTranslationY(0.62f, goingDown = false, maxOffsetPx = maxOff), 0.01f)
    }

    @Test
    fun shareWatermark_formatsPatternNameAndDynamicallyLandsInEmptyDarkSpotInsideSafeZone() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val formattedText = context.getString(R.string.share_watermark_text, "Silk Flow Ribbons")
        assertEquals("Procedurally generated by Rimalux · Silk Flow Ribbons", formattedText)

        val width = 1080
        val height = 2400
        val safe = com.example.engine.WatermarkPlacementSolver.computeSocialSafeZone(width, height)
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        try {
            // Paint bright artwork everywhere except a pure #000000 dark pocket in the upper-right of the safe zone
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(0xFF3A86FF.toInt())
            val darkPaint = android.graphics.Paint().apply {
                color = 0xFF000000.toInt()
                style = android.graphics.Paint.Style.FILL
            }
            val pocketLeft = safe.rightPx - 460
            val pocketRight = safe.rightPx - 8
            val pocketTop = safe.topPx + 18
            val pocketBottom = safe.topPx + 130
            canvas.drawRect(
                pocketLeft.toFloat(),
                pocketTop.toFloat(),
                pocketRight.toFloat(),
                pocketBottom.toFloat(),
                darkPaint
            )

            val placement = com.example.platform.WallpaperExporter.applyShareWatermark(
                context = context,
                bitmap = bitmap,
                patternTitle = "Silk Flow Ribbons",
                paletteColors = listOf(0xFF00F5D4L, 0xFF3A86FFL)
            )

            assertTrue(placement.leftPx >= safe.leftPx.toFloat())
            assertTrue(placement.leftPx + placement.widthPx <= safe.rightPx.toFloat() + 0.5f)
            assertTrue(placement.topPx >= safe.topPx.toFloat())
            assertTrue(placement.topPx + placement.heightPx <= safe.bottomPx.toFloat() + 0.5f)

            assertTrue(
                "Expected watermark X (${placement.leftPx}..${placement.leftPx + placement.widthPx}) inside dark pocket [$pocketLeft..$pocketRight]",
                placement.leftPx >= pocketLeft - 10f && placement.leftPx + placement.widthPx <= pocketRight + 10f
            )
            assertTrue(
                "Expected watermark Y (${placement.topPx}..${placement.topPx + placement.heightPx}) inside dark pocket [$pocketTop..$pocketBottom]",
                placement.topPx >= pocketTop - 10f && placement.topPx + placement.heightPx <= pocketBottom + 10f
            )
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun bottomSheetAboutFooter_displaysVersionOnePointZeroPointZeroAndStudioCreditAtBottom() {
        assertEquals("1.0.0", BuildConfig.VERSION_NAME)

        composeTestRule.waitForIdle()
        val viewModel = ViewModelProvider(
            composeTestRule.activity,
            RimaluxViewModel.Factory(composeTestRule.activity)
        )[RimaluxViewModel::class.java]
        awaitPreviewReady(viewModel)

        composeTestRule.onNodeWithTag("home_bottom_sheet_handle").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("home_screen_scroll")
            .performScrollToNode(hasTestTag("sheet_about_footer"))
        composeTestRule.waitForIdle()

        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val expectedText = "Rimalux v1.0.0 by Cptodix Studio @$currentYear"
        composeTestRule.onNodeWithTag("sheet_about_footer").assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedText).assertIsDisplayed()
    }

    @Test
    fun multiLanguageReadiness_resolvesSystemLocaleWithFallback_andRendersSelectorBetweenLikeAppAndAbout() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Only en-US is currently shipped (no extra languages added yet)
        assertEquals(listOf(com.example.model.AppLanguage.EN_US), com.example.model.AppLanguage.entries)
        assertEquals("en-US", com.example.model.AppLanguage.DEFAULT.languageTag)

        // 2. First-launch resolution with unsupported system locales falls back cleanly to en-US and persists
        com.example.platform.AppLocaleManager.clearStoredLanguageForTesting(context)
        val resolvedFromUnsupported = com.example.platform.AppLocaleManager.resolveOrInitializeLanguage(
            context = context,
            systemLocalesOverride = listOf(java.util.Locale.JAPAN, java.util.Locale.GERMANY)
        )
        assertEquals(com.example.model.AppLanguage.EN_US, resolvedFromUnsupported)

        // 3. BCP-47 3-tier negotiation engine is ready for future multi-language expansion
        val futureSupported = listOf("en-US", "fr", "pt-PT", "pt-BR", "zh-Hans", "zh-Hant", "id", "he")
        assertEquals(
            "pt-BR",
            com.example.model.AppLanguage.resolveBestSupportedTag(
                systemLocales = listOf(java.util.Locale.forLanguageTag("pt-BR")),
                supportedTags = futureSupported
            )
        )
        assertEquals(
            "zh-Hans",
            com.example.model.AppLanguage.resolveBestSupportedTag(
                systemLocales = listOf(java.util.Locale.SIMPLIFIED_CHINESE),
                supportedTags = futureSupported
            )
        )
        assertEquals(
            "zh-Hant",
            com.example.model.AppLanguage.resolveBestSupportedTag(
                systemLocales = listOf(java.util.Locale.forLanguageTag("zh-HK")),
                supportedTags = futureSupported
            )
        )
        assertEquals(
            "id",
            com.example.model.AppLanguage.resolveBestSupportedTag(
                systemLocales = listOf(java.util.Locale("in", "ID")),
                supportedTags = futureSupported
            )
        )
        assertEquals(
            "fr",
            com.example.model.AppLanguage.resolveBestSupportedTag(
                systemLocales = listOf(
                    java.util.Locale.KOREA,
                    java.util.Locale.CANADA_FRENCH
                ),
                supportedTags = futureSupported
            )
        )
        assertEquals(
            "en-US",
            com.example.model.AppLanguage.resolveBestSupportedTag(
                systemLocales = listOf(java.util.Locale.forLanguageTag("sv-SE")),
                supportedTags = futureSupported
            )
        )

        // 4. Every built-in starter palette maps to a localized @StringRes entry
        for (starter in StarterPalettes.defaults) {
            val resId = starter.nameResOrNull
            assertNotNull("Starter palette '${starter.id}' must map to @StringRes", resId)
            val resolvedName = starter.resolveDisplayName(context)
            assertTrue(resolvedName.isNotBlank())
        }

        // 5. Verify low-emphasis Language selector sits strictly between Enjoying Rimalux? and About text
        composeTestRule.waitForIdle()
        val activity = composeTestRule.activity
        val viewModel = ViewModelProvider(
            activity,
            RimaluxViewModel.Factory(activity)
        )[RimaluxViewModel::class.java]
        awaitPreviewReady(viewModel)

        composeTestRule.onNodeWithTag("home_bottom_sheet_handle").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("home_screen_scroll")
            .performScrollToNode(hasTestTag("sheet_about_footer"))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("btn_like_the_app").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_open_language_selector").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sheet_about_footer").assertIsDisplayed()
        composeTestRule.onNodeWithText("Language · English (US)").assertIsDisplayed()

        val likeBounds = composeTestRule.onNodeWithTag("btn_like_the_app").fetchSemanticsNode().boundsInRoot
        val langBounds = composeTestRule.onNodeWithTag("btn_open_language_selector").fetchSemanticsNode().boundsInRoot
        val aboutBounds = composeTestRule.onNodeWithTag("sheet_about_footer").fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Language selector (${langBounds.top}) must sit below Enjoying Rimalux? (${likeBounds.bottom})",
            langBounds.top >= likeBounds.bottom - 1f
        )
        assertTrue(
            "About text (${aboutBounds.top}) must sit below Language selector (${langBounds.bottom})",
            aboutBounds.top >= langBounds.bottom - 1f
        )

        // 6. Tap Language selector -> opens unified Language modal with English (US) selected
        composeTestRule.onNodeWithTag("btn_open_language_selector").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dialog_language_selector").assertIsDisplayed()
        composeTestRule.onNodeWithTag("language_option_en-us").assertIsDisplayed()
        composeTestRule.onNode(
            hasText("English (US)") and hasAnyAncestor(hasTestTag("dialog_language_selector"))
        ).assertIsDisplayed()

        composeTestRule.onNodeWithTag("language_option_en-us").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_language_selector").assertDoesNotExist()
        assertEquals(com.example.model.AppLanguage.EN_US, viewModel.uiState.value.appLanguage)
    }
}
