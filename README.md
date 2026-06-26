# CarLauncher

> A personal Android car launcher for **Lenovo Tab M10 Plus (3rd Gen)** mounted in a car dashboard. Replaces the default Android home screen with a fullscreen, landscape-only interface built for driving — no distractions, no clutter.

---

## What it looks like

The interface is split into three fixed horizontal bands:

```
┌─────────────────────────────────────────────────────┐
│  StatusBar — time · Czech date · battery · WiFi · GPS│
├────────────────────────────────┬────────────────────┤
│                                │   MusicWidget       │
│         MapWidget              │                     │
│   (GPS marker + speed)         ├────────────────────┤
│                                │  QuickDestWidget    │
├────────────────────────────────┴────────────────────┤
│         DockBar — 6 configurable app slots           │
└─────────────────────────────────────────────────────┘
```

Swipe left from the main screen to reach the **Smart Stacks** widget page — a grid of Android home screen widgets stacked per slot.

---

## Screenshots

<img width="1920" height="1200" alt="image" src="https://github.com/user-attachments/assets/80797ebd-efdd-4070-ac33-c4f40e059df6" />
<img width="1920" height="1200" alt="image" src="https://github.com/user-attachments/assets/7ca096b1-ff9b-41fa-87c0-ffe5f3e0c6e8" />
<img width="1920" height="1200" alt="image" src="https://github.com/user-attachments/assets/e9f8166f-3805-453d-ade5-3812f2afb1a0" />

---

## Features

### Map
- MapLibre GL with **Mapy.cz** raster tiles
- Smooth GPS marker with real-time bearing rotation
- Auto-follow camera with 10 s timeout after manual pan
- Nearby **POI overlay** — fuel stations, parking, restaurants, hospitals
- Speed display with color thresholds: white < 90 km/h · orange 90–120 · red > 120

### GPS Pipeline
```
FusedLocationProviderClient (500 ms interval)
  → LocationProcessor
      → 2D Kalman filter (Q = 3 m/s) — smooths GPS noise
      → Rolling average speed (last 3 samples)
  → VehicleDisplayLocation StateFlow
  → MapViewModel + LauncherViewModel
```

### Smart Stacks (widget page)
- Each grid slot holds a **stack** of standard Android widgets
- Swipe up/down inside a card to switch between stacked widgets
- Vertical stack indicator on the right edge (active = white, inactive = dim)
- Three layout templates:

| Template | Slots | Layout |
|----------|-------|--------|
| 2 × 2 Grid | 4 | equal quadrants |
| Large top + 2 small | 3 | full-width top, two halves below |
| 2 wide rows | 2 | two full-width slots |

- Long-press any widget to enter edit mode → delete or add to that slot
- Widget picker launches Android's system widget chooser

### Music Widget
- Reads the active **MediaSession** via `MediaSessionManager`
- Album art, track title, artist, real-time progress bar
- Play / pause / skip controls
- Requires notification access (`NotificationListenerService`) — the widget shows a grant-access button if permission is missing

### Dock Bar
- 6 configurable slots, persisted in **DataStore**
- Each slot can be:
  - a single app
  - a **split-screen pair** (e.g. Waze + YouTube Music)
  - empty
- Long-press any slot to reassign it via the app picker
- Split-screen is launched with `FLAG_ACTIVITY_LAUNCH_ADJACENT` after a 650 ms delay so Android has time to open the first app

### Status Bar
- Time in `HH:mm`, Czech date (`pondělí 26. června`)
- Battery percentage + icon
- WiFi indicator
- GPS dot — green = fix, red = no signal
- Volume slider + brightness slider (tap the icons on the right to open)

### System Controls
- Volume and brightness sliders accessible from the status bar
- Android `AudioManager` for volume, `Settings.System.SCREEN_BRIGHTNESS` for brightness

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose |
| Dependency injection | Hilt |
| Maps | MapLibre GL Android 11.5.x |
| Tile source | Mapy.cz raster tiles |
| Location | FusedLocationProviderClient + 2D Kalman filter |
| State management | StateFlow + ViewModel |
| Persistence | DataStore Preferences |
| Media | MediaSessionManager + NotificationListenerService |
| Widgets | AppWidgetHost / AppWidgetManager |
| POI data | OpenStreetMap Overpass API |

---

## Target Device

| Property | Value |
|----------|-------|
| Device | Lenovo Tab M10 Plus (3rd Gen) |
| Chipset | MediaTek Helio G80 |
| Android | 16 (API 36) |
| Screen | ~1143 × 686 dp — landscape locked, fullscreen |
| minSdk | 31 |
| compileSdk / targetSdk | 36 |

> **Emulator note:** use an API 34 (Android 14) x86_64 image with Google Play. API 35+ emulators may fail to load `libmaplibre.so` due to the 16 KB page-size constraint (the Helio G80 physical device is not affected).

---

## Build & Install

### Prerequisites
- Android Studio Hedgehog or later
- ADB in PATH or via `$env:LOCALAPPDATA\Android\Sdk\platform-tools\`
- A **Mapy.cz API key** (free tier works) — [get one here](https://developer.mapy.cz/)

### Setup

```bash
# Add your Mapy.cz API key to local.properties (gitignored)
echo "MAPYCZ_API_KEY=your_key_here" >> local.properties
```

### Gradle

```bash
./gradlew assembleDebug        # build APK
./gradlew installDebug         # build + push to connected device
./gradlew lint                 # lint
./gradlew test                 # unit tests
```

### Manual ADB install (PowerShell)

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### ADB logcat (key tags)

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb logcat -s MediaDebug,PackageCheck,MapWidget,DockViewModel -d
```

---

## First-run Setup

1. **Set as home launcher** — Android will prompt on first launch, or go to *Settings → Apps → Default apps → Home app → CarLauncher*
2. **Notification access** — required for the music widget: *Settings → Apps → Special app access → Notification access → CarLauncher → Enable*
3. **Location permission** — requested automatically on first launch; grant *Precise location*

---

## Architecture

Single-module app (`app/`). Package layout:

```
ui/
  launcher/     LauncherScreen (root layout), LauncherViewModel
                StatusBar, QuickDestWidget
  map/          MapWidget, MapViewModel, MapStyleHelper, TileConfig
  speed/        SpeedDisplay
  music/        MusicWidget, MediaViewModel
  dock/         DockBar, DockViewModel, SlotPicker
  widgets/      WidgetScreen, WidgetViewModel
                WidgetLayoutTemplate, LongPressWidgetHost
  theme/        CarLauncherTheme (dark only), CarColors

data/
  location/     LocationRepository, LocationProcessor, KalmanFilter
  media/        MediaSessionObserver
  dock/         DockDataStoreExt
  map/          PmtilesHttpServer (offline tile server — not active)
  model/        VehicleDisplayLocation, TrackInfo, DockItem, DockSlot

di/
  LocationModule

service/
  LocationForegroundService  (stub — not yet implemented)
  MediaListenerService       (NotificationListenerService)
```

### Key architectural decisions

**Hilt everywhere** — `@HiltAndroidApp` on `CarLauncherApp`, `@AndroidEntryPoint` on `MainActivity`. ViewModels use `@HiltViewModel`. Types without `@Inject constructor` (e.g. `FusedLocationProviderClient`) are provided via modules in `di/`.

**Fullscreen** — `MainActivity` hides status and nav bars via `WindowInsetsControllerCompat` and sets `FLAG_KEEP_SCREEN_ON`. `enableEdgeToEdge()` is intentionally NOT called — it conflicts with the manual insets setup.

**MapLibre in Compose** — `MapView` runs in an `AndroidView`. TextureMode (`MapLibreMapOptions.textureMode(true)`) is required so Compose's `clip()` / rounded corners work — the default SurfaceView is composited outside the Compose hierarchy and ignores clip modifiers. `MapLibreMap` and `GeoJsonSource` are stored in a plain class inside `remember {}`, never in `mutableStateOf` (MapLibre objects are not snapshot-safe).

**Widget long-press** — implemented via a custom `LongPressWidgetHostView extends AppWidgetHostView` that uses `dispatchTouchEvent` + `GestureDetectorCompat`. Standard `setOnLongClickListener` fails because child views consume `ACTION_DOWN`. A `longPressConsumed` flag replaces the following `ACTION_UP` with `ACTION_CANCEL` so the widget doesn't launch its app after the long-press fires.

---

## Planned / In Progress

| Feature | Status |
|---------|--------|
| Adaptive GPS interval (500 ms driving → 5 s parked) | planned |
| Speed limit roundel from OSM `maxspeed` | planned v0.3 |
| QuickDest navigation wiring | placeholder UI only |
| Offline map (PMTiles v3 + NanoHTTPD server) | code present, not wired |
| LocationForegroundService | manifest stub only |

---

## License

Personal project — not licensed for redistribution.
