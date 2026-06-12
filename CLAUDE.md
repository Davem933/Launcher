# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Personal Android Car Launcher for Lenovo Tab M10 Plus (3rd Gen). Landscape-locked fullscreen app that replaces the default Android launcher. Built with Kotlin + Jetpack Compose + Hilt.

**Target:** minSdk 31, compileSdk/targetSdk 36 (Android 16), screen ~1143×686 dp landscape only.

**Device:** Lenovo Tab M10 Plus (3rd Gen), Android 16 (API 36), MediaTek Helio G80. The physical device runs fine — the 16 KB page size constraint applies only to certain ARM chipsets; Helio G80 does not enforce it.

**Emulator:** Use API 34 (Android 14) x86_64 with Google Play. Do NOT use API 35+ emulators — `libmaplibre.so` 11.5.2 is not 16 KB page-aligned and will fail to load.

## Build & Install

```bash
./gradlew assembleDebug                  # build APK
./gradlew installDebug                   # build + install (needs USB/WiFi ADB)
./gradlew lint
./gradlew test
./gradlew test --tests "com.example.carlauncher.SomeTest"
```

Direct ADB install (PowerShell):
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r app/build/outputs/apk/debug/app-debug.apk
```

ADB logcat (PowerShell):
```powershell
& $adb logcat -s MediaDebug,PackageCheck,MapWidget,DockViewModel -d
```

## Architecture

Single-module app (`app/`). Package structure:

```
ui/
  launcher/     LauncherScreen (root layout), LauncherViewModel, StatusBar, QuickDestWidget
  theme/        CarLauncherTheme (dark only), CarColors (design tokens)
  map/          MapWidget, MapViewModel, MapStyleHelper, TileConfig
  speed/        SpeedDisplay composable
  music/        MusicWidget, MediaViewModel
  dock/         DockBar, DockViewModel, SlotPicker
data/
  location/     LocationRepository, LocationProcessor, KalmanFilter
  media/        MediaSessionObserver
  dock/         DockDataStoreExt (preferencesDataStore extension)
  model/        VehicleDisplayLocation, TrackInfo, DockItem, DockSlot
di/
  LocationModule.kt
service/
  LocationForegroundService  (stub — not yet implemented)
  MediaListenerService       (NotificationListenerService stub)
```

**DI:** Hilt throughout. `@HiltAndroidApp` on `CarLauncherApp`, `@AndroidEntryPoint` on `MainActivity`. ViewModels use `@HiltViewModel`. Hilt modules in `di/` only provide types without `@Inject constructor` (e.g. `FusedLocationProviderClient`). Singletons with `@Inject constructor` get `@Singleton` on the class directly.

**Theme:** `ui/theme/Theme.kt` defines `CarColorScheme` (dark only). Key tokens: background `#0D0D0F`, surface `#1A1A1F`, accent green `#22C55E` (unified across all widgets per design spec). Hardcoded hex is acceptable in existing widgets; new composables should prefer theme tokens.

**Fullscreen:** `MainActivity` hides status + nav bars via `WindowInsetsControllerCompat` and sets `FLAG_KEEP_SCREEN_ON`. Do not call `enableEdgeToEdge()` — it conflicts. `screenOrientation="landscape"` and `windowSoftInputMode="adjustNothing"` are intentional.

## LauncherScreen Layout (Modul 7 — final)

`LauncherScreen` is a `Column` with three fixed bands:

```
Column(fillMaxSize, bg = CarColors.Bg) {
    StatusBar(44dp)                       ← time HH:mm 28sp + Czech date | battery, WiFi, GPS dot
    Box(weight 1f) {
        Row(p = 12/8dp, spacing 12dp) {
            MapWidget(weight 1.85f)       ← ~65% width, SpeedDisplay + Navigovat overlays inside
            Column(weight 1f) {           ← ~35% width
                MusicWidget(weight 1f)
                QuickDestWidget()         ← wraps content, no fixed height
            }
        }
        [GPS debug overlay, TopStart]     ← BuildConfig.DEBUG only
    }
    DockBar(88dp, ph = 12dp, pb = 8dp)
}
```

`onLaunchSplitScreen` lambda is passed from `MainActivity` → `LauncherScreen` → `DockBar` (split screen must launch from Activity context).

SpeedDisplay is rendered **inside** `MapWidget` at `BottomStart` with `padding(18dp)` — the dock no longer overlays the map.

**Design tokens:** `ui/theme/CarColors.kt` — object with the final mockup palette (`Bg #0D0E12`, `Surface #161820`, `Go #5DBF7A`, `Accent #6B8EF0`, `Text/Text2/Text3`, …). New composables (StatusBar, QuickDestWidget) use these; older widgets still use private color constants.

**StatusBar:** updates time + battery every 30s via `LaunchedEffect` loop. Czech date via `DateTimeFormatter.ofPattern("EEEE d. MMMM", Locale("cs","CZ"))`. GPS dot green = fix (location != null), red = no signal.

## Module: Map (MapWidget.kt)

**Tile source:** Mapy.cz online raster tiles. URL template is in `TileConfig.MAPYCZ_BASIC`. The API key lives in `local.properties` (`MAPYCZ_API_KEY=...`, gitignored) and reaches code via `BuildConfig.MAPYCZ_API_KEY` — never hardcode, display, or log the key value.

**Style JSON** is built by `buildMapyczStyleJson(tileUrl)` in `MapStyleHelper.kt` — a raster source + single raster layer. No vector layers.

**Rounded corners:** the MapWidget Box is clipped to `RoundedCornerShape(24.dp)` with a 1dp border. This requires `MapLibreMapOptions.textureMode(true)` — the default SurfaceView is composited separately and ignores Compose `clip()`.

**Initialization:**
1. `MapView` created in `remember { MapView(context, options.textureMode(true)).also { it.onCreate(null) } }`
2. `getMapAsync` sets `mapAsyncReady = true`
3. `LaunchedEffect(mapAsyncReady)` loads the Mapy.cz style, adds `GeoJsonSource` + `SymbolLayer` for the vehicle marker

**Vehicle marker:** canvas-drawn 128px bitmap (halo + green disc + white direction wedge) created by `createVehicleMarkerBitmap()` inside MapWidget.kt. Bearing rotation via `iconRotate` on the SymbolLayer with `iconRotationAlignment("map")` — updated in `LaunchedEffect(location)`.

**Camera + marker updates:** `LaunchedEffect(location)` — single `animateCamera(CameraPosition.Builder().target().bearing(), 300ms)`. Never use two sequential `animateCamera()` calls — they cancel each other.

**MapLibre objects in Compose:** `MapLibreMap` and `GeoJsonSource` are stored in a plain `MapState` class inside `remember {}` — **never** in `mutableStateOf` (not snapshot-safe).

**MapLibre API quirks (11.5.x):**
- `addOnDidFailLoadingMapListener` is on `MapView`, takes `String`. `addOnMapLoadErrorListener` does not exist.
- `addOnCameraChangeListener` does not exist on `MapLibreMap`.
- Style JSON built with `buildString { append() }` to avoid trailing comma parse errors.
- `{fontstack}` / `{range}` inside Kotlin `${}` string templates are misread. Use `buildString`.
- `MapView.onStart()` / `onResume()` must be replayed manually in `DisposableEffect` — lifecycle may already be RESUMED when the composable first composes.

**PmtilesHttpServer** (`data/map/`) — full PMTiles v3 reader + NanoHTTPD. Present in codebase but **not used by MapWidget** (switched to Mapy.cz). Kept for future offline tile support. Endpoints: `GET /tilejson`, `GET /tile/{z}/{x}/{y}`.

## Module: SpeedDisplay (ui/speed/SpeedDisplay.kt)

Stateless composable: `SpeedDisplay(speedKmh: Float, modifier)`. Receives speed from `MapViewModel.vehicleLocation` via the `location` state already collected in `MapWidget`.

- Displays `0` when `speedKmh < 3f` (GPS noise at standstill)
- Color states: white < 90 km/h, orange `#FF9500` 90–120, red `#FF3B30` > 120
- `fontFeatureSettings = "tnum"` for tabular numbers
- Speed limit roundel (42dp white circle, red border) right of "km/h" — hardcoded "50", dynamic OSM maxspeed planned for v0.3

## Module: MusicWidget (ui/music/)

**MediaSession access on Android 13+:** `MediaSessionManager.getActiveSessions()` requires the `NotificationListenerService` to be **actively bound** (not just permitted). The flow:

1. `MediaListenerService` (stub `NotificationListenerService`) tracks its connection state in a companion `StateFlow<Boolean> isConnected`.
2. `MediaSessionObserver.start()` calls `NotificationListenerService.requestRebind()` then collects `isConnected`.
3. Only when `isConnected == true` does `querySessions()` call `getActiveSessions()`.
4. `SecurityException` from `getActiveSessions()` = notification access not granted — silent fallback.

**User setup required:** Settings → Apps → Special app access → Notification access → CarLauncher → Enable.

`MediaViewModel` calls `mediaObserver.start()` in `init`, `stop()` in `onCleared`. Position polling is a 1-second `flow { while(true) { emit(observer.currentPositionMs); delay(1000) } }` stateIn.

`currentPositionMs` on `MediaSessionObserver` calculates real-time position: `state.position + (elapsedRealtime - lastPositionUpdateTime) * playbackSpeed`.

## Module: DockBar (ui/dock/)

**DockSlot sealed class** (`data/model/DockItem.kt`):
```kotlin
sealed class DockSlot {
    data class App(val packageName: String) : DockSlot()
    data class SplitScreen(val packageName1: String, val packageName2: String, val label: String) : DockSlot()
    object Empty    : DockSlot()
    object Navigate : DockSlot()   // fixed last slot — never stored in DataStore
}
```

**Split screen rule (enforced):** `packageName1` = navigation app (left), `packageName2` = music/secondary (right). NEVER swap.

**DataStore serialization** (`dock_slots_v2` key, comma-separated, 6 entries):
- `"com.example.pkg"` — App
- `"split:pkg1:pkg2:label"` — SplitScreen (label uses `|` instead of `,`)
- `"empty"` — Empty

Default 6 slots: Dialer, YouTube Music, Google Maps, Mapy.cz, `split:com.waze:…youtube.music:Waze + Hudba`, `split:com.tomtom.speedcams.android.map:…youtube.music:TomTom + Hudba`.

**Icons** are not persisted — resolved lazily at render time via `rememberAppIcon(packageName)` / `rememberAppLabel(packageName)` (synchronous `PackageManager` calls inside `remember`).

**Edit mode:** long press any slot → wiggle animation (`Animatable` + `LaunchedEffect`) + `SlotPicker` bottom sheet opens.

**launchSplitScreen:** No public API on Android 16 for forced split screen. Launches pkg1 immediately from Activity, then 650ms later launches pkg2 with `FLAG_ACTIVITY_LAUNCH_ADJACENT + FLAG_ACTIVITY_MULTIPLE_TASK` via `lifecycleScope.launch { delay(650L) }`. Must be called from Activity instance — application Context does not trigger the adjacent split.

**Dock style:** 88dp rounded card (24dp radius, `CarColors.Surface` + `BorderSoft`), slots `SpaceEvenly`. The Navigovat button is NOT in the dock — it lives as an overlay in `MapWidget` (BottomEnd), wired via `onNavigate` lambda from LauncherScreen → `DockViewModel.launchNavigation()`.

**MusicWidget header:** Bluetooth icon + "YouTube Music" label (static) + `EqualizerAnimation` (4 bars, animates only when `isPlaying`).

**SlotPicker:** `ModalBottomSheet` loading all CATEGORY_LAUNCHER apps via `Intent(ACTION_MAIN).addCategory(CATEGORY_LAUNCHER)`, sorted alphabetically, filtered by search query. 5-column grid.

## GPS Pipeline

```
FusedLocationProviderClient (500ms / 5s / 30s)
  → LocationCallback  [HandlerThread("location-thread") — NOT main thread]
  → LocationProcessor.process(Location)
      → KalmanFilter (2D, Q=3 m/s)
      → speed: location.speed * 3.6f, rolling avg of last 3 samples
  → VehicleDisplayLocation (lat, lng, speedKmh, bearingDeg, accuracyM, timestamp)
  → LocationRepository._vehicleLocation: MutableStateFlow
  → MapViewModel / LauncherViewModel: StateFlow
```

`LocationRepository.startTracking()` / `stopTracking()` called by `LauncherViewModel` init/onCleared. Has `isTracking` guard. Runtime location permission requested in `MainActivity.onCreate()`.

## Planned / Not Yet Implemented

- **`LocationForegroundService`** — declared in manifest, stub only
- **Offline map** — PMTiles v3 parser + NanoHTTPD present in `data/map/`; MapWidget needs to switch back to vector style when offline tiles are needed
- **Adaptive GPS interval** — fixed at 500ms; should drop to 5s when parked
- **Speed limit feature** — planned for v0.3 (OSM `maxspeed` tag)
- **QuickDest navigation wiring** — `QuickDestWidget` shows static placeholder data; tapping does nothing yet

## Design Reference

Finalized UI design: `.claude/design/` (CarLauncher.html, app.jsx, widgets.jsx, icons.jsx). Do not iterate — implement as-is.

Key layout values:
- SpeedDisplay number: 56sp bold, tabular-nums
- DockBar height: 88dp
- Dock icon outer: 64dp touch target, 52dp visual, 14dp corner radius
- Navigovat button: 52dp height, accent green, 26dp corner radius
- All touch targets: min 48×48 dp
- MusicWidget: 220dp wide, TopEnd, 16dp padding

## Subagents

| Task | Load file |
|------|-----------|
| Code review (any Kotlin/Compose) | `.claude/subagent_01_code_review.md` |
| Performance audit, GPS latency, FPS | `.claude/subagent_02_performance.md` |
| UI layout, tap targets, car-safe design | `.claude/subagent_03_uiux.md` |
| Architecture decision, Hilt scope | `.claude/subagent_04_architecture.md` |
