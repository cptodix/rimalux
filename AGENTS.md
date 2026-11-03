# AGENTS.md — Rimalux AI Coding Agent Handover Guide

Read this file completely before modifying, extending, or porting Rimalux. It defines the architectural boundaries, mathematical contracts, concurrency rules, UI/UX policies, and build/test restrictions that keep the codebase portable and regression-free.

For deep-dive cross-platform porting equations and module maps, also read [`docs/ARCHITECTURE_AND_PORTING.md`](docs/ARCHITECTURE_AND_PORTING.md). For manual GitHub Actions release builds (`.apk`/`.aab`) and direct Google Play Console publishing setup, see [`docs/GITHUB_ACTIONS_PUBLISHING.md`](docs/GITHUB_ACTIONS_PUBLISHING.md).

---

## 1. Project Identity & Product Summary

- **App Name**: `Rimalux` (must remain synced between `metadata.json` and `app/src/main/res/values/strings.xml`).
- **Namespace / Package Root**: `com.cptodix.rimalux.oled.wallpaper` (`app/src/main/java/com/cptodix/rimalux/oled/wallpaper/`; keep `namespace = "com.cptodix.rimalux.oled.wallpaper"` in `app/build.gradle.kts` synced with directory structure).
- **Application ID**: `com.cptodix.rimalux.oled.wallpaper` (do **NOT** change unless explicitly instructed).
- **App Version**: `1.0.0` (`versionCode = 1`, surfaced at the bottom of the Studio sheet via `sheet_about_footer`).
- **Core Purpose**: Procedural mathematical vector wallpaper studio engineered specifically for OLED displays, guaranteeing an exact user-selected `#000000` True Black Ratio (`0%–95%`, default `85%`) across **80 procedural math patterns**, **Normal & Center Weighted** layout modes, **41 curated non-black palettes** (including Monochromatic OLED), **8 HSV color-theory random harmonies**, lossless PNG export/share, multi-language localization (`en-US` default, `id-ID` Bahasa Indonesia), and resilient background auto-change.
- **Non-App Repository Layout**: Keep documentation in `docs/` and all store/promo/brand assets consolidated under `distribution/` (`whatsnew/`, `promotional_wallpapers/`, `showcase_wallpapers/`, `brand/`). Never scatter loose archives or `public/` folders in `/` or `app/`.

---

## 2. Strict Core vs. Android Architectural Boundary (CRITICAL)

All core wallpaper generation, mathematical algorithms, color harmony math, and `#000000` True Black Ratio calculation **MUST** stay 100% isolated from Android SDK, Jetpack Compose, Android resources (`R.*`), and app state/UI/scheduler logic so the engine can be lifted directly into another platform (iOS, Web, Desktop, Kotlin Multiplatform).

### Package Layer Rules

1. **`com.cptodix.rimalux.oled.wallpaper.engine` (`app/src/main/java/com/cptodix/rimalux/oled/wallpaper/engine/`) — PURE PORTABLE CORE**
   - **Allowed imports**: `kotlin.*` standard library **ONLY** (`kotlin.math.*`, `kotlin.random.*`, `kotlin.collections.*`).
   - **STRICTLY FORBIDDEN in `engine/`**:
     - `android.*` (no `Bitmap`, `Canvas`, `Color`, `Context`, `WallpaperManager`, `MediaStore`, `DisplayMetrics`)
     - `androidx.*` (no Compose `DrawScope`, `Color`, `Brush`, `@StringRes`, `WorkManager`, `DataStore`, `ViewModel`)
     - `com.cptodix.rimalux.oled.wallpaper.R` (no resource IDs)
     - `com.cptodix.rimalux.oled.wallpaper.model.*`, `com.cptodix.rimalux.oled.wallpaper.platform.*`, `com.cptodix.rimalux.oled.wallpaper.data.*`, `com.cptodix.rimalux.oled.wallpaper.scheduler.*`, `com.cptodix.rimalux.oled.wallpaper.ui.*`
   - **Automated Enforcement**: `CoreEnginePortabilityTest` scans every `.kt` file in `engine/` on the bare JVM and fails the test suite if any forbidden import or inline reference is introduced.
   - **Key Core Entry Points**:
     - `WallpaperGeneratorEngine`: High-level portable synthesis facade (`synthesize`, `synthesizeFromParameters`, `synthesizeDistinctPair`, `sampleIntricacy`, `nextDistinctSeed`).
     - `MathPatternGenerator` + `SculptField` + 11 `*Patterns.kt` suites: Generates all 80 procedural patterns in normalized Euclidean coordinates `[0, 1] x [0, aspectRatio]` with `PatternWeightMode` (`NORMAL` / `CENTER_WEIGHTED`).
     - `CoverageCalibrator`: 2D rasterized coverage mask (`GRID_W = 200`) + center-outward priority ranking & gapless faceted underlay mosaic that calibrates each primitive's `threshold` and `areaWeight`.
     - `DeepBlackRatioEngine`: Single/range target black ratio resolution, `isPrimitiveVisible`, analytical `SceneRatioStats`, pure pixel-grid `#000000` sampler (`measureTrueBlackPercentage`), `clampDimensionsToMaxAxis`, and `computeViewportTransform`.
     - `ColorHarmonyEngine` & `RandomPaletteGenerator`: Pure HSV-to-ARGB math, non-black OLED shading floor, linear gradient endpoint math (`computeLinearGradient`), and deterministic palette selection.
     - `WatermarkPlacementSolver`: Pure 2D summed-area-table emptiness/luminance solver and social-media safe-zone perimeter calculator (`computeSocialSafeZone`, `findOptimalPlacement`, `computeSubtleWatermarkArgb`) for share-only edge watermarks (horizontal top/bottom or vertical left/right; never interior/center).
     - `WallpaperCoreModels`: `PatternCategory`, `ArtPattern` (with portable `defaultTitle` & `defaultSubtitle`), `PatternWeightMode`, `ColorFillMode`, `PaletteMode`, `PitchBlackMode`, `ColorPalette`, `StarterPalettes`, `CoreGenerationSpec`, `SynthesizedWallpaper`.

2. **`com.cptodix.rimalux.oled.wallpaper.platform` (`app/src/main/java/com/cptodix/rimalux/oled/wallpaper/platform/`) — ANDROID GRAPHICS & OS ADAPTERS**
   - `WallpaperRenderer.kt`: Rasterizes `VectorScene` onto `android.graphics.Bitmap`, measures rendered `#000000` percentage via `DeepBlackRatioEngine.measureTrueBlackPercentage`, queries `WindowManager` display metrics, and applies wallpapers via `WallpaperManager` (`HOME`, `LOCK`, `BOTH`, `BOTH_SEPARATE`).
   - `ComposeSceneRenderer.kt`: Jetpack Compose `DrawScope.drawVectorScene` adapter.
   - `WallpaperExporter.kt`: Unwatermarked lossless PNG gallery export (`MediaStore` scoped storage) and `FileProvider` share chooser (`cacheDir/shared_wallpapers`, retains max 3 files) with a subtle `"Procedurally generated by Rimalux · <pattern>"` watermark placed strictly along the darkest/emptiest `#000000` perimeter rail (horizontal top/bottom or vertical left/right) of the social media safe zone.
   - `AppLocaleManager.kt`: First-launch OS system locale negotiation (`LocaleManager.systemLocales` / `Resources.getSystem()`) with `en-US` fallback, synchronous `MainActivity.attachBaseContext` wrapping, and localized `Context` + RTL layout-direction creation.
   - `PlayBillingManager.kt`: Google Play Billing Library (`com.android.billingclient:billing-ktx`) adapter for querying localized one-time product prices, launching purchase flows, handling `PENDING` states, and consuming `PURCHASED` tips (`consumeAsync`).

3. **`com.cptodix.rimalux.oled.wallpaper.model` (`app/src/main/java/com/cptodix/rimalux/oled/wallpaper/model/`) — ANDROID APP MODELS & RESOURCE MAPPINGS**
   - `AppLanguage.kt`: Single-source supported language registry (`EN_US` `"en-US"`, `ID_ID` `"id-ID"`) and pure 3-tier BCP-47 language/script/region negotiator (`resolveInitialLanguage`, `resolveBestSupportedTag`).
   - `TipProduct.kt`: Canonical Google Play Console One-Time Product IDs (`rimalux_tip_1`, `rimalux_tip_3`, `rimalux_tip_5`, `rimalux_tip_10`, `rimalux_tip_20`) and `TipCatalogItem` UI model.
   - `PatternResourceMappings.kt`: Maps `ArtPattern`, `PatternCategory`, and all 41 built-in `ColorPalette` entries to Android `@StringRes` / `@DrawableRes` (`titleRes`, `subtitleRes`, `previewRes`, `labelRes`, `nameResOrNull`, `resolveDisplayName`).
   - `WallpaperModels.kt`: Re-exports core types via `typealias` and defines Android app state (`WallpaperTarget`, `MockupOverlayMode`, `GeneratorConfig` with `toCoreSpec()`, `GeneratedHistoryItem`, localized `IntervalFormatter`).

4. **`com.cptodix.rimalux.oled.wallpaper.data` (`app/src/main/java/com/cptodix/rimalux/oled/wallpaper/data/`) — PERSISTENCE**
   - `WallpaperPreferencesRepository.kt`: Preferences DataStore (`rimalux_wallpaper_v3_prefs`).

5. **`com.cptodix.rimalux.oled.wallpaper.scheduler` (`app/src/main/java/com/cptodix/rimalux/oled/wallpaper/scheduler/`) — BACKGROUND AUTO-CHANGE**
   - `WallpaperScheduler.kt`, `WallpaperRotationWorker.kt`, `WallpaperAlarmReceiver.kt`, `BootCompletedReceiver.kt`, `OemBackgroundHelper.kt`.

6. **`com.cptodix.rimalux.oled.wallpaper.ui` (`app/src/main/java/com/cptodix/rimalux/oled/wallpaper/ui/`) — PRESENTATION & COMPOSE UI**
   - `RimaluxViewModel.kt`, `RimaluxApp.kt`, `screens/*` (`StudioScreen`, `PatternsScreen`, `PalettesScreen`, `ScheduleScreen`, `AutoChangeHelpScreen`, `CustomPaletteSheet`), `components/*` (`VectorCanvasView`, `PhoneMockupOverlays`, `StudioGestureOverlay`, `RimaluxModalDialog`, `LikeAppDialogs`), `theme/*` (`Color`, `Theme`, `Type`).

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

## 5. UI/UX, Material 3, Performance & Copywriting Handover Rules

1. **Capitalization & Copywriting Policy**:
   - Use **normal UI capitalization** throughout the entire app: **Title Case** for screen titles, modal titles, section headers, buttons, and segmented controls; **Sentence case** for subtitles, helper descriptions, and toast/status messages. (Do **not** revert to all-lowercase.)
   - **Mandatory Terminology**:
     - Call procedural designs **"Patterns"** (never `"Styles"`).
     - Call the palette/shading page & navigation row **"Colors"**.
     - Call `#000000` coverage **"True Black Ratio"** (never `"Pitch Black Ratio"` in user-facing copy).
     - Call `WallpaperTarget.HOME` **"Home Screen"** (never `"Wallpaper"`, which creates redundant `"Apply Wallpaper To -> Wallpaper"` phrasing).
     - Use **"Enjoying Rimalux?"** consistently on both the Studio bottom-sheet trigger (`btn_like_the_app`) and the appreciation modal title (`dialog_like_app`).
     - Do **not** add a live countdown timer on the Auto-Change switch (omitted intentionally so OS alarm batching never causes out-of-sync countdown text).
2. **OLED Dynamic Theme (`Theme.kt` & `Color.kt`)**:
   - Do **not** alter `createDynamicColorScheme` color-generation math unless explicitly instructed.
   - `background` and `surface` are strictly `#000000`.
   - Tonal containers (`surfaceContainerLowest` -> `surfaceContainerLow` -> `surfaceContainer` -> `surfaceContainerHigh` -> `surfaceContainerHighest`) must maintain strictly monotonic luminance `< 0.03f`, and `primary` against `background`/`surfaceContainer` must maintain `>= 7.0:1` WCAG contrast.
3. **Medium Architectural Shape Scale (`Shape.kt`)**:
   - Keep corner radii restrained at medium sharpness (`extraSmall = 6.dp`, `small = 8.dp`, `medium = 12.dp`, `large = 14.dp`, `extraLarge = 16.dp`) and `16.dp` top corners on sheets (`home_settings_sheet`, `CreatePaletteSheet`).
   - **Never** revert rectangular buttons, chips, segmented controls, cards, or modals to `28.dp` or `CircleShape` pills. Reserve `CircleShape` strictly for 1:1 circular color dots, circular icon badges, and the floating sheet handle.
4. **Material 3 Component Hierarchy & Modal Unification**:
   - **4-Tier Action Emphasis**:
     1. `Button` (filled primary, `shapes.medium`) for primary commits (`Save`, `Create Palette`, `Got It`, primary OEM battery shortcut).
     2. `FilledTonalButton` (`shapes.medium`) for secondary constructive actions.
     3. `OutlinedButton` (`shapes.medium`) for bounded utility/secondary actions (`Save to Device`, `Share`, `Open App Info`, `Enjoying Rimalux?`).
     4. `TextButton` (`shapes.small`, `onSurfaceVariant` content color) for low-emphasis dismiss/cancel/reset actions (`Cancel`, `Remove`, `Select None`, `Not your device?`).
   - **Tonal Surface Hierarchy**:
     - `surfaceContainerLow`: Bottom sheets and passive informational cards (`AutoChangeHelpScreen` cards, gallery unselected cards).
     - `surfaceContainer` + `0.5.dp` `outlineVariant` hairline border: Primary grouped control cards (`StudioScreen` main control card, `AutoChangeSection`, `Pattern Layout` / `Color Fill Style` cards).
     - `surfaceContainerHigh` + `1.dp` `outlineVariant` border: Modal dialogs (`RimaluxModalDialog`) and selected gallery cards.
     - `surfaceContainerHighest`: Interactive option rows and text inputs inside modals (`secondaryContainer` + `1.dp` `primary` border + `Icons.Rounded.Check` when selected).
   - **Grouped Control Rows & Disabled Child States**:
     - Primary settings rows (`True Black Ratio`, `Patterns`, `Colors`, `Auto-Change Wallpaper`) use `titleMedium`.
     - Subordinate quick-popup rows inside grouped cards (`Pattern Layout`, `Color Fill Style`, `Apply To`, `Interval`) use `titleSmall` and open single-choice popups rather than inline segmented buttons.
     - Dependent child rows (`Apply To`, `Interval`) **must** set `enabled = config.autoRotateEnabled` in addition to visual dimming (`0.55f` alpha) so disabled-looking rows are non-clickable while Auto-Change is off.
   - **Unified Modal Container (`RimaluxModalDialog`)**:
     - Every popup modal (`Apply Wallpaper To`, `Pattern Layout`, `Color Fill Style`, `Apply To`, `Interval` / `Custom Interval`, `Select Brand`, `Enjoying Rimalux?`, `Send a Thanks`, `Language`) **must** use `RimaluxModalDialog` (`BasicAlertDialog` in `ui/components/RimaluxModalDialog.kt`) for unified window animations, focus trapping, and TalkBack announcements. Never mix raw `Dialog`, `AlertDialog`, and custom scrim `Box`es.
   - **Adaptive Grids & Filter Chips**:
     - Multi-column visual galleries (`PatternsScreen`) must use `LazyVerticalGrid(GridCells.Adaptive(minSize = 160.dp))` (`.widthIn(max = 920.dp)`) with stable `key` and `contentType`—never manual `chunked(2)` rows inside a `LazyColumn`.
     - Selected `FilterChip`s must include `Icons.Rounded.Check` (`FilterChipDefaults.IconSize`) as `leadingIcon`.
5. **Typography Hierarchy (`Type.kt`)**:
   - All 15 Material 3 typography tokens use bundled `PlusJakartaSansFontFamily` (`res/font/plus_jakarta_sans.ttf`) with official M3 baseline weights: `FontWeight.Normal` (`400`) for `display*`, `headline*`, `titleLarge`, and `body*`; `FontWeight.Medium` (`500`) for `titleMedium`, `titleSmall`, and `label*`.
   - Do **not** scatter ad-hoc `fontWeight = FontWeight.Bold` / `SemiBold` overrides on individual `Text` composables—express hierarchy through semantic token selection (`titleLarge` -> `titleMedium` -> `titleSmall` -> `bodyMedium` -> `bodySmall` -> `labelLarge`/`labelSmall`) and tonal color (`onSurface` vs `onSurfaceVariant` vs `primary`).
   - Full-screen sub-pages (`PatternsScreen`, `PalettesScreen`, `AutoChangeHelpScreen`) use `LargeTopAppBar` with `TopAppBarDefaults.exitUntilCollapsedScrollBehavior`.
6. **UI & Gesture Performance Rules**:
   - **Defer Motion Reads to Layout/Draw Phases**: Never read continuous `Animatable` or drag-offset values directly in composition to drive animations. Gate structural composition booleans with `remember { derivedStateOf { ... } }` and apply per-frame transforms inside `Modifier.graphicsLayer { ... }` or `Modifier.offset { ... }`.
   - **Allocation Discipline**: Memoize `ColorScheme` via `remember(seedColorHex, darkTheme)`, precompute static `Brush`es outside `Canvas`, keep off-screen swipe deck neighbors hidden (`alpha = 0f`) while stationary (`abs(offsetX) < 0.5f`), and pass `key` + `contentType` to all `LazyColumn` / `LazyVerticalGrid` items.
7. **Studio Gesture Physics, Hint System & Compose `testTag` Stability**:
   - Horizontal swipe left advances to next wallpaper (`PREFETCH_AHEAD_TARGET = 3`); swipe right restores previous (`MAX_PAST_STACK = 24`) or triggers elastic edge bounce when empty.
   - Double-tap opens `Apply Wallpaper To` dialog (`Home Screen`, `Lock Screen`, `Both`, `Both Separate`, plus `Save to Device` and `Share`); swipe-down cycles `MockupOverlayMode` (`NONE` -> `LOCK_SCREEN` -> `HOME_GRID`, rendered without fake status bars and clearing both top mini hints and bottom info pills); vertical swipe-up reveals the hidden settings bottom sheet.
   - **Hint Window & Idle Mini Hints**: `StudioGestureOverlay` (`gesture_tutorial_overlay`) uses a darker translucent card without outer backdrop darkening, never opens/dismisses on single-tap, and closes explicitly via `"Got It"` (`btn_dismiss_tutorial`). Successful executions of all 5 `StudioGestureAction`s persist timestamps in DataStore; after `4s` idle (`MINI_HINT_IDLE_DELAY_MS = 4_000L`), `StudioMiniHintBar` (`studio_mini_hint`) rotates every `6s` (`MINI_HINT_ROTATE_INTERVAL_MS = 6_000L`) across gestures unused `> 1 week` (left playful message + CTA, right 4 palette-colored dots: `SWIPE_LEFT`/`SWIPE_RIGHT` size waves, `SWIPE_DOWN`/`SWIPE_UP` vertical `o->O->O->o` glide, `DOUBLE_TAP` heartbeat pulse), hiding immediately on any action or opening `StudioGestureOverlay` on tap.
   - Preserve all `Modifier.testTag(...)` IDs and `>= 48.dp` touch targets (`minimumInteractiveComponentSize()`). In Robolectric `BasicAlertDialog` tests, scope ambiguous text matchers with `hasAnyAncestor(hasTestTag("dialog_..."))`.
8. **Multi-Language Readiness & Language Selector**:
   - All user-facing strings, `<plurals>` (`interval_desc_*`), and built-in starter palette names (`palette_name_*`) live in `res/values/strings.xml`; always pass `Locale.ROOT` to non-UI `.lowercase()` / `.uppercase()` calls on IDs, hex codes, and filenames.
   - First launch after install resolves the user's OS system locale via `AppLocaleManager.resolveOrInitializeLanguage` -> `AppLanguage.resolveInitialLanguage` and falls back to `AppLanguage.EN_US` (`en-US`). Enabled languages: `en-US` (`res/values/strings.xml`) and `id-ID` (`res/values-in/strings.xml`). To add another language later, add `res/values-<lang>/strings.xml`, register `<locale android:name="<bcp47>" />` in `res/xml/locales_config.xml`, and add the enum entry to `AppLanguage`.
   - Keep the low-emphasis language selector (`btn_open_language_selector` -> `dialog_language_selector`) in the Studio bottom-sheet footer strictly between `Enjoying Rimalux?` (`btn_like_the_app`) and the version/credit line (`sheet_about_footer`).

---

## 6. Build, Permissions & Verification Restrictions

- **Gradle Command**: Always use `gradle` (e.g., `gradle :app:testDebugUnitTest`), **NEVER** `./gradlew` or `gradlew`.
- **Single Gradle Process Rule**: **NEVER** run two Gradle commands concurrently in parallel tool calls—spawning multiple Gradle Daemons can exhaust container RAM.
- **No Emulator / No ADB**: Never run `adb` or `connectedAndroidTest`. Verify exclusively via `compile_applet` (or `gradle assembleDebug`) and `gradle :app:testDebugUnitTest`.
- **Signing & Gradle Config**: Never modify `signingConfigs`, `debug.keystore`, `debug.keystore.base64`, AGP/Kotlin versions, or `compileSdk`/`targetSdk`. Keep unused dependencies commented out in `app/build.gradle.kts`.
- **Google Play Policy Permissions**:
  - Allowed permissions in `AndroidManifest.xml`: `SET_WALLPAPER`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `com.android.vending.BILLING`, and `WRITE_EXTERNAL_STORAGE` (`android:maxSdkVersion="28"`).
  - **NEVER** add `READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, or `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- **File Modularity**: Keep source files focused and modular (ideally `< 500` lines when creating or splitting modules).

### Standard Regression Verification Checklist

Run sequentially after changes:
1. `gradle :app:testDebugUnitTest --console=plain`
   - `CoreEnginePortabilityTest`: Verifies 0 Android/Compose/app imports in `engine/` + pure-JVM synthesis of all 80 patterns across `NORMAL` and `CENTER_WEIGHTED` modes.
   - `WallpaperEngineAndAutoScheduleTest`: Verifies pixel-level `#000000` calibration across all 80 patterns + Center Weighted side-margin emptiness, QHD+/4K clamping, foreground/background auto-change, Force-Close & reboot recovery, and 3-way race / Doze deduplication.
   - `ExampleRobolectricTest`: Verifies full Compose UI journeys, swipe deck, bottom sheet flick physics, pattern layout & color screens, WCAG contrast, lossless PNG save/share, and OEM troubleshooting shortcuts.
