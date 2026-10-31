# AGENTS.md — Rimalux AI Coding Agent Handover Guide

Read this file completely before modifying, extending, or porting Rimalux. It defines the architectural boundaries, mathematical contracts, concurrency rules, UI/UX policies, and build/test restrictions that keep the codebase portable and regression-free.

For deep-dive cross-platform porting equations and module maps, also read [`docs/ARCHITECTURE_AND_PORTING.md`](docs/ARCHITECTURE_AND_PORTING.md).

---

## 1. Project Identity & Product Summary

- **App Name**: `Rimalux` (must remain synced between `metadata.json` and `app/src/main/res/values/strings.xml`).
- **Namespace / Package Root**: `com.example` (do **NOT** rename `com/example` directories or `namespace = "com.example"` in `app/build.gradle.kts`).
- **Application ID**: `com.aistudio.rimaluxwallpaper.qrxvmn` (do **NOT** change unless explicitly instructed).
- **Core Purpose**: Procedural mathematical vector wallpaper studio engineered specifically for OLED displays, guaranteeing an exact user-selected `#000000` True Black Ratio (`0%–95%`, default `85%`) across **80 procedural math patterns**, **Normal & Center Weighted** layout modes, **40 curated non-black palettes**, **8 HSV color-theory random harmonies**, lossless PNG export/share, and resilient background auto-change.

---

## 2. Strict Core vs. Android Architectural Boundary (CRITICAL)

All core wallpaper generation, mathematical algorithms, color harmony math, and `#000000` True Black Ratio calculation **MUST** stay 100% isolated from Android SDK, Jetpack Compose, Android resources (`R.*`), and app state/UI/scheduler logic so the engine can be lifted directly into another platform (iOS, Web, Desktop, Kotlin Multiplatform).

### Package Layer Rules

1. **`com.example.engine` (`app/src/main/java/com/example/engine/`) — PURE PORTABLE CORE**
   - **Allowed imports**: `kotlin.*` standard library **ONLY** (`kotlin.math.*`, `kotlin.random.*`, `kotlin.collections.*`).
   - **STRICTLY FORBIDDEN in `engine/`**:
     - `android.*` (no `Bitmap`, `Canvas`, `Color`, `Context`, `WallpaperManager`, `MediaStore`, `DisplayMetrics`)
     - `androidx.*` (no Compose `DrawScope`, `Color`, `Brush`, `@StringRes`, `WorkManager`, `DataStore`, `ViewModel`)
     - `com.example.R` (no resource IDs)
     - `com.example.model.*`, `com.example.platform.*`, `com.example.data.*`, `com.example.scheduler.*`, `com.example.ui.*`
   - **Automated Enforcement**: `CoreEnginePortabilityTest` scans every `.kt` file in `engine/` on the bare JVM and fails the test suite if any forbidden import or inline reference is introduced.
   - **Key Core Entry Points**:
     - `WallpaperGeneratorEngine`: High-level portable synthesis facade (`synthesize`, `synthesizeFromParameters`, `synthesizeDistinctPair`, `sampleIntricacy`, `nextDistinctSeed`).
     - `MathPatternGenerator` + `SculptField` + 11 `*Patterns.kt` suites: Generates all 80 procedural patterns in normalized Euclidean coordinates `[0, 1] x [0, aspectRatio]` with `PatternWeightMode` (`NORMAL` / `CENTER_WEIGHTED`).
     - `CoverageCalibrator`: 2D rasterized coverage mask (`GRID_W = 200`) + center-outward priority ranking & gapless faceted underlay mosaic that calibrates each primitive's `threshold` and `areaWeight`.
     - `DeepBlackRatioEngine`: Single/range target black ratio resolution, `isPrimitiveVisible`, analytical `SceneRatioStats`, pure pixel-grid `#000000` sampler (`measureTrueBlackPercentage`), `clampDimensionsToMaxAxis`, and `computeViewportTransform`.
     - `ColorHarmonyEngine` & `RandomPaletteGenerator`: Pure HSV-to-ARGB math, non-black OLED shading floor, linear gradient endpoint math (`computeLinearGradient`), and deterministic palette selection.
     - `WallpaperCoreModels`: `PatternCategory`, `ArtPattern` (with portable `defaultTitle` & `defaultSubtitle`), `PatternWeightMode`, `ColorFillMode`, `PaletteMode`, `PitchBlackMode`, `ColorPalette`, `StarterPalettes`, `CoreGenerationSpec`, `SynthesizedWallpaper`.

2. **`com.example.platform` (`app/src/main/java/com/example/platform/`) — ANDROID GRAPHICS & OS ADAPTERS**
   - `WallpaperRenderer.kt`: Rasterizes `VectorScene` onto `android.graphics.Bitmap`, measures rendered `#000000` percentage via `DeepBlackRatioEngine.measureTrueBlackPercentage`, queries `WindowManager` display metrics, and applies wallpapers via `WallpaperManager` (`HOME`, `LOCK`, `BOTH`, `BOTH_SEPARATE`).
   - `ComposeSceneRenderer.kt`: Jetpack Compose `DrawScope.drawVectorScene` adapter.
   - `WallpaperExporter.kt`: Lossless PNG gallery export (`MediaStore` scoped storage) and `FileProvider` share chooser (`cacheDir/shared_wallpapers`, retains max 3 files).

3. **`com.example.model` (`app/src/main/java/com/example/model/`) — ANDROID APP MODELS & RESOURCE MAPPINGS**
   - `PatternResourceMappings.kt`: Maps `ArtPattern` and `PatternCategory` to Android `@StringRes` / `@DrawableRes` (`titleRes`, `subtitleRes`, `previewRes`, `labelRes`).
   - `WallpaperModels.kt`: Re-exports core types via `typealias` and defines Android app state (`WallpaperTarget`, `MockupOverlayMode`, `GeneratorConfig` with `toCoreSpec()`, `GeneratedHistoryItem`, `IntervalFormatter`).

4. **`com.example.data` (`app/src/main/java/com/example/data/`) — PERSISTENCE**
   - `WallpaperPreferencesRepository.kt`: Preferences DataStore (`rimalux_wallpaper_v3_prefs`).

5. **`com.example.scheduler` (`app/src/main/java/com/example/scheduler/`) — BACKGROUND AUTO-CHANGE**
   - `WallpaperScheduler.kt`, `WallpaperRotationWorker.kt`, `WallpaperAlarmReceiver.kt`, `BootCompletedReceiver.kt`, `OemBackgroundHelper.kt`.

6. **`com.example.ui` (`app/src/main/java/com/example/ui/`) — PRESENTATION & COMPOSE UI**
   - `RimaluxViewModel.kt`, `RimaluxApp.kt`, `screens/*` (`StudioScreen`, `PatternsScreen`, `PalettesScreen`, `ScheduleScreen`, `AutoChangeHelpScreen`), `components/*` (`VectorCanvasView`, `CreatePaletteSheet`), `theme/*`.

---

## 3. Non-Negotiable Domain & Mathematical Invariants

1. **Exact `#000000` True Black Calibration**:
   - Every pattern generator returns `List<DraftCell>` and **MUST** pass through `CoverageCalibrator.calibrate(...)`.
   - Visibility rule: a `VectorPrimitive` is drawn iff `primitive.threshold >= clampedBlackRatio`.
   - Measured rendered `#000000` pixels (`(argb and 0x00FFFFFF) == 0`) must stay within `±7%` of target across `[25%, 65%, 85%]` and increase monotonically with target black ratio across all 80 patterns in both `NORMAL` and `CENTER_WEIGHTED` modes.
2. **Non-Black Palette & Shading Floor**:
   - Starter palettes and `RandomPaletteGenerator` must **never** include `#000000`.
   - `ColorHarmonyEngine.resolveArgbColor` clamps any non-black swatch that rounds to `(0, 0, 0)` under dark shading up to `(0, 0, 4)` (`0xFF000004L`) so colored geometry is never miscounted as `#000000` OLED void.
3. **Intricacy Window**:
   - Sampled uniformly in `[0.40f, 0.60f]` (`WallpaperGeneratorEngine.sampleIntricacy`).
4. **4K Flagship Cap & Bitmap Lifecycle**:
   - `DeepBlackRatioEngine.MAX_WALLPAPER_AXIS_PX = 4096`. Any higher display resolution is proportionally scaled down to avoid `Canvas` / Binder `TransactionTooLargeException` OOMs.
   - System/export `Bitmap` instances **must** always call `bitmap.recycle()` in a `finally` block.
5. **Static Pattern Previews**:
   - Whenever adding a new `ArtPattern`, add its localized title/subtitle to `strings.xml`, map `titleRes`/`subtitleRes`/`previewRes` in `PatternResourceMappings.kt`, and provide its static preview image in `res/drawable-nodpi/`.

---

## 4. Background Auto-Change & Concurrency Rules (CRITICAL)

1. **Existence-First Schedule Verification (`ensureScheduled`)**:
   - Opening the app calls `WallpaperScheduler.ensureScheduled(...)`.
   - It **MUST** check `isWorkScheduled(...)` and `isAlarmScheduled(...)` (`PendingIntent.FLAG_NO_CREATE`) **first**. If both already exist and `desiredNextRunMillis` is a valid future timestamp, it **MUST** return immediately as a strict **no-op** without re-arming `AlarmManager` or recreating the `WorkManager` job.
   - It only re-arms/repairs schedules if an OEM battery saver Force-Stopped the app, cleared alarms on reboot, or let `nextRunEpochMillis` expire while dead.
2. **Dual-Engine Deduplication Window**:
   - Both `WorkManager` (`PeriodicWorkRequest` for `>= 15m`, chained `OneTimeWorkRequest` for `< 15m`) and `AlarmManager.setAndAllowWhileIdle` are used for reliability, plus a foreground monitor while the app is open.
   - `executeAutoRotationCycle` enforces a **75% interval deduplication window** (`max(30_000L, intervalMillis * 3 / 4)`) inside `rotationMutex` so simultaneous or Doze-staggered triggers never produce duplicate wallpaper rotations.
3. **Dispatcher & Mutex Deadlock Prevention (DO NOT VIOLATE)**:
   - In `WallpaperScheduler.kt`, `withContext(Dispatchers.IO)` **MUST** remain *outside* `scheduleMutex.withLock` and `rotationMutex.withLock`.
   - In `WallpaperPreferencesRepository.kt`:
     - All `DataStore.edit` and `stateFlow.first()` calls **MUST** execute inside `withContext(Dispatchers.IO)` and `stateFlow` must end with `.flowOn(Dispatchers.IO)`. Calling `DataStore.edit` directly on `Dispatchers.Main` deadlocks Robolectric's paused `MainLooper` because DataStore's `writeActor` dispatches `transform` onto the caller's `CoroutineContext`.
     - `rimaluxDataStoreRegistry` **MUST** remain keyed by `appCtx.filesDir.canonicalPath` so isolated test contexts never collide on a stale static singleton directory.
4. **UI Config Saves Must Preserve Background Schedule Fields**:
   - When `RimaluxViewModel.persistConfigChanges` writes pattern/palette/ratio edits, it **MUST** preserve `storedConfig.autoRotateEnabled`, `storedConfig.intervalMinutes`, and `storedConfig.nextRunEpochMillis` from DataStore so slider drags never clobber a background worker's newly advanced `nextRunEpochMillis`.

---

## 5. UI/UX, Copywriting & Accessibility Rules

1. **Capitalization Policy**:
   - Use **normal UI capitalization** throughout the entire app: Title Case for screen titles, section headers, buttons, andsegmented controls; Sentence case for subtitles, helper descriptions, and toast/status messages. (Do **not** revert to all-lowercase.)
2. **Terminology**:
   - Call procedural designs **"Patterns"** (never "Styles").
   - Call the palette/shading page & nav item **"Colors"**.
   - Call `#000000` coverage **"True Black Ratio"** (never "Pitch Black Ratio" in user-facing copy).
   - No live countdown timer on the Auto-Change switch (removed intentionally to avoid out-of-sync display states when OS batches alarms).
3. **OLED Dynamic Theme (`Theme.kt`)**:
   - `background` and `surface` are strictly `#000000`.
   - Tonal containers (`surfaceContainerLow` -> `surfaceContainerHighest`) must maintain strictly monotonic luminance `< 0.03f`, and `primary` against `background`/`surfaceContainer` must maintain `>= 7.0:1` WCAG contrast.
4. **Studio Gesture Physics**:
   - Horizontal swipe left advances to next wallpaper (`PREFETCH_AHEAD_TARGET = 3`); swipe right restores previous (`MAX_PAST_STACK = 24`) or triggers elastic edge bounce when empty.
   - Double-tap opens `Apply Wallpaper To` dialog (includes `Save to Device` and `Share`).
   - Vertical swipe-up reveals the hidden settings bottom sheet (`56dp` drag threshold or `-220dp/s` upward flick velocity so casual flicks never snap back closed).
5. **Compose `testTag` Stability**:
   - Preserve existing `Modifier.testTag(...)` IDs (`vector_preview_canvas`, `home_bottom_sheet`, `dialog_set_wallpaper_target`, `btn_save_to_device`, `btn_share_wallpaper`, `btn_auto_change_troubleshoot`, etc.) and `>= 48.dp` minimum touch targets.

---

## 6. Build, Permissions & Verification Restrictions

- **Gradle Command**: Always use `gradle` (e.g., `gradle :app:testDebugUnitTest`), **NEVER** `./gradlew` or `gradlew`.
- **Single Gradle Process Rule**: **NEVER** run two Gradle commands concurrently in parallel tool calls—spawning multiple Gradle Daemons can exhaust container RAM.
- **No Emulator / No ADB**: Never run `adb` or `connectedAndroidTest`. Verify exclusively via `compile_applet` (or `gradle assembleDebug`) and `gradle :app:testDebugUnitTest`.
- **Signing & Gradle Config**: Never modify `signingConfigs`, `debug.keystore`, `debug.keystore.base64`, AGP/Kotlin versions, or `compileSdk`/`targetSdk`. Keep unused dependencies commented out in `app/build.gradle.kts`.
- **Google Play Policy Permissions**:
  - Allowed permissions in `AndroidManifest.xml`: `SET_WALLPAPER`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, and `WRITE_EXTERNAL_STORAGE` (`android:maxSdkVersion="28"`).
  - **NEVER** add `READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, or `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- **File Modularity**: Keep source files focused and modular (ideally `< 500` lines when creating or splitting modules).

### Standard Regression Verification Checklist

Run sequentially after changes:
1. `gradle :app:testDebugUnitTest --console=plain`
   - `CoreEnginePortabilityTest`: Verifies 0 Android/Compose/app imports in `engine/` + pure-JVM synthesis of all 80 patterns across `NORMAL` and `CENTER_WEIGHTED` modes.
   - `WallpaperEngineAndAutoScheduleTest`: Verifies pixel-level `#000000` calibration across all 80 patterns + Center Weighted side-margin emptiness, QHD+/4K clamping, foreground/background auto-change, Force-Close & reboot recovery, and 3-way race / Doze deduplication.
   - `ExampleRobolectricTest`: Verifies full Compose UI journeys, swipe deck, bottom sheet flick physics, pattern layout & color screens, WCAG contrast, lossless PNG save/share, and OEM troubleshooting shortcuts.
