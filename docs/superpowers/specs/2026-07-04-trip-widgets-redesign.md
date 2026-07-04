# Trip Computer & Jízdní deník — Redesign (větší, hezčí, vyplnění prostoru)

**Datum:** 2026-07-04
**Stav:** Schváleno uživatelem (mockup potvrzen)
**Navazuje na:** [2026-07-03-trip-computer-design.md](2026-07-03-trip-computer-design.md)

---

## Kontext

`TripComputerWidget` a `JizdniDenikWidget` jsou na 3. stránce (`TripScreen.kt`) v `Row(...).weight(1f)`, který jim dává celou dostupnou výšku obrazovky. Obsah widgetů ale používá `Column` bez `fillMaxHeight`/`weight`, takže karty se scvrknou na výšku obsahu a zůstanou přilepené nahoře — cca 80 % výšky obrazovky pod nimi je prázdný černý prostor (ověřeno screenshotem z emulátoru).

Cíl: karty mají vyplnit celou dostupnou výšku a vypadat vizuálně bohatší — bez přidávání nové datové vrstvy (žádný graf průběhu rychlosti v čase — `TripDetector` ho nesbírá a přidání by byla samostatná funkce, ne redesign). Využívají se pouze data, která `TripViewModel` už exponuje (`liveTrip`, `lastTrips` až 30 záznamů, `tripsThisWeek`).

Mockup (icon-grid + týdenní graf) byl uživateli ukázán přes vizuálního brainstorming companiona a schválen beze změn.

---

## Design

### TripComputerWidget

- Hlavička beze změny (stavová tečka + "JÍZDA PROBÍHÁ"/"Poslední jízda" + čas), o něco větší písmo.
- Místo dvou `Row` se `StatItem` páry: **2×2 grid** (`Modifier.weight(1f)` na Column i grid), který vyplní zbylou výšku.
- Každá buňka gridu = karta (`CarColors.Surface2`, rounded 16dp) s:
  - barevným ikonovým odznakem nahoře (kruh/rounded square, barva dle typu statistiky — vzdálenost = Accent, průměr = Go, maximum = Warn, trvání = Text2)
  - velkým číslem (24sp, bold) pod ikonou
  - popiskem (11sp, Text2)
- Čtyři statistiky: vzdálenost, průměr, maximum, trvání (aktivní jízda) / trvání jízdy (idle stav) — stejná data jako dnes, jen v gridu místo dvou řádků.
- Ikony: použít existující `androidx.compose.material.icons.filled` sadu (např. `Route`/`Straighten` pro vzdálenost, `Speed` pro průměr, `TrendingUp` pro maximum, `Timer` pro trvání) — konzistentní s zbytkem appky (žádné emoji, to bylo jen v HTML mockupu).

### JizdniDenikWidget

- Hlavička "Tento týden" + souhrn (km, počet jízd) beze změny, o něco větší písmo.
- **Nový prvek: sloupcový graf týdne** — 7 sloupců (Po–Ne), výška = poměr denní vzdálenosti k max. dni v týdnu, dnešní den zvýrazněný `CarColors.Accent`, ostatní `CarColors.Surface3`. Data z `tripsThisWeek` (seskupit podle dne v týdnu, sečíst `distanceKm`). Canvas-based kreslení, stejný vzor jako `SystemControlsWidget` (`Canvas` + `drawRect`).
- Seznam posledních jízd: rozšířit z `.take(2)` na `.take(4)` (data už dostupná z `lastTrips`, žádná nová query). Každý řádek dostane malou ikonovou značku (pin/location ikona v `CarColors.Surface3` odznaku) vlevo, stejně jako v mockupu.
- Tlačítko "Exportovat CSV" beze změny funkčně, jen dole pod rozšířeným seznamem.
- Celý `Column` uvnitř dostane `Modifier.weight(1f)` na sekci s grafem+historií, ať widget vyplní výšku stejně jako Trip Computer.

### Sdílené

- Nahradit hardcoded `Color(0xFF2A2C35)` (border) v obou widgetech za `CarColors.BorderSoft`, ať jsou konzistentní s tokeny.
- Žádná změna v `TripScreen.kt` layoutu (Row + weight(1f) zůstává) — problém byl uvnitř widgetů, ne v containeru.
- Žádná změna v datové vrstvě (`TripEntity`, `TripDetector`, `TripRepository`, `TripViewModel`) — čistě UI redesign nad existujícími daty.

---

## Soubory k úpravě

| Soubor | Změna |
|--------|-------|
| `ui/widgets/TripComputerWidget.kt` | 2×2 ikonový grid místo dvou řádků se statistikami |
| `ui/widgets/JizdniDenikWidget.kt` | Přidat týdenní sloupcový graf (Canvas), rozšířit historii na 4 řádky, přidat ikony u řádků |

---

## Ověření

1. `./gradlew assembleDebug` bez chyb.
2. Instalace na emulátor/tablet, screenshot přes `adb shell screencap`.
3. Vizuální porovnání s mockupem (`layout-v1.html`) — karty vyplňují výšku, graf a historie viditelné, žádný velký prázdný prostor.
4. Ověřit oba stavy `TripComputerWidget` (aktivní jízda / idle bez jízdy / idle s poslední jízdou) — GPX replay nebo emulátor route simulace.
5. Ověřit `JizdniDenikWidget` s 0 jízdami týdne (prázdný graf) i s daty (graf + historie).
