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

## Architecture
Single-module app (`app/`). No multi-module split yet.

**DI:** Hilt (`@HiltAndroidApp` on `CarLauncherApp`, `@AndroidEntryPoint` on `MainActivity`). All ViewModels injected via `@HiltViewModel`. Hilt modules live in `di/`.

**UI layer:** Jetpack Compose throughout. Entry point is `MainActivity → CarLauncherTheme → LauncherScreen`. New screens/widgets go under `ui/` grouped by feature:
```
ui/
  launcher/   ← LauncherScreen + LauncherViewModel
  theme/      ← CarLauncherTheme, color tokens
  map/        ← MapWidget + MapViewModel
  speed/      ← SpeedDisplay
  music/      ← MusicWidget + MusicViewModel
  dock/       ← DockBar
```

**Theme:** `ui/theme/Theme.kt` defines `CarColorScheme` (dark, no light variant). Key colors: background `#0D0D0F`, surface `#1A1A1F`, primary (accent green) `#00C853`. Always use `MaterialTheme.colorScheme.*` tokens rather than hardcoded hex in new composables.

**Fullscreen:** `MainActivity` hides status + nav bars via `WindowInsetsControllerCompat` and sets `FLAG_KEEP_SCREEN_ON`. Do not call `enableEdgeToEdge()` — it conflicts with the manual insets setup.

**Manifest:** `MainActivity` has `HOME` + `DEFAULT` categories making it a system launcher. `screenOrientation="landscape"` + `windowSoftInputMode="adjustNothing"` are intentional and must not be changed.

## GPS Pipeline (Module 2)
Data flows unidirectionally:
```
FusedLocationProviderClient (500 ms interval)
  → LocationCallback
  → LocationProcessor.process(Location)
      → KalmanFilter  (2D lat/lng smoothing, Q = 3 m/s)
      → speed rolling average (last 3 samples)
  → VehicleDisplayLocation  (DTO)
  → LocationRepository._vehicleLocation: MutableStateFlow
  → LauncherViewModel.vehicleLocation: StateFlow
  → LauncherScreen (collectAsStateWithLifecycle)
```
- **`KalmanFilter`** — plain class, stateful (call `reset()` after GPS gap). When `variance < 0` it is uninitialized and passes the first fix through raw.
- **`LocationRepository`** — `@Singleton`. Owns the `LocationCallback`. `startTracking()` / `stopTracking()` called by `LauncherViewModel` init/onCleared. Catches `SecurityException` silently if permission is missing — the UI stays on "Waiting for GPS...".
- **`LocationForegroundService`** — stub only, declared in manifest with `foregroundServiceType="location"`. Logic added in a later module.
- Runtime location permission is **not yet requested** — needs `ActivityResultContracts.RequestPermission` wired into `MainActivity` in a future module.
- **GPS update frequency is adaptive** — 500 ms at speed > 5 km/h, 5000 ms when parked. Use `shouldUpdateMarker()` with speed-based distance threshold before any MapLibre marker update.

## Map Widget
- **Renderer:** MapLibre GL Android SDK — open-source, no API keys, no costs.
- **Tile data:** PMTiles format, sourced from Geofabrik (czech-republic). File stored locally on the tablet. Zero network traffic in production.
- **Map matching:** Two-stage — offline SQLite R-tree snap first, OSRM API fallback when online.
- **Marker animation:** `animateMarkerTo()` with 400 ms ValueAnimator + `lerpAngle()` for bearing. Cancel animator in `DisposableEffect` / `onDestroy`.
- **Camera:** Follow mode with bearing rotation. Do not reset zoom on every location update.
- MapLibre `MapView` lives inside `AndroidView` composable — never store `MapLibreMap` in Compose state.

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
- Runtime permission request for location
- Map widget (MapLibre + PMTiles)
- Speed display widget
- Music/media widget (MediaSession API)
- App dock (QUERY_ALL_PACKAGES permission already declared)
- Debug tools: GPS overlay, CSV logger, GPX replay (DEBUG build only, wrapped in BuildConfig.DEBUG)

## Subagents
Specialist review instructions live in `.claude/agents/`. Load the relevant file when the task matches.

| When Claude Code should load | File |
|------------------------------|------|
| Code review of any Kotlin/Compose file | `.claude/agents/subagent_01_code_review.md` |
| Performance audit, GPS latency, FPS, jitter | `.claude/agents/subagent_02_performance.md` |
| UI layout, tap targets, car-safe design check | `.claude/agents/subagent_03_uiux.md` |
| Architecture decision, module boundaries, Hilt scope | `.claude/agents/subagent_04_architecture.md` |

**Usage examples:**
```
"Code review LocationRepository.kt"
→ Load .claude/agents/subagent_01_code_review.md, then review the file.

"Performance audit of the GPS pipeline"
→ Load .claude/agents/subagent_02_performance.md, then audit.

"UI/UX review of SpeedDisplay composable"
→ Load .claude/agents/subagent_03_uiux.md, then review.

"Is this architecture correct for adding reverse geocoding?"
→ Load .claude/agents/subagent_04_architecture.md, then advise.
```
