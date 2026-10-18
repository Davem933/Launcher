# Nastavení launcheru — implementační plán

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dlaždice „Nastavení“ na první pozici menu aplikací otevře fullscreen obrazovku se sekcemi Oprávnění (živý stav + odkaz do systému) a O aplikaci.

**Architecture:** Čistá logika stavu oprávnění v `ui/settings/PermissionStatus.kt` (žádný Compose), UI v `ui/settings/LauncherSettingsScreen.kt` jako overlay ve stejném vzoru jako `AppDrawer`. Viditelnost overlaye drží `DockViewModel` (vedle `showAppDrawer`), `LauncherScreen` ho vykresluje přes `AnimatedVisibility`. Nic se neukládá.

**Tech Stack:** Kotlin, Jetpack Compose (Material3 + material-icons-extended), AndroidX Activity/Lifecycle, Hilt (jen existující `DockViewModel`).

**Spec:** `docs/superpowers/specs/2026-10-10-launcher-settings-design.md`

## Global Constraints

- minSdk 31 → `RoleManager` a `ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` (API 30) bez version checků.
- Barvy jen z `CarColors` (`ui/theme/CarColors.kt`): stav OK = `CarColors.Go`, chybí = `CarColors.Warn`, dlaždice = `CarColors.Accent`.
- Všechny touch targety min. 48×48 dp.
- Lifecycle pro `ON_RESUME` = lifecycle **Activity** (`context as ComponentActivity`), nikdy `LocalLifecycleOwner` (per-page z `HorizontalPager`u).
- Řádek Kamera jen při `BuildConfig.INCIDENT_RECORDER_ENABLED`.
- Texty UI česky. Commity bez `Co-Authored-By` (pravidlo z `C:\Users\David\CLAUDE.md`).
- V projektu nejsou unit testy ani testovací závislosti; veškerá logika jsou systémové dotazy (bez Robolectricu netestovatelné) → ověření = `./gradlew assembleDebug` + ruční test na zařízení. Testovací infrastrukturu **nepřidávat** (YAGNI).
- Build příkazy spouštět z kořene worktree `C:\Users\David\orca\workspaces\CarLauncher\testWorkTreeDesignChange`.

## File Structure

| Soubor | Akce | Odpovědnost |
|---|---|---|
| `app/src/main/java/com/example/carlauncher/ui/settings/PermissionStatus.kt` | Create | Model `PermissionItem`, `permissionItems(context)`, `openSystemSettings(context, intent)` |
| `app/src/main/java/com/example/carlauncher/ui/settings/LauncherSettingsScreen.kt` | Create | Overlay UI, `BackHandler`, refresh na `ON_RESUME` |
| `app/src/main/java/com/example/carlauncher/ui/dock/DockViewModel.kt` | Modify (ř. 58–62) | `showSettings`, `openSettings()`, `closeSettings()` |
| `app/src/main/java/com/example/carlauncher/ui/launcher/AppDrawer.kt` | Modify | Parametr `onOpenSettings`, dlaždice `SettingsTile` jako první položka mřížky |
| `app/src/main/java/com/example/carlauncher/ui/launcher/LauncherScreen.kt` | Modify | Druhý `AnimatedVisibility` overlay |
| `CLAUDE.md` | Modify | Zmínka o `ui/settings/` + krátká sekce modulu |

---

### Task 1: Logika stavu oprávnění (`PermissionStatus.kt`)

**Files:**
- Create: `app/src/main/java/com/example/carlauncher/ui/settings/PermissionStatus.kt`

**Interfaces:**
- Consumes: `com.example.carlauncher.service.MediaListenerService` (existuje), `com.example.carlauncher.BuildConfig.INCIDENT_RECORDER_ENABLED`.
- Produces:
  - `enum class PermissionId { NOTIFICATIONS, LOCATION, CALENDAR, CAMERA, HOME_LAUNCHER }`
  - `data class PermissionItem(val id: PermissionId, val title: String, val description: String, val granted: Boolean, val settingsIntent: Intent)`
  - `fun permissionItems(context: Context): List<PermissionItem>`
  - `fun openSystemSettings(context: Context, intent: Intent)`

- [ ] **Step 1: Vytvořit soubor**

```kotlin
package com.example.carlauncher.ui.settings

import android.Manifest
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.carlauncher.BuildConfig
import com.example.carlauncher.service.MediaListenerService

enum class PermissionId { NOTIFICATIONS, LOCATION, CALENDAR, CAMERA, HOME_LAUNCHER }

/** One row of the Settings → Oprávnění section. Plain data — icons are mapped by [id] in the UI. */
data class PermissionItem(
    val id: PermissionId,
    val title: String,
    val description: String,
    val granted: Boolean,
    val settingsIntent: Intent,
)

/**
 * Current state of everything the launcher needs from the system. Cheap synchronous
 * queries — safe to call on the main thread on every ON_RESUME.
 */
fun permissionItems(context: Context): List<PermissionItem> = buildList {
    add(
        PermissionItem(
            id = PermissionId.NOTIFICATIONS,
            title = "Přístup k oznámením",
            description = "Hudba a navigace z notifikací",
            granted = NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName),
            settingsIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(context, MediaListenerService::class.java).flattenToString()
                ),
        )
    )
    add(
        PermissionItem(
            id = PermissionId.LOCATION,
            title = "Poloha",
            description = "Mapa, GPS a počasí",
            granted = isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION),
            settingsIntent = appDetailsIntent(context),
        )
    )
    add(
        PermissionItem(
            id = PermissionId.CALENDAR,
            title = "Kalendář",
            description = "Dnešní události ve widgetu",
            granted = isGranted(context, Manifest.permission.READ_CALENDAR),
            settingsIntent = appDetailsIntent(context),
        )
    )
    if (BuildConfig.INCIDENT_RECORDER_ENABLED) {
        add(
            PermissionItem(
                id = PermissionId.CAMERA,
                title = "Kamera",
                description = "Incident Recorder",
                granted = isGranted(context, Manifest.permission.CAMERA),
                settingsIntent = appDetailsIntent(context),
            )
        )
    }
    add(
        PermissionItem(
            id = PermissionId.HOME_LAUNCHER,
            title = "Výchozí launcher",
            description = "CarLauncher jako domovská obrazovka",
            granted = context.getSystemService(RoleManager::class.java)
                ?.isRoleHeld(RoleManager.ROLE_HOME) == true,
            settingsIntent = Intent(Settings.ACTION_HOME_SETTINGS),
        )
    )
}

/**
 * Opens [intent]; if the device has no such settings screen, falls back to this app's
 * details page, which exists everywhere. Runtime permissions deliberately go through the
 * details page too — after two denials Android stops showing the request dialog.
 */
fun openSystemSettings(context: Context, intent: Intent) {
    try {
        context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(appDetailsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun isGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun appDetailsIntent(context: Context): Intent =
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )
```

- [ ] **Step 2: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL` (soubor zatím nikdo nevolá — jen ověření, že se kompiluje).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/ui/settings/PermissionStatus.kt
git commit -m "feat(settings): add permission status checks for launcher settings"
```

---

### Task 2: Obrazovka nastavení (`LauncherSettingsScreen.kt`)

**Files:**
- Create: `app/src/main/java/com/example/carlauncher/ui/settings/LauncherSettingsScreen.kt`

**Interfaces:**
- Consumes (Task 1): `PermissionId`, `PermissionItem`, `permissionItems(context: Context): List<PermissionItem>`, `openSystemSettings(context: Context, intent: Intent)`.
- Produces: `@Composable fun LauncherSettingsScreen(onDismiss: () -> Unit)`

- [ ] **Step 1: Vytvořit soubor**

```kotlin
package com.example.carlauncher.ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.carlauncher.BuildConfig
import com.example.carlauncher.ui.theme.CarColors

@Composable
fun LauncherSettingsScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var permissions by remember { mutableStateOf(permissionItems(context)) }

    // Re-check on return from system settings. Activity lifecycle, not LocalLifecycleOwner —
    // the latter is per-page from HorizontalPager and need not reach RESUMED.
    val activity = context as ComponentActivity
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissions = permissionItems(context)
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CarColors.Bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Nastavení",
                    style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = CarColors.Text)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Zavřít", tint = CarColors.Text2)
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { SectionHeader("Oprávnění") }
                items(permissions, key = { it.id }) { item ->
                    PermissionRow(
                        item = item,
                        onClick = { openSystemSettings(context, item.settingsIntent) }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
                item { SectionHeader("O aplikaci") }
                item { InfoRow("Aplikace", "CarLauncher") }
                item { InfoRow("Verze", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") }
                item { InfoRow("Build", BuildConfig.BUILD_TYPE) }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CarColors.Text3),
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun PermissionRow(item: PermissionItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(CarColors.Surface, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(item.id.icon(), contentDescription = null, tint = CarColors.Text2, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = CarColors.Text))
            Text(item.description, style = TextStyle(fontSize = 13.sp, color = CarColors.Text3))
        }
        Spacer(Modifier.width(16.dp))
        Icon(
            imageVector = if (item.granted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = if (item.granted) "Povoleno" else "Chybí",
            tint = if (item.granted) CarColors.Go else CarColors.Warn,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(CarColors.Surface, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = TextStyle(fontSize = 15.sp, color = CarColors.Text2))
        Text(value, style = TextStyle(fontSize = 15.sp, color = CarColors.Text))
    }
}

private fun PermissionId.icon(): ImageVector = when (this) {
    PermissionId.NOTIFICATIONS -> Icons.Default.Notifications
    PermissionId.LOCATION      -> Icons.Default.LocationOn
    PermissionId.CALENDAR      -> Icons.Default.CalendarMonth
    PermissionId.CAMERA        -> Icons.Default.PhotoCamera
    PermissionId.HOME_LAUNCHER -> Icons.Default.Home
}
```

- [ ] **Step 2: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/ui/settings/LauncherSettingsScreen.kt
git commit -m "feat(settings): add launcher settings screen with permissions and about"
```

---

### Task 3: Napojení — dlaždice v menu, stav v ViewModelu, overlay, dokumentace

**Files:**
- Modify: `app/src/main/java/com/example/carlauncher/ui/dock/DockViewModel.kt:58-62`
- Modify: `app/src/main/java/com/example/carlauncher/ui/launcher/AppDrawer.kt`
- Modify: `app/src/main/java/com/example/carlauncher/ui/launcher/LauncherScreen.kt`
- Modify: `CLAUDE.md`

**Interfaces:**
- Consumes (Task 2): `LauncherSettingsScreen(onDismiss: () -> Unit)`.
- Produces: `DockViewModel.showSettings: StateFlow<Boolean>`, `openSettings()`, `closeSettings()`; `AppDrawer(onAppClick, onOpenSettings, onDismiss)`.

- [ ] **Step 1: `DockViewModel` — stav overlaye**

Nahradit blok (ř. 58–62):

```kotlin
    private val _showAppDrawer = MutableStateFlow(false)
    val showAppDrawer: StateFlow<Boolean> = _showAppDrawer.asStateFlow()

    fun openAppDrawer()  { _showAppDrawer.value = true }
    fun closeAppDrawer() { _showAppDrawer.value = false }
```

za:

```kotlin
    private val _showAppDrawer = MutableStateFlow(false)
    val showAppDrawer: StateFlow<Boolean> = _showAppDrawer.asStateFlow()

    fun openAppDrawer()  { _showAppDrawer.value = true }
    fun closeAppDrawer() { _showAppDrawer.value = false }

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    /** Opened from the AppDrawer tile — replaces the drawer rather than stacking on it. */
    fun openSettings() {
        _showAppDrawer.value = false
        _showSettings.value = true
    }
    fun closeSettings() { _showSettings.value = false }
```

- [ ] **Step 2: `AppDrawer` — parametr a dlaždice**

a) Signatura:

```kotlin
@Composable
fun AppDrawer(
    onAppClick: (packageName: String) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
```

b) V `LazyVerticalGrid` vložit první položku **před** `items(filtered, …)` — mimo `filtered`, takže ji vyhledávání nikdy neschová:

```kotlin
                    item(key = "launcher_settings") {
                        SettingsTile(onClick = onOpenSettings)
                    }
                    items(filtered, key = { it.packageName }) { app ->
```

c) Na konec souboru přidat:

```kotlin
@Composable
private fun SettingsTile(onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(CarColors.Accent, shape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Nastavení launcheru",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Nastavení",
            style = TextStyle(fontSize = 11.sp, color = CarColors.Text2),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
```

d) Importy přidat:

```kotlin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.Color
```

- [ ] **Step 3: `LauncherScreen` — overlay**

a) K ostatním `collectAsStateWithLifecycle` přidat:

```kotlin
    val showSettings   by dockViewModel.showSettings.collectAsStateWithLifecycle()
```

b) Volání `AppDrawer` rozšířit o `onOpenSettings`:

```kotlin
        AppDrawer(
            onAppClick = { packageName ->
                dockViewModel.closeAppDrawer()
                dockViewModel.launchApp(packageName)
            },
            onOpenSettings = { dockViewModel.openSettings() },
            onDismiss = { dockViewModel.closeAppDrawer() }
        )
```

c) Hned za `AnimatedVisibility` blok `AppDrawer`u (před `} // end outer Box`) přidat:

```kotlin
    // ── Launcher settings overlay — opened from the AppDrawer's first tile ────
    AnimatedVisibility(
        visible = showSettings,
        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 8 },
        exit  = fadeOut(tween(150))
    ) {
        LauncherSettingsScreen(onDismiss = { dockViewModel.closeSettings() })
    }
```

d) Import:

```kotlin
import com.example.carlauncher.ui.settings.LauncherSettingsScreen
```

- [ ] **Step 4: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. Pokud jiné místo volá `AppDrawer(...)` bez `onOpenSettings`, kompilátor ho nahlásí — `grep -rn "AppDrawer(" app/src/main/java` má ukázat jen `LauncherScreen.kt` a definici.

- [ ] **Step 5: Instalace a ruční ověření**

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r "C:\Users\David\orca\workspaces\CarLauncher\testWorkTreeDesignChange\app\build\outputs\apk\debug\app-debug.apk"
& $adb shell dumpsys package com.example.carlauncher | Select-String "lastUpdateTime"
```

Expected: `Success` a `lastUpdateTime` odpovídá právě teď (ne jen hláška Success).

Ručně na zařízení:
1. Menu → první dlaždice je modrá „Nastavení“ s ozubeným kolem.
2. Napsat do hledání „youtube“ → dlaždice Nastavení zůstane na první pozici.
3. Klik → menu zmizí, otevře se Nastavení. Systémové Zpět zavře; znovu otevřít, křížek zavře.
4. Systém: odebrat přístup k oznámením CarLauncheru → v Nastavení řádek „Přístup k oznámením“ ⚠️ → klik → otevře se detail listeneru → povolit → Zpět → řádek ✅ bez zavření obrazovky.
5. Klik na „Poloha“ → otevře se detail aplikace CarLauncher.
6. Klik na „Výchozí launcher“ → otevře se systémová volba domovské appky; stav ✅ pokud je CarLauncher výchozí.
7. Sekce O aplikaci: `1.0 (1)`, `debug`.

- [ ] **Step 6: Aktualizovat `CLAUDE.md`**

a) V bloku `Architecture` pod řádek `  theme/        CarLauncherTheme (dark only), CarColors` přidat:

```
  settings/     LauncherSettingsScreen (overlay z AppDrawer dlaždice), PermissionStatus
```

b) Před sekci `## GPS Pipeline` přidat:

```markdown
## Module: Nastavení launcheru (ui/settings/)

První dlaždice v `AppDrawer` („Nastavení“, `CarColors.Accent`) — mimo `filtered` seznam, takže ji vyhledávání neschová. Otevře `LauncherSettingsScreen` jako fullscreen overlay v `LauncherScreen`; viditelnost drží `DockViewModel.showSettings` (`openSettings()` zároveň zavře drawer).

**Oprávnění** — `PermissionStatus.kt` (čisté funkce, bez Compose): přístup k oznámením, poloha, kalendář, kamera (jen při `INCIDENT_RECORDER_ENABLED`), výchozí launcher (`RoleManager.ROLE_HOME`). Klik vždy otevírá systémové nastavení, ne runtime dialog (ten po dvojím zamítnutí mlčí); `ActivityNotFoundException` → fallback na detail aplikace. Stav se přepočítá na `ON_RESUME` lifecycle **Activity**.

**O aplikaci** — `VERSION_NAME`, `VERSION_CODE`, `BUILD_TYPE`.

Nic se zatím neukládá — DataStore `launcher_prefs` vznikne až s první sekcí, která něco persistuje.
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/ui/dock/DockViewModel.kt app/src/main/java/com/example/carlauncher/ui/launcher/AppDrawer.kt app/src/main/java/com/example/carlauncher/ui/launcher/LauncherScreen.kt CLAUDE.md
git commit -m "feat(settings): open launcher settings from the app drawer's first tile"
```
