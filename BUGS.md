# BUGS.md — Car Launcher Bug Vault

> Centrální evidence bugů projektu Android Car Launcher.
> Aktualizuje Claude Code automaticky. Nikdy nesmazávat záznamy — pouze měnit status.
> Stavy: `OPEN` · `IN_PROGRESS` · `FIXED` · `WONTFIX` · `DEFERRED`

---

## Statistiky

| Status | Počet |
|--------|-------|
| OPEN | 8 |
| IN_PROGRESS | 0 |
| FIXED | 9 |
| WONTFIX | 0 |
| DEFERRED | 0 |

*Aktualizuj ručně nebo nech Claude Code spočítat při každém sezení.*

---

## Severity stupnice

| Úroveň | Popis |
|--------|-------|
| **CRITICAL** | App crash, nelze spustit, ztráta dat |
| **HIGH** | Klíčová funkce nefunguje (GPS, mapa, rychlost) |
| **MEDIUM** | Funkce funguje, ale chybně nebo s artefakty |
| **LOW** | Kosmetická chyba, drobné UI problémy |
| **TRIVIAL** | Vylepšení, nice-to-have |

---

## Šablona záznamu

```
## BUG-XXX · [STATUS] · M? ModuleName · SEVERITY
**Popis:** Jednořádkový popis co je rozbité.
**Kroky:**
1. Krok 1
2. Krok 2
3. Krok 3
**Chování:** Co se stane.
**Očekáváno:** Co by se mělo stát.
**Logcat:** `tag: relevantní řádek z logcatu` (nebo "viz bug-session-DATUM.txt")
**Prostředí:** Android 16 · Lenovo Tab M10 Plus 3rd Gen · Build: debug/release
**Objeven:** RRRR-MM-DD · Kdo: Potato / Claude Code / Code Review
**Fix commit:** — (doplní Claude Code po opravě)
**Zavřen:** — (doplní po ověření na zařízení)
**Poznámky:** Volitelné — kontext, pokusy o fix, workaroundy.
```

---

## OPEN bugy

## BUG-010 · [OPEN] · M7 StatusBar / GPS Pipeline · MEDIUM
**Popis:** GPS status dot v StatusBaru problikává (zelená → červená → zelená) zejména při rychlosti blízké 5 km/h nebo při přepínání GPS intervalu DRIVING ↔ PARKED.
**Kroky:**
1. Spustit CarLauncher s aktivním GPS fixem (zelená tečka v pravém horním rohu)
2. Jet rychlostí okolo 5 km/h (pomalý provoz, zácpa, parkování)
3. Sledovat GPS dot — viditelné problikávání zelená/červená
4. Alternativně: GPX replay s segmentem ~5 km/h
**Chování:** GPS dot bliká červeně a ihned se vrací na zelenou, opakovaně.
**Očekáváno:** GPS dot zůstane stabilně zelený po dobu, kdy je GPS fix k dispozici.
**Root cause (2 příčiny):**
- `adjustIntervalIfNeeded()` v `LocationRepository` volá `removeLocationUpdates()` + `requestLocationUpdates()` při každém překročení prahu 5 km/h. Při oscilaci rychlosti kolem prahu nastávají opakované GPS callback gaps.
- `gpsFix = location != null` v `LauncherScreen.kt:57` — jakákoli null emise z `_vehicleLocation` (incl. `result.lastLocation` == null z Android API 12+) okamžitě přepne dot na červenou.
**Logcat:** neuvedeno
**Jira:** [KAN-1](https://carlauncher.atlassian.net/browse/KAN-1)
**Prostředí:** Android 16 · Lenovo Tab M10 Plus 3rd Gen · Build: debug
**Objeven:** 2026-06-21 · Kdo: Potato
**Fix commit:** —
**Zavřen:** —
**Poznámky:** Doporučený fix: debounce/timeout na `gpsFix` — přepnout na `false` pouze po N sekundách bez location eventu (doporučeno 5 s). Alternativa: `_vehicleLocation` nikdy neemitovat null po prvním fixu (pouze v `stopTracking()`).

## BUG-009 · [FIXED] · M8 Debug · LOW
**Popis:** CSV GPS logy se neukládaly do přístupné složky — soubory nebyly vidět přes MTP/Explorer.
**Kroky:**
1. Zapnout CSV logování v DebugPanel
2. Logovat 2–3 minuty
3. Hledat soubor přes Explorer → `Android/data/com.example.carlauncher/files`
**Chování:** Složka prázdná, žádný CSV soubor.
**Očekáváno:** Soubor `gps_log_YYYYMMDD_HHMMSS.csv` viditelný.
**Logcat:** neuvedeno
**Prostředí:** Android 16 · Lenovo Tab M10 Plus 3rd Gen · Build: debug
**Objeven:** 2026-06-20 · Kdo: Potato
**Fix commit:** inline — CsvGpsLogger.kt
**Zavřen:** 2026-06-20
**Poznámky:** Android 11+ blokuje MTP přístup do `Android/data/` — soubory tam vznikaly, ale nebyly viditelné. Fix: přechod na MediaStore API, soubory se ukládají do `Download/gps_log_*.csv` (plně přístupné přes Explorer i file manager).

## BUG-008 · [FIXED] · M6 DockBar · HIGH
**Popis:** Kliknutí na split skupinu TomTom+YTMusic po předchozí Waze+YTMusic otevře jen YouTube Music.
**Kroky:**
1. Kliknout na slot "Waze + Hudba" → split screen [Waze | YTMusic] se načte
2. Vrátit se na launcher (Home)
3. Kliknout na slot "TomTom + Hudba"
**Chování:** Otevře se pouze YouTube Music, TomTom se neobjeví.
**Očekáváno:** Split screen [TomTom | YTMusic].
**Logcat:** neuvedeno
**Prostředí:** Android 16 · Lenovo Tab M10 Plus 3rd Gen · Build: debug
**Objeven:** 2026-06-20 · Kdo: Potato
**Fix commit:** inline — MainActivity.kt
**Zavřen:** 2026-06-20
**Poznámky:** Příčina: `launchSplitScreen` nespravovala běžící coroutine — delayed launch pkg2 z předchozího kliknutí mohl interferovat. Fix: `splitScreenJob: Job?` — každý nový klik cancelluje předchozí job. Delay zvýšen 650→800ms pro spolehlivější split transition. Toast při selhání pkg2.

## BUG-007 · [FIXED] · M6 AppDrawer · MEDIUM
**Popis:** AppDrawer se otevírá s ~5s prodlevou, scrollování v gridu je pomalé a sekavé.
**Kroky:**
1. Spustit app
2. Klepnout na ikonu menu (mřížka) v doku
3. Scrollovat v gridu aplikací
**Chování:** Viditelná prodleva ~5s před zobrazením draweru, scrollování trhá.
**Očekáváno:** Okamžité otevření, plynulé scrollování.
**Logcat:** neuvedeno
**Prostředí:** Android 16 · Lenovo Tab M10 Plus 3rd Gen · Build: debug
**Objeven:** 2026-06-20 · Kdo: Potato
**Fix commit:** inline — DockViewModel.kt, AppDrawer.kt, LauncherScreen.kt
**Zavřen:** 2026-06-20
**Poznámky:** (1) loadLabel() voláno 2× per app → přepracováno na 1× s cache tuple. (2) produceState per-item odstraněn — ikony se pre-načítají v DockViewModel.init na IO thread do Map<String, ImageBitmap>. AppDrawer dostane hotovou mapu a jen vykresluje.

## BUG-005 · [FIXED] · M3 MapWidget · HIGH
**Popis:** Marker se na mapě občas ztrácí a mapa bliká při pohybu.
**Kroky:**
1. Spustit app s GPS fixem
2. Jet autem
**Chování:** Marker mizí na sekundy, mapa bliká/trhá.
**Očekáváno:** Marker stabilně viditelný, mapa plynulá.
**Logcat:** neuvedeno
**Prostředí:** Android 16 · Lenovo Tab M10 Plus 3rd Gen · Build: debug
**Objeven:** 2026-06-15 · Kdo: Potato
**Fix commit:** inline — MapWidget.kt
**Zavřen:** 2026-06-15
**Poznámky:** (1) Kamera přepnuta z `newCameraPosition(bearing=...)` na `newLatLng()` — žádná rotace mapy = žádné re-načítání tiles. (2) Rotace markeru přesunuta z `layer.setProperties()` (style re-evaluation) do data-driven `Expression.get("bearing")` na feature — jen GeoJSON data se mění. (3) Práh 5m/5° odstraněn — marker se aktualizuje na každý GPS event.

## BUG-006 · [FIXED] · M3 MapWidget · MEDIUM
**Popis:** Marker ignoruje zatáčky a jede mimo silnici.
**Kroky:**
1. Spustit app s GPS fixem
2. Jet autem zatáčkou nebo kruhovým objezdem
**Chování:** Marker míří špatným směrem, po zatáčce se snapne zpět se zpožděním.
**Očekáváno:** Marker plynule sleduje směr jízdy.
**Logcat:** neuvedeno
**Prostředí:** Android 16 · Lenovo Tab M10 Plus 3rd Gen · Build: debug
**Objeven:** 2026-06-15 · Kdo: Potato
**Fix commit:** inline — LocationProcessor.kt, KalmanFilter.kt, LocationRepository.kt
**Zavřen:** 2026-06-15
**Poznámky:** (1) `LocationProcessor` přidává bearing smoothing: lerp 0.7× GPS bearing (< 30° accuracy), fallback computeBearing z delta pozice, freeze při < 3 km/h. (2) KalmanFilter Q: 0.8 → 0.5. (3) GPS DRIVING interval: 500ms → 300ms.

---

## IN_PROGRESS bugy

*viz OPEN — BUG-005 a BUG-006 jsou IN_PROGRESS*

---

## FIXED bugy

## BUG-004 · [FIXED] · M6 DockBar · HIGH
**Popis:** Po fixu BUG-003 chybí Waze a Mapy.cz v chooseru navigace.
**Kroky:**
1. Spustit app
2. Klepnout na tlačítko "Navigovat"
3. Otevře se chooser
**Chování:** Waze a Mapy.cz nejsou v seznamu — zobrazí se pouze Google Maps.
**Očekáváno:** Waze, Mapy.cz i Google Maps, každá jednou.
**Logcat:** neuvedeno
**Prostředí:** Android 16 · Lenovo Tab M10 Plus 3rd Gen · Build: debug
**Objeven:** 2026-06-15 · Kdo: Potato
**Fix commit:** inline — DockViewModel.buildNavigateIntent() + AndroidManifest queries
**Zavřen:** 2026-06-15
**Poznámky:** `geo:0,0?q=0,0` nematechuje Waze (`waze://`) ani Mapy.cz (`mapsczv10://`). Fix: explicitní Intent per app s app-specific URI scheme; fallback generic geo: pro ostatní apps; `<queries>` v manifestu pro Android 11+ package visibility.

## BUG-003 · [FIXED] · M6 DockBar · MEDIUM
**Popis:** Chooser dialog tlačítka Navigovat zobrazuje každou navigační aplikaci dvakrát.
**Kroky:**
1. Spustit app
2. Klepnout na tlačítko "Navigovat" (overlay v MapWidget)
3. Otevře se chooser dialog
**Chování:** Každá navigace (Waze, Google Maps, …) se zobrazí 2×.
**Očekáváno:** Každá aplikace jednou.
**Logcat:** neuvedeno
**Prostředí:** Android 16 · Lenovo Tab M10 Plus 3rd Gen · Build: debug
**Objeven:** 2026-06-15 · Kdo: Potato
**Fix commit:** inline — DockViewModel.buildNavigateIntent()
**Zavřen:** 2026-06-15
**Poznámky:** `Intent(ACTION_VIEW, geo:0,0)` matchoval main i navigation-specific activity stejné aplikace. Fix: `queryIntentActivities` + `filter { seen.add(packageName) }` deduplikuje na úrovni package, pak sestaví chooser s explicitními Intent per app.

## BUG-002 · [FIXED] · M6 DockBar / AppDrawer · MEDIUM
**Popis:** AppDrawer se otevírá pomalu a animace otevření je sekaná.
**Kroky:**
1. Spustit app
2. Klepnout na ikonu menu (mřížka) v doku
3. Počkat na otevření AppDrawer
**Chování:** Viditelná prodleva před zobrazením, animace se zasekává nebo chybí.
**Očekáváno:** Plynulé otevření s fade animací, ikony se načtou postupně.
**Logcat:** neuvedeno
**Prostředí:** Android 16 · Lenovo Tab M10 Plus 3rd Gen · Build: debug
**Objeven:** 2026-06-12 · Kdo: Potato
**Fix commit:** inline — DockViewModel, AppDrawer, LauncherScreen
**Zavřen:** 2026-06-12
**Poznámky:** 3 příčiny opraveny: (1) App metadata (bez ikon) se načítají jednou v DockViewModel.init → cache, reopen = okamžitý. (2) Ikony načítány lazily v `DrawerAppItem` via `produceState` na Dispatchers.IO — žádné blokování main threadu. (3) Přidán `AnimatedVisibility` s `fadeIn+slideInVertically(200ms)` v LauncherScreen.

## BUG-001 · [FIXED] · M5 MusicWidget · LOW
**Popis:** Tři tečky (⋯) vpravo nahoře v MusicWidget nereagují na tap — žádná akce.
**Kroky:**
1. Spustit app
2. Podívat se na MusicWidget — vpravo nahoře jsou vidět tři tečky / animované bary
3. Tapnout na daný element
**Chování:** Nic se nestane — žádná vizuální odezva ani akce.
**Očekáváno:** Otevře se kontextové menu nebo jiná akce.
**Logcat:** neuvedeno
**Prostředí:** Android 16 · Lenovo Tab M10 Plus 3rd Gen · Build: debug
**Objeven:** 2026-06-12 · Kdo: Potato
**Fix commit:** inline — EqualizerAnimation odstraněna z MusicWidget.kt
**Zavřen:** 2026-06-12
**Poznámky:** Element byl EqualizerAnimation (4 animované bary). Rozhodnutí: smazat — není potřeba.

---

## WONTFIX / DEFERRED

*Žádné.*

---

## Logcat sessions

Archiv logcat výstupů ze session testování na zařízení.
Ukládat jako: `logs/bug-session-RRRR-MM-DD-HHMM.txt`

| Soubor | Datum | Popis session |
|--------|-------|---------------|
| — | — | — |

---

*Poslední aktualizace: 2026-06-21 · BUG-010 přidán (GPS dot problikává) · Jira KAN-1*
*Projekt: Android Car Launcher v0.1 · Modul: M7 LauncherScreen*
