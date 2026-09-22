# CarLauncher

> A personal Android car launcher for a tablet mounted on the dashboard. It replaces the default Android home screen with a fullscreen, landscape-only interface built for driving: a 3D Mapbox map with built-in turn-by-turn navigation, music, weather, a trip log and a manual dashcam.

---

## What it looks like

The launcher has three pages you swipe between (dot indicator above the dock):

| Page | Content |
|------|---------|
| **0 — Launcher** | Map / navigation panel + music, volume & brightness, weather & calendar |
| **1 — Widgets** | Android AppWidget grid with swipeable widget stacks |
| **2 — Trips** | Trip computer + driving log |

```
+--------------------------------------+-------------------+
|                                      |   MusicWidget     |
|        MapNavPanel (~65 %)           |                   |
|   Mapbox 3D map  <-long press->      +-------------------+
|   navigation from Google Maps/Mapy   | Volume | Brightness|
|                                      +-------------------+
|                                      | Weather | Calendar |
+--------------------------------------+-------------------+
| (Menu)      ( app slots . split-screen slots )  time/batt |
+----------------------------------------------------------+
```

There is no status bar on purpose — the map gets the space. Time, battery and weather live in the bottom-right corner of the dock.

---

## Screenshots

<img width="1920" height="1200" alt="Screenshot_20260629-165512" src="https://github.com/user-attachments/assets/438e8ab0-43a7-4c18-b504-28c25f9463af" />
<img width="1920" height="1200" alt="Screenshot_20260629-165619" src="https://github.com/user-attachments/assets/92b05616-a44a-42f6-bc77-83a89db958bc" />
<img width="1920" height="1200" alt="image" src="https://github.com/user-attachments/assets/7ca096b1-ff9b-41fa-87c0-ffe5f3e0c6e8" />
<img width="1920" height="1200" alt="image" src="https://github.com/user-attachments/assets/e9f8166f-3805-453d-ade5-3812f2afb1a0" />
<img width="1030" height="642" alt="image" src="https://github.com/user-attachments/assets/360fdb70-0d9f-46e4-879a-91dc1e7fcadc" />

---

## Features

### Map (Mapbox Maps SDK)
- Mapbox **Standard** style — 3D buildings and landmarks, night light preset, 45° pitch
- Live **traffic** layer coloured by congestion
- **Parking** pins from OpenStreetMap (Overpass API)
- Vehicle puck fed by the app's own Kalman-filtered GPS
- Auto-follow camera; a **locate-me** button recentres instantly and shows whether the camera is following
- Tap a POI or parking pin → card with name, distance and a **Navigate** button
- Online only — no offline fallback

### Built-in navigation (Mapbox Navigation + Search SDK)
- **Destination search** — full-panel search with live suggestions, recent searches and **voice input**
- **POI categories** — fuel, parking, restaurants, shops, coffee — with numbered pins on the map
- Picking a destination starts guidance immediately (no preview step)
- Maneuver banner, trip progress (ETA / distance / time), route line and a follow camera
- **Voice guidance** in the device language
- Navigation ends automatically on arrival; it keeps running when you switch the panel

### Navigation from other apps
- Reads **Google Maps** and **Mapy.cz** turn-by-turn notifications via `NotificationListenerService`
- Shows distance to the next maneuver, street, maneuver icon, ETA and an end button
- Long press on the left panel (1.5 s) switches between Map and this Navigation view; active navigation always wins
- Waze, TomTom and HERE WeGo only post placeholder notifications, so they aren't supported

### Trip computer & driving log
- Trips are detected automatically (moving > 5 km/h for 10 s starts a trip, 30 s stationary ends it)
- Stored in a Room database; live trip stats plus a history list
- **CSV export** via the Android share sheet

### Incident recorder (manual dashcam)
- Draggable floating button opens a fullscreen camera overlay
- Records video with CameraX (HD, no audio) to the app's external files folder
- Logs GPS and detects **licence plates** (ML Kit text recognition, offline) during recording
- Writes a JSON sidecar next to each `.mp4` with timestamps, GPS track and detected plates — the video itself is untouched
- Can be switched off with the `INCIDENT_RECORDER_ENABLED` build flag

### Music
- Reads the active **MediaSession** — album art, title, artist, progress, play / pause / skip
- Needs notification access; shows a grant button if it's missing

### Volume & brightness
- Two cards with a coloured fill that rises with the level
- Swipe up / down to change (1.5× sensitivity); brightness uses the window attribute, no `WRITE_SETTINGS`

### Weather & calendar
- **Weather**: [Open-Meteo](https://open-meteo.com) (free, no key), refreshed every 30 min, Prague fallback without GPS
- **Calendar**: today's events from `CalendarContract` (max 3)

### Dock
- Floating circular dock: **Menu** (app drawer) pinned left, app slots in the middle, time / battery / weather pinned right
- Slots can hold a single app or a **split-screen pair** (navigation left, music right); split-screen slots are locked
- Long press a slot to reassign it; configuration is stored in DataStore

### Widgets page
- Hosts regular Android home-screen widgets in three layout templates
- Each slot is a vertical stack you swipe through; long press for edit mode

---

## Tech stack

| Layer | Technology |
|-------|-----------|
| Language / UI | Kotlin, Jetpack Compose |
| DI | Hilt |
| Map | Mapbox Maps SDK (Standard style) |
| Navigation & search | Mapbox Navigation SDK, Mapbox Search Box SDK |
| Location | FusedLocationProviderClient + 2D Kalman filter |
| Persistence | DataStore Preferences, Room (trips) |
| Camera / ML | CameraX, ML Kit Text Recognition v2 (bundled) |
| Media & 3rd-party nav | MediaSessionManager + NotificationListenerService |
| Weather | Open-Meteo REST API |
| Parking / speed limit | OpenStreetMap Overpass, Nominatim |
| Calendar | Android CalendarContract |

---

## Target device

| Property | Value |
|----------|-------|
| Device | Lenovo Tab M10 Plus (3rd Gen) |
| Chipset | MediaTek Helio G80 |
| Android | 16 (API 36) |
| Screen | ~1143 × 686 dp, landscape locked, fullscreen |
| minSdk | 31 |
| compileSdk / targetSdk | 36 |

> **Emulator note:** use an API 34 (Android 14) x86_64 image with Google Play. API 35+ emulators enforce 16 KB page alignment for native libraries, which hasn't been verified for the Mapbox `.so` files. The Helio G80 device is not affected.

---

## Build & install

### Prerequisites
- Android Studio (recent stable)
- Two Mapbox tokens from https://account.mapbox.com/access-tokens/

### Mapbox tokens

```properties
# local.properties — public token (pk.…)
MAPBOX_ACCESS_TOKEN=pk.your_token

# ~/.gradle/gradle.properties — secret token (sk.…) with DOWNLOADS:READ scope,
# used only by Gradle to download the Mapbox SDK
MAPBOX_DOWNLOADS_TOKEN=sk.your_token
```

### Gradle

```bash
./gradlew assembleDebug        # build APK
./gradlew installDebug         # build + install to connected device
./gradlew lint
./gradlew test
```

### Manual ADB install (PowerShell)

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## First-run setup

1. **Set as home app** — *Settings › Apps › Default apps › Home app › CarLauncher*
2. **Notification access** (music + navigation from other apps) — *Settings › Apps › Special app access › Notification access › CarLauncher*
3. **Location** — grant *Precise location* when asked
4. **Calendar** — grant `READ_CALENDAR` for the calendar widget
5. **Camera** — asked the first time you open the incident recorder

---

## Architecture

Single-module app (`app/`), package `com.example.carlauncher`:

```
ui/
  launcher/    LauncherScreen, MapNavPanel, WeatherCalendarWidget,
               SystemControlsWidget, AppDrawer
  map/         MapWidget, MapViewModel, SearchOverlay, DestinationSearchBar
  navigation/  NavAreaWidget, NavWidget (notification-based navigation)
  music/       MusicWidget
  dock/        DockBar, SlotPicker
  widgets/     WidgetScreen, TripScreen, LongPressWidgetHost
  incident/    IncidentFab, IncidentRecorderScreen, IncidentOverlay
  theme/       CarColors (dark only)
data/
  location/    LocationRepository, LocationProcessor, KalmanFilter
  navigation/  NavRepository, MapboxVoiceGuidanceObserver,
               MapboxArrivalTeardownObserver
  trip/        TripDetector, TripRepository, Room DB, CsvExporter
  incident/    IncidentRecorder, IncidentLocationSource, PlateDetector
  poi/         ParkingRepository
  speedlimit/  weather/  calendar/  media/  dock/  widgets/  model/
di/            Hilt modules
service/       MediaListenerService (media + navigation notifications)
```

More detail for contributors is in [CLAUDE.md](CLAUDE.md).

---

## Planned

| Feature | Status |
|---------|--------|
| Adaptive GPS interval (500 ms driving → 5 s parked) | planned |
| `LocationForegroundService` | manifest stub only |
| Licence-plate detection on ONNX (YOLO + OCR) | on a feature branch, not merged |

---

## License

Personal project — not licensed for redistribution.
