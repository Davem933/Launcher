# Mapbox Turn-by-Turn Navigation Implementation Plan

> **Pro agentní pracovníky:** POVINNÝ SUB-SKILL: použij superpowers:subagent-driven-development (doporučeno) nebo superpowers:executing-plans k provedení tohoto plánu task po tasku. Kroky používají checkbox (`- [ ]`) syntaxi pro sledování postupu.

**Cíl:** Přidat turn-by-turn navigaci (vyhledání cíle, trasa, pokyny, hlas) přes Mapbox Navigation SDK + Search SDK jako overlay nad `MapWidget`, nezávisle na dnešním notification-based `NavAreaWidget`/panelu Navigace.

**Architektura:** `DestinationSearchBar` (Mapbox Search Box SDK) → `MapboxNavigation.requestRoutes(...)` → `MapboxRouteLineApi/View` vykreslí trasu → `startTripSession()` spustí aktivní guidance s Mapboxími hotovými UI komponentami (`MapboxManeuverView`, `MapboxTripProgressView`) a `MapboxVoiceInstructionsPlayer` pro hlas. `MapboxNavigation` instance žije mimo `MapWidget`'s `remember` scope (přes `MapboxNavigationApp`/`requireMapboxNavigation`), aby přežila přepnutí panelu Mapa↔Navigace (`MapWidget` se při tom kompletně disposuje — oprava leaku z Fáze 2).

**Tech Stack:** Kotlin, Jetpack Compose, `com.mapbox.navigationcore:navigation` + `:ui-components` (Navigation SDK v3), `com.mapbox.search:mapbox-search-android` + `:mapbox-search-android-ui` (Search SDK v2).

## Globální omezení

- **Verze SDK — vzor párování, ne jistota:** Mapbox páruje SDK podle **koncového minor.patch čísla**, ne podle hlavní verze — Maps `11.30.1` (co už máme) páruje s Navigation Core `3.30.x` a Search `2.30.1` (ověřeno reálným GitHub release tagem `v2.30.1` v `mapbox/mapbox-search-android`). Tento plán píše kód proti `3.30.1`/`2.30.1` jako nejlepší odhad — **ověř přesné existující verze na GitHub releases stránkách (`mapbox/mapbox-navigation-android` a `mapbox/mapbox-search-android`) před finálním zafixováním**, stejně jako jsme to dělali včera pro Maps SDK.
- **NDK varianta musí sedět napříč všemi Mapbox SDK v appce** — dnes používáme `com.mapbox.maps:android` (BEZ `-ndk27` přípony). Nové závislosti (Navigation, Search) proto taky BEZ `-ndk27` přípony (`com.mapbox.navigationcore:navigation`, ne `:navigation-ndk27`) — míchání variant v jedné appce riskuje konflikt nativních knihoven.
- **Žádný nový Mapbox repo/token setup** — stejný Maven repo v `settings.gradle.kts` (autentizovaný přes `MAPBOX_DOWNLOADS_TOKEN`) funguje pro všechny Mapbox produkty; stejný `MAPBOX_ACCESS_TOKEN` (nastavený v `CarLauncherApp.onCreate()`) funguje pro Maps, Navigation i Search SDK. Pokud by stažení Search SDK selhalo na autentizaci, ověřit token, ne přidávat nový repo blok.
- **Nová oprávnění v `AndroidManifest.xml`:** `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS` (Android 13+) — Navigation SDK běží trip session jako foreground service.
- **Zachovat `Style.STANDARD`** (Fáze 2 3D styl) — nepřecházet na `NavigationStyles.NAVIGATION_DAY_STYLE`/`NIGHT_STYLE` (klasické 2D styly bez 3D budov). Mapboxí oficiální příklady používají `NavigationStyles` + `routeLineBelowLayerId("road-label-navigation")`, což se Standardem nefunguje (statická layer ID ze stylu se neaplikují) — **potvrzeno v Mapbox dokumentaci:** route-line komponenta sama detekuje Standard a vloží se do MIDDLE slotu automaticky, takže `.routeLineBelowLayerId(...)` se u nás prostě vynechá (viz Task 3 Krok 1).
- **`MapWidget`'s vlastní kamerový/puck kód (Fáze 2) se nesmí trvale nahradit** — jen dočasně vynechat/přepnout, dokud běží trip session; po `stopTripSession()` se vrací dnešní chování (`isFollowing`, `AppLocationProvider` krmený z `VehicleDisplayLocation`).
- **Nic v `NavAreaWidget`, `NavWidget`, `NavRepository`, `MediaListenerService`, `MapNavPanel` se nemění.**
- **Bez test frameworku** (stejná konvence jako Fáze 1/2) — ověření: compile + manuální QA na fyzickém zařízení.
- **API povrch v tomto plánu je z reálných, aktuálních Mapbox příkladů** (`mapbox/mapbox-navigation-android-examples`, soubor `app/src/main/java/com/mapbox/navigation/examples/standalone/compose/JetpackComposeActivity.kt` a `.../fetchroute/FetchARouteActivity.kt`; `mapbox/mapbox-search-android`), ne z dohadu — ale přesto ověřuj přes WebSearch/WebFetch/gh při jakékoli nesrovnalosti, stejně jako včera.

---

### Task 1: Gradle závislosti, oprávnění, NavigationOptions bootstrap

**Soubory:**
- Upravit: `app/build.gradle.kts`
- Upravit: `app/src/main/AndroidManifest.xml`
- Upravit: `app/src/main/java/com/example/carlauncher/CarLauncherApp.kt`

**Rozhraní:**
- Vstup: existující Mapbox Maven repo v `settings.gradle.kts` (Fáze 2), `BuildConfig.MAPBOX_ACCESS_TOKEN`.
- Výstup: `com.mapbox.navigationcore:navigation`, `:ui-components`, `com.mapbox.search:mapbox-search-android`, `:mapbox-search-android-ui` na classpath; `MapboxNavigationApp` nastavený a připravený pro `requireMapboxNavigation()` volání v pozdějších tascích.

- [ ] **Krok 1: Zjistit přesné aktuální verze**

Spusť (nebo použij WebFetch/gh):
```bash
gh api repos/mapbox/mapbox-navigation-android/releases --jq '.[0:3][].tag_name'
gh api repos/mapbox/mapbox-search-android/releases --jq '.[0:3][].tag_name'
```
Vyber nejnovější stabilní (ne `-rc`/`-beta`) verze. Očekávej něco blízkého `3.30.x` pro Navigation a `2.30.x` pro Search (párování s naší Maps SDK `11.30.1`) — pokud se čísla výrazně liší od tohoto očekávání, něco je špatně pochopené a stojí za to ověřit párovací pravidlo znovu na https://docs.mapbox.com/android/navigation/guides/install/ než pokračuješ.

- [ ] **Krok 2: Přidat závislosti do `app/build.gradle.kts`**

Za tento blok (z Fáze 2):
```kotlin
    // Mapbox Maps SDK — Fáze 2, nahrazuje MapLibre
    implementation("com.mapbox.maps:android:11.30.1")
```
přidej (nahraď `X.Y.Z`/`A.B.C` verzemi zjištěnými v Kroku 1):
```kotlin

    // Mapbox Navigation SDK — Fáze 3, turn-by-turn navigace
    implementation("com.mapbox.navigationcore:navigation:X.Y.Z")
    implementation("com.mapbox.navigationcore:ui-components:X.Y.Z")

    // Mapbox Search SDK — Fáze 3, vyhledávání cíle
    implementation("com.mapbox.search:mapbox-search-android:A.B.C")
    implementation("com.mapbox.search:mapbox-search-android-ui:A.B.C")
```

- [ ] **Krok 3: Přidat oprávnění do `AndroidManifest.xml`**

Za `android.permission.ACCESS_COARSE_LOCATION` řádek přidej:
```xml
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```
(Pokud `FOREGROUND_SERVICE` už v manifestu existuje — zkontroluj, možná je tam z jiného důvodu — nepřidávej duplicitně, jen doplň chybějící dvě.)

- [ ] **Krok 4: Inicializovat `MapboxNavigationApp` v `CarLauncherApp.kt`**

Nahraď celý obsah `app/src/main/java/com/example/carlauncher/CarLauncherApp.kt`:

```kotlin
package com.example.carlauncher

import android.app.Application
import com.mapbox.common.MapboxOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CarLauncherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapboxOptions.accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN
        MapboxNavigationApp.setup(
            NavigationOptions.Builder(this).build()
        )
    }
}
```

Pokud `MapboxNavigationApp`/`NavigationOptions` import cesty neodpovídají (SDK mezi verzemi mění balíčky), ověř přes WebSearch "MapboxNavigationApp setup NavigationOptions kotlin" nebo přímo v `mapbox/mapbox-navigation-android-examples` repu (`app/src/main/java/com/mapbox/navigation/examples/standalone/fetchroute/FetchARouteActivity.kt`, funkce `initNavigation()`).

- [ ] **Krok 5: Ověřit, že se závislosti natáhnou a appka se zkompiluje**

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath
```
Očekávaný výsledek: `com.mapbox.navigationcore:navigation`, `:ui-components`, `com.mapbox.search:mapbox-search-android`, `:mapbox-search-android-ui` se objeví vyřešené, žádná `401`/`Could not resolve` chyba. Pokud selže na autentizaci, ověř `MAPBOX_DOWNLOADS_TOKEN` v `~/.gradle/gradle.properties` (měl by tam už být z Fáze 2) — nepřidávej nový repo blok.

```bash
./gradlew compileDebugKotlin
```
Očekávaný výsledek: `BUILD SUCCESSFUL`.

- [ ] **Krok 6: Commit**

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/com/example/carlauncher/CarLauncherApp.kt
git commit -m "$(cat <<'EOF'
build: add Mapbox Navigation + Search SDK dependencies for Fáze 3

Adds com.mapbox.navigationcore:navigation/:ui-components and
com.mapbox.search:mapbox-search-android/:mapbox-search-android-ui,
paired by trailing minor.patch with the existing Maps SDK 11.30.1 (no
-ndk27 variant, matching what Maps SDK already uses). Same Maven repo
and MAPBOX_ACCESS_TOKEN as Fáze 2 — no new credentials. Initializes
MapboxNavigationApp in CarLauncherApp.onCreate(). Adds
FOREGROUND_SERVICE/FOREGROUND_SERVICE_LOCATION/POST_NOTIFICATIONS
permissions the Navigation SDK's trip session foreground service needs.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: DestinationSearchBar — vyhledávání cíle přes Mapbox Search Box SDK

**Soubory:**
- Vytvořit: `app/src/main/java/com/example/carlauncher/ui/map/DestinationSearchBar.kt`
- Upravit: `app/src/main/java/com/example/carlauncher/ui/map/MapWidget.kt` (přidat overlay do `Box`)

**Rozhraní:**
- Vstup: `viewModel.vehicleLocation` (pro location bias) — už dostupné v `MapWidget`.
- Výstup: `DestinationSearchBar(currentLocation: VehicleDisplayLocation?, onDestinationSelected: (Point, String) -> Unit, modifier: Modifier = Modifier)` — callback s vybraným cílem (souřadnice + název), který Task 3 použije k vyžádání trasy.

- [ ] **Krok 1: Prostudovat referenční příklad**

Než začneš psát, stáhni si a přečti referenční příklad pro Search Box API v Kotlinu:
```bash
gh api "repos/mapbox/mapbox-search-android/contents/MapboxSearch/sample/src/main/java/com/mapbox/search/sample" --jq '.[].name'
```
Najdi soubor s "SearchBox" nebo "Category" v názvu (Kotlin, ne Java) a stáhni ho:
```bash
gh api "repos/mapbox/mapbox-search-android/contents/MapboxSearch/sample/src/main/java/com/mapbox/search/sample/<NAZEV_SOUBORU>" --jq '.content' | base64 -d
```
Tohle je tvůj hlavní zdroj pravdy pro `SearchEngine`/`SearchEngineSettings`/callback rozhraní — kód níže je odhad, uprav podle toho, co v příkladu skutečně najdeš.

- [ ] **Krok 2: Napsat `DestinationSearchBar.kt`**

Vytvoř `app/src/main/java/com/example/carlauncher/ui/map/DestinationSearchBar.kt` s touto STRUKTUROU (přesné Search SDK volání dopň/uprav podle Kroku 1 — toto je návrh, ne finální ověřený kód):

```kotlin
package com.example.carlauncher.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.model.VehicleDisplayLocation
import com.example.carlauncher.ui.theme.CarColors
import com.mapbox.geojson.Point
import com.mapbox.search.ApiType
import com.mapbox.search.ResponseInfo
import com.mapbox.search.SearchEngine
import com.mapbox.search.SearchEngineSettings
import com.mapbox.search.SearchOptions
import com.mapbox.search.SearchSuggestionsCallback
import com.mapbox.search.result.SearchResult
import com.mapbox.search.result.SearchSuggestion

@Composable
fun DestinationSearchBar(
    currentLocation: VehicleDisplayLocation?,
    onDestinationSelected: (Point, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val searchEngine = remember {
        SearchEngine.createSearchEngine(
            apiType = ApiType.SEARCH_BOX,
            settings = SearchEngineSettings(),
        )
    }
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<SearchSuggestion>>(emptyList()) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(CarColors.Surface)
                .border(1.dp, CarColors.BorderSoft, RoundedCornerShape(28.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = CarColors.Text3,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 10.dp))
            BasicTextField(
                value = query,
                onValueChange = { newQuery ->
                    query = newQuery
                    if (newQuery.length >= 2) {
                        searchEngine.search(
                            query = newQuery,
                            options = SearchOptions(
                                proximity = currentLocation?.let { Point.fromLngLat(it.lng, it.lat) },
                            ),
                            callback = object : SearchSuggestionsCallback {
                                override fun onSuggestions(
                                    suggestions_: List<SearchSuggestion>,
                                    responseInfo: ResponseInfo,
                                ) {
                                    suggestions = suggestions_
                                }

                                override fun onError(e: Exception) {
                                    suggestions = emptyList()
                                }
                            },
                        )
                    } else {
                        suggestions = emptyList()
                    }
                },
                textStyle = TextStyle(color = CarColors.Text, fontSize = 16.sp),
                cursorBrush = SolidColor(CarColors.Text),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text("Hledat", color = CarColors.Text3, fontSize = 16.sp)
                    }
                    innerTextField()
                },
            )
        }

        if (suggestions.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CarColors.Surface),
            ) {
                items(suggestions) { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Text(
                            text = suggestion.name,
                            color = CarColors.Text,
                            fontSize = 15.sp,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).let { it }
                        )
                    }
                }
            }
        }
    }
}
```

**Poznámka implementátorovi:** `suggestion.name` řádek výše nemá `onClick` — tap na `Row` musí zavolat `searchEngine.select(suggestion, callback)` (SDK dvoukrokový flow: suggestions → select → SearchResult s finálními souřadnicemi), a teprve v tom callbacku `onResult(result: SearchResult, ...)` zavolat `onDestinationSelected(result.coordinate, result.name)`. Přesný název `select`/callback rozhraní ověř v Kroku 1 referenčním souboru — výše uvedený kód je neúplný záměrně tam, kde závisí na přesném SDK rozhraní, které jsi ještě needčetl.

- [ ] **Krok 3: Zapojit do `MapWidget.kt`**

V `Box` v `MapWidget.kt` (stejná úroveň jako `SpeedDisplay`/tlačítko "Navigovat") přidej:

```kotlin
DestinationSearchBar(
    currentLocation = location,
    onDestinationSelected = { point, name ->
        // Task 3 doplní: vyžádání trasy k `point`
    },
    modifier = Modifier
        .align(Alignment.TopCenter)
        .fillMaxWidth()
        .padding(16.dp)
)
```

- [ ] **Krok 4: Ověřit kompilaci a chování na zařízení**

```bash
./gradlew compileDebugKotlin
./gradlew assembleDebug
```
Nainstaluj a ověř na fyzickém tabletu: search bar je vidět nahoře přes mapu, psaní textu (min. 2 znaky) vyvolá reálné návrhy z Mapbox Search, tap na návrh volá `onDestinationSelected` (dočasně jen zaloguj přes `Log.d`, dokud Task 3 nezapojí trasu).

- [ ] **Krok 5: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/ui/map/DestinationSearchBar.kt app/src/main/java/com/example/carlauncher/ui/map/MapWidget.kt
git commit -m "$(cat <<'EOF'
feat(map): add DestinationSearchBar with Mapbox Search Box integration

Always-visible search overlay on the map panel, matching the reference
app's search-bar-over-map convention. Wired to Mapbox Search Box SDK
for live suggestions biased to current location. onDestinationSelected
callback is wired but not yet consumed — Task 3 requests the route.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Vyžádání trasy a vykreslení na mapě

**Soubory:**
- Upravit: `app/src/main/java/com/example/carlauncher/ui/map/MapWidget.kt`

**Rozhraní:**
- Vstup: `DestinationSearchBar`'s `onDestinationSelected: (Point, String) -> Unit` (Task 2).
- Výstup: `MapWidget`'s interní `MapboxNavigation` instance a aktivní `NavigationRoute` list, které Task 4 použije pro `startTripSession()`.

- [ ] **Krok 1: Prostudovat referenční kód**

Přečti si `mapbox/mapbox-navigation-android-examples`, soubor `app/src/main/java/com/mapbox/navigation/examples/standalone/compose/JetpackComposeActivity.kt` (stáhni přes `gh api "repos/mapbox/mapbox-navigation-android-examples/contents/app/src/main/java/com/mapbox/navigation/examples/standalone/compose/JetpackComposeActivity.kt" --jq '.content' | base64 -d`) — konkrétně `addWaypoint()`, `setRouteAndStartNavigation()`, `routesObserver`, a inicializaci `routeLineApi`/`routeLineView` v `onCreate()`.

**Standard styl (potvrzeno, ne jen odhad):** příklad používá `NavigationStyles.NAVIGATION_DAY_STYLE` + `MapboxRouteLineViewOptions.routeLineBelowLayerId("road-label-navigation")` — my zůstáváme na `Style.STANDARD` (Fáze 2), kde tohle **nefunguje** (statické layer ID ze stylu se u Standardu neaplikují, jen za-běhu-přidané). Dobrá zpráva: podle Mapbox dokumentace (`docs.mapbox.com/android/navigation/guides/ui-components/route-line/`) route-line komponenta **sama detekuje Standard styl a vloží se do MIDDLE slotu automaticky** — takže `.routeLineBelowLayerId(...)` v `MapboxRouteLineViewOptions.Builder(context)` prostě **vynech úplně**, nic dalšího konfigurovat netřeba. (Novější verze SDK mají i explicitní `slotName` property, výchozí `RouteLayerConstants.DEFAULT_ROUTE_LINE_SLOT` — jen pro info, pro nás není potřeba.)

- [ ] **Krok 2: Přidat route line a routes observer do `MapWidget.kt`**

V `MapWidget`'s composable, přidej `MapboxNavigation` instanci a route-line state:

```kotlin
val mapboxNavigation by requireMapboxNavigation()
val routeLineApi = remember { MapboxRouteLineApi(MapboxRouteLineApiOptions.Builder().build()) }
val routeLineView = remember {
    MapboxRouteLineView(MapboxRouteLineViewOptions.Builder(context).build())
}
```

(`requireMapboxNavigation()` je Kotlin property delegate z `com.mapbox.navigation.core.lifecycle` — ověř přesný import a signaturu v referenčním souboru z Kroku 1, `MapWidget` na rozdíl od příkladu není `Activity`, takže roli `onInitialize`/`onResumedObserver` parametrů ověř zvlášť pro Compose kontext.)

V style-load callbacku (`mapboxMap.loadStyle(...) { style -> ... }`), za existující traffic/parking kód přidej:
```kotlin
routeLineView.initializeLayers(style)
```

Registruj `RoutesObserver` (kopíruj logiku z referenčního souboru — `routeLineApi.setNavigationRoutes(...)` na nové trasy, `routeLineApi.clearRouteLine { ... }` na prázdné), a v `DestinationSearchBar`'s `onDestinationSelected` callbacku zavolej:

```kotlin
onDestinationSelected = { point, name ->
    val currentLoc = location ?: return@DestinationSearchBar
    routeRequestError = null
    mapboxNavigation.requestRoutes(
        RouteOptions.builder()
            .applyDefaultNavigationOptions()
            .applyLanguageAndVoiceUnitOptions(context)
            .coordinatesList(listOf(Point.fromLngLat(currentLoc.lng, currentLoc.lat), point))
            .build(),
        object : NavigationRouterCallback {
            override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {}
            override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                Log.e("MapWidget", "Route request failed: $reasons")
                routeRequestError = "Trasu se nepodařilo najít"
            }
            override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
                mapboxNavigation.setNavigationRoutes(routes)
            }
        }
    )
}
```

`routeRequestError` je `var routeRequestError by remember { mutableStateOf<String?>(null) }` v `MapWidget` — zobraz ho jako krátký `Text` pod `DestinationSearchBar` (červený/CarColors error tón), zmiz po dalším úspěšném hledání nebo po pár vteřinách (`LaunchedEffect(routeRequestError) { delay(4000); routeRequestError = null }`). Spec §5 vyžaduje viditelnou chybu v search UI, ne jen log.

- [ ] **Krok 3: Ověřit na zařízení**

```bash
./gradlew assembleDebug
```
Nainstaluj, vyhledej reálné místo v okolí, vyber ho ze seznamu, ověř že se na mapě vykreslí trasa (barevná čára od aktuální polohy k cíli).

- [ ] **Krok 4: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/ui/map/MapWidget.kt
git commit -m "$(cat <<'EOF'
feat(map): request and render route on destination selection

Wires MapboxNavigation.requestRoutes()/setNavigationRoutes() to
DestinationSearchBar's selection callback, drawing the route via
MapboxRouteLineApi/View on top of the existing Style.STANDARD map.
Trip session / active guidance is Task 4.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Aktivní navigace — trip session, kamera, banner, trip progress, puck

**Soubory:**
- Upravit: `app/src/main/java/com/example/carlauncher/ui/map/MapWidget.kt`

**Rozhraní:**
- Vstup: `mapboxNavigation`, `routeLineApi`/`routeLineView` (Task 3).
- Výstup: funkční turn-by-turn zážitek — `startTripSession()`/`stopTripSession()`, `MapboxManeuverView`/`MapboxTripProgressView` overlaye, přepnutí pucku a kamery při aktivní navigaci.

- [ ] **Krok 1: Prostudovat referenční kód (znovu, detailně)**

Vrať se k `JetpackComposeActivity.kt` z Tasku 3 Kroku 1 — tentokrát pozorně projdi: `locationObserver` (`LocationMatcherResult.enhancedLocation` → `navigationLocationProvider.changePosition(...)`), `routeProgressObserver` (`viewportDataSource.onRouteProgressChanged`, `maneuverApi.getManeuvers(...)`, `tripProgressApi.getTripProgress(...)`), inicializaci `NavigationCamera`/`MapboxNavigationViewportDataSource`/`NavigationBasicGesturesHandler`, a Compose `AndroidView` wrappery pro `MapboxManeuverView`/`MapboxTripProgressView` (`update = { it.renderManeuvers(...) }` / `update = { it.render(...) }`).

- [ ] **Krok 2: Přepnutí kamery a pucku podle stavu navigace**

Přidej stav `var isNavigating by remember { mutableStateOf(false) }`. Inicializuj `NavigationCamera`/`MapboxNavigationViewportDataSource` po vytvoření mapy (stejné místo jako `this.location.setLocationProvider(...)` z Fáze 2), ale **nepřipoj** `NavigationBasicGesturesHandler`/nezačni sledovat, dokud `isNavigating == false` — mimo navigaci se používá dnešní `easeTo`/`AppLocationProvider` logika beze změny (podmiň existující `LaunchedEffect(location)` blok s `easeTo` na `!isNavigating`).

Když `isNavigating` přejde na `true` (v `onRoutesReady`/po `startTripSession()`):
```kotlin
this.location.setLocationProvider(navigationLocationProvider) // Mapboxí vlastní, ne AppLocationProvider
navigationCamera.requestNavigationCameraToFollowing()
```
Když se navigace ukončí (`isNavigating = false`):
```kotlin
this.location.setLocationProvider(locationProvider) // zpátky náš AppLocationProvider z Fáze 2
```

(`navigationLocationProvider` je `com.mapbox.navigation.ui.maps.location.NavigationLocationProvider` — hotová Mapboxí implementace `LocationProvider`, feeduje se sama přes `LocationObserver.onNewLocationMatcherResult`, žádný vlastní most na `AppLocationProvider` není potřeba.)

- [ ] **Krok 3: Maneuver banner + trip progress overlay**

Přidej do `MapWidget`'s `Box` (nad mapou, pod/vedle `DestinationSearchBar`):

```kotlin
if (isNavigating) {
    AndroidView(
        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp),
        factory = { MapboxManeuverView(it) },
        update = { view -> currentManeuvers?.let { view.renderManeuvers(ExpectedFactory.createValue(it)) } }
    )
    AndroidView(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(64.dp),
        factory = { MapboxTripProgressView(it) },
        update = { view -> currentTripProgress?.let { view.render(it) } }
    )
    // Ukončit — stejný vzor jako NavWidget's tlačítko
    IconButton(onClick = {
        mapboxNavigation.stopTripSession()
        mapboxNavigation.setNavigationRoutes(emptyList())
        isNavigating = false
    }) { Icon(Icons.Default.Close, contentDescription = "Ukončit navigaci") }
}
```

`currentManeuvers`/`currentTripProgress` jsou `remember { mutableStateOf(...) }` aktualizované v `RouteProgressObserver` (registruj ho spolu s existujícím `RoutesObserver` z Tasku 3), přesně jako `maneuvers.value = maneuverApi.getManeuvers(routeProgress)...` v referenčním souboru.

**Kdy se `isNavigating` nastaví na `true`:** po úspěšném `onRoutesReady` (Task 3) rovnou zavolej `mapboxNavigation.startTripSession()` a nastav `isNavigating = true` — tenhle plán nemá samostatný "náhled trasy s tlačítkem Start" krok, navigace začíná hned po výběru cíle (odpovídá spec §3 flow).

- [ ] **Krok 4: Ověřit na zařízení — celý flow**

```bash
./gradlew assembleDebug
```
Nainstaluj, projdi: vyhledání → výběr cíle → trasa se vykreslí → navigace se rovnou spustí → banner s pokynem a trip progress se zobrazí → kamera sleduje trasu → tlačítko Ukončit vrátí volný pohled s search barem.

**Přežití přepnutí panelu (spec §5):** během aktivní navigace přepni na panel Navigace (dlouhý stisk → "Navigace") a zpět na Mapu. Ověř, že `MapboxNavigation`/trip session neshořela — pokud `mapboxNavigation` proměnná žije v `MapWidget`'s vlastním `remember` (ne přes `requireMapboxNavigation()` navázané na Activity), tohle selže, protože `MapWidget` se při přepnutí kompletně disposuje (Fáze 2 leak fix). Pokud test selže, over, že `requireMapboxNavigation()` (Task 3) skutečně drží instanci mimo `MapWidget`'s scope — to je přesně důvod, proč tenhle plán trvá na tom rozhraní místo `remember { MapboxNavigation(...) }`.

**Regresní kontrola (spec §6 bod 6):** mimo aktivní Mapbox navigaci ověř, že zelené tlačítko "Navigovat" a panel Navigace (notifikace z Google Maps/Mapy.cz) fungují úplně beze změny — otevři Navigaci přes zelené tlačítko, zkontroluj že se nic z Tasků 1-4 do toho flow nepromítlo.

- [ ] **Krok 5: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/ui/map/MapWidget.kt
git commit -m "$(cat <<'EOF'
feat(map): active turn-by-turn guidance — camera, maneuver banner, trip progress

startTripSession() begins right after a route is ready. While active:
Navigation SDK's own NavigationCamera drives the camera (not our
easeTo/isFollowing logic), and the puck switches to Mapbox's own
NavigationLocationProvider (route-matched) instead of AppLocationProvider.
MapboxManeuverView/MapboxTripProgressView render as AndroidView overlays.
Ending navigation restores Fáze 2's default free-drive behavior exactly.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Hlasové pokyny

**Soubory:**
- Upravit: `app/src/main/java/com/example/carlauncher/ui/map/MapWidget.kt`

**Rozhraní:**
- Vstup: aktivní trip session, `RouteProgressObserver` registrace (Task 4).
- Výstup: hlasité pokyny během navigace.

- [ ] **Krok 1: Zapojit `MapboxSpeechApi` + `MapboxVoiceInstructionsPlayer`**

Podle referenčního souboru z Tasku 4 Kroku 1 (`speechApi`, `voiceInstructionsPlayer`, `voiceInstructionsObserver`, `speechCallback`) — inicializuj obě `remember { }` na úrovni `MapWidget`, registruj `VoiceInstructionsObserver` spolu s ostatními observery z Tasku 4 (jen když je trip session aktivní), a v callbacku přehraj přes `voiceInstructionsPlayer.play(...)` s fallbackem na on-device TTS (`error.fallback` větev — SDK to řeší samo, jen musíš správně napojit `speechCallback`).

- [ ] **Krok 2: Uklidit prostředky**

V `DisposableEffect`'s `onDispose` (existující blok z Fáze 2, kde se ničí `mapView`) přidej:
```kotlin
speechApi.cancel()
voiceInstructionsPlayer.shutdown()
routeLineApi.cancel()
```
(z referenčního `onDestroy()` — zabraňuje leaku audio/API zdrojů při disposu `MapWidget`.)

- [ ] **Krok 3: Ověřit na zařízení**

Spusť navigaci, ověř že se pokyny přehrávají nahlas (ne jen vizuálně v banneru). Pokud appka běží s vypnutým zvukem/tichým režimem zařízení, zapni hlasitost přes `SystemControlsWidget` a zkus znovu.

- [ ] **Krok 4: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/ui/map/MapWidget.kt
git commit -m "$(cat <<'EOF'
feat(map): voice guidance during active navigation

MapboxSpeechApi + MapboxVoiceInstructionsPlayer wired to
VoiceInstructionsObserver, active only during a trip session. Cleans
up (cancel/shutdown) on MapWidget disposal alongside the existing
MapView teardown from Fáze 2.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```
