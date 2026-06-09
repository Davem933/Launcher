# Subagenti — Car Launcher Project
## Přehled a workflow

---

## Co je tady

| Soubor | Subagent | Použití |
|--------|----------|---------|
| `subagent_01_code_review.md` | 🔍 Code Review | Po dokončení každého modulu |
| `subagent_02_performance.md` | ⚡ Performance | Po GPS + MapLibre integraci, při jitteru |
| `subagent_03_uiux.md` | 🎨 UI/UX | Po každém vizuálním buildu |
| `subagent_04_architecture.md` | 🏗️ Architecture | PŘED každou novou fází |

---

## Workflow — jak to funguje

```
Claude.ai (planning)
    ↓  připraví implementační prompt
Claude Code (implementation)
    ↓  napíše modul
Ty vložíš kód do subagenta
    ↓  dostaneš konkrétní feedback
Claude.ai (opravy)
    ↓  zpracuje feedback do opravného promptu
Claude Code (fix)
    ↓  opraví
    → next iteration
```

---

## Kdy který subagent

### Fáze 1 — Project Setup
- 🏗️ **Architecture** — před startem: "jak strukturovat Hilt moduly?"

### Fáze 2 — GPS Layer
- 🏗️ **Architecture** — před implementací: "kde žije LocationProcessor?"
- ⚡ **Performance** — po implementaci: "měří Kalman filter na správném threadu?"
- 🔍 **Code Review** — po dokončení: "LocationRepository.kt + LocationProcessor.kt"

### Fáze 3 — MapWidget
- 🏗️ **Architecture** — před: "jak napojit MapLibre na StateFlow?"
- ⚡ **Performance** — po: "marker threshold, GPU calls, ValueAnimator"
- 🔍 **Code Review** — po: "MapWidget.kt + MapViewModel.kt"
- 🎨 **UI/UX** — po prvním vizuálním buildu: "speedBadge, navBtn, gpsChip"

### Fáze 4 — SpeedDisplay
- 🎨 **UI/UX** — ihned po buildu: tap target, font size, kontrast
- 🔍 **Code Review** — po buildu

### Fáze 5 — MusicWidget
- 🔍 **Code Review** — po buildu: MediaSession lifecycle
- 🎨 **UI/UX** — po buildu: artwork, play button, EQ animace

### Fáze 6 — DockBar
- 🎨 **UI/UX** — po buildu: ikona velikosti, spacing
- 🔍 **Code Review** — po buildu

### Fáze 7 — LauncherScreen Assembly
- 🏗️ **Architecture** — "jak spojit všechny ViewModely?"
- 🎨 **UI/UX** — celkový layout review
- ⚡ **Performance** — celková recomposice analýza

### Fáze 8 — Debug Tools
- 🔍 **Code Review** — "jsou debug tools obaleny v BuildConfig.DEBUG?"
- ⚡ **Performance** — "GpxReplay neovlivňuje produkční kód?"

---

## Jak použít subagent v Claude Code

1. Otevři novou Claude Code session (nebo novou konverzaci)
2. Zkopíruj celý obsah příslušného `.md` souboru jako první zprávu
3. Za prompt přidej svůj kód / otázku / problém
4. Dostaneš strukturovaný feedback
5. Přines feedback zpět do Claude.ai pro přípravu opravného promptu

---

## Quick reference — kritické metriky

| Metrika | Target | Měřit pomocí |
|---------|--------|-------------|
| GPS → UI latence | < 100 ms | `LatencyAudit` logcat tag |
| MapLibre FPS | ≥ 30 FPS | Android GPU Profiler |
| Kalman processing | < 5 ms | `GpsKalman` logcat tag |
| Startup time | < 2 s | Android Studio Launch Profiler |
| Min tap target | 48×48 dp | Layout Inspector |
| Speed font | ≥ 56sp | Code review |
| Kontrast text/bg | ≥ 4.5:1 | Accessibility Scanner app |

---

*Projekt: Android Car Launcher | Stack: Kotlin · Compose · MapLibre · Hilt | Device: Lenovo Tab M10 Plus*
