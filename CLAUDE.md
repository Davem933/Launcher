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
```

Direct ADB install (PowerShell):
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r app/build/outputs/apk/debug/app-debug.apk
```

ADB logcat tags: `NavRaw`, `NavListener`, `MediaDebug`, `SpeedLimit`, `MapWidget`, `DockViewModel`

## Architecture

Single-module app (`app/`). Package structure:

```
ui/
  launcher/     LauncherScreen, LauncherViewModel, StatusBar
                WeatherCalendarWidget + ViewModel (počasí + kalendář)
                SystemControlsWidget (hlasitost + jas)
  navigation/   NavAreaWidget (podmíněný wrapper), NavWidget (turn-by-turn)
  map/          MapWidget, MapViewModel, MapStyleHelper, TileConfig
  speed/        SpeedDisplay
  music/        MusicWidget, MediaViewModel
  dock/         DockBar, DockViewModel, SlotPicker
  widgets/      WidgetScreen, WidgetViewModel, LongPressWidgetHost, WidgetLayoutTemplate
  theme/        CarLauncherTheme (dark only), CarColors
data/
  location/     LocationRepository, LocationProcessor, KalmanFilter
  navigation/   NavRepository (Compose singleton object)
  speedlimit/   SpeedLimitRepository (Nominatim reverse geocoding)
  weather/      WeatherRepository (Open-Meteo API)
  calendar/     CalendarRepository (CalendarContract.Instances)
  media/        MediaSessionObserver
  dock/         DockDataStoreExt
  widgets/      WidgetDataStoreExt
  model/        VehicleDisplayLocation, DockItem, DockSlot, WidgetSlot, Poi
  map/          PmtilesHttpServer (offline, nepoužíváno)
di/
  LocationModule.kt
service/
  MediaListenerService   (NotificationListenerService — media + nav notifikace)
  LocationForegroundService  (stub)
```

**DI:** Hilt throughout. `@HiltAndroidApp` na `CarLauncherApp`, `@AndroidEntryPoint` na `MainActivity`. ViewModels přes `@HiltViewModel`. Hilt moduly v `di/` jen pro typy bez `@Inject constructor`. Singletony s `@Inject constructor` mají `@Singleton` přímo na třídě.

**Theme:** `CarColors.kt` — `Bg #0D0E12`, `Surface #161820`, `Go #5DBF7A`, `Accent #6B8EF0`, `Text/Text2/Text3`. Nové composables používají CarColors tokeny; starší widgety mají private color constants.

**Fullscreen:** `MainActivity` skryje status + nav bary přes `WindowInsetsControllerCompat`, nastaví `FLAG_KEEP_SCREEN_ON`. Nevolat `enableEdgeToEdge()` — konflikt. `screenOrientation="landscape"` a `windowSoftInputMode="adjustNothing"` jsou záměrné.

## Dual-screen layout (HorizontalPager)

`MainActivity` má `HorizontalPager` se dvěma stranami (dot indikátor nad DockBar):
- **Page 0** — `LauncherScreen` (mapa + hudba + navigace + počasí)
- **Page 1** — `WidgetScreen` (Android AppWidget grid)

Přechod swipe doleva/doprava. `beyondViewportPageCount = 1` → obě stránky jsou preloadnuty.

## LauncherScreen Layout

```
Column(fillMaxSize, bg = CarColors.Bg) {
    StatusBar(44dp)                        ← čas HH:mm + české datum | baterie, WiFi, GPS tečka
    Box(weight 1f) {
        Row(p = 12/8dp, spacing 12dp) {
            NavAreaWidget(weight 1.85f)    ← ~65% šířka: NavLanding nebo NavWidget
            Column(weight 1f) {            ← ~35% šířka
                MusicWidget(weight 1f)
                SystemControlsWidget()     ← hlasitost (modrá) + jas (jantarová), swipe nahoru/dolů
                WeatherCalendarWidget()    ← počasí vlevo, kalendář vpravo
            }
        }
        [GPS debug overlay, TopStart]      ← jen BuildConfig.DEBUG
    }
    DockBar(88dp, ph = 12dp, pb = 8dp)
}
```

`SpeedDisplay` je uvnitř `MapWidget` v `BottomStart` s `padding(18dp)`.

## Module: Navigation

**NavAreaWidget** (ui/navigation/) — zobrazí `NavWidget` pokud `NavRepository.isActive`, jinak `NavLanding` (picker navigačních appek).

**NavRepository** (`data/navigation/NavRepository.kt`) — `object` s Compose `mutableStateOf` poli. Mutace musí přijít na main thread (MediaListenerService dispatchuje přes `mainHandler.post`). `isActive = maneuverStreet.isNotEmpty() || maneuverDistance.isNotEmpty()`.

**MediaListenerService** (`service/`) — `NotificationListenerService` s dvojí rolí:
1. Media session tracking (`isConnected` StateFlow pro `MediaSessionObserver`)
2. Parsování nav notifikací → `NavRepository`

**Nav formáty notifikací:**
- Google Maps CZ: `TITLE="za 600 m"`, `TEXT="směr Klenovecká"` — "za " prefix v title
- Waze: `TITLE="Odbočte vpravo"`, `TEXT="za 200 m"` — "za " prefix v textu
- Mapy.cz: `TITLE="150 m"`, `TEXT="Klenovecká"`, `SUBTEXT="19:18 příjezd • 6 min • 2,8 km"`

`LOGO_ONLY_ICON_PACKAGES = setOf("com.waze")` — tyto apps posílají jen logo jako ikonu, vrací se `null` → NavWidget zobrazí výchozí navigační šipku.

Idle notifikace jsou přeskočeny: `hasNavContent = distance.isNotEmpty() || distLeft.isNotEmpty() || cancelIntent != null`.

`parseTripSummary` splituje na `·` (U+00B7, Google Maps) i `•` (U+2022, Mapy.cz).

## Module: Map (MapWidget.kt)

**Tile source:** Mapy.cz online raster tiles. API klíč v `local.properties` (`MAPYCZ_API_KEY=...`, gitignored) → `BuildConfig.MAPYCZ_API_KEY` — nikdy hardcodovat, zobrazovat ani logovat hodnotu klíče.

**Style JSON** builduje `buildMapyczStyleJson(tileUrl)` v `MapStyleHelper.kt` — raster source + raster layer.

**Rounded corners** vyžaduje `MapLibreMapOptions.textureMode(true)` — výchozí SurfaceView ignoruje Compose `clip()`.

**Inicializace:** `MapView` v `remember {}` → `getMapAsync` → `LaunchedEffect(mapAsyncReady)` načte styl, přidá `GeoJsonSource` + `SymbolLayer` pro marker.

**Marker:** canvas-drawn 128px bitmap (halo + zelený disk + bílý klín). Rotace přes `iconRotate` + `iconRotationAlignment("map")`.

**Kamera + marker:** `LaunchedEffect(location)` — jeden `animateCamera(CameraPosition.Builder().target().bearing(), 300ms)`. Nikdy dva sekvenční `animateCamera()` — ruší se navzájem.

**MapLibre objekty v Compose:** `MapLibreMap` a `GeoJsonSource` v plain `MapState` v `remember {}` — **nikdy** v `mutableStateOf`.

**MapLibre API quirks (11.5.x):** `addOnDidFailLoadingMapListener` je na `MapView`. `addOnMapLoadErrorListener` neexistuje. `addOnCameraChangeListener` neexistuje na `MapLibreMap`. Style JSON přes `buildString { append() }` (avoid trailing comma). `{fontstack}`/`{range}` v Kotlin `${}` templates jsou špatně parsed — použít `buildString`. `MapView.onStart()`/`onResume()` replay v `DisposableEffect`.

## Module: SpeedDisplay (ui/speed/SpeedDisplay.kt)

`SpeedDisplay(speedKmh: Float, speedLimitKmh: Int = 50, modifier)` — zobrazuje 0 pod 3f km/h, barvy: bílá <90, oranžová 90–120, červená >120. Roundel s limitem vpravo od "km/h" — dynamická hodnota z `SpeedLimitRepository`.

## Module: SpeedLimit (data/speedlimit/SpeedLimitRepository.kt)

Nominatim reverse geocoding (`nominatim.openstreetmap.org`) — dotaz při přesunu >200m, vrací 50 (obec: city/town/village/suburb) nebo 90 (mimo). `User-Agent: CarLauncher/1.0` povinný. `LauncherViewModel` triggeruje `updateIfMoved(lat, lon)` při každé location změně. Sdílené přes Hilt singleton — `MapViewModel` a `LauncherViewModel` oba injectují a exposují `speedLimit: StateFlow<Int>`.

## Module: WeatherCalendarWidget (ui/launcher/)

**Počasí:** Open-Meteo API (zdarma, bez klíče) — `temperature_2m` + `weathercode` (WMO). Refresh každých 30 min. Fallback Praha (50.08, 14.42) pokud GPS nedostupné.

**Kalendář:** `CalendarContract.Instances` — dnešní události (00:00–23:59), max 3, seřazené dle začátku. Vyžaduje `READ_CALENDAR` runtime permission.

## Module: SystemControlsWidget (ui/launcher/)

Dva cards: HLASITOST (modrá `#60A5FA`) + JAS (jantarová `#FFC107`). Canvas kreslí barevnou výplň od spodku dle úrovně. Swipe nahoru = více, dolů = méně (citlivost 1.5×). Jas mění `window.attributes.screenBrightness`.

## Module: DockBar (ui/dock/)

**DockSlot sealed class** (`data/model/DockItem.kt`): `App(packageName)`, `SplitScreen(pkg1, pkg2, label)`, `Empty`, `Navigate`.

**DataStore** klíč `dock_slots_v2`, čárkou oddělené, 6 slotů: `"pkg"` / `"split:pkg1:pkg2:label"` (label používá `|`) / `"empty"`.

**Long press** — 1500ms přes `withTimeout` + `TimeoutCancellationException` v `pointerInput`. `combinedClickable` byl odstraněn — `waitForUpOrCancellation()` vrací null i při gesture cancellation (ne jen na timeout), takže by spouštěl edit mode omylem.

**SplitScreen pravidlo:** `packageName1` = navigace (vlevo), `packageName2` = hudba/sekundární (vpravo). NIKDY nezaměňovat.

**launchSplitScreen:** Bez public API na Android 16. Launches pkg1 ihned, pak po 650ms pkg2 s `FLAG_ACTIVITY_LAUNCH_ADJACENT + FLAG_ACTIVITY_MULTIPLE_TASK`. Musí být voláno z Activity kontextu.

## Module: WidgetScreen (ui/widgets/)

Android AppWidget host se dvěma vrstvami:

**WidgetViewModel** — `LongPressWidgetHost` (HOST_ID=1337), `AppWidgetManager`. Stavy: `stacks: List<WidgetStack>` (každý slot = `WidgetStack(widgetIds: List<Int>)`), `template: WidgetLayoutTemplate`. Persistence: DataStore `"widgets"` — sloty odděleny `;`, widget IDs v slotu `|`.

**WidgetLayoutTemplate:** `GRID_2X2` (4 sloty), `WIDE_TOP_TWO_BOTTOM` (3 sloty), `TWO_WIDE_ROWS` (2 sloty).

**WidgetScreen:** grid slotů, každý `SlotCard` → `StackContent` s `VerticalPager` (swipe pro přepínání widgetů v stacku). Edit mode overlay (tmavý scrim + Delete/Add tlačítka) se aktivuje long pressem na widget.

**LongPressWidgetHostView** — override `AppWidgetHostView` s `Handler.postDelayed(1500ms)`. `ACTION_DOWN` → start timer, `ACTION_MOVE` >12dp → cancel timer, `ACTION_UP` → cancel timer. Při fired long pressu: nahrazuje `ACTION_UP` za `ACTION_CANCEL` pro children (zabraňuje spuštění widgetu).

**Widget picker:** `MainActivity.launchWidgetPicker(slotIndex)` — `AppWidgetManager.ACTION_APPWIDGET_PICK` Intent, result v `onActivityResult` → `viewModel.addWidgetToSlot(slotIndex, widgetId)`.

## Module: MusicWidget (ui/music/)

`MediaSessionManager.getActiveSessions()` vyžaduje aktivně bound `NotificationListenerService`. Flow: `MediaListenerService.isConnected` StateFlow → `MediaSessionObserver.start()` čeká na `isConnected==true` → `querySessions()`. `SecurityException` = notification access not granted → silent fallback.

`currentPositionMs` na `MediaSessionObserver`: `state.position + (elapsedRealtime - lastPositionUpdateTime) * playbackSpeed`.

**User setup:** Nastavení → Aplikace → Speciální přístup → Přístup k oznámením → CarLauncher → Povolit.

## GPS Pipeline

```
FusedLocationProviderClient (500ms / 5s / 30s)
  → LocationCallback  [HandlerThread("location-thread")]
  → LocationProcessor.process(Location)
      → KalmanFilter (2D, Q=3 m/s)
      → speed: location.speed * 3.6f, rolling avg 3 samples
  → VehicleDisplayLocation (lat, lng, speedKmh, bearingDeg, accuracyM, timestamp)
  → LocationRepository._vehicleLocation: MutableStateFlow
  → LauncherViewModel / MapViewModel: StateFlow
  → SpeedLimitRepository.updateIfMoved() (při každé změně)
```

`LocationRepository.startTracking()` / `stopTracking()` volá `LauncherViewModel` v init/onCleared.

## Planned / Not Yet Implemented

- **`LocationForegroundService`** — deklarováno v manifestu, stub pouze
- **Offline mapa** — PMTiles v3 parser + NanoHTTPD v `data/map/`; MapWidget potřebuje přepnout na vector styl
- **Adaptive GPS interval** — fixní 500ms; mělo by klesnout na 5s při parkování
- **QuickDest navigační wiring** — QuickDestWidget / QuickDestViewModel existuje ale nepoužívá se

## Design Reference

Finalizovaný design: `.claude/design/` (CarLauncher.html, app.jsx, widgets.jsx, icons.jsx). Implementovat přesně, neiterorat.

Klíčové hodnoty:
- SpeedDisplay číslo: 56sp bold, tabular-nums
- DockBar výška: 88dp, slot touch target: 64dp, vizuální: 52dp, corner: 14dp
- Všechny touch targets: min 48×48 dp

## Subagents

| Task | Load file |
|------|-----------|
| Code review (Kotlin/Compose) | `.claude/subagent_01_code_review.md` |
| Performance audit, GPS latency, FPS | `.claude/subagent_02_performance.md` |
| UI layout, tap targets, car-safe design | `.claude/subagent_03_uiux.md` |
| Architecture decision, Hilt scope | `.claude/subagent_04_architecture.md` |
