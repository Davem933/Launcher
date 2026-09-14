# Mapbox Map Engine — Design Spec (Fáze 2)
**Date:** 2026-09-14
**Status:** Approved

---

## 1. Overview

Nahradit mapový engine v panelu "Mapa" (`MapWidget`, dnes MapLibre nad offline PMTiles daty) za **Mapbox Maps SDK for Android** s online Mapbox Standard/Dark stylem. Panel "Navigace" (notification-scraping z Google Maps/Mapy.cz/Organic Maps přes `MediaListenerService`/`NavRepository`/`NavWidget`/`NavAreaWidget`) se **nemění vůbec** — oba panely z Fáze 1 (`MapNavPanel`, přepínání dlouhým stiskem) zůstávají jako dvě nezávislé, přepínatelné možnosti přesně jako dnes.

Toto je **čistá výměna engine** uvnitř `MapWidget.kt` — žádný routing, žádné vyhledávání cíle, žádný Mapbox Navigation SDK. "Co potřebuju od Mapboxu" je proto jen základní Maps SDK přístup (public token), ne Navigation SDK.

**Zjištění během brainstormingu (oprava zastaralé CLAUDE.md dokumentace):** Dnešní `MapWidget` už nepoužívá online Mapy.cz raster dlaždice ani `MAPYCZ_API_KEY` (mrtvý kód od commitu `bfd481e`). Aktuálně běží nad vlastním vektorovým stylem ([map_style_dark.json](../../../app/src/main/assets/style/map_style_dark.json)) nad **offline PMTiles souborem** (`czech.pmtiles`) servírovaným lokálně přes `PmtilesHttpServer` (NanoHTTPD). Fonty/sprity táhne online z protomaps.github.io. Tato offline schopnost se přechodem na Mapbox **záměrně opouští** (viz §5) — jde o vědomé rozhodnutí, ne přehlédnutí.

---

## 2. Rozsah

**Mění se:**
- `app/src/main/java/com/example/carlauncher/ui/map/MapWidget.kt` — přepsán na Mapbox Maps SDK API
- `app/src/main/java/com/example/carlauncher/ui/map/MapViewModel.kt` — odstranění PMTiles server inicializace
- `app/src/main/java/com/example/carlauncher/ui/map/TileConfig.kt` — nahrazen konstantou pro Mapbox styl (nebo smazán, viz §5)
- `app/src/main/java/com/example/carlauncher/CarLauncherApp.kt` — `MapLibre.getInstance(this)` → `MapboxOptions.accessToken = ...`
- `app/build.gradle.kts`, `settings.gradle.kts` — závislosti a Maven repo pro Mapbox

**Nemění se (mimo rozsah):**
- `NavAreaWidget.kt`, `NavWidget.kt`, `NavRepository.kt`, `MediaListenerService.kt` — panel "Navigace" beze změny
- `MapNavPanel.kt` (Fáze 1) — přepínací logika, dlouhý stisk, bottom sheet — beze změny; jen `MapWidget` uvnitř něj teď kreslí přes jiný engine
- `RouteSnapHelper.kt` — nepoužitý dnes, zůstává nepoužitý
- Žádný routing, žádné vyhledávání destinace, žádný Mapbox Navigation SDK, žádné hlasové pokyny

---

## 3. Migrace API (MapLibre → Mapbox Maps SDK)

`MapWidget.kt` je přepsán 1:1 podle této tabulky — canvas-kreslené bitmapy (vozidlo, POI ikony) a `RouteSnapHelper` volání zůstávají beze změny, mění se jen mapové API pod nimi:

| Dnes (MapLibre `org.maplibre.gl:android-sdk:11.8.0`) | Mapbox Maps SDK ekvivalent |
|---|---|
| `MapLibre.getInstance(this)` v `CarLauncherApp.kt` | `MapboxOptions.accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN` |
| `MapLibreMapOptions.createFromAttributes(context).textureMode(true)` | `MapInitOptions(context, textureView = true)` |
| `Style.Builder().fromUri(TileConfig.STYLE_ASSET)` (lokální JSON nad PMTiles) | `mapboxMap.loadStyleUri(Style.STANDARD)`, poté nastavit tmavý motiv přes Config API (`style.setStyleImportConfigProperty("basemap", "lightPreset", "night")`) |
| `GeoJsonSource` + `SymbolLayer` + `Expression.get("bearing")` (vozidlo, POI vrstvy) | Stejné třídy 1:1 (`com.mapbox.maps.extension.style.sources.generated.GeoJsonSource`, `SymbolLayer`, `Expression.get`) |
| `style.addImage(id, bitmap)` (vozidlo, POI ikony) | Stejná metoda, stejná signatura |
| `CameraUpdateFactory.newLatLngZoom(...)` / `map.moveCamera(...)` (okamžitá pozice) | `mapboxMap.setCamera(CameraOptions.Builder().center(point).zoom(z).build())` |
| `map.animateCamera(CameraUpdateFactory.newLatLng(...), 500)` (plynulý posun) | `mapboxMap.easeTo(CameraOptions.Builder().center(point).build(), MapAnimationOptions.mapAnimationOptions { duration(500) })` |
| `map.uiSettings.isScrollGesturesEnabled/isZoomGesturesEnabled = true`, `isRotateGesturesEnabled/isTiltGesturesEnabled = false` | `mapView.gestures.updateSettings { scrollEnabled = true; pinchToZoomEnabled = true; rotateEnabled = false; pitchEnabled = false }` |
| `map.uiSettings.isLogoEnabled/isAttributionEnabled = false` | **Zůstává zapnuté** (viz níže — Mapbox ToS) |
| `map.addOnCameraMoveStartedListener { reason -> if (reason == 1) isFollowing = false }` (detekce doteku) | `mapView.gestures.addOnMoveListener` (`onMoveBegin` = uživatel začal posouvat mapu prstem) |
| `mapView.onCreate/onStart/onResume/onPause/onStop/onDestroy` (lifecycle) | Stejné metody na Mapbox `MapView`, stejné zapojení do `DisposableEffect`/`LifecycleEventObserver` z Fáze 1 |

**Mapbox attribution/logo:** Na rozdíl od open-source MapLibre vyžadují podmínky Mapboxu viditelné logo a attribution na mapě, pokud nemáš enterprise smlouvu. Dnešní kód je schválně vypíná (`isLogoEnabled = false`) — u Mapboxu je necháme **zapnuté** (malé, v rohu mapy), aby nedošlo k porušení ToS.

---

## 4. Nastavení — co je potřeba od Mapboxu

- Mapbox účet (free tier stačí)
- **Public access token** (`pk.…`) → jde do `local.properties` jako `MAPBOX_ACCESS_TOKEN`, odtud do `BuildConfig`, stejný vzor jako dnešní (mrtvý) `MAPYCZ_API_KEY`
- **Secret downloads token** (`sk.…`, scope `DOWNLOADS:READ`) → jde do `~/.gradle/gradle.properties` jako `MAPBOX_DOWNLOADS_TOKEN` (nikdy do repozitáře), potřebný jen pro Gradle k stažení SDK z Mapbox Maven repa — přidá se `maven { url = ...; credentials { username = "mapbox"; password = MAPBOX_DOWNLOADS_TOKEN } }` do `settings.gradle.kts`
- Žádný Navigation SDK, žádné Search Box API — mimo rozsah této fáze
- Přesná verze `com.mapbox.maps:android:<verze>` se ověří v Mapbox dokumentaci při implementaci (pinovaná, ne "latest")

---

## 5. Co se odstraní (mrtvý kód po přechodu)

- Gradle závislosti: `org.maplibre.gl:android-sdk:11.8.0`, `org.nanohttpd:nanohttpd:2.3.1`
- Soubor [PmtilesHttpServer.kt](../../../app/src/main/java/com/example/carlauncher/data/map/PmtilesHttpServer.kt) (celý)
- `TileConfig.kt` — `PMTILES_PATH`/`STYLE_ASSET` konstanty smazány; pokud zůstane potřeba pojmenované konstanty pro styl URI, přejmenovat/nahradit jednou konstantou pro Mapbox styl
- Soubor [map_style_dark.json](../../../app/src/main/assets/style/map_style_dark.json)
- `MAPYCZ_API_KEY` z `app/build.gradle.kts` (`buildConfigField` + `localProps.getProperty(...)`) — už dnes mrtvý, tímto definitivně pryč
- `pmtilesServer` pole a jeho inicializace/`stop()` v `MapViewModel`

Offline PMTiles data (`czech.pmtiles` soubor na zařízení) se fyzicky nemažou (je to uživatelův soubor mimo repo) — jen se přestanou používat.

---

## 6. Okrajové případy a testování

- **16KB page-size:** stejné riziko jako u MapLibre (native `.so` knihovny) — dokud neověříme kompatibilitu Mapbox SDK na API 35+, testovat výhradně na API 34 x86_64 emulátoru nebo fyzickém Lenovo tabletu, per CLAUDE.md konvence.
- **Bez signálu:** online Mapbox dlaždice se bez připojení nenačtou (na rozdíl od dnešního offline PMTiles) — přijaté riziko pro v1, offline řešení je budoucí práce.
- **Bez testů** (stejná konvence jako Fáze 1, žádný test framework v repu) — ověření: `./gradlew compileDebugKotlin`/`assembleDebug` + manuální QA na fyzickém tabletu:
  1. Mapa se načte a zobrazí tmavý Mapbox styl (ne černá obrazovka)
  2. Marker vozidla se zobrazuje a otáčí podle bearingu
  3. POI ikony (pokud jsou v okolí) se zobrazují
  4. Kamera sleduje vozidlo (auto-follow) při pohybu
  5. Dotek/posun mapy zastaví auto-follow na 10s (stejné chování jako dnes)
  6. Zaoblené rohy panelu (textureMode) fungují
  7. Tlačítko "Navigovat" funguje (přepne panel na Navigaci, beze změny z Fáze 1)
  8. Přepínání Mapa↔Navigace dlouhým stiskem (Fáze 1 mechanismus) funguje beze změny
  9. Mapbox logo/attribution je viditelné (ToS)

---

## 7. Mimo rozsah

- Mapbox Navigation SDK, routing, hlasové pokyny, vyhledávání destinace (Search Box API)
- Obnovení offline mapové schopnosti (PMTiles nebo Mapbox Offline Manager) — budoucí práce, pokud bude potřeba
- Jakékoli změny v `NavAreaWidget`, `NavWidget`, `NavRepository`, `MediaListenerService`, `MapNavPanel` (Fáze 1 přepínač)
- `RouteSnapHelper` zůstává nepoužitý
