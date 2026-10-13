# Tablet Auto-Scale Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Layout vypadá na tabletech 8–13″ (landscape) stejně jako na Lenovo M10, jen úměrně přeškálovaný.

**Architecture:** Jedna škálovací vrstva (`DeviceScaleProvider`) přepíše `LocalDensity` v `MainActivity` faktorem `computeScale(widthDp, heightDp)`. Všechny `.dp` a `.sp` v Compose se přeškálují najednou. Místa, která čtou `resources.displayMetrics.density` přímo, se opraví ručně.

**Tech Stack:** Kotlin, Jetpack Compose (BOM z `libs.compose.bom`), JUnit 4 (nově), Gradle, Android emulátor API 34 x86_64.

Spec: `docs/superpowers/specs/2026-10-13-tablet-auto-scale-design.md`

## Global Constraints

- Referenční rozměr: 1143×686 dp. Meze škály: `MIN_SCALE = 0.85f`, `MAX_SCALE = 1.6f`. Zóna kolem 1.0: `±0.03` se zaokrouhlí na `1.0f`.
- `fontScale` se **nemění** (jen `density`), jinak se písmo škáluje dvakrát.
- Orientace zůstává `landscape`, žádné nové widgety ani skrývání obsahu.
- Na M10 žádná vizuální změna (scale = 1.0).
- Emulátory: pouze API 34 (Android 14) x86_64 s Google Play, ne API 35+ (viz CLAUDE.md).
- Nesahat na `local.properties` ani ho necommitovat.
- Jeden úkol = jeden commit. Commit zprávy končí řádkem `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- Příkazy se spouští z kořene worktree (`C:\Users\David\carLauncher\CarLauncher\.claude\worktrees\tablet-layout-all-devices-e7cd6a`), v Git Bash `./gradlew`, v PowerShellu `.\gradlew.bat`.

## File Structure

- Create: `app/src/main/java/com/example/carlauncher/ui/theme/DeviceScale.kt` – konstanty, `computeScale`, `DeviceScaleProvider`.
- Create: `app/src/test/java/com/example/carlauncher/ui/theme/DeviceScaleTest.kt` – jednotkové testy.
- Modify: `app/build.gradle.kts` – `testImplementation("junit:junit:4.13.2")`.
- Modify: `app/src/main/java/com/example/carlauncher/MainActivity.kt:115-176` – obalit obsah.
- Modify: `app/src/main/java/com/example/carlauncher/ui/map/MapWidget.kt` (řádky ~384 a ~646) – density pro `EdgeInsets`.
- Modify: `app/src/main/java/com/example/carlauncher/ui/widgets/WidgetScreen.kt:462-463` – reálná density pro AppWidget options.
- Modify: `CLAUDE.md` – nová sekce o škálování.

---

### Task 1: Test infrastruktura a `computeScale`

**Files:**
- Modify: `app/build.gradle.kts` (blok `dependencies`, za řádek `debugImplementation(libs.compose.ui.tooling)`)
- Create: `app/src/main/java/com/example/carlauncher/ui/theme/DeviceScale.kt`
- Test: `app/src/test/java/com/example/carlauncher/ui/theme/DeviceScaleTest.kt`

**Interfaces:**
- Produces: `const val REFERENCE_WIDTH_DP: Float`, `REFERENCE_HEIGHT_DP: Float`, `MIN_SCALE: Float`, `MAX_SCALE: Float`; `fun computeScale(widthDp: Float, heightDp: Float): Float` (top-level, package `com.example.carlauncher.ui.theme`).

Projekt dnes nemá žádný unit test ani JUnit závislost (`app/src/test` neexistuje).

- [ ] **Step 1: Přidat JUnit závislost**

V `app/build.gradle.kts` za `debugImplementation(libs.compose.ui.tooling)` vložit:

```kotlin
    testImplementation("junit:junit:4.13.2")
```

- [ ] **Step 2: Napsat padající test**

Vytvořit `app/src/test/java/com/example/carlauncher/ui/theme/DeviceScaleTest.kt`:

```kotlin
package com.example.carlauncher.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceScaleTest {

    private val eps = 0.001f

    @Test
    fun reference_device_is_exactly_one() {
        assertEquals(1f, computeScale(1143f, 686f), eps)
    }

    @Test
    fun near_reference_snaps_to_one() {
        // M10 s mírně jinými reálnými dp (+0,8 %) nesmí dostat škálu ≠ 1.0
        assertEquals(1f, computeScale(1152f, 691f), eps)
        assertEquals(1f, computeScale(1110f, 670f), eps)
    }

    @Test
    fun small_16_10_tablet_is_clamped_to_min() {
        // 8" 16:10 ≈ 962×601 dp → min(0,842; 0,876) = 0,842 → ořez na 0,85
        assertEquals(MIN_SCALE, computeScale(962f, 601f), eps)
    }

    @Test
    fun four_by_three_uses_the_limiting_width() {
        // 1024×768 dp → min(0,896; 1,120) = 0,896 (výška není omezující)
        assertEquals(1024f / 1143f, computeScale(1024f, 768f), eps)
    }

    @Test
    fun large_tablet_scales_up_proportionally() {
        // 1480×924 dp → min(1,295; 1,347) = 1,295
        assertEquals(1480f / 1143f, computeScale(1480f, 924f), eps)
    }

    @Test
    fun extreme_large_is_clamped_to_max() {
        assertEquals(MAX_SCALE, computeScale(3000f, 2000f), eps)
    }

    @Test
    fun extreme_small_is_clamped_to_min() {
        assertEquals(MIN_SCALE, computeScale(400f, 300f), eps)
    }
}
```

- [ ] **Step 3: Spustit test, ověřit selhání**

Run: `./gradlew testDebugUnitTest --tests "com.example.carlauncher.ui.theme.DeviceScaleTest"`
Expected: FAIL – kompilace hlásí `Unresolved reference: computeScale` (a `MIN_SCALE`, `MAX_SCALE`).

- [ ] **Step 4: Minimální implementace**

Vytvořit `app/src/main/java/com/example/carlauncher/ui/theme/DeviceScale.kt`:

```kotlin
package com.example.carlauncher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

// Layout je navržený pro Lenovo Tab M10 Plus (~1143×686 dp). Na jiných tabletech se celé UI
// přeškáluje přepsáním LocalDensity, takže vypadá stejně a jen se úměrně zvětší/zmenší.
const val REFERENCE_WIDTH_DP = 1143f
const val REFERENCE_HEIGHT_DP = 686f
const val MIN_SCALE = 0.85f   // pod tím by touch targety (min 48dp) klesly příliš
const val MAX_SCALE = 1.6f    // pojistka proti absurdním displejům
private const val SNAP_TOLERANCE = 0.03f

fun computeScale(widthDp: Float, heightDp: Float): Float {
    val raw = minOf(widthDp / REFERENCE_WIDTH_DP, heightDp / REFERENCE_HEIGHT_DP)
    val clamped = raw.coerceIn(MIN_SCALE, MAX_SCALE)
    // M10 s mírně jinými reálnými dp zůstává na přesně 1.0 (žádná viditelná změna)
    return if (kotlin.math.abs(clamped - 1f) <= SNAP_TOLERANCE) 1f else clamped
}

@Composable
fun DeviceScaleProvider(content: @Composable () -> Unit) {
    val config = LocalConfiguration.current
    val base = LocalDensity.current
    val scale = computeScale(config.screenWidthDp.toFloat(), config.screenHeightDp.toFloat())
    if (scale == 1f) {
        content()
        return
    }
    // fontScale se NEMĚNÍ: sp se počítá jako sp × fontScale × density, takže písmo
    // se přeškáluje samo přes density (násobení fontScale by škálovalo dvakrát).
    CompositionLocalProvider(
        LocalDensity provides Density(base.density * scale, base.fontScale)
    ) {
        content()
    }
}
```

- [ ] **Step 5: Spustit test, ověřit úspěch**

Run: `./gradlew testDebugUnitTest --tests "com.example.carlauncher.ui.theme.DeviceScaleTest"`
Expected: PASS, 7 testů.

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/example/carlauncher/ui/theme/DeviceScale.kt app/src/test/java/com/example/carlauncher/ui/theme/DeviceScaleTest.kt
git commit -m "feat(scale): add computeScale and DeviceScaleProvider with unit tests

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Napojení v MainActivity

**Files:**
- Modify: `app/src/main/java/com/example/carlauncher/MainActivity.kt` (import ~řádek 46, tělo `CarLauncherTheme { … }` řádky 115–176)

**Interfaces:**
- Consumes: `DeviceScaleProvider(content: @Composable () -> Unit)` z Tasku 1.

- [ ] **Step 1: Přidat import**

Do `MainActivity.kt` k ostatním importům `com.example.carlauncher.ui.theme.*` (za řádek `import com.example.carlauncher.ui.theme.CarLauncherTheme`):

```kotlin
import com.example.carlauncher.ui.theme.DeviceScaleProvider
```

- [ ] **Step 2: Obalit obsah**

Uvnitř `CarLauncherTheme(isDark = isDark) {` obalit celý stávající obsah (od `val pagerState` po konec vnějšího `Box`) do `DeviceScaleProvider { … }`. Struktura po úpravě:

```kotlin
            CarLauncherTheme(isDark = isDark) {
                DeviceScaleProvider {
                    val pagerState = rememberPagerState(pageCount = { 3 })
                    // … beze změny: incidentEnabled, showIncidentRecorder, mapTouchActive,
                    // Box { HorizontalPager, dot indikátor, IncidentFab, IncidentRecorderScreen }
                }
            }
```

Žádný jiný řádek se nemění, pouze se přidá úroveň odsazení a obalení.

- [ ] **Step 3: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Ověřit na M10 / emulátoru bez změny vzhledu**

Nainstalovat (`./gradlew installDebug` nebo ADB s **absolutní** cestou k APK, viz paměť projektu) a projít 3 stránky. Na M10 musí být vzhled identický s předchozí verzí (scale = 1.0 a `DeviceScaleProvider` jen volá `content()`).

Pokud nemáš M10 po ruce: na emulátoru 1143×686 dp (viz Task 4) ověř totéž.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/MainActivity.kt
git commit -m "feat(scale): wrap launcher content in DeviceScaleProvider

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Opravit místa, která obcházejí LocalDensity

**Files:**
- Modify: `app/src/main/java/com/example/carlauncher/ui/map/MapWidget.kt` (import ~řádek 49, začátek `MapWidget` ~řádek 226, řádky ~384 a ~646)
- Modify: `app/src/main/java/com/example/carlauncher/ui/widgets/WidgetScreen.kt:462-463`

**Interfaces:**
- Consumes: škálovaný `LocalDensity` z Tasku 2.

`EdgeInsets` v Mapboxu jsou v **pixelech** a kryjí Compose prvky (karta výsledků, banner), které se po Tasku 2 škálují. Proto musí používat škálovanou density. Naproti tomu AppWidgety se renderují v reálných dp, takže `WidgetScreen` musí používat reálnou density.

- [ ] **Step 1: MapWidget – zachytit škálovanou density**

Přidat import vedle `import androidx.compose.ui.platform.LocalContext`:

```kotlin
import androidx.compose.ui.platform.LocalDensity
```

V `MapWidget` hned za `val context = LocalContext.current` přidat:

```kotlin
    // Škálovaná (UI) density – EdgeInsets jsou v pixelech a kryjí Compose prvky, které se škálují
    // přes DeviceScaleProvider; resources.displayMetrics.density by škálování obešlo.
    val uiDensity = LocalDensity.current.density
```

- [ ] **Step 2: MapWidget – nahradit dvě čtení**

V `updateCategoryPins` (~řádek 384) smazat řádek `val density = context.resources.displayMetrics.density` a v `EdgeInsets(...)` použít `uiDensity`:

```kotlin
            map.cameraForCoordinates(
                results.map { it.coordinate },
                CameraOptions.Builder().build(),
                EdgeInsets(300.0 * uiDensity, 48.0 * uiDensity, 96.0 * uiDensity, 48.0 * uiDensity),
                null,
                null,
            ) { camera -> map.setCamera(camera) }
```

Na ~řádku 646 smazat `val density = context.resources.displayMetrics.density` a upravit:

```kotlin
                    viewportDataSource.followingPadding = EdgeInsets(
                        140.0 * uiDensity,
                        24.0 * uiDensity,
                        110.0 * uiDensity,
                        24.0 * uiDensity,
                    )
```

Pokud je `density` v okolním kódu použito i jinde, ověřit grepem `density` v souboru, aby nezůstala nedefinovaná reference.

- [ ] **Step 3: WidgetScreen – reálná density pro AppWidget options**

V `WidgetPage` (~řádek 462) nahradit výpočet přes `LocalDensity` výpočtem přes reálnou density:

```kotlin
            if (cardSize != IntSize.Zero) {
                // AppWidgety se renderují v reálných dp, ne v přeškálovaných (DeviceScaleProvider)
                val realDensity = context.resources.displayMetrics.density
                val wDp = (cardSize.width / realDensity).toInt()
                val hDp = (cardSize.height / realDensity).toInt()
```

Zbytek `updateAppWidgetOptions(...)` beze změny. Pokud je `val density = LocalDensity.current` v `WidgetPage` po úpravě nepoužité, smazat ho i s importem pouze pokud ho nepoužívá nic jiného v souboru (zkontrolovat grepem).

- [ ] **Step 4: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`, žádný nový warning „unused variable".

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/ui/map/MapWidget.kt app/src/main/java/com/example/carlauncher/ui/widgets/WidgetScreen.kt
git commit -m "fix(scale): use scaled density for Mapbox EdgeInsets, real density for AppWidget sizes

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Ověření na emulátorech a doladění

**Files:**
- Modify (podle nálezů): konkrétní widgety v `app/src/main/java/com/example/carlauncher/ui/**`
- Create: screenshoty v `docs/superpowers/plans/screenshots/` (volitelné, necommitovat velké soubory)

Žádný nový kód předem – tento úkol je ověřovací a jeho výstupem jsou drobné opravy podle skutečných nálezů.

- [ ] **Step 1: Vytvořit AVD profily (API 34 x86_64, Google Play)**

Nainstalovat system image, pokud chybí:

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
& "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" "system-images;android-34;google_apis_playstore;x86_64"
```

Vytvořit zařízení (rozlišení v px a dpi; hodnoty dávají dp uvedené v závorce):

| Název | Rozlišení | dpi | ≈ dp |
|---|---|---|---|
| tab8_1610 | 1200×1920 → v landscape 1920×1200 | 320 | 960×600 |
| tab10_1610 | 1920×1200 | 240 | 1280×800 |
| tab11_32 | 2400×1600 | 240 | 1600×1067 |
| tab129_43 | 2732×2048 | 264 | 1034×775 |
| tab13_1610 | 2560×1600 | 192 | 1707×1067 |

Pro každý:

```powershell
& "$sdk\cmdline-tools\latest\bin\avdmanager.bat" create avd -n tab10_1610 -k "system-images;android-34;google_apis_playstore;x86_64" -d "pixel_c"
```

Po vytvoření upravit `~\.android\avd\<název>.avd\config.ini`: `hw.lcd.width`, `hw.lcd.height`, `hw.lcd.density` podle tabulky a `hw.initialOrientation=landscape`. Případně `adb shell wm size 1920x1200` a `adb shell wm density 240` na běžícím emulátoru.

Expected: `emulator -list-avds` vypíše 5 profilů.

- [ ] **Step 2: Nainstalovat APK a pořídit screenshoty**

Pro každý AVD: spustit, nainstalovat s **absolutní cestou** k APK a po instalaci ověřit nainstalovaný APK (viz paměť projektu), potom:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r "$PWD\app\build\outputs\apk\debug\app-debug.apk"
& $adb exec-out screencap -p > "screenshots\tab10_page0.png"
```

Opakovat pro stránku 0 (Launcher), 1 (Widgety), 2 (Trip) – mezi snímky swipe přes `adb shell input swipe 1500 600 300 600`.

- [ ] **Step 3: Projít kontrolní seznam na každém zařízení**

- Nic není ořezané ani se nepřekrývá (dock, hudba, počasí, kalendář, ovládání hlasitosti/jasu).
- Touch targety ≥ 48 dp po škálování (hlavně dock sloty 64 dp při scale 0,85 → ≈ 54 dp, dock výška 88 dp).
- Mapa: gesta, tlačítko „Moje poloha", klik na POI, SearchOverlay, piny kategorií se vejdou nad kartu výsledků.
- Navigace: banner nezasahuje pod tlačítko Ukončit, trip progress dole.
- Widgety: přidání AppWidgetu, velikost odpovídá slotu.
- `IncidentFab`: lze přetáhnout, nezůstane mimo obrazovku.
- Mapboxí View (maneuver banner, trip progress) – pokud na 13″ vypadají neúměrně malé proti zbytku, zaznamenat do nálezů.

- [ ] **Step 4: Opravit nálezy**

Každý nález opravit minimálním zásahem v dotčeném souboru (typicky `BoxWithConstraints` nebo úprava konkrétní konstanty) a znovu ověřit na dotčeném zařízení **i na referenčním M10-ekvivalentu 1143×686 dp**, aby se nezměnil. Pokud nálezů není, tento krok přeskočit a zapsat to do zprávy commitu.

- [ ] **Step 5: Spustit celé unit testy a build**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add -A app/src
git commit -m "fix(scale): tablet layout tweaks found during emulator verification

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

(Pokud nevznikly žádné úpravy kódu, commit vynechat.)

---

### Task 5: Dokumentace v CLAUDE.md

**Files:**
- Modify: `CLAUDE.md` (sekce „Architecture", za blok „Fullscreen")

- [ ] **Step 1: Přidat sekci**

Za odstavec začínající `**Fullscreen:**` vložit:

```markdown
**Škálování pro tablety:** `ui/theme/DeviceScale.kt` — `DeviceScaleProvider` v `MainActivity` přepíše `LocalDensity` faktorem `computeScale(screenWidthDp, screenHeightDp)` vůči referenci M10 (1143×686 dp), ořezaným na 0,85–1,6, s pásmem ±3 % → přesně 1,0. Všechny `.dp`/`.sp` se tak přeškálují najednou. `fontScale` se nemění (jinak dvojí škálování písma). **Pozor při úpravách:** `resources.displayMetrics.density` škálování obchází — pro pixelové hodnoty kryjící Compose prvky (Mapbox `EdgeInsets`) použít `LocalDensity.current.density`; pro AppWidget options (`WidgetScreen`) naopak reálnou density, protože AppWidgety se renderují v reálných dp. Mapboxí View komponenty (maneuver banner, trip progress, puck) se neškálují.
```

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: document tablet auto-scale in CLAUDE.md

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

## Self-Review

- **Spec coverage:** škálovací vrstva + snap + fontScale → Task 1; napojení v MainActivity → Task 2; body 4 specu (EdgeInsets, AppWidget, `displayMetrics`) → Task 3; ostatní body 4 (BottomSheet, AppDrawer, IncidentFab, banner, Mapbox Views) a testování na emulátorech → Task 4; dokumentace → Task 5. Jednotkové testy → Task 1.
- **Placeholdery:** žádné; Task 4 je záměrně ověřovací, nálezy se opraví podle skutečného stavu.
- **Konzistence typů:** `computeScale(Float, Float): Float`, `MIN_SCALE`/`MAX_SCALE`, `DeviceScaleProvider(content)` shodně v Tascích 1, 2 a testech; `uiDensity` definováno a použito jen v Tasku 3.
