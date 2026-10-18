# Nastavení launcheru — design

**Datum:** 2026-10-10
**Stav:** schváleno (brainstorming), čeká na implementační plán

## Cíl

Přidat do menu aplikací (`AppDrawer`) vlastní ikonu „Nastavení“, která otevře obrazovku s konfigurací launcheru. První verze je záměrně malá kostra: **Oprávnění** + **O aplikaci**. Další sekce (Mapa, Incident Recorder, Vývojář, …) přibudou v dalších iteracích.

## Mimo rozsah (v1)

- **Vzhled / volba tématu** — vynecháno. Světlé téma dnes mění jen `MaterialTheme`; většina UI používá pevné tmavé `CarColors`, takže „vždy světlé“ by vypadalo rozbitě.
- **Perzistence nastavení** — v1 nic neukládá (stav oprávnění i verze se čtou živě ze systému). DataStore `launcher_prefs` + `LauncherSettingsRepository` vznikne až s první sekcí, která něco ukládá.
- Mapa, Navigace, Incident Recorder, Dock, Vývojář — budoucí iterace.

## Vstupní bod: dlaždice v menu aplikací

- `AppDrawer` dostane nový parametr `onOpenSettings: () -> Unit`.
- V `LazyVerticalGrid` se **před** `items(filtered, …)` vloží `item(key = "launcher_settings")`.
- Dlaždice je **vždy na první pozici a vždy viditelná** — vyhledávací filtr se na ni nevztahuje (není součástí `allApps` / `filtered`).
- Vzhled: 52dp zaoblený čtverec (corner 14dp, stejně jako `DrawerAppItem`), pozadí `CarColors.Accent`, ikona `Icons.Default.Settings` bílá, popisek „Nastavení“ (11sp, `CarColors.Text2`). Stejné rozměry a padding jako ostatní dlaždice.

## Architektura

Přístup: **fullscreen Compose overlay** (stejný vzor jako `AppDrawer` a `IncidentRecorderScreen`), žádná nová Activity, žádná změna manifestu.

### Nové soubory — `ui/settings/`

| Soubor | Odpovědnost |
|---|---|
| `LauncherSettingsScreen.kt` | Overlay UI: pozadí `CarColors.Bg`, hlavička „Nastavení“ + křížek (`onDismiss`), `LazyColumn` se sekcemi Oprávnění a O aplikaci. `BackHandler { onDismiss() }`. Přepočet stavu oprávnění na `ON_RESUME`. |
| `PermissionStatus.kt` | Čisté (non-Compose) funkce: model `PermissionItem` (id, název, popis, `granted: Boolean`, `settingsIntent: Intent`) a `fun permissionItems(context: Context): List<PermissionItem>`. Žádná UI logika. |

### Úpravy existujících souborů

- **`ui/dock/DockViewModel.kt`** — `showSettings: StateFlow<Boolean>`, `openSettings()` (nastaví `showSettings = true` **a** `showAppDrawer = false`), `closeSettings()`.
- **`ui/launcher/AppDrawer.kt`** — parametr `onOpenSettings` + dlaždice viz výše.
- **`ui/launcher/LauncherScreen.kt`** — `AppDrawer(onOpenSettings = { dockViewModel.openSettings() })` a druhý `AnimatedVisibility` overlay pro `LauncherSettingsScreen` vedle existujícího overlaye `AppDrawer`u (stejné enter/exit animace).

## Obsah

### Sekce „Oprávnění“

Každý řádek: ikona, název, jednořádkový popis „k čemu to je“, stavový indikátor (✅ `CarColors.Go` = povoleno / ⚠️ `CarColors.Warn` = chybí). Celý řádek je klikací, min. výška 48dp, klik spustí `settingsIntent` (vždy, i když je povoleno — umožní oprávnění i odebrat).

| Položka | Popis | Kontrola stavu | Klik otevře |
|---|---|---|---|
| Přístup k oznámením | Hudba a navigace z notifikací | `NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)` | `Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` s `EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME` = `ComponentName(context, MediaListenerService::class.java)` |
| Poloha | Mapa, GPS, počasí | `checkSelfPermission(ACCESS_FINE_LOCATION) == GRANTED` | `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` (`package:<pkg>`) |
| Kalendář | Dnešní události ve widgetu | `checkSelfPermission(READ_CALENDAR) == GRANTED` | `ACTION_APPLICATION_DETAILS_SETTINGS` |
| Kamera | Incident Recorder | `checkSelfPermission(CAMERA) == GRANTED` — **řádek jen při `BuildConfig.INCIDENT_RECORDER_ENABLED`** | `ACTION_APPLICATION_DETAILS_SETTINGS` |
| Výchozí launcher | CarLauncher jako domovská obrazovka | `context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)` (minSdk 31 → bez version checku) | `Settings.ACTION_HOME_SETTINGS` |

**Proč detail aplikace a ne runtime dialog:** po dvojím zamítnutí Android dialog už nezobrazí a klik by tiše nic neudělal. Detail aplikace funguje vždy.

Všechny intenty se spouští s `FLAG_ACTIVITY_NEW_TASK` z Activity kontextu; `ActivityNotFoundException` se chytí a fallbackne na `ACTION_APPLICATION_DETAILS_SETTINGS` (tenhle intent existuje na každém zařízení).

### Sekce „O aplikaci“

Read-only řádky: název aplikace, `BuildConfig.VERSION_NAME` (`BuildConfig.VERSION_CODE`), typ buildu `BuildConfig.BUILD_TYPE`.

## Datový tok a obnova stavu

1. `LauncherSettingsScreen` drží `var items by remember { mutableStateOf(permissionItems(context)) }`.
2. `DisposableEffect` registruje `LifecycleEventObserver` na **lifecycle Activity** (`(context as ComponentActivity).lifecycle`, ne `LocalLifecycleOwner` — ten je per-page z `HorizontalPager`u a nemusí dojet do RESUMED, viz CLAUDE.md). Na `ON_RESUME` → `items = permissionItems(context)`.
3. Uživatel klikne na řádek → systémové nastavení → změní oprávnění → Zpět → `ON_RESUME` → stav se překreslí.

Kontroly jsou levné synchronní systémové dotazy, není potřeba ViewModel ani coroutine.

## Ošetření chyb

- Chybějící systémová obrazovka (`ActivityNotFoundException`) → fallback na detail aplikace (viz výše).
- `RoleManager` nedostupný (null service) → řádek „Výchozí launcher“ zobrazí ⚠️, klik otevře `ACTION_HOME_SETTINGS`.

## Testování

V projektu zatím nejsou unit testy (`app/src/test` neexistuje). Ověření:

1. `./gradlew assembleDebug` projde.
2. Ručně na tabletu / emulátoru API 34:
   - Menu → dlaždice „Nastavení“ je první; zůstane vidět i po zadání textu do vyhledávání.
   - Klik otevře nastavení a zavře menu; systémové Zpět i křížek nastavení zavřou.
   - Odebrat přístup k oznámením → řádek ⚠️ → klik → povolit → Zpět → řádek ✅ bez znovuotevření obrazovky.
   - S `INCIDENT_RECORDER_ENABLED=false` řádek Kamera chybí.
   - Sekce O aplikaci ukazuje `1.0 (1)` a `debug`.
