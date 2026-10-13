# Automatické škálování layoutu pro tablety 8–13″

Datum: 2026-10-13 · Větev: `claude/tablet-layout-all-devices-e7cd6a`

## Cíl

Layout je dnes navržený pevně pro Lenovo Tab M10 Plus (~1143×686 dp). Appka má vypadat stejně na všech tabletech 8–13″ v landscape, tedy mapa ~65 %, vpravo hudba, ovládání a počasí, dole dock. Na M10 se nesmí nic změnit.

## Rozsah

- **V rozsahu:** tablety 8–13″, pouze landscape (zůstává `screenOrientation="landscape"`), poměry stran 16:10, 3:2, 4:3.
- **Mimo rozsah:** portrait, telefony, nové widgety, skrývání nebo přidávání obsahu podle velikosti.

## Přístup

Globální škála přes `LocalDensity`, ne ruční responzivita v každém composable. V `MainActivity` se jednou přepíše `Density` o faktor `scale`, takže se současně přeškálují všechny hodnoty `.dp` (382 výskytů v 26 souborech) i písma. Případné nesrovnalosti se po testování doladí ručně v konkrétních widgetech.

## Návrh

### 1. Škálovací vrstva

Nový soubor `ui/theme/DeviceScale.kt`:

- Konstanty `REFERENCE_WIDTH_DP = 1143f`, `REFERENCE_HEIGHT_DP = 686f`, `MIN_SCALE = 0.85f`, `MAX_SCALE = 1.6f`.
- Čistá funkce `computeScale(widthDp: Float, heightDp: Float): Float` vrací `min(widthDp/REFERENCE_WIDTH_DP, heightDp/REFERENCE_HEIGHT_DP)` oříznuté na `[MIN_SCALE, MAX_SCALE]`.
- Composable `DeviceScaleProvider(content)` čte rozměry z `LocalConfiguration` (`screenWidthDp`, `screenHeightDp`) a zabalí obsah do `CompositionLocalProvider(LocalDensity provides Density(base.density * scale, base.fontScale * scale))`.
- Dolní mez 0,85 hlídá, aby touch targety (min 48 dp, dock slot 64 dp) neklesly příliš. Pokud testování ukáže jiný ideální práh, upraví se konstanta.

### 2. Napojení

`MainActivity` obalí `DeviceScaleProvider` obsah, který zahrnuje `HorizontalPager` i `IncidentFab`/overlaye, takže škála platí pro všechny 3 stránky, dock a plovoucí tlačítko.

### 3. Poměry stran

Layout už používá váhy (1,85 : 1) a `fillMaxSize`. Přebytek (na 4:3 výška, na širokých displejích šířka) pohltí mapa a hudební widget. Škála se bere z menšího rozměru, takže nic nepřeteče.

### 4. Místa k ověření pod přeškálovaným Density

- Mapbox `MapView`: gesta, puck, piny kategorií, `EdgeInsets` kamery (~300 dp).
- `AppWidgetHost` na stránce Widgety: velikost widgetů se počítá z `LocalDensity`.
- `ModalBottomSheet` (přepínání Mapa/Navigace) a `AppDrawer`.
- `IncidentFab`: pozice a clamp v dp.
- Navigační banner: `MANEUVER_BANNER_END_PADDING` (72 dp) musí dál odpovídat šířce tlačítka Ukončit (obojí se škáluje stejně, ale ověřit).
- Přístupy přes `Resources.displayMetrics` nebo `px`↔`dp` konverze mimo Compose (grep při implementaci).

## Testování

- **Jednotkové testy** `computeScale`: M10 (→ 1,0), 8″ 16:10, 12,9″ 4:3, 13″, extrémně malý a velký vstup (ořez na meze).
- **Emulátory API 34 x86_64 (Google Play):** 8″ 16:10, 10,1″ 16:10, 11″ 3:2, 12,9″ 4:3, 13″. Screenshoty všech 3 stránek na každém, porovnání s M10.
- **Kritéria úspěchu:** na M10 identický vzhled, na ostatních nic neořezané ani překrývající se, touch targety ≥ 48 dp po přeškálování, mapa a navigace fungují.

## Rizika

- Komponenty pracující s fyzickými pixely (Mapbox, AppWidget) se mohou chovat odlišně, řeší bod 4.
- Systémové dialogy (oprávnění, hlasové hledání) se neškálují, což je záměr a bez dopadu.
- Dolní mez škály může na velmi malém displeji (<8″) nechat obsah těsný. Mimo rozsah.
