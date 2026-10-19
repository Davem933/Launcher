# Výkonnostní testy

Dvě úrovně měření, obě na **fyzickém tabletu** (Lenovo Tab M10 Plus 3rd Gen, TB311FU,
Helio G80, Android 16). Emulátor na výkon nepoužívat — čísla z něj nic neříkají.

**Nikdy neměřit debug build.** Compose je v debugu výrazně pomalejší (studený start ~1,9 s
vs. ~1,0 s, viz [výsledky](2026-10-19-baseline.md)). Na měření je build typ `benchmark`
(`app/build.gradle.kts`): release (s R8 + Baseline Profile), podepsaný debug klíčem, `profileable`, vlastní
`applicationId` `com.example.carlauncher.benchmark` — instaluje se vedle debug buildu a
není výchozí HOME, takže ho systém po zabití procesu sám nespouští (jinak by „studený“
start nebyl studený).

Worktree nemá `local.properties` → Gradle potřebuje `ANDROID_HOME`:

```bash
export ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"
```

## Fáze 0 — rychlé ruční měření (`scripts/perf/baseline.sh`)

Bez instrumentace, jen `adb`: 10× studený start (`am start -W`), plynulost swipe mezi
stránkami a posunu mapy (`dumpsys gfxinfo`), paměť (`dumpsys meminfo`).

```bash
./gradlew :app:assembleBenchmark
adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk
bash scripts/perf/baseline.sh com.example.carlauncher.benchmark 10
```

Skript tablet probudí a odemkne (jen zámek bez PINu) — na uspaném displeji gfxinfo hlásí
0 snímků.

## Fáze 1 — Macrobenchmark (`:macrobenchmark`)

Opakovatelné testy přes `androidx.benchmark.macro` + UiAutomator:

| Test | Co měří |
|---|---|
| `StartupBenchmark.coldStartNoCompilation` | TTID hned po instalaci (vše interpretováno) — nejhorší případ |
| `StartupBenchmark.coldStartBaselineProfile` | TTID hned po instalaci, zkompilovaný jen přibalený Baseline Profile — to, co dostane uživatel |
| `StartupBenchmark.warmStartBaselineProfile` | teplý start, totéž |
| `StartupBenchmark.coldStartWarmedUp` | TTID po „zahřátí“ (ART JIT profil, bez Baseline Profile) — srovnání s fází 1 |
| `StartupBenchmark.warmStartWarmedUp` | teplý start, totéž |
| `FrameTimingBenchmark.pagerSwipe` | swipe Launcher → Widgety → Trip a zpět |
| `FrameTimingBenchmark.mapPan` | posun Mapbox mapy (3D Standard, pitch 45°) |
| `FrameTimingBenchmark.appDrawerOpenAndScroll` | otevření AppDraweru + fling mřížkou |

```bash
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
```

Pod 25 % baterie knihovna odmítne měřit (`LOW-BATTERY`). Pokud tablet nabíjí a nemá zapnutý
úsporný režim, jde to jednorázově potlačit (čísla pak berte s rezervou):

```bash
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=LOW-BATTERY
```

`androidx.benchmark` musí být **≥ 1.5.0** — 1.3.x na Android 16 nenajde proces appky.

Trvá ~20 min. Výsledky: `macrobenchmark/build/outputs/connected_android_test_additional_output/benchmark/connected/<device>/`
(`*-benchmarkData.json` + Perfetto `*.perfetto-trace` pro každou iteraci — otevřít v
https://ui.perfetto.dev).

Frame testy předpokládají výchozí vzhled **Klasický** s dockem dole (souřadnice tahů jsou
zlomky obrazovky). Mapa tahá dlaždice ze sítě → porovnávat jen běhy na stejné Wi-Fi.

## Baseline Profile (fáze 2)

Profil horkých cest (start, swipe stránek, AppDrawer) je v
`app/src/main/generated/baselineProfiles/` a `androidx.profileinstaller` ho při instalaci
předá ART → kód je předkompilovaný hned po instalaci. Generuje ho
`BaselineProfileGenerator` v `:macrobenchmark` nad variantou `nonMinifiedRelease` (vytváří ji
plugin; id `com.example.carlauncher.benchmark`, takže nepřepíše debug build). Po větších
změnách UI přegenerovat (~10 min) a commitnout oba soubory:

```bash
./gradlew :app:generateBaselineProfile -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=LOW-BATTERY
```

Generátor i frame testy sdílí cesty uživatele z `BenchmarkSetup.kt`.

## R8

Release (a tím i `benchmark`) má zapnuté `isMinifyEnabled` + `isShrinkResources`, pravidla
v `app/proguard-rules.pro`. Mapbox v logcatu i s R8 vypisuje `ClassNotFoundException:
com.mapbox.common.…` z nativních vláken (`System.err`, ne pád) — funkčnost to neovlivňuje.
Po změně závislostí proklikat na tabletu mapu, vyhledávání + navigaci, Incident Recorder a
hlasové poznámky (enumy se ukládají jménem → pravidlo `-keepclassmembers enum`).

## Jak číst čísla

- `timeToInitialDisplayMs` — první vykreslený frame. Pod ~1 s je OK pro launcher.
- `frameDurationCpuMs` — čas CPU na snímek; při 60 Hz je rozpočet 16,7 ms.
- `frameOverrunMs` — o kolik snímek přetáhl deadline (záporné = v pořádku). P90+ kladné = viditelné trhání.

Výsledky jednotlivých měření: `docs/perf/<datum>-*.md`.
