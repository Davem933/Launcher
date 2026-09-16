# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Personal Android Car Launcher for Lenovo Tab M10 Plus (3rd Gen). Landscape-locked fullscreen app that replaces the default Android launcher. Built with Kotlin + Jetpack Compose + Hilt.

**Target:** minSdk 31, compileSdk/targetSdk 36 (Android 16), screen ~1143×686 dp landscape only.

**Device:** Lenovo Tab M10 Plus (3rd Gen), Android 16 (API 36), MediaTek Helio G80. The physical device runs fine — the 16 KB page size constraint applies only to certain ARM chipsets; Helio G80 does not enforce it.

**Emulator:** Use API 34 (Android 14) x86_64 with Google Play. Do NOT use API 35+ emulators — this was confirmed for MapLibre's `libmaplibre.so` (not 16 KB page-aligned); the app now uses Mapbox Maps SDK instead, whose native `.so` libraries carry the same theoretical risk but haven't been individually re-verified — keep testing on API 34 or the physical device until someone checks.

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

ADB logcat tags: `NavRaw`, `NavListener`, `MediaDebug`, `SpeedLimit`, `MapWidget`, `DockViewModel`, `ParkingRepository`, `MapViewModel`, `DestinationSearchBar`, `MapboxVoice`

**Mapbox setup (required to build):** two tokens, neither committed to git in principle —
- `MAPBOX_ACCESS_TOKEN` (public, `pk.…`) in `local.properties` → `BuildConfig.MAPBOX_ACCESS_TOKEN` → set via `MapboxOptions.accessToken` in `CarLauncherApp.onCreate()`.
- `MAPBOX_DOWNLOADS_TOKEN` (secret, `sk.…`, scope `DOWNLOADS:READ`) in `~/.gradle/gradle.properties` (machine-wide, never per-project) — required just for Gradle to authenticate against Mapbox's private Maven repo (`settings.gradle.kts`) to download `com.mapbox.maps:android`. Get both at https://account.mapbox.com/access-tokens/.
- ⚠️ `local.properties` is (incorrectly, pre-existing) tracked in git in this repo and not in `.gitignore` — it already contains both `MAPBOX_ACCESS_TOKEN` and the older `MAPYCZ_API_KEY` in the working copy. Don't `git add`/commit it. This should eventually be fixed with `git rm --cached local.properties` + a `.gitignore` entry, but that cleanup hasn't happened yet.

## Architecture

Single-module app (`app/`). Package structure:

```
ui/
  launcher/     LauncherScreen, LauncherViewModel, StatusBar
                MapNavPanel (dlouhý stisk přepíná Mapa ↔ Navigace)
                WeatherCalendarWidget + ViewModel (počasí + kalendář)
                SystemControlsWidget (hlasitost + jas)
  navigation/   NavAreaWidget (podmíněný wrapper), NavWidget (notifikační turn-by-turn)
  map/          MapWidget, MapViewModel, TileConfig, AppLocationProvider
                DestinationSearchBar (Mapbox Search Box)
  speed/        SpeedDisplay
  music/        MusicWidget, MediaViewModel
  dock/         DockBar, DockViewModel, SlotPicker
  widgets/      WidgetScreen, WidgetViewModel, LongPressWidgetHost, WidgetLayoutTemplate
  theme/        CarLauncherTheme (dark only), CarColors
data/
  location/     LocationRepository, LocationProcessor, KalmanFilter
  navigation/   NavRepository (Compose singleton object, notifikační navigace)
                MapboxVoiceGuidanceObserver (hlas Mapbox navigace, app-level)
  speedlimit/   SpeedLimitRepository (Nominatim reverse geocoding)
  weather/      WeatherRepository (Open-Meteo API)
  calendar/     CalendarRepository (CalendarContract.Instances)
  media/        MediaSessionObserver
  dock/         DockDataStoreExt
  widgets/      WidgetDataStoreExt
  poi/          ParkingRepository (Overpass), ParkingFetchThrottle
  model/        VehicleDisplayLocation, DockItem, DockSlot, WidgetSlot, Parking
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
            MapNavPanel(weight 1.85f)      ← ~65% šířka: MapWidget (default) nebo NavAreaWidget,
                                              přepínatelné dlouhým stiskem (viz níže)
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

`SpeedDisplay` je uvnitř `MapWidget` v `BottomStart` s `padding(18dp)` — skrytý po dobu aktivní Mapbox navigace (kolize s trip progress barem, viz Module: Navigace přes Mapbox).

## Module: MapNavPanel (ui/launcher/MapNavPanel.kt)

Levý panel (~65% šířky) přepíná mezi `MapWidget` a `NavAreaWidget` dlouhým stiskem (1500ms, zrušení při pohybu >12dp — stejný vzor jako `LongPressWidgetHostView`, vlastní `pointerInput` detektor s `PointerEventPass.Initial` a bez `consume()`, aby pan/zoom mapy a tlačítka pod ním fungovaly beze změny).

`resolveEffectiveView(isNavActive, manualView)` je čistá funkce: `NavRepository.isActive == true` vždy vyhraje (bezpečnostní priorita) bez ohledu na ruční volbu; jinak poslední ruční volba v této relaci, nebo `MAP` jako výchozí. Stav je jen v `remember` (ne DataStore) — resetuje se na Mapu po restartu.

Dlouhý stisk otevře `ModalBottomSheet` s kartami "Mapa"/"Navigace". Tlačítko "Navigovat" v `MapWidget` (`onNavigate` callback) přepne panel na `NavAreaWidget`.

**Pozor při úpravách:** lokální composable proměnná pojmenovaná `location` (GPS fix) stíní `MapView.location` (Mapbox location-component plugin extension property) uvnitř `mapView.apply { }` bloků — vždy použít `this.location`, jinak dostane "Unresolved reference" na Mapbox API bez zjevné příčiny.

## Module: Navigation (notifikační — Google Maps / Mapy.cz / …)

Čte cizí navigační appky z notifikací. **Úplně oddělené** od vlastní Mapbox navigace níže — jiný stav, jiné UI, dvě různá tlačítka "Ukončit".

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

## Module: Map (MapWidget.kt) — Mapbox Maps SDK

Přešlo z MapLibre + offline PMTiles na **Mapbox Maps SDK** (`com.mapbox.maps:android`, `TileConfig.kt` má přesnou verzi) — online, žádný offline fallback. `MapView` a `GeoJsonSource`/`MapboxMap` reference žijí v plain `MapState` třídě v `remember {}` — **nikdy** v `mutableStateOf` (stejné pravidlo jako u MapLibre dřív).

**Styl:** `Style.STANDARD` (Mapboxův dynamický 3D styl — budovy, landmarky), kamera s `pitch(45.0)` pro 3D pohled. Config vlastnosti (`lightPreset="night"`, `show3dObjects=true`) se nastavují přes `style.setStyleImportConfigProperty(importId, key, value)` **s importId `"basemap"`** — `"standard"` se použije jen když je Standard vnořený v custom style JSON pod tím jménem; špatné ID selže potichu (vrátí `Expected` s chybou, ne výjimku) a config se prostě neaplikuje.

**Doprava:** Standard nemá vestavěnou dopravu (na rozdíl od klasických `TRAFFIC_DAY`/`TRAFFIC_NIGHT` stylů) — přidána ručně jako extra `VectorSource` (`mapbox://mapbox.mapbox-traffic-v1`, `sourceLayer("traffic")`) + `LineLayer` obarvený podle `congestion` property (`Expression.match`), `slot("middle")`.

**Parkoviště:** Mapboxova vlastní POI data jsou v této oblasti řídká pro parkoviště — `ParkingRepository` (Overpass/OSM), fialová "P" ikona (`SymbolLayer` + canvas bitmap). **Overpass dotaz musí hledat `node` i `way`/`relation` s `out center`** — parkoviště jsou v OSM většinou plochy (`way`), ne body; dotaz jen na `node` vrací 0 výsledků i tam, kde parkoviště reálně jsou.

**Location puck:** Mapboxův vestavěný `mapView.location` (ne vlastní canvas marker) — nakrmený z `AppLocationProvider` (implementuje `LocationProvider`/`LocationConsumer`), který dostává už Kalman-filtrovanou/route-snapnutou pozici z `VehicleDisplayLocation`, ne z Mapboxího výchozího GPS providera → není potřeba location permission handling v tomto plugin. Ikona přes `createDefault2DPuck(withBearing = true)` — holý `LocationPuck2D()` bez obrázků nevykreslí nic (všechny image parametry `null`).

**Inicializace:** `MapView` v `remember {}` s `MapInitOptions(textureView = true)` (kvůli `clip()` zaoblení rohů). Gesta, location provider a `subscribeMapLoadingError` se nastavují **hned po vytvoření mapy** (mimo `loadStyle` callback) — ten callback fire-uje jen při úspěšném (online) načtení stylu, takže cokoliv uvnitř by bez signálu nikdy neproběhlo.

**Gotcha:** `this.location` (viz Module: MapNavPanel výše) — composable `location` (GPS StateFlow) stíní `MapView.location` plugin property uvnitř `mapView.apply { }`.

**Nedostupné offline:** žádný fallback bez signálu (na rozdíl od starého MapLibre+PMTiles řešení) — přijaté riziko.

## Module: Navigace přes Mapbox (Fáze 3) — MapWidget.kt + DestinationSearchBar.kt

Turn-by-turn počítaný přímo v appce přes **Mapbox Navigation SDK + Search Box SDK**, jako druhá možnost vedle notifikační navigace výše (ta zůstává beze změny). Celé to žije jako overlay nad `MapWidget`em, přepínané stavem `isNavigating`.

**Instance `MapboxNavigation`:** `MapboxNavigationApp.setup()` v `CarLauncherApp.onCreate()`, v `MapWidget`u se bere přes `remember { lifecycleOwner.requireMapboxNavigation() }` (kvůli side-effectu attachnutí) + `MapboxNavigationApp.current()`. `by requireMapboxNavigation()` v `@Composable` **nejde** — delegát má `getValue(thisRef: Any, …)`, ale lokální delegovaná property potřebuje nullable `thisRef` (ověřeno kompilátorem, ne odhad). `lifecycleOwner` je `context as ComponentActivity`, ne `LocalLifecycleOwner` (ten je per-page z `HorizontalPager`u a nemusí dojet na RESUMED).

**Hledání cíle — `DestinationSearchBar`:** Search Box SDK (`SearchEngine`, `ApiType.SEARCH_BOX`), dvoukrokové: `search(query)` → `SearchSuggestion`y (**bez souřadnic**), pak `select(suggestion)` → `SearchResult` se souřadnicí. `SearchSelectionCallback` má tři úspěšné větve: `onResult` (konkrétní místo), `onResults` (kategorie/brand → bere se první, tj. nejbližší dle `proximity`), `onSuggestions` (dotazová sugesce se rozbalí na další sugesce, ne na místo). Debounce 300 ms, min. 2 znaky. `proximity` se čte přes `rememberUpdatedState` — kdyby byl `LaunchedEffect` keyovaný přímo na `currentLocation`, každý GPS tick (500 ms) by debounce zrušil a za jízdy by hledání nikdy neodešlo. Vždy viditelný overlay v `TopCenter`, skrytý při `isNavigating` (tam je to místo obsazené maneuver bannerem).

**Tok: výběr cíle → trasa → navigace hned.** `requestRoutes()` → `onRoutesReady` → `setNavigationRoutes()` + `startTripSession()` + `isNavigating = true`. **Žádný preview / mezikrok "Start"** — je to záměr, ne chybějící feature. Při rychlém přepsání cíle se rozjetý request nejdřív `cancelRouteRequest(id)`, a každý callback před zápisem porovná svoje `requestId` s `activeRequestId` — bez toho může opožděný výsledek staršího hledání přepsat novější trasu (nebo mu `onCanceled` odtrackovat id).

**`isNavigating` se NEinicializuje na `false`**, ale z `mapboxNavigation.getTripSessionState() == STARTED`. Trip session žije nad kompozicí (na Activity), zatímco `MapWidget` se při přepnutí `MapNavPanel`u Mapa→Navigace celý disposuje — bez tohohle by se po návratu zpět přes běžící navigaci vykreslilo volné UI.

**Kamera a puck se při navigaci předávají SDK:** `mapView.location` se přepne z `AppLocationProvider` (Kalman/route-snap, viz GPS Pipeline) na Mapboxí `NavigationLocationProvider`, krmený `LocationObserver`em (map-matched `enhancedLocation`), a kameru převezme `NavigationCamera` + `MapboxNavigationViewportDataSource` (`requestNavigationCameraToFollowing()`). Volný režim se přitom **musí stáhnout** — `easeTo` v `LaunchedEffect(location)` je proto podmíněné `!isNavigating`, jinak by si s `NavigationCamera` praly kameru při každém fixu. Konec navigace vrací obojí zpět (`…ToIdle()`).

**Hlas: `data/navigation/MapboxVoiceGuidanceObserver.kt`, registrovaný v `CarLauncherApp.onCreate()` přes `MapboxNavigationApp.registerObserver(...)` — NE v `MapWidget`u.** `MapWidget` se disposuje při každém přepnutí panelu, takže cokoliv v jeho `remember`/`DisposableEffect` by řidiči uprostřed trasy umlčelo navigaci ve chvíli, kdy se podívá na panel Navigace. `MapboxSpeechApi` i `MapboxVoiceInstructionsPlayer` se tvoří až v `onAttached` a zahazují v `onDetached` — ty se volají **opakovaně** za život procesu (odchod appky do pozadí = `onDetached`) a `MapboxVoiceInstructionsPlayer.shutdown()` je **nevratný**, takže jedna instance z konstruktoru by po prvním přechodu do pozadí navždy oněměla. Jazyk = locale zařízení, ne `Locale.US` (route requesty jedou přes `applyLanguageAndVoiceUnitOptions(context)`, syntéza to musí respektovat).

**Příjezd ukončí navigaci sám:** `ArrivalObserver.onFinalDestinationArrival` volá stejný `endGuidance()` jako tlačítko Ukončit (stop trip session + prázdné routes + reset stavu). Bez toho by na trvale zapnutém launcheru běžela foreground služba + GPS + hlas donekonečna, dokud to někdo ručně nevypne. `onWaypointArrival` / `onNextRouteLegStart` jsou schválně prázdné (mezicíl nesmí ukončit trasu; `navigateNextRouteLeg()` appka nevolá).

**Overlaye při navigaci:** maneuver banner (`MapboxManeuverView`, TopCenter, `end = 72dp` aby text nelezl pod tlačítko Ukončit) + trip progress (`MapboxTripProgressView`, BottomCenter, 64dp). Po dobu navigace se **skrývá `SpeedDisplay` i tlačítko "Navigovat"** — oba sedí ve stejném spodním pásu jako trip progress bar. Ve volné jízdě je layout beze změny.

**Tmavý styl obou hotových View:** SDK defaulty jsou světlé — `MapboxTripProgressView` má pozadí `@color/colorSurface` = **bílá** (i v Mapboxím `values-night`), tedy v noci svítící pruh přes celou spodní hranu mapy; maneuver banner je modrošedý `#37516F`. Řeší to `res/values/nav_colors.xml` (tokeny 1:1 z `CarColors.kt`) + `res/values/nav_styles.xml` (styly dědí z originálních `MapboxStyle*` a přebíjí jen barvy). XML je nutné, protože Mapbox API bere `@ColorRes`/`@StyleRes`, ne Compose `Color`. Aplikuje se v `factory` bloku: `MapboxTripProgressView.updateStyle(R.style.CarTripProgressView)`, `MapboxManeuverView.updateManeuverViewOptions(darkManeuverViewOptions())` — maneuver View **nemá** `updateStyle`, jediná runtime cesta jsou `ManeuverViewOptions`. `tripProgressViewBackgroundColor` **musí být reference na `@color/`**, ne barevný literál — View ho čte přes `getResourceId()` a posílá do `ContextCompat.getColor()`.

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

## Module: Incident Recorder (data/incident/, ui/incident/)

Manuální „dashcam“ — `IncidentFab` v `MainActivity`: přesouvatelné plovoucí tlačítko (`detectDragGestures`, pozice clampovaná do viewportu, uložená v `incidentDataStore` přes `IncidentButtonViewModel` jako dp offset). Tap otevře celoobrazovkový `IncidentRecorderScreen` overlay. Toggle spustí/zastaví nahrávání videa přes CameraX. `IncidentCameraView` rebinduje kameru na `Lifecycle.Event.ON_RESUME` (jinak černý náhled po návratu z jiné appky).

**Feature flag:** `BuildConfig.INCIDENT_RECORDER_ENABLED` (z `local.properties`, default `true`). Vypnuto → tlačítko se nevykreslí, overlay nedostupný. CameraX + ML Kit závislosti se buildí vždy.

**Nahrávání:** CameraX `VideoCapture<Recorder>` (Quality.HD → SD fallback), **bez zvuku** (žádné `RECORD_AUDIO`). Výstup do `getExternalFilesDir(null)/incidents/incident_<yyyyMMdd_HHmmss>.mp4`. `IncidentRecorder` (`@Singleton`) vlastní `ProcessCameraProvider` + `Preview` + `VideoCapture` + `ImageAnalysis`, `bind(activity, surfaceProvider)` je idempotentní, `bindToLifecycle(activity, ...)` (ne `LocalLifecycleOwner`). 3-use-case bind má fallback na `Preview + VideoCapture`.

**GPS:** `IncidentLocationSource` (`@Singleton`) — raw `LocationManager.GPS_PROVIDER` (+ `NETWORK_PROVIDER` warm-up), interval 1500 ms, vlastní `HandlerThread("incident-gps")`. **Zcela oddělené** od `LocationRepository` (žádné Play Services, žádný Kalman).

**Detekce SPZ:** `ImageAnalysis` → `PlateAnalyzer` (jen každý `DETECT_EVERY_N_FRAMES`=8. snímek, `@Volatile busy` guard, single-thread executor) → `PlateDetector` (rozhraní, bindnuté v `di/IncidentBindsModule.kt`). Výchozí `MlKitPlateDetector` = ML Kit Text Recognition v2 **bundled** (offline, `Tasks.await`) + `PlateRegex` (CZ/EU formát) + geometrie. TFLite YOLO lze doplnit výměnou `@Binds`.

**Metadata:** overlay (`IncidentOverlay` Compose `Canvas`) je sourozenec `PreviewView` — **nikdy** se nepředá CameraX, `.mp4` zůstává nezměněný. Sidecar `incident_<...>.json` (stejný basename, `org.json`) se zapisuje ve `VideoRecordEvent.Finalize`: `startedAt`/`stoppedAt`, `gps[]` časová řada, `plates[]` (`text` + normalizovaný `box` 0..1). GPS body + detekce se během nahrávání bufferují in-memory v `IncidentSession` pod `synchronized`.

**Oprávnění:** `CAMERA` + `ACCESS_FINE_LOCATION` řešeno in-Compose (`rememberLauncherForActivityResult`) v `IncidentRecorderScreen`, ne v `MainActivity.onCreate`. Zavření overlaye / `onCleared` volá `recorder.stop()` (kvůli `moov` atomu) + `locationSource.stop()`.

## Planned / Not Yet Implemented

- **`LocationForegroundService`** — deklarováno v manifestu, stub pouze
- **Offline mapa** — záměrně opuštěno při přechodu na Mapbox (žádný fallback bez signálu); staré PMTiles+NanoHTTPD řešení bylo smazané, ne jen nepoužívané
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
