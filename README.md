# CarLauncher

Personal Android Car Launcher for **Lenovo Tab M10 Plus (3rd Gen)** mounted in a car dashboard. Replaces the default Android home screen with a fullscreen, landscape-only interface optimized for driving.

## Features

- **Map** — MapLibre GL with Mapy.cz raster tiles, smooth GPS marker with bearing, auto-follow with 10 s timeout after manual pan, nearby POI overlay (fuel, parking, restaurant, hospital)
- **Speed display** — real-time km/h with color thresholds (white / orange / red), GPS Kalman filter
- **Smart Stacks** — 2×2 widget grid where each slot holds a *stack* of Android widgets; swipe up/down inside a card to switch between them; vertical stack indicator on the right edge
- **Layout templates** — switch between 2×2 grid, large top + 2 small, and 2 wide rows
- **Music widget** — MediaSession integration, track info, progress bar, playback controls
- **Dock bar** — 6 configurable slots supporting single apps and split-screen pairs (Waze + YouTube Music, etc.)
- **Status bar** — time, Czech date, battery, WiFi, GPS signal dot
- **Volume & brightness controls** — quick-access overlay in the status bar
- **System controls** — brightness and volume sliders

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose |
| DI | Hilt |
| Maps | MapLibre GL Android 11.5.x |
| Tile source | Mapy.cz raster tiles (API key in `local.properties`) |
| Location | FusedLocationProviderClient + Kalman filter |
| State | StateFlow + ViewModel |
| Persistence | DataStore Preferences |
| Media | MediaSessionManager + NotificationListenerService |

## Target Device

- **Device:** Lenovo Tab M10 Plus (3rd Gen) — MediaTek Helio G80, Android 16 (API 36)
- **Screen:** ~1143 × 686 dp, landscape locked, fullscreen
- **minSdk:** 31 · **compileSdk/targetSdk:** 36

## Build

```bash
# Copy and fill in your Mapy.cz API key
echo "MAPYCZ_API_KEY=your_key_here" >> local.properties

./gradlew assembleDebug
./gradlew installDebug
```

ADB install (PowerShell):
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Architecture

```
ui/
  launcher/   LauncherScreen, StatusBar, QuickDestWidget
  map/        MapWidget, MapViewModel, MapStyleHelper
  speed/      SpeedDisplay
  music/      MusicWidget, MediaViewModel
  dock/       DockBar, DockViewModel, SlotPicker
  widgets/    WidgetScreen, WidgetViewModel, Smart Stacks
  theme/      CarLauncherTheme, CarColors
data/
  location/   LocationRepository, LocationProcessor, KalmanFilter
  media/      MediaSessionObserver
  dock/       DockDataStoreExt
  poi/        PoiRepository, PoiUseCase
di/
  LocationModule
service/
  LocationForegroundService (stub)
  MediaListenerService
```

## Screenshots

> Dashboard in use — map with GPS marker, music widget, dock bar with split-screen shortcuts.

## License

Personal project — not licensed for redistribution.
