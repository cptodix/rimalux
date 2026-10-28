package com.example

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appName_matchesRimaluxIdentity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Rimalux", context.getString(R.string.app_name))
    }

    @Test
    fun uiFlow_stylesGallery_solidAndGradientModes_andTargetsWork() {
        composeTestRule.waitForIdle()

        // 1. Decluttered Home screen: preview, Pitch Black Ratio slider, manual target (Wallpaper / Lock / Both), and buttons
        composeTestRule.onNodeWithTag("vector_preview_canvas").assertIsDisplayed()
        composeTestRule.onNodeWithTag("slider_pitch_black_ratio")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.82f) }
        composeTestRule.onNodeWithTag("manual_target_home").performClick()
        composeTestRule.onNodeWithTag("manual_target_lock").performClick()
        composeTestRule.onNodeWithTag("manual_target_both").performClick()
        composeTestRule.onNodeWithTag("btn_generate_wallpaper").assertIsDisplayed()

        // 2. Styles tab: switch to multi-select and pick multiple mathematical art styles
        composeTestRule.onNodeWithTag("nav_tab_styles").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_styles_multiselect").performClick()
        composeTestRule.onNodeWithTag("style_card_crystal_voronoi").performClick()
        composeTestRule.onNodeWithTag("styles_screen_list")
            .performScrollToNode(hasTestTag("style_card_penrose_quasicrystal"))
        composeTestRule.onNodeWithTag("style_card_penrose_quasicrystal").performClick()
        composeTestRule.waitForIdle()

        // 3. Palettes tab: test Solid vs Gradient shading + Randomized shuffle & Palette List
        composeTestRule.onNodeWithTag("nav_tab_palettes").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_fill_mode_solid").performClick()
        composeTestRule.onNodeWithTag("btn_fill_mode_gradient").performClick()
        composeTestRule.onNodeWithTag("btn_shuffle_colors").performClick()
        composeTestRule.onNodeWithTag("btn_palette_mode_list").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_open_create_palette").assertIsDisplayed()

        // 4. Auto tab: test dedicated Automatic Target & interval presets
        composeTestRule.onNodeWithTag("nav_tab_auto").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("auto_target_lock").performClick()
        composeTestRule.onNodeWithTag("auto_target_home").performClick()
        composeTestRule.onNodeWithTag("schedule_screen_list")
            .performScrollToNode(hasTestTag("chip_interval_15"))
        composeTestRule.onNodeWithTag("chip_interval_15").performClick()
        composeTestRule.waitForIdle()
    }
}
