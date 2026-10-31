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
│  • WallpaperCoreModels.kt     (ArtPattern[64], ColorPalette, CoreSpec)    │
│  • WallpaperGeneratorEngine.kt(Single portable synthesis entry point)     │
│  • MathPatternGenerator.kt    (Seeded PRNG + SculptField + 64 dispatch)   │
│  • *Patterns.kt (9 files)     (64 procedural geometry algorithms)         │
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

### 2.2 `SculptField` & `DraftCell` Pipeline
1. `MathPatternGenerator.generate(seed, pattern, intricacy, aspectRatio)` initializes a deterministic `Random(seed)` and constructs a multi-octave spatial `SculptField(rng, safeAspect)`.
2. The selected pattern builder produces `List<DraftCell>`. Each `DraftCell` has:
   - `score: Float` (spatial priority evaluated via `SculptField` so negative `#000000` space forms cohesive, balanced sculptural voids rather than clustering in one corner),
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
| Studio Canvas | `vector_preview_canvas` | Full-bleed swipe deck & double-tap target |
| Studio Overlay | `btn_toggle_overlay_top` | Cycles Clean / Lock Screen / Home Icons mockup |
| Studio Sheet | `home_bottom_sheet`, `bottom_sheet_drag_handle` | Swipe-up settings bottom sheet |
| True Black Controls | `seg_black_single`, `seg_black_range`, `slider_pitch_black`, `range_slider_pitch_black` | True Black Ratio mode & sliders |
| Summary Rows | `row_open_patterns`, `row_open_palettes` | Navigates to Patterns / Colors screens |
| Apply / Export Dialog | `dialog_set_wallpaper_target`, `target_option_<id>`, `btn_save_to_device`, `btn_share_wallpaper` | Double-tap wallpaper apply, lossless PNG save & share |
| Auto-Change Section | `switch_auto_rotate`, `card_auto_target_picker`, `card_interval_picker`, `btn_auto_change_troubleshoot` | Background schedule controls & help entry |
| Auto-Change Help | `auto_change_help_screen`, `vendor_chip_<id>`, `btn_open_vendor_settings`, `btn_open_app_info_settings` | OEM battery-saver guide & deep-link shortcuts |
| Patterns Screen | `patterns_mode_random`, `patterns_mode_select`, `btn_patterns_all`, `btn_patterns_none`, `pattern_card_<id>` | 64-pattern randomization & custom multi-select |
| Colors Screen | `fill_mode_solid`, `fill_mode_gradient`, `palette_mode_random`, `palette_mode_list`, `btn_shuffle_random_colors`, `btn_open_create_palette` | Solid/Gradient fill & palette selection/creation |
