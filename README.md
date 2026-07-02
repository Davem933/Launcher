# CarLauncher

> A personal Android car launcher for mounted in a car dashboard. Replaces the default Android home screen with a fullscreen, landscape-only interface built for driving — no distractions, no clutter.

---

## What it looks like

The interface is split into three fixed horizontal bands:

```
+-----------------------------------------------------+
|  StatusBar -- time . Czech date . battery . WiFi . GPS|
+--------------------------------+--------------------+
|                                |   MusicWidget       |
|         MapWidget              |                     |
|   (GPS marker + speed)         +--------------------+
|                                | WeatherCalendar     |
+--------------------------------+--------------------+
|         DockBar -- 6 configurable app slots          |
+-----------------------------------------------------+
```

When navigation is active, **NavWidget** replaces the right column with turn-by-turn instructions.

---

## Screenshots

<img width="1920" height="1200" alt="Screenshot_20260629-165512" src="https://github.com/user-attachments/assets/438e8ab0-43a7-4c18-b504-28c25f9463af" />
<img width="1920" height="1200" alt="Screenshot_20260629-165619" src="https://github.com/user-attachments/assets/92b05616-a44a-42f6-bc77-83a89db958bc" />
<img width="1920" height="1200" alt="image" src="https://github.com/user-attachments/assets/7ca096b1-ff9b-41fa-87c0-ffe5f3e0c6e8" />
<img width="1920" height="1200" alt="image" src="https://github.com/user-attachments/assets/e9f8166f-3805-453d-ade5-3812f2afb1a0" />

---

## Features

### Map
- MapLibre GL with **Mapy.cz** raster tiles
- Smooth GPS marker with real-time bearing rotation
- Auto-follow camera with 10 s timeout after manual pan
- Nearby **POI overlay** -- fuel stations, parking, restaurants, hospitals
- Speed display with color thresholds: white < 90 km/h . orange 90-120 . red > 120

### GPS Pipeline
```
FusedLocationProviderClient (500 ms interval)
  -> LocationProcessor
      -> 2D Kalman filter (Q = 3 m/s) -- smooths GPS noise
      -> Rolling average speed (last 3 samples)
  -> VehicleDisplayLocation StateFlow
  -> MapViewModel + LauncherViewModel
```

### Navigation (NavWidget)
- Parses **Google Maps** and **Waze** notifications in real time via `NotificationListenerService`
- Center: **"za X m"** (distance to next maneuver) + street name below — hidden at 0 m
- Bottom bar: current speed . route progress bar . ETA . End navigation button
- Handles Google Maps non-breaking spaces in distance strings and content-based trip summary detection (ETA / time remaining / distance remaining)
- Maneuver arrow icon from notification large icon

### Weather + Calendar (WeatherCalendarWidget)
- **Weather**: [Open-Meteo](https://open-meteo.com) API — free, no key required; temperature, WMO condition, icon; refreshes every 30 min; coordinates from GPS with Prague fallback
- **Calendar**: Android `CalendarContract` — today's events (max 3), colored dot from calendar color, time or "all day"

### Music Widget
- Reads the active **MediaSession** via `MediaSessionManager`
- Album art, track title, artist, real-time progress bar
- Play / pause / skip controls
- Requires notification access (`NotificationListenerService`) — the widget shows a grant-access button if permission is missing

### System Controls
- **Volume** (blue) and **Brightness** (amber) cards
- Colored fill rises from the bottom of the card based on current level
- Swipe up to increase, swipe down to decrease — 1.5x sensitivity for comfortable in-car use
- Brightness changes the window attribute directly (no `WRITE_SETTINGS` required)

### Dock Bar
- 6 configurable slots, persisted in **DataStore**
- Each slot can be:
  - a single app
  - a **split-screen pair** (e.g. Waze + YouTube Music)
  - empty
- Long-press any slot to reassign it via the app picker
- Split-screen is launched with `FLAG_ACTIVITY_LAUNCH_ADJACENT` after a 650 ms delay so Android has time to open the first app

### Status Bar
- Time in `HH:mm`, Czech date (`pondeli 26. cervna`)
- Battery percentage + icon
- WiFi indicator
- GPS dot -- green = fix, red = no signal

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
| Navigation | NotificationListenerService (Google Maps / Waze) |
| Weather | Open-Meteo REST API (no key) |
| Calendar | Android CalendarContract |
| POI data | OpenStreetMap Overpass API |

---

## Target Device

| Property | Value |
|----------|-------|
| Device | Lenovo Tab M10 Plus (3rd Gen) |
| Chipset | MediaTek Helio G80 |
| Android | 16 (API 36) |
| Screen | ~1143 x 686 dp -- landscape locked, fullscreen |
| minSdk | 31 |
| compileSdk / targetSdk | 36 |

> **Emulator note:** use an API 34 (Android 14) x86_64 image with Google Play. API 35+ emulators may fail to load `libmaplibre.so` due to the 16 KB page-size constraint (the Helio G80 physical device is not affected).

---

## Build & Install

### Prerequisites
- Android Studio Hedgehog or later
- ADB in PATH or via `$env:LOCALAPPDATA\Android\Sdk\platform-tools\`
- A **Mapy.cz API key** (free tier works) -- [get one here](https://developer.mapy.cz/)

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

---

## First-run Setup

1. **Set as home launcher** -- Android will prompt on first launch, or go to *Settings > Apps > Default apps > Home app > CarLauncher*
2. **Notification access** -- required for music widget and navigation: *Settings > Apps > Special app access > Notification access > CarLauncher > Enable*
3. **Location permission** -- requested automatically on first launch; grant *Precise location*
4. **Calendar permission** -- `READ_CALENDAR` requested on first launch; grant to enable the calendar widget

---

## Architecture

Single-module app (`app/`). Package layout:

```
ui/
  launcher/     LauncherScreen, LauncherViewModel, StatusBar
                WeatherCalendarWidget, WeatherCalendarViewModel
                SystemControlsWidget
  map/          MapWidget, MapViewModel, MapStyleHelper, TileConfig
  speed/        SpeedDisplay
  music/        MusicWidget, MediaViewModel
  navigation/   NavWidget
  dock/         DockBar, DockViewModel, SlotPicker
  theme/        CarLauncherTheme (dark only), CarColors

data/
  location/     LocationRepository, LocationProcessor, KalmanFilter
  media/        MediaSessionObserver
  navigation/   NavRepository
  weather/      WeatherRepository
  calendar/     CalendarRepository
  dock/         DockDataStoreExt
  map/          PmtilesHttpServer (offline tile server -- not active)
  model/        VehicleDisplayLocation, TrackInfo, DockItem, DockSlot

di/
  LocationModule

service/
  MediaListenerService   (NotificationListenerService -- media + navigation)
  LocationForegroundService  (stub)
```

---

## Changelog

### v0.2.0
- **NavWidget** -- real-time Google Maps / Waze navigation overlay; distance + street; route progress bar; ETA
- **WeatherCalendarWidget** -- Open-Meteo weather + Android calendar events; replaces quick-dest placeholder
- **System Controls** -- volume/brightness cards with fill-from-bottom visual; swipe up/down gesture

### v0.1.0
- Core launcher: map, speedometer, music widget, dock
- Smooth GPS marker with Kalman filter + EMA bearing
- POI overlay (Overpass API)
- Smart Stacks widget page

---

## Planned

| Feature | Status |
|---------|--------|
| Adaptive GPS interval (500 ms driving -> 5 s parked) | planned |
| Speed limit roundel from OSM `maxspeed` | planned v0.3 |
| Offline map (PMTiles v3 + NanoHTTPD) | code present, not wired |
| LocationForegroundService | manifest stub only |

---

## License

Personal project -- not licensed for redistribution.
