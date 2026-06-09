# CLAUDE.md
This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project
Personal Android Car Launcher for Lenovo Tab M10 Plus (3rd Gen). Landscape-locked fullscreen app that replaces the default Android launcher. Built with Kotlin + Jetpack Compose + Hilt.

**Target:** minSdk 31, targetSdk 34, screen ~1143×686 dp landscape only.

## Build Commands
All build/run commands require Android Studio or the Android SDK with Gradle wrapper. Open the project root in Android Studio — it will generate `gradle/wrapper/` on first sync.

```bash
# Assemble debug APK
./gradlew assembleDebug
# Install on connected device
./gradlew installDebug
# Run lint
./gradlew lint
# Run unit tests
./gradlew test
# Run a single test class
./gradlew test --tests "com.example.carlauncher.ExampleTest"
```

**Emulator:** Use API 34 (Android 14) x86_64 with Google Play. Do NOT use API 35 — `libmaplibre.so` in the current version is not 16 KB page-aligned and will fail to load on Android 15 emulators.

## Architecture
Single-module app (`app/`). No multi-module split yet.

**DI:** Hilt (`@HiltAndroidApp` on `CarLauncherApp`, `@AndroidEntryPoint` on `MainActivity`). All ViewModels use `@HiltViewModel` + `@Inject constructor`. Hilt modules live in `di/` — only provide things that have no `@Inject constructor` (e.g. `FusedLocationProviderClient`). Classes with `@Inject constructor` get `@Singleton` directly on the class, not in a `@Provides` method.

**UI layer:** Jetpack Compose throughout. Entry point is `MainActivity → CarLauncherTheme → LauncherScreen`. New screens/widgets go under `ui/` grouped by feature:
```
ui/
  launcher/   ← LauncherScreen + LauncherViewModel
  theme/      ← CarLauncherTheme, color tokens
  map/        ← MapWidget + MapViewModel
  speed/      ← SpeedDisplay (planned)
  music/      ← MusicWidget + MusicViewModel (planned)
  dock/       ← DockBar (planned)
```

**Theme:** `ui/theme/Theme.kt` defines `CarColorScheme` (dark, no light variant). Key colors: background `#0D0D0F`, surface `#1A1A1F`, primary (accent green) `#00C853`. Always use `MaterialTheme.colorScheme.*` tokens rather than hardcoded hex in new composables.

**Fullscreen:** `MainActivity` hides status + nav bars via `WindowInsetsControllerCompat` and sets `FLAG_KEEP_SCREEN_ON`. Do not call `enableEdgeToEdge()` — it conflicts with the manual insets setup.

**Manifest:** `MainActivity` has `HOME` + `DEFAULT` categories making it a system launcher. `screenOrientation="landscape"` + `windowSoftInputMode="adjustNothing"` are intentional and must not be changed.

**Permissions:** Runtime location permission requested in `MainActivity.onCreate()` via `ActivityResultContracts.RequestMultiplePermissions`. `LocationRepository.startTracking()` catches `SecurityException` silently — UI stays on "Waiting for GPS..." if denied.

## GPS Pipeline (Module 2)
Data flows unidirectionally:
```
FusedLocationProviderClient (500 ms interval)
  → LocationCallback  [runs on HandlerThread("location-thread") — NOT Main thread]
  → LocationProcessor.process(Location)
      → KalmanFilter  (2D lat/lng smoothing, Q = 3 m/s)
      → speed rolling average (last 3 samples)
  → VehicleDisplayLocation  (DTO, uses location.time for timestamp)
  → LocationRepository._vehicleLocation: MutableStateFlow
  → LauncherViewModel.vehicleLocation: StateFlow
  → LauncherScreen (collectAsStateWithLifecycle)
```
- **`KalmanFilter`** — plain class, stateful. Call `reset()` after GPS gap. `variance < 0` = uninitialized, first fix passes raw.
- **`LocationRepository`** — `@Singleton`. `startTracking()` / `stopTracking()` called by `LauncherViewModel` init/onCleared. Has `isTracking` guard against double registration. Callback runs on dedicated `HandlerThread` to keep Kalman processing off the Main thread.
- **`LocationForegroundService`** — stub only, declared in manifest with `foregroundServiceType="location"`. Logic added in a later module.
- **GPS update frequency is adaptive** — 500 ms at speed > 5 km/h, 5000 ms when parked (not yet implemented — fixed 500 ms for now). Use `shouldUpdateMarker()` with speed-based distance threshold before MapLibre marker updates.

## Map Widget (Module 3)
- **Renderer:** MapLibre GL Android SDK 11.0.1 — open-source, no API keys.
- **Tile data:** PMTiles format, stored at `assets/maps/czech-republic.pmtiles`. File not yet present — style currently shows dark background only. Full road layers are defined in `map_style_dark.json` and will activate once the file is placed.
- **Style:** `assets/style/map_style_dark.json`. Currently contains only a `background` layer (no sources) to avoid MapLibre failing on missing PMTiles. Restore full style once `czech-republic.pmtiles` is available.
- **MapView lifecycle:** `MapView` requires `onCreate(null)` before `getMapAsync`. In `MapWidget`, this is called inside `remember { MapView(context).also { it.onCreate(null) } }`. `onStart()`/`onResume()` are replayed manually in `DisposableEffect` because the lifecycle may already be RESUMED when the composable first composes.
- **MapLibre objects in Compose:** `MapLibreMap` and `GeoJsonSource` are stored in a plain `MapState` holder inside `remember {}` — **never in `mutableStateOf`** (not Compose-snapshot-safe, causes spurious recomposition).
- **Camera updates:** Always use a single `CameraPosition.Builder` combining `.target()` + `.bearing()` in one `animateCamera()` call. Two sequential `animateCamera()` calls cancel each other.
- **Marker animation:** `animateMarkerTo()` with 400 ms ValueAnimator + `lerpAngle()` for bearing (not yet implemented — direct `setGeoJson` for now).

## Design Reference
The finalized UI design lives in `.claude/design/` (CarLauncher.html, app.jsx, widgets.jsx, icons.jsx).
Do not iterate on the design — implement it as-is.

Key layout values from the design:
- SpeedDisplay number: 56sp monospace, tabular-nums
- Navigovat button: min height 52 dp, accent green gradient
- Dock icons: 72×72 dp outer, 54×54 dp inner icon, rounded 15 dp
- All interactive elements: min 48×48 dp tap target (car-safe)
- GPS chip: 36 dp height, 14sp font

## Planned Modules (not yet implemented)
- Speed display widget
- Music/media widget (MediaSession API)
- App dock (QUERY_ALL_PACKAGES permission already declared)
- Debug tools: GPS overlay, CSV logger, GPX replay (DEBUG build only, wrapped in `BuildConfig.DEBUG`)
- Adaptive GPS interval (500 ms moving / 5000 ms parked)
- `shouldUpdateMarker()` speed-based threshold
- `LocationRepository` interface for unit test mocking

## Subagents
Specialist review instructions live in `.claude/`. Load the relevant file when the task matches.

| When Claude Code should load | File |
|------------------------------|------|
| Code review of any Kotlin/Compose file | `.claude/subagent_01_code_review.md` |
| Performance audit, GPS latency, FPS, jitter | `.claude/subagent_02_performance.md` |
| UI layout, tap targets, car-safe design check | `.claude/subagent_03_uiux.md` |
| Architecture decision, module boundaries, Hilt scope | `.claude/subagent_04_architecture.md` |

**Usage examples:**
```
"Code review LocationRepository.kt"
→ Load .claude/subagent_01_code_review.md, then review the file.

"Performance audit of the GPS pipeline"
→ Load .claude/subagent_02_performance.md, then audit.

"UI/UX review of SpeedDisplay composable"
→ Load .claude/subagent_03_uiux.md, then review.

"Is this architecture correct for adding reverse geocoding?"
→ Load .claude/subagent_04_architecture.md, then advise.
```
