# Map/Nav Widget Switcher — Design Spec (Fáze 1)
**Date:** 2026-09-14
**Status:** Approved

---

## 1. Overview

Umožnit ruční přepínání levého panelu (~65 % šířky, dnes vždy `NavAreaWidget`) mezi živou mapou (`MapWidget`) a navigačními instrukcemi (`NavAreaWidget`) pomocí dlouhého stisku, který otevře bottom sheet s výběrem "Mapa" / "Navigace".

Toto je **Fáze 1** dvoufázového plánu:
- **Fáze 1 (tento spec):** UI mechanika přepínání nad existujícími daty. `MapWidget` (MapLibre) je hotový, ale dosud nikde zapojený — tento spec ho poprvé zapojí do `LauncherScreen`. `NavAreaWidget`/`NavWidget`/`NavRepository` se nemění.
- **Fáze 2 (samostatný budoucí spec):** Nahrazení pasivního parsování navigačních notifikací (Google Maps/Mapy.cz) skutečnou in-app navigací přes Mapbox Navigation SDK (routing, hlasové pokyny, search). Vyžaduje Mapbox účet, `pk.`/`sk.` tokeny s `DOWNLOADS:READ`, aktivaci Navigation SDK v3 a rozhodnutí o Search Box API a o vztahu k MapLibre. Mimo rozsah tohoto spec.

Motivace: `MapWidget` má už dnes připravené CTA tlačítko "Navigovat" (`onNavigate` callback) a `DockBar` obsahuje komentář *"Navigovat is in MapWidget overlay, not in dock"* — mapa jako hlavní pohled byla zamýšlena, jen nikdy dokončena.

---

## 2. Stavový model

Nová malá composable-vrstva (v `LauncherScreen.kt`, případně nový soubor `ui/launcher/MapNavPanel.kt`) nahrazuje přímé volání `NavAreaWidget(...)` v hlavním `Row`.

```kotlin
enum class PanelView { MAP, NAV }

var manualView by remember { mutableStateOf<PanelView?>(null) }  // null = žádná ruční volba
var showPanelPicker by remember { mutableStateOf(false) }

val effectiveView = if (NavRepository.isActive) PanelView.NAV
                    else manualView ?: PanelView.MAP
```

- `NavRepository.isActive == true` → vždy `NAV` (bezpečnostní priorita), bez ohledu na ruční volbu.
- Jinak → poslední ruční volba v této relaci, nebo `MAP` jako výchozí.
- Stav je pouze v paměti (`remember`, ne `rememberSaveable`, ne DataStore) — po restartu aktivity/procesu se resetuje na výchozí mapu.

Renderování:
```kotlin
when (effectiveView) {
    PanelView.NAV -> NavAreaWidget(speedKmh, speedLimitKmh, modifier)   // beze změny
    PanelView.MAP -> MapWidget(modifier, onNavigate = { manualView = PanelView.NAV })
}
```

---

## 3. Dlouhý stisk → bottom sheet

Panel je obalen `Box` s `pointerInput` detekcí dlouhého stisku, stejný vzor jako `LongPressWidgetHostView` (1500 ms timeout, zrušení při pohybu > 12dp), aby nekolidoval s pan/zoom gesty mapy ani s tapy na existující tlačítka:

```kotlin
Box(
    modifier = modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(pass = PointerEventPass.Initial)
            val job = launch { delay(1500); showPanelPicker = true }
            // sledování ACTION_UP / pohybu > 12dp → job.cancel()
            // down se NEKONZUMUJE — krátký tap a pan procházejí beze změny
        }
    }
) { /* MapWidget nebo NavAreaWidget dle effectiveView */ }
```

Bottom sheet (`ModalBottomSheet`, stejný vzor jako `NavAppPickerContent`/`SlotPicker`):

```kotlin
if (showPanelPicker) {
    ModalBottomSheet(onDismissRequest = { showPanelPicker = false }) {
        Text("ZOBRAZENÍ PANELU", ...)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PanelChoiceCard(Icons.Default.Map, "Mapa",
                selected = effectiveView == PanelView.MAP,
                onClick = { manualView = PanelView.MAP; showPanelPicker = false })
            PanelChoiceCard(Icons.Default.Navigation, "Navigace",
                selected = effectiveView == PanelView.NAV,
                onClick = { manualView = PanelView.NAV; showPanelPicker = false })
        }
    }
}
```

Dvě velké karty vedle sebe, aktuálně aktivní zvýrazněná (border/accent barva), vizuálně konzistentní s existujícími pickery v aplikaci.

**"Navigovat" tlačítko v `MapWidget`:** `onNavigate = { manualView = PanelView.NAV }` — přepne panel na `NavAreaWidget`, kde uživatel pak tapne velké zelené "Navigovat" pro otevření `NavAppPickerContent`. Jeden tap navíc oproti přímému otevření pickeru z mapy, ale nevyžaduje sdílení privátní composable mezi `ui.map` a `ui.navigation` balíčky.

---

## 4. Okrajové případy

- **Rychlý tap/pan během 1500ms okna** — `job.cancel()` na pohyb > 12dp nebo `ACTION_UP`; normální tap/pan projde beze změny, protože `down` se nekonzumuje.
- **Long-press během aktivní navigace** — sheet se dá otevřít, ale `effectiveView` stejně přebije volbu zpět na `NAV` při další rekompozici, dokud je `isActive == true`. Bez zašedivení volby "Mapa" v sheetu pro Fázi 1 (YAGNI) — doladí se jen pokud se v praxi ukáže matoucí.
- **MapWidget lifecycle** — beze změny, řeší `ComponentActivity` lifecycle nezávisle na pageru (viz existující implementace).

---

## 5. Testování

Bez unit testů (žádné v repu pro srovnatelné Compose/MapLibre widgety) — manuální ověření na emulátoru API 34 a reálném zařízení:
1. Tap na mapu → pan/zoom funguje normálně.
2. Podržení 1500 ms → objeví se bottom sheet.
3. Výběr "Mapa"/"Navigace" v sheetu přepíná panel.
4. Příchozí navigační notifikace za běhu přebije ruční volbu "Mapa" a zobrazí `NavWidget`.
5. Tlačítko "Navigovat" na mapě přepne panel na `NavAreaWidget` (landing/picker).

---

## 6. Mimo rozsah (Fáze 2)

- Mapbox Navigation SDK, routing, hlasové pokyny, in-app search destinace.
- Perzistence ruční volby přes DataStore.
- Jakékoli změny uvnitř `NavWidget`, `NavRepository`, `MediaListenerService`.
