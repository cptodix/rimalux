# Rimalux Architecture & Cross-Platform Porting Guide

This document explains Rimalux's internal architecture and provides the exact mathematical and rendering specifications needed to port the core wallpaper engine (`com.example.engine`) to iOS (Swift / CoreGraphics / Metal), Web (TypeScript / Canvas2D / WebGL / SVG), Desktop, or Kotlin Multiplatform (`commonMain`).

Make sure to also read [`../AGENTS.md`](../AGENTS.md) for mandatory project rules and restrictions.

---

## 1. Layered Module Dependency Graph

```text
┌───────────────────────────────────────────────────────────────────────────┐
│                  com.example.engine  (100% Pure Kotlin)                   │
│  Zero Android, Compose, R.*, Context, Bitmap, DataStore, or UI imports!   │
│                                                                           │
│  • WallpaperCoreModels.kt     (ArtPattern[80], WeightMode, CoreSpec)      │
│  • WallpaperGeneratorEngine.kt(Single portable synthesis entry point)     │
│  • MathPatternGenerator.kt    (Seeded PRNG + SculptField + 80 dispatch)   │
│  • *Patterns.kt (11 files)    (80 procedural geometry algorithms)         │
│  • VectorPrimitives.kt        (Vec2, Polygon, ArcSector, PolylineRibbon)  │
│  • CoverageCalibrator.kt      (200xH 2D coverage rasterizer & underlay)   │
│  • DeepBlackRatioEngine.kt    (#000000 ratio math, pixel sampler, scale)  │
│  • ColorHarmonyEngine.kt      (HSV harmony math, OLED shade floor, grad)  │
└─────────────────────────────────────▲─────────────────────────────────────┘
                                      │
          ┌───────────────────────────┼───────────────────────────┐
          │                           │                           │
┌─────────┴──────────┐     ┌──────────┴───────────┐    ┌──────────┴───────────┐
│ com.example.model  │     │ com.example.platform │    │     Other Targets    │
│ • WallpaperModels  │     │ • WallpaperRenderer  │    │ (Future Ports: iOS,  │
│ • PatternResource  │     │ • ComposeSceneRender │    │  Web, Desktop, KMP)  │
│   Mappings (R.*)   │     │ • WallpaperExporter  │    └──────────────────────┘
└─────────▲──────────┘     └──────────▲───────────┘
          │                           │
          ├─────────────┬─────────────┤
          │             │             │
┌─────────┴────────┐ ┌──┴───────────┐ ┌┴──────────────────┐
│ com.example.data │ │ .scheduler   │ │  com.example.ui   │
│ DataStore Prefs  │ │ WorkManager  │ │ RimaluxViewModel  │
│ Repository       │ │ AlarmManager │ │ Jetpack Compose   │
│                  │ │ OemHelper    │ │ Material 3 Screens│
└──────────────────┘ └──────────────┘ └───────────────────┘
```

### Strict Dependency Direction
- `engine` depends on **nothing** except Kotlin stdlib (`kotlin.*`).
- `model` depends on `engine` + Android `R` / `WallpaperManager` constants.
- `platform` depends on `engine` + `model.WallpaperTarget` + Android `Bitmap`/`Canvas`/`MediaStore`/`WallpaperManager` & Compose `DrawScope`.
- `data` depends on `engine`/`model` + Jetpack Preferences `DataStore`.
- `scheduler` depends on `engine` + `platform` + `data` + `model` + `WorkManager`/`AlarmManager`.
- `ui` depends on `engine` + `platform` + `data` + `scheduler` + `model` + Jetpack Compose M3.

---

## 2. How the Core Mathematical Engine Works

### 2.1 Normalized Euclidean Scene Space
Every pattern in `com.example.engine` generates geometry inside an aspect-corrected normalized 2D space:
- **Horizontal axis (`x`)**: `[0.0f, 1.0f]`
- **Vertical axis (`y`)**: `[0.0f, aspectRatio]` where `aspectRatio = height / width` (clamped to `[0.45f, 3.2f]`).

Because `1.0` unit in `x` equals `1.0` unit in `y` in physical pixel distance, circles, logarithmic spirals, regular polygons, and sacred geometry angles remain 100% distortion-free on portrait phones, unfolded foldables, tablets, and desktop monitors.

### 2.2 `SculptField`, `PatternWeightMode` & `DraftCell` Pipeline
1. `MathPatternGenerator.generate(seed, pattern, intricacy, aspectRatio, patternWeightMode)` initializes a deterministic `Random(seed)` and constructs a multi-octave spatial `SculptField(rng, safeAspect, patternWeightMode)`.
2. When `patternWeightMode == PatternWeightMode.CENTER_WEIGHTED`:
   - `SculptField.evaluateCenterContourDistance(x, y)` computes an aspect-aware radial contour distance from `(0.5f, aspect * 0.5f)`.
   - `CoverageCalibrator.calibrate(...)` compacts sparse full-span primary geometry toward `(0.5f, aspect * 0.5f)` and re-ranks `DraftCell`s + underlay mosaic tiles strictly from the center outward so graphics clump in the middle with empty `#000000` space on the sides while preserving exact `#000000` True Black Ratio calibration.
3. The selected pattern builder produces `List<DraftCell>`. Each `DraftCell` has:
   - `score: Float` (spatial priority evaluated via `SculptField`),
   - `area: Float` (relative weight within multi-primitive cells),
   - `build: (assignedThreshold: Float) -> List<VectorPrimitive>`.

### 2.3 `CoverageCalibrator` (Guaranteed `#000000` True Black Ratio)
Raw procedural geometry (overlapping ribbons, concentric rings, sparse attractors) would naturally have wildly varying background percentages if drawn uncalibrated. `CoverageCalibrator.calibrate(...)` solves this deterministically:
1. Allocates a `BooleanArray` 2D coverage grid of width `GRID_W = 200` and height `round(200 * aspect)`.
2. Sorts `DraftCell`s descending by `score` and rasterizes each cell's primitives (`PolygonPrimitive`, `ArcSectorPrimitive`, `PolylineRibbonPrimitive`) onto the boolean grid, counting `newlyCovered` pixels not yet claimed by higher-priority cells.
3. Assigns each cell an exact threshold:
   $$\text{threshold} = 1.0 - \frac{\text{coveredCount} + 0.5 \cdot \text{newlyCovered}}{\text{totalPixels}}$$
   and `uniqueViewportShare = newlyCovered / totalPixels`.
4. **Gapless Underlay Mosaic**: If the primary pattern leaves `> 1.5%` uncovered background (e.g., sparse chaotic attractors when the user requests `10%` True Black / `90%` color), `CoverageCalibrator` synthesizes a bottom-layer jittered triangular mosaic (`buildGaplessUnderlayMosaic`) that fills ONLY the uncovered void cells and receives low thresholds (`< 1 - primaryCoverage`). At typical high black ratios (e.g., `85%`), `0` underlay triangles are drawn; at low black ratios (e.g., `15%`), the underlay seamlessly illuminates the remaining negative space to hit the exact target ratio.

---

## 3. Step-by-Step Cross-Platform Porting Guide

### Option A: Kotlin Multiplatform (iOS, Desktop, Web/Wasm)
1. Copy all `.kt` files in `app/src/main/java/com/example/engine/` directly into your KMP `commonMain` source set. **Zero code edits are required.**
2. Call `WallpaperGeneratorEngine.synthesize(seed, spec, availablePalettes, aspectRatio)` from `commonMain`.
3. Implement a thin platform rasterizer (~150 lines) per target using the exact rendering spec in **Section 3.1** below.

### Option B: Porting to Swift (iOS/macOS), TypeScript (Web), or Rust/C++
Translate the pure files in `engine/` in this order:
1. `WallpaperCoreModels` + `VectorPrimitives`
2. `DeepBlackRatioEngine` + `ColorHarmonyEngine`
3. `CoverageCalibrator` + `MathPatternGenerator` + `*Patterns`
4. `WallpaperGeneratorEngine`

### 3.1 Exact Platform Renderer Specification
Given a `SynthesizedWallpaper` (or `VectorScene` + `paletteColors` + `pitchBlackRatio` + `colorFillMode`) and target surface `(widthPx, heightPx)`:

1. **Clamp Dimensions**:
   `(safeW, safeH) = DeepBlackRatioEngine.clampDimensionsToMaxAxis(widthPx, heightPx)` (`maxAxis = 4096`).
2. **Clear Surface to Pure OLED Black**:
   Fill the entire framebuffer with `0xFF000000` (`R=0, G=0, B=0, A=255`).
3. **Compute Viewport Transform**:
   `transform = DeepBlackRatioEngine.computeViewportTransform(safeW, safeH, scene.aspectRatio)`:
   - `scale = max(safeW / 1.0, safeH / scene.aspectRatio)`
   - `offsetX = (safeW - scale) * 0.5`
   - `offsetY = (safeH - scale * scene.aspectRatio) * 0.5`
   - `drawMicroSeams = (scale >= 220.0)`
   Every normalized point `(x, y)` maps to pixel `(offsetX + x * scale, offsetY + y * scale)`.
4. **Iterate `scene.primitives` in Order**:
   - **Visibility Check**: Skip primitive if `!DeepBlackRatioEngine.isPrimitiveVisible(primitive, pitchBlackRatio)` (i.e., `primitive.threshold < clampPitchBlackRatio(pitchBlackRatio)`).
   - **Fill / Stroke Paint**:
     - If `colorFillMode == SOLID`: use `ColorHarmonyEngine.resolveArgbColor(paletteColors, primitive.colorIndex, primitive.shadeFactor)`.
     - If `colorFillMode == GRADIENT`: compute primitive pixel bounding box `[minX, minY, maxX, maxY]` and call `ColorHarmonyEngine.computeLinearGradient(paletteColors, primitive, minX, minY, maxX, maxY)` with clamped endpoints.
   - **Primitive Geometry**:
     - **`PolygonPrimitive`**:
       - Draw closed polygon with **Non-Zero Winding** fill rule.
       - If `drawMicroSeams && primitive.strokeSeamNorm > 0`: stroke polygon outline in `#000000` with round join and width `clamp(primitive.strokeSeamNorm * scale, 0.5, 1.7)`.
     - **`ArcSectorPrimitive`**:
       - `cx = offsetX + center.x * scale`, `cy = offsetY + center.y * scale`, `outR = outerRadius * scale`, `inR = innerRadius * scale`. Skip if `outR <= 0.5`.
       - `inR <= 0.5 && sweepAngleDeg >= 359.5`: filled circle of radius `outR`.
       - `inR <= 0.5`: pie sector from `(cx, cy)` along outer arc `[startAngleDeg, +sweepAngleDeg]` closed back to `(cx, cy)`.
       - `inR > 0.5 && sweepAngleDeg >= 359.5`: annulus (ring) using **Even-Odd** fill rule with outer oval `outR` and inner oval `inR`.
       - `inR > 0.5 && sweepAngleDeg < 359.5`: annular sector moving to inner start point, outer arc `[startAngleDeg, +sweepAngleDeg]`, inner arc `[startAngleDeg + sweepAngleDeg, -sweepAngleDeg]`, closed with **Non-Zero Winding**.
     - **`PolylineRibbonPrimitive`**:
       - Stroke open or closed polyline (`primitive.closed`) with **Round Cap**, **Round Join**, and width `max(primitive.strokeWidthNorm * scale, 1.3)`.
5. **Verify `#000000` Coverage**:
   Call `DeepBlackRatioEngine.measureTrueBlackPercentage(safeW, safeH) { x, y -> getPixelArgb(x, y) }`.

---

## 4. How to Add a New Procedural Pattern (#65+)

1. Add the enum entry in `engine/WallpaperCoreModels.kt` (`ArtPattern`) with a unique snake_case `id`, `PatternCategory`, `defaultTitle`, and `defaultSubtitle`.
2. Implement the pure `DraftCell` builder in the matching `engine/*Patterns.kt` file (or a new pure file in `engine/` if approaching 500 lines) using only `kotlin.math.*` and the passed `Random` + `SculptField`.
3. Wire the `when (pattern)` branch in `engine/MathPatternGenerator.kt`.
4. Add Android string resources (`pattern_<id>`, `pattern_<id>_sub`) in `app/src/main/res/values/strings.xml`, static preview asset `pattern_preview_<id>` in `app/src/main/res/drawable-nodpi/`, and resource mappings in `model/PatternResourceMappings.kt`.
5. Run `gradle :app:testDebugUnitTest` — `CoreEnginePortabilityTest`, `WallpaperEngineAndAutoScheduleTest`, and `ExampleRobolectricTest` will automatically validate all patterns!

---

## 5. Key Compose `testTag` Registry (UI Regression Contract)

| Screen / Component | `testTag` | Purpose |
| :--- | :--- | :--- |
| Studio Canvas & Overlays | `vector_preview_canvas`, `preview_info_overlay`, `preview_pending_notice`, `gesture_tutorial_overlay`, `btn_dismiss_tutorial` | Full-bleed swipe deck, double-tap/single-tap/swipe-down target, info pill & translucent gesture tutorial |
| Phone Mockup Overlays | `mockup_lock_screen_overlay`, `mockup_home_screen_overlay`, `mockup_app_icon_<key>`, `mockup_app_label_<key>` | Realistic Lock Screen & labeled Home Screen preview overlays |
| Studio Bottom Sheet | `home_settings_sheet`, `home_bottom_sheet_handle`, `home_screen_scroll` | Hidden-when-closed swipe-up settings sheet & floating chevron handle |
| True Black Controls | `btn_black_mode_single`, `btn_black_mode_range`, `slider_pitch_black_ratio`, `range_slider_pitch_black` | True Black Ratio mode & sliders |
| Studio Summary & Quick Rows | `pill_open_patterns`, `pill_open_pattern_layout`, `pill_open_palettes`, `pill_open_color_fill`, `btn_like_the_app` | Navigates to Patterns/Colors pages or opens quick Layout/Fill/Appreciation popups |
| Studio Quick Popups | `dialog_pattern_layout` (`layout_option_normal`, `layout_option_center`), `dialog_color_fill` (`fill_option_solid`, `fill_option_gradient`) | Unified `BasicAlertDialog` popups for Pattern Layout & Color Fill Style |
| Apply / Export Dialog | `dialog_set_wallpaper_target`, `manual_target_<id>`, `btn_save_to_device`, `btn_share_wallpaper` | Double-tap wallpaper apply (`Home Screen`, `Lock Screen`, `Both`, `Both (Separate)`), lossless PNG save & share |
| Auto-Change Section & Modals | `switch_auto_rotate`, `pill_open_auto_target`, `pill_open_interval`, `dialog_auto_target`, `dialog_interval_picker`, `dialog_custom_interval`, `btn_auto_change_help` | Background schedule controls (disabled when switch is off), unified target/interval popups & help entry |
| Auto-Change Help | `auto_change_help_screen`, `btn_not_your_device`, `dialog_select_brand`, `chip_vendor_<id>`, `btn_open_vendor_shortcut`, `btn_open_app_battery_settings` | OEM battery-saver guide, brand picker popup & deep-link shortcuts |
| Appreciation & Tip Jar | `dialog_like_app`, `btn_dialog_rate_app`, `btn_dialog_send_thanks`, `dialog_send_thanks`, `btn_tip_<amount>` | Enjoying Rimalux? review prompt & single-column direct-action tip jar |
| Patterns Screen | `patterns_screen_list`, `btn_weight_mode_normal`, `btn_weight_mode_center`, `btn_patterns_random`, `btn_patterns_multiselect`, `chip_pattern_cat_<id>`, `pattern_card_<id>` | Adaptive `LazyVerticalGrid` (`minSize = 160.dp`), Normal/Center Weighted layout & 80-pattern selection |
| Colors Screen | `fill_mode_solid`, `fill_mode_gradient`, `palette_mode_random`, `palette_mode_list`, `btn_shuffle_random_colors`, `btn_open_create_palette` | Solid/Gradient fill & palette selection/creation |
