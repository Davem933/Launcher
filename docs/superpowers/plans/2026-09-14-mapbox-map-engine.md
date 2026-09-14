# Implementační plán: Mapbox mapový engine

> **Pro agentní pracovníky:** POVINNÝ SUB-SKILL: použij superpowers:subagent-driven-development (doporučeno) nebo superpowers:executing-plans k provedení tohoto plánu task po tasku. Kroky používají checkbox (`- [ ]`) syntaxi pro sledování postupu.
>
> **Task 1 je RUČNÍ, čistě lidský task — NEPOSÍLAT ho implementačnímu subagentovi.** Vyžaduje registraci u třetí strany a vložení tajných přihlašovacích údajů do konfigurace lokálního stroje; žádný subagent nemá přístup k uživatelovu Mapbox účtu. Před spuštěním Tasku 2 si potvrď s člověkem, že Task 1 je hotový.

**Cíl:** Nahradit mapový engine MapLibre (offline PMTiles) uvnitř `MapWidget` za Mapbox Maps SDK (online styl z rodiny Standard/Dark), beze změny panelu "Navigace" nebo přepínače `MapNavPanel` z Fáze 1.

**Architektura:** `MapWidget.kt` je přepsán funkci po funkci proti Kotlin API Mapbox Maps SDK, které je velmi blízké API MapLibre (MapLibre je fork stejné linie) — stejné koncepty GeoJsonSource/SymbolLayer/kamera/gesta, jiné názvy balíčků a pár přejmenovaných metod. Inicializační volání SDK v `CarLauncherApp.kt` se vymění. Mrtvá PMTiles infrastruktura (`PmtilesHttpServer`, vlastní style JSON asset, `MAPYCZ_API_KEY`) se smaže.

**Tech stack:** Kotlin, Jetpack Compose, `com.mapbox.maps:android` (nahrazuje `org.maplibre.gl:android-sdk`). Žádné nové architektonické vzory — stejná struktura `MapViewModel`/Hilt/Compose jako dnes.

## Globální omezení

- Mění se jen `MapWidget.kt`, `MapViewModel.kt`, `CarLauncherApp.kt`, `TileConfig.kt`, `app/build.gradle.kts`, `settings.gradle.kts` a smazané mrtvé soubory. NEDOTÝKAT SE `NavAreaWidget.kt`, `NavWidget.kt`, `NavRepository.kt`, `MediaListenerService.kt` ani `MapNavPanel.kt` — mimo rozsah dle spec.
- Mapbox podmínky užití vyžadují viditelné logo + attribution (na rozdíl od open-source MapLibre) — NEVYPÍNAT je. Stará volání `map.uiSettings.isLogoEnabled = false` / `isAttributionEnabled = false` se prostě nepřenášejí (Mapbox je defaultně zobrazuje).
- Žádný Mapbox Navigation SDK, žádné Search Box API, žádný routing — jen Maps SDK (vykreslování).
- Žádný nový test framework/závislosti — repo nemá žádnou testovací infrastrukturu (žádné `app/src/test`, žádné `testImplementation` položky). Ověření: Kotlin se zkompiluje + manuální QA na zařízení (fyzický Lenovo tablet nebo emulátor API 34 x86_64, nikdy API 35+ — neověřené riziko 16KB page-size, dle CLAUDE.md, platí pro nativní `.so` knihovny Mapboxu stejně jako platilo pro MapLibre).
- **Riziko API povrchu:** Mapbox kód v tomto plánu (Task 3) je nejlepší možný odhad překladu z MapLibre na základě blízké příbuznosti API rodin, NEOVĚŘENÝ proti živé Mapbox dokumentaci (při psaní plánu nebyl k dispozici internet). Task 3 výslovně povoluje WebSearch/WebFetch a nařizuje ověřit na https://docs.mapbox.com/android/maps/ každé volání, které se nezkompiluje — to se očekává, není to znamení, že celý přístup je špatně.
- V `app/build.gradle.kts` napevno zvolit konkrétní verzi Mapbox Maps SDK (tento plán píše kód proti `11.8.0` jako zástupné verzi) — Task 2 výslovně vyžaduje zkontrolovat https://github.com/mapbox/mapbox-maps-android/releases kvůli skutečně nejnovější stabilní 11.x verzi, než se verze finálně zafixuje, dle §4 spec dokumentu (neházet tam neověřené číslo naslepo).

---

### Task 1: Založení Mapbox účtu a tokenů (RUČNÍ — akce člověka, ne subagenta)

**Soubory:** Žádné — jde o nastavení účtu/přihlašovacích údajů mimo repozitář.

**Rozhraní:** Vytváří dvě hodnoty, na kterých závisí další tasky: public access token (`pk.…`), který skončí v projektovém `local.properties` jako `MAPBOX_ACCESS_TOKEN`, a secret downloads token (`sk.…`), který skončí v globálním Gradle nastavení uživatele jako `MAPBOX_DOWNLOADS_TOKEN`. Task 2 nemůže bez secret tokenu, který už je na stroji, natáhnout Mapbox závislost.

- [ ] **Krok 1: Založit nebo se přihlásit do Mapbox účtu**

Jdi na https://account.mapbox.com/ a založ účet (free tier) nebo se přihlas, pokud účet už existuje.

- [ ] **Krok 2: Získat public access token**

Jdi na https://account.mapbox.com/access-tokens/. Výchozí public token (začíná `pk.`) se u nových účtů vytváří automaticky — zkopíruj ho, nebo klikni na "Create a token" pro nový s výchozími public scopes (pro základní vykreslování mapy není třeba nic měnit).

- [ ] **Krok 3: Vytvořit secret downloads token**

Na stejné stránce https://account.mapbox.com/access-tokens/ klikni na "Create a token". Pojmenuj ho (např. `CarLauncher downloads`). V sekci "Secret scopes" zaškrtni `DOWNLOADS:READ`. Sekci public scopes nech beze změny (pro tento token není potřeba — používá se jen k autentizaci Gradle při stahování SDK, nikdy za běhu appky). Klikni na "Create token" a **hned si ho zkopíruj** — Mapbox secret token zobrazí jen jednou; pokud se ztratí, musí se vygenerovat nový.

- [ ] **Krok 4: Vložit secret token do globálních Gradle vlastností**

Jde o systémový Gradle credential, ne o projektový soubor (nikdy se necommituje). Na Windows vytvoř nebo uprav `%USERPROFILE%\.gradle\gradle.properties` (pokud složka `.gradle` neexistuje, vytvoř ji) a přidej řádek:

```
MAPBOX_DOWNLOADS_TOKEN=sk.PASTE_YOUR_SECRET_TOKEN_HERE
```

- [ ] **Krok 5: Vložit public token do projektového local.properties**

V kořeni projektu CarLauncher uprav (nebo vytvoř) `local.properties` (už je v gitignore — stejný soubor, kde dřív žil `MAPYCZ_API_KEY`) a přidej:

```
MAPBOX_ACCESS_TOKEN=pk.PASTE_YOUR_PUBLIC_TOKEN_HERE
```

- [ ] **Krok 6: Ověřit, že obě hodnoty jsou na místě (bez vypsání tajných hodnot)**

Spusť z kořene projektu (uprav cestu ke Gradle properties, pokud nejsi na Windows):

```bash
grep -c "MAPBOX_DOWNLOADS_TOKEN" "$USERPROFILE/.gradle/gradle.properties"
grep -c "MAPBOX_ACCESS_TOKEN" local.properties
```

Očekávaný výstup: oba příkazy vypíšou `1`.

Potvrď tomu, kdo plán spouští (člověk nebo kontrolér), že obě hodnoty jsou na místě, než začne Task 2 — jinak Gradle sync v Tasku 2 spadne na chybu 401/403.

---

### Task 2: Přidat Mapbox Maps SDK závislost a Gradle propojení

**Soubory:**
- Upravit: `settings.gradle.kts`
- Upravit: `app/build.gradle.kts`

**Rozhraní:**
- Vstup: `MAPBOX_DOWNLOADS_TOKEN` z `~/.gradle/gradle.properties`, `MAPBOX_ACCESS_TOKEN` z `local.properties` (oboje z Tasku 1).
- Výstup: `BuildConfig.MAPBOX_ACCESS_TOKEN: String` (použije se v Tasku 3 v `CarLauncherApp.kt`). Gradle závislost `com.mapbox.maps:android` se natáhne a je na compile/runtime classpath pro Task 3.

**Než začneš:** ověř, že Task 1 je hotový (oba grep příkazy v Kroku 6 Tasku 1 vypsaly `1`). Pokud ne, ZASTAV a eskaluj — bez toho tento task nemůže uspět.

- [ ] **Krok 1: Přidat Mapbox Maven repozitář s přihlašovacími údaji**

V `settings.gradle.kts` přidej import na začátek souboru a blok `maven { ... }` pro Mapbox dovnitř `dependencyResolutionManagement.repositories`, za existující řádek `maven { url = uri("https://jitpack.io") }`:

```kotlin
import org.gradle.api.authentication.http.BasicAuthentication

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            authentication {
                create<BasicAuthentication>("basic")
            }
            credentials {
                username = "mapbox"
                password = providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN").orNull
                    ?: System.getenv("MAPBOX_DOWNLOADS_TOKEN")
                    ?: ""
            }
        }
    }
}

rootProject.name = "CarLauncher"
include(":app")
```

(Toto je standardní zdokumentovaný Gradle setup snippet od Mapboxu pro jejich privátní Maven repo — pokud se `providers.gradleProperty(...)` v této verzi Gradle nevyřeší, zkontroluj https://docs.mapbox.com/android/maps/guides/install/ pro aktuální doporučený snippet; klíčový požadavek je, aby `password` odpovídal hodnotě `MAPBOX_DOWNLOADS_TOKEN` z `~/.gradle/gradle.properties`.)

- [ ] **Krok 2: Přidat Mapbox access token do BuildConfig, odstranit mrtvé MAPYCZ_API_KEY**

V `app/build.gradle.kts` nahraď (blízko začátku souboru):

```kotlin
val mapyczApiKey: String = localProps.getProperty("MAPYCZ_API_KEY", "MAPY_API_KEY_HERE")
```

za:

```kotlin
val mapboxAccessToken: String = localProps.getProperty("MAPBOX_ACCESS_TOKEN", "MAPBOX_TOKEN_HERE")
```

A nahraď tento řádek uvnitř `defaultConfig { ... }`:

```kotlin
        buildConfigField("String", "MAPYCZ_API_KEY", "\"$mapyczApiKey\"")
```

za:

```kotlin
        buildConfigField("String", "MAPBOX_ACCESS_TOKEN", "\"$mapboxAccessToken\"")
```

(`MAPYCZ_API_KEY` je potvrzeně mrtvý kód — nic v `app/src` neodkazuje na `BuildConfig.MAPYCZ_API_KEY`.)

- [ ] **Krok 3: Přidat Mapbox Maps SDK závislost**

Než se v Tasku 3 odstraní MapLibre (to se stane, až `MapWidget.kt` přestane MapLibre importovat), přidej Mapbox závislost vedle stávajících na konci `app/build.gradle.kts`:

```kotlin
    // MapLibre
    implementation("org.maplibre.gl:android-sdk:11.8.0")

    // PMTiles HTTP server (serves PMTiles file to MapLibre via localhost)
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // Mapbox Maps SDK — Fáze 2, nahrazuje MapLibre (odstraní se v dalším tasku, až MapWidget.kt přejde)
    implementation("com.mapbox.maps:android:11.8.0")
}
```

Než to finálně potvrdíš, zkontroluj na https://github.com/mapbox/mapbox-maps-android/releases skutečně nejnovější stabilní 11.x verzi a pokud existuje novější, použij ji místo `11.8.0` — nenechávej ve finálním commitu neověřený odhad.

- [ ] **Krok 4: Ověřit, že se závislost natáhne a nic dalšího se nerozbilo**

Spusť:
```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath
```
Očekávaný výsledek: exit kód 0, výstup obsahuje řádek pro `com.mapbox.maps:android:<verze>` bez chyby `Could not resolve` / `401` / `403` poblíž. Pokud selže na chybě autentizace, secret token z Tasku 1 chybí nebo je špatně — nepokračuj, znovu ověř Task 1.

Pak spusť:
```bash
./gradlew compileDebugKotlin
```
Očekávaný výsledek: `BUILD SUCCESSFUL` (v Kotlin zdrojácích se zatím nic nezměnilo, takže tohle jen potvrzuje, že úpravy Gradle souborů nic nerozbily).

- [ ] **Krok 5: Commit**

```bash
git add settings.gradle.kts app/build.gradle.kts
git commit -m "$(cat <<'EOF'
build: add Mapbox Maps SDK dependency and access token plumbing

Adds Mapbox's Maven repo (authenticated via MAPBOX_DOWNLOADS_TOKEN in
~/.gradle/gradle.properties) and the com.mapbox.maps:android dependency
alongside the still-present MapLibre dependency. Replaces the dead
MAPYCZ_API_KEY BuildConfig field with MAPBOX_ACCESS_TOKEN. MapWidget.kt
itself is not yet touched — this only proves the new dependency resolves.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Přepsat MapWidget.kt na Mapbox, odstranit MapLibre a mrtvý PMTiles kód

**Soubory:**
- Upravit: `app/src/main/java/com/example/carlauncher/CarLauncherApp.kt`
- Upravit: `app/src/main/java/com/example/carlauncher/ui/map/MapWidget.kt` (úplný přepis vnitřku mapového enginu; pomocné funkce pro canvas kreslení na konci souboru zůstávají koncepčně stejné)
- Upravit: `app/src/main/java/com/example/carlauncher/ui/map/MapViewModel.kt` (odstranit inicializaci PMTiles serveru)
- Upravit: `app/src/main/java/com/example/carlauncher/ui/map/TileConfig.kt` (obsah smazat, nahradit jedinou konstantou Mapbox stylu)
- Upravit: `app/build.gradle.kts` (odstranit MapLibre + nanohttpd závislosti)
- Smazat: `app/src/main/java/com/example/carlauncher/data/map/PmtilesHttpServer.kt`
- Smazat: `app/src/main/assets/style/map_style_dark.json`

**Rozhraní:**
- Vstup: `BuildConfig.MAPBOX_ACCESS_TOKEN` (z Tasku 2), `com.mapbox.maps:android` na classpath (z Tasku 2).
- Výstup: `MapWidget(modifier: Modifier = Modifier, onNavigate: () -> Unit = {}, viewModel: MapViewModel = hiltViewModel())` — stejná veřejná signatura jako dřív, takže `MapNavPanel.kt` (Fáze 1, beze změny) se dál kompiluje proti němu beze změny.

**Než začneš:** Mapbox API volání v tomto tasku jsou nejlepší možný odhad překladu z odstraněného MapLibre kódu, napsaný bez živého přístupu k aktuální Mapbox dokumentaci. Máš k dispozici nástroje WebSearch a WebFetch — použij je. Pokud se jakákoliv třída, metoda nebo konstanta níže při kompilaci nevyřeší, vyhledej aktuální ekvivalent v Mapbox Maps SDK Android (např. "Mapbox Maps SDK Android GeoJsonSource kotlin", "Mapbox Maps SDK Android camera easeTo", "Mapbox Maps SDK Android gestures plugin") a nahlédni na https://docs.mapbox.com/android/maps/ — to se očekává, není to znamení, že je potřeba celý přístup předělat. Celková struktura (co která část kódu zajišťuje, dle tabulky v design spec dokumentu `docs/superpowers/specs/2026-09-14-mapbox-map-engine-design.md` §3) je skutečný požadavek; přesné názvy metod jsou výchozí bod k ověření.

- [ ] **Krok 1: Vyměnit inicializační volání SDK v CarLauncherApp.kt**

Nahraď celý obsah `app/src/main/java/com/example/carlauncher/CarLauncherApp.kt`:

```kotlin
package com.example.carlauncher

import android.app.Application
import com.mapbox.common.MapboxOptions
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CarLauncherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapboxOptions.accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN
    }
}
```

Pokud se `com.mapbox.common.MapboxOptions` nevyřeší, vyhledej "Mapbox Maps SDK Android set access token programmatically Kotlin" — požadavek je, aby public access token z `BuildConfig.MAPBOX_ACCESS_TOKEN` byl na SDK nastaven dřív, než se vytvoří jakýkoliv `MapView`.

- [ ] **Krok 2: Nahradit TileConfig.kt jedinou konstantou stylu**

Nahraď celý obsah `app/src/main/java/com/example/carlauncher/ui/map/TileConfig.kt`:

```kotlin
package com.example.carlauncher.ui.map

import com.mapbox.maps.Style

object TileConfig {
    const val MAP_STYLE_URI = Style.DARK
}
```

Pokud `Style.DARK` nejde použít jako `const val` (kompilátor vyžaduje kompilačně-časovou konstantu; pokud je `Style.DARK` runtime `String` vlastnost místo toho), zahoď `const` a použij `val MAP_STYLE_URI: String = Style.DARK` — obojí je v pořádku, cokoliv se zkompiluje.

- [ ] **Krok 3: Odstranit inicializaci PMTiles serveru z MapViewModel.kt**

V `app/src/main/java/com/example/carlauncher/ui/map/MapViewModel.kt` odstraň kód týkající se PMTiles. Soubor by měl vypadat takto:

```kotlin
package com.example.carlauncher.ui.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.carlauncher.data.location.LocationRepository
import com.example.carlauncher.data.model.Poi
import com.example.carlauncher.data.model.VehicleDisplayLocation
import com.example.carlauncher.data.poi.PoiRepository
import com.example.carlauncher.data.poi.PoiUseCase
import com.example.carlauncher.data.speedlimit.SpeedLimitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    repository: LocationRepository,
    private val poiRepository: PoiRepository,
    speedLimitRepository: SpeedLimitRepository,
) : ViewModel() {

    val vehicleLocation: StateFlow<VehicleDisplayLocation?> = repository.vehicleLocation
    val speedLimit: StateFlow<Int> = speedLimitRepository.speedLimit

    private val _nearbyPois = MutableStateFlow<List<Poi>>(emptyList())
    val nearbyPois: StateFlow<List<Poi>> = _nearbyPois.asStateFlow()

    private val _routePolyline = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val routePolyline: StateFlow<List<Pair<Double, Double>>> = _routePolyline.asStateFlow()

    fun setRoutePolyline(points: List<Pair<Double, Double>>) { _routePolyline.value = points }
    fun clearRoutePolyline() { _routePolyline.value = emptyList() }

    private val poiUseCase = PoiUseCase()

    init {
        viewModelScope.launch {
            repository.vehicleLocation.collect { fix ->
                fix ?: return@collect
                if (poiUseCase.shouldFetch(fix.lat, fix.lng)) {
                    poiUseCase.recordQuery(fix.lat, fix.lng)
                    val lat = fix.lat; val lng = fix.lng
                    viewModelScope.launch(Dispatchers.IO) {
                        val pois = poiRepository.fetchPois(lat, lng)
                        Log.d("MapViewModel", "POI fetch done: ${pois.size} POIs")
                        _nearbyPois.value = pois
                    }
                }
            }
        }
    }
}
```

(Odstraněno: import a použití `File`/`PmtilesHttpServer`, pole `pmtilesServer`, kontrola existence souboru v `init` bloku a teď už prázdný override `onCleared()` spolu s jeho voláním `pmtilesServer?.stop()` a nadbytečným samotným `super.onCleared()`.)

- [ ] **Krok 4: Přepsat MapWidget.kt**

Nahraď celý obsah `app/src/main/java/com/example/carlauncher/ui/map/MapWidget.kt`:

```kotlin
package com.example.carlauncher.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.example.carlauncher.data.model.Poi
import com.example.carlauncher.data.model.PoiType
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.ui.speed.SpeedDisplay
import com.example.carlauncher.ui.theme.CarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.maps.extension.style.expressions.dsl.generated.get
import com.mapbox.maps.extension.style.layers.generated.SymbolLayer
import com.mapbox.maps.extension.style.layers.properties.generated.IconRotationAlignment
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource

private const val MARKER_IMAGE_ID = "vehicle-marker"
private const val VEHICLE_SOURCE_ID = "vehicle-source"
private const val VEHICLE_LAYER_ID = "vehicle-layer"

private const val POI_SOURCE_ID = "poi-source"
private const val POI_LAYER_ID = "poi-layer"

private class MapState {
    var mapboxMap: MapboxMap? = null
    var vehicleSource: GeoJsonSource? = null
    var poiSource: GeoJsonSource? = null
    var destroyed = false
}

@Composable
fun MapWidget(
    modifier: Modifier = Modifier,
    onNavigate: () -> Unit = {},
    viewModel: MapViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    // Use the Activity lifecycle directly — HorizontalPager gives each page its own
    // LocalLifecycleOwner that may not advance to RESUMED while the page is offscreen,
    // which would leave MapView stuck and rendering a black surface.
    val lifecycleOwner = context as ComponentActivity
    val location   by viewModel.vehicleLocation.collectAsStateWithLifecycle()
    val speedLimit by viewModel.speedLimit.collectAsStateWithLifecycle()

    val mapView = remember {
        // textureView: render via TextureView so Compose clip() can round the corners
        // (default SurfaceView is composited separately and ignores clipping)
        MapView(context, MapInitOptions(context = context, textureView = true))
    }
    val mapState = remember { MapState() }
    var styleLoaded by remember { mutableStateOf(false) }
    var isFollowing by remember { mutableStateOf(true) }

    LaunchedEffect(styleLoaded) {
        if (!styleLoaded) return@LaunchedEffect
        val map = mapState.mapboxMap ?: return@LaunchedEffect

        // Race-condition fix: seed source immediately if location already available
        val currentLoc = location
        if (currentLoc != null) {
            mapState.vehicleSource?.feature(
                featureWithBearing(currentLoc.lat, currentLoc.lng, currentLoc.bearingDeg)
            )
            map.setCamera(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(currentLoc.lng, currentLoc.lat))
                    .zoom(17.5)
                    .build()
            )
        } else {
            map.setCamera(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(14.4378, 50.0755))
                    .zoom(17.5)
                    .build()
            )
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .border(width = 1.dp, color = Color(0xFF2A2C35), shape = RoundedCornerShape(24.dp))
    ) {
        AndroidView(
            factory = {
                mapView.apply {
                    mapboxMap.loadStyleUri(TileConfig.MAP_STYLE_URI) { style ->
                        Log.d("MapWidget", "Style loaded OK")
                        mapState.mapboxMap = mapboxMap

                        // POI layer — below vehicle marker
                        PoiType.entries.forEach { type ->
                            style.addImage("poi-${type.name.lowercase()}", createPoiIcon(type))
                        }
                        val poiSource = GeoJsonSource.Builder(POI_SOURCE_ID).build()
                        style.addSource(poiSource)
                        mapState.poiSource = poiSource
                        style.addLayer(
                            SymbolLayer(POI_LAYER_ID, POI_SOURCE_ID)
                                .iconImage(get("icon"))
                                .iconAllowOverlap(true)
                                .iconIgnorePlacement(true)
                                .iconSize(0.8)
                        )
                        val pendingPois = viewModel.nearbyPois.value
                        if (pendingPois.isNotEmpty()) {
                            poiSource.featureCollection(poisToFeatureCollection(pendingPois))
                        }

                        // Vehicle marker layer — on top
                        style.addImage(MARKER_IMAGE_ID, createVehicleMarkerBitmap())
                        val vehicleSource = GeoJsonSource.Builder(VEHICLE_SOURCE_ID).build()
                        style.addSource(vehicleSource)
                        mapState.vehicleSource = vehicleSource

                        // icon-rotate reads "bearing" property from each GeoJSON feature —
                        // rotation updates without touching the layer style (no style re-evaluation)
                        style.addLayer(
                            SymbolLayer(VEHICLE_LAYER_ID, VEHICLE_SOURCE_ID)
                                .iconImage(MARKER_IMAGE_ID)
                                .iconSize(0.5)
                                .iconAllowOverlap(true)
                                .iconIgnorePlacement(true)
                                .iconRotationAlignment(IconRotationAlignment.MAP)
                                .iconRotate(get("bearing"))
                        )

                        gestures.updateSettings {
                            scrollEnabled = true
                            pinchToZoomEnabled = true
                            rotateEnabled = false
                            pitchEnabled = false
                        }
                        // Detect user touch to pause auto-follow — mirrors the old
                        // "reason == REASON_GESTURE" check from MapLibre's camera listener.
                        gestures.addOnMoveListener(object : OnMoveListener {
                            override fun onMoveBegin(detector: MoveGestureDetector) {
                                isFollowing = false
                            }
                            override fun onMove(detector: MoveGestureDetector): Boolean = false
                            override fun onMoveEnd(detector: MoveGestureDetector) {}
                        })

                        styleLoaded = true
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        SpeedDisplay(
            speedKmh = location?.speedKmh ?: 0f,
            speedLimitKmh = speedLimit,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 18.dp, bottom = 18.dp)
        )

        // Navigovat — primary CTA, bottom-right corner of the map
        Button(
            onClick = onNavigate,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CarColors.Go),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 15.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Navigation,
                contentDescription = null,
                tint = Color(0xFF06281B),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Navigovat",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF06281B)
            )
        }
    }

    LaunchedEffect(location) {
        val loc    = location ?: return@LaunchedEffect
        val source = mapState.vehicleSource ?: return@LaunchedEffect

        val (snapLat, snapLng) = RouteSnapHelper.snapToRoute(
            loc.lat, loc.lng, viewModel.routePolyline.value
        )

        source.feature(featureWithBearing(snapLat, snapLng, loc.bearingDeg))

        if (isFollowing) {
            mapState.mapboxMap?.easeTo(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(snapLng, snapLat))
                    .build(),
                MapAnimationOptions.mapAnimationOptions { duration(500) }
            )
        }
    }

    // Resume following 10s after user last touched the map
    LaunchedEffect(isFollowing) {
        if (!isFollowing) {
            delay(10_000)
            isFollowing = true
        }
    }

    LaunchedEffect("poi") {
        viewModel.nearbyPois.collect { pois ->
            mapState.poiSource?.featureCollection(poisToFeatureCollection(pois))
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        val state = lifecycleOwner.lifecycle.currentState
        if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // The MapView instance lives in `remember` inside this composable's own scope, so
            // once MapWidget leaves composition (e.g. MapNavPanel switching to NAV) this exact
            // instance is gone for good regardless of what we do here — a fresh MapView is
            // created via `remember` if/when MapWidget re-enters composition. Always destroy it
            // to release its resources; the `mapState.destroyed` guard prevents a double-destroy.
            if (!mapState.destroyed) { mapState.destroyed = true; mapView.onDestroy() }
        }
    }
}

private fun poisToFeatureCollection(pois: List<Poi>): FeatureCollection {
    val features = pois.map { poi ->
        Feature.fromGeometry(Point.fromLngLat(poi.lng, poi.lat)).also {
            it.addStringProperty("icon", "poi-${poi.type.name.lowercase()}")
            it.addStringProperty("name", poi.name ?: "")
        }
    }
    return FeatureCollection.fromFeatures(features)
}

private fun createPoiIcon(type: PoiType): Bitmap {
    val size = 64
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val c = size / 2f
    val r = c - 4f
    val bgColor = when (type) {
        PoiType.FUEL       -> android.graphics.Color.parseColor("#22C55E")
        PoiType.PARKING    -> android.graphics.Color.parseColor("#3B82F6")
        PoiType.RESTAURANT -> android.graphics.Color.parseColor("#F97316")
        PoiType.HOSPITAL   -> android.graphics.Color.parseColor("#EF4444")
    }
    val label = when (type) {
        PoiType.FUEL       -> "⛽"
        PoiType.PARKING    -> "P"
        PoiType.RESTAURANT -> "☕"
        PoiType.HOSPITAL   -> "+"
    }
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor; style = Paint.Style.FILL }
    if (type == PoiType.PARKING) canvas.drawRoundRect(RectF(4f, 4f, size - 4f, size - 4f), 10f, 10f, bgPaint)
    else canvas.drawCircle(c, c, r, bgPaint)
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = if (type == PoiType.PARKING || type == PoiType.HOSPITAL) 30f else 22f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    canvas.drawText(label, c, c - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
    return bitmap
}

// Encode bearing as GeoJSON feature property so icon-rotate is data-driven.
// Updating only the source data never triggers a style re-evaluation → no flicker.
private fun featureWithBearing(lat: Double, lng: Double, bearing: Float): Feature =
    Feature.fromGeometry(Point.fromLngLat(lng, lat)).also {
        it.addNumberProperty("bearing", bearing)
    }

private fun createVehicleMarkerBitmap(): Bitmap {
    // 128px ≈ 46dp at ~2.75x density — big enough to spot at a glance while driving
    val size = 128
    val c = size / 2f
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Translucent halo
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#40FFFFFF")
        style = Paint.Style.FILL
    }.also { canvas.drawCircle(c, c, 60f, it) }

    // Green disc
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#22C55E")
        style = Paint.Style.FILL
    }.also { canvas.drawCircle(c, c, 40f, it) }

    // White direction wedge pointing north (iconRotate aligns it to bearing)
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }.also {
        val path = android.graphics.Path().apply {
            moveTo(c, c - 28f)        // tip
            lineTo(c - 16f, c + 14f)  // bottom left
            lineTo(c, c + 4f)         // notch
            lineTo(c + 16f, c + 14f)  // bottom right
            close()
        }
        canvas.drawPath(path, it)
    }

    return bitmap
}
```

Nápadné záměrné rozdíly oproti starému MapLibre kódu (nejsou to bugy — než diff vyhodnotíš jako chybu, ověř, že sedí):
- Žádné `map.uiSettings.isLogoEnabled = false` / `isAttributionEnabled = false` — záměrně vynecháno kvůli Mapbox ToS (viz Globální omezení).
- `MapState.source` přejmenováno na `MapState.vehicleSource`, aby bylo jasné, že teď vedle něj existuje i `poiSource` — všechny odkazy uprav podle toho (výše v kódu už hotovo; jen znovu nezaváděj staré jméno).
- POI GeoJSON se sestavuje jako typovaná `FeatureCollection` přes modelové třídy `com.mapbox.geojson` místo ručního skládání JSON stringu (stará `poisToGeoJson` vracela syrový JSON `String`). Vygenerovaný setter `GeoJsonSource.featureCollection(FeatureCollection)` v Mapboxu bere typovaný objekt přímo, takže skládání stringu už není potřeba. Pokud tento setter neexistuje, vyhledej v Mapbox dokumentaci správnou metodu pro update dat `GeoJsonSource` — starý string-based setter `data(String)` (pokud existuje) je přijatelná záloha, pokud znovu postavíš stejný tvar JSON stringu jako smazaná `poisToGeoJson`/`featureWithBearing`, ale dej přednost typovanému API, pokud je dostupné.
- Zapojeny jsou jen lifecycle volání `onStart`/`onStop`/`onDestroy` (starý kód volal i `onResume`/`onPause`). Pokud `MapView.onResume()`/`onPause()` v této verzi Mapbox SDK existují, přidej je zpátky stejným způsobem (`ON_RESUME -> mapView.onResume()`, `ON_PAUSE -> mapView.onPause()`) kvůli paritě — zkontroluj dostupné veřejné metody třídy `MapView`.

- [ ] **Krok 5: Odstranit MapLibre a nanohttpd Gradle závislosti**

V `app/build.gradle.kts` smaž tyto dva řádky (teď nepoužité — nic v `app/src` po Krocích 1–4 neimportuje `org.maplibre.*` ani `org.nanohttpd.*`):

```kotlin
    // MapLibre
    implementation("org.maplibre.gl:android-sdk:11.8.0")

    // PMTiles HTTP server (serves PMTiles file to MapLibre via localhost)
    implementation("org.nanohttpd:nanohttpd:2.3.1")
```

- [ ] **Krok 6: Smazat mrtvé soubory**

```bash
git rm app/src/main/java/com/example/carlauncher/data/map/PmtilesHttpServer.kt
git rm app/src/main/assets/style/map_style_dark.json
```

- [ ] **Krok 7: Ověřit kompilaci a že nezůstaly žádné odkazy na MapLibre**

Spusť:
```bash
./gradlew compileDebugKotlin
```
Očekávaný výsledek: `BUILD SUCCESSFUL`. Případné nevyřešené Mapbox API volání oprav vyhledáním v aktuální Mapbox dokumentaci dle instrukcí výše — bez ověření dál nehádej.

Pak potvrď, že v celém app modulu nezůstal žádný MapLibre import:
```bash
grep -ri "maplibre" app/src -r
```
Očekávaný výsledek: žádný výstup (prázdno).

- [ ] **Krok 8: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/CarLauncherApp.kt
git add app/src/main/java/com/example/carlauncher/ui/map/MapWidget.kt
git add app/src/main/java/com/example/carlauncher/ui/map/MapViewModel.kt
git add app/src/main/java/com/example/carlauncher/ui/map/TileConfig.kt
git add app/build.gradle.kts
git commit -m "$(cat <<'EOF'
feat(map): replace MapLibre with Mapbox Maps SDK in MapWidget

Rewrites MapWidget's map engine against Mapbox Maps SDK (online
Style.DARK) instead of MapLibre over offline PMTiles. Vehicle marker
rotation, POI overlay, camera auto-follow-with-touch-pause, rounded
corners, and the Navigovat CTA all carry over unchanged in behavior.
Removes the now-dead PmtilesHttpServer, its style JSON asset, and the
MapLibre/nanohttpd dependencies. Mapbox's logo/attribution stay visible
per their ToS (MapLibre's were intentionally hidden; Mapbox's aren't).

The Navigace panel, MediaListenerService, NavRepository, and the Fáze 1
MapNavPanel switcher are all untouched — MapWidget's public signature
is unchanged so it keeps compiling against MapNavPanel as-is.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Manuální QA na zařízení

**Soubory:** Žádné — jen ověření.

**Rozhraní:** Vstupem je plně migrovaná appka z Tasku 3 (nainstalovaná jako debug APK).

- [ ] **Krok 1: Build a instalace**

```bash
./gradlew assembleDebug
```
Očekávaný výsledek: `BUILD SUCCESSFUL`, vznikne `app/build/outputs/apk/debug/app-debug.apk`.

Nainstaluj na fyzický Lenovo tablet nebo emulátor API 34 x86_64 (ne API 35+):
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r "app\build\outputs\apk\debug\app-debug.apk"
```
Očekávaný výsledek: `Success`.

- [ ] **Krok 2: Projít QA checklist ze spec dokumentu**

Spusť appku (defaultně naskočí panel Mapa dle Fáze 1) a ověř každý z těchto bodů, u každého kroku pořiď screenshot jako důkaz (`adb exec-out screencap -p > shot.png`, pak ho zobraz):

1. Mapa se načte a zobrazí tmavý Mapbox styl — ne černá obrazovka, ne chyba.
2. Marker vozidla (zelený disk s bílým směrovým klínem) je vidět a otáčí se podle hlásené orientace zařízení (pokud testuješ bez reálného GPS pohybu, ověř aspoň že se marker vůbec vykresluje — plné ověření rotace potřebuje skutečný pohyb nebo simulovanou trasu).
3. Pokud jsou v okolí nějaké POI, jejich ikony (benzínka/parkoviště/restaurace/nemocnice) se na mapě zobrazují.
4. Kamera sleduje marker vozidla podle příchozích aktualizací polohy (auto-follow).
5. Dotek/posun mapy zastaví auto-follow — kamera zůstane tam, kde jsi ji nechal — a zhruba 10 sekund po posledním doteku se sledování obnoví.
6. Panel mapy má viditelně zaoblené rohy (ne hranaté).
7. Zelené tlačítko "Navigovat" vpravo dole přepne panel na pohled Navigace (nezměněné chování z Fáze 1) — potvrzuje, že `onNavigate` je stále správně zapojené.
8. Dlouhý stisk panelu (přepínač `MapNavPanel` z Fáze 1) pořád otevře výběr "Mapa"/"Navigace" a přepíná správně oběma směry.
9. Mapbox logo a attribution jsou někde na mapě viditelné (malé, v rohu) — potvrzuje, že Krok 4/5 Tasku 3 je nevypnul.

Očekávaný výsledek: všech 9 bodů projde. Pokud selže bod 1 (černá obrazovka nebo chyba načtení), nejdřív zkontroluj `adb logcat` na chybu načtení Mapbox stylu — pravděpodobně neplatný/chybějící access token (znovu ověř Task 1/Task 2) nebo špatná konstanta style URI.

- [ ] **Krok 3: Commit není potřeba**

Tento task je jen ověřovací — pokud všechny kontroly projdou, není co commitovat. Pokud nějaká kontrola selže a vyžaduje opravu kódu, oprav ji, znovu spusť Kroky 1–2 a opravu commitni se zprávou popisující, co bylo špatně a proč.
