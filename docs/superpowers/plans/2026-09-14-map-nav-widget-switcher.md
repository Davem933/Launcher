# Map/Nav Widget Switcher Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the driver long-press the left ~65%-width panel on `LauncherScreen` to choose between the live map (`MapWidget`) and navigation instructions (`NavAreaWidget`), with a new `MapWidget` default and safety-priority auto-switch back to navigation whenever a nav notification is active.

**Architecture:** A new stateful composable `MapNavPanel` wraps the existing `MapWidget`/`NavAreaWidget` composables (neither of which is modified). It resolves which one to show via a pure function of (`NavRepository.isActive`, an in-memory manual choice), and detects a 1500ms long-press using a custom low-level pointer-input gesture (so pan/zoom on the map and existing button taps keep working underneath). A `ModalBottomSheet` lets the user pick "Mapa" / "Navigace".

**Tech Stack:** Kotlin, Jetpack Compose (Foundation pointer input, Material3), Hilt (unchanged — no new DI). No new dependencies.

## Global Constraints

- Package: new file goes in `com.example.carlauncher.ui.launcher` (same package as `LauncherScreen.kt`).
- Do NOT modify `NavAreaWidget.kt`, `NavWidget.kt`, `NavRepository.kt`, or `MediaListenerService` — out of scope per spec (Phase 2).
- Manual view selection is in-memory only (`remember`, not `rememberSaveable`, not DataStore) — resets to `MAP` default on process/activity recreation.
- Long-press threshold: 1500ms hold, cancel on >12dp movement — matches the existing convention in `LongPressWidgetHostView` (`app/src/main/java/com/example/carlauncher/ui/widgets/LongPressWidgetHost.kt`).
- `NavRepository.isActive == true` always wins over manual choice (safety priority) — re-evaluated on every recomposition.
- **No new test framework/dependencies.** This repo has zero test infrastructure today (no `app/src/test`, no `testImplementation` entries in `app/build.gradle.kts`) and no precedent of testing Compose/MapLibre UI. Adding one for a single pure function would be scope creep beyond this feature. Verification is: (a) Kotlin compiles, (b) manual on-device/emulator QA — consistent with this project's existing practice for UI work.
- Emulator: API 34 x86_64 with Google Play only — do NOT use API 35+ (MapLibre's `libmaplibre.so` is not 16KB-page-aligned, per project CLAUDE.md).
- Keep the new file under 500 lines (project rule) — it will land around 150-170 lines.

---

### Task 1: Create `MapNavPanel.kt` — state model, gesture detector, and UI

**Files:**
- Create: `app/src/main/java/com/example/carlauncher/ui/launcher/MapNavPanel.kt`

**Interfaces:**
- Consumes: `NavRepository.isActive: Boolean` (`com.example.carlauncher.data.navigation.NavRepository`), `MapWidget(modifier: Modifier, onNavigate: () -> Unit, viewModel: MapViewModel = hiltViewModel())` (`com.example.carlauncher.ui.map.MapWidget`), `NavAreaWidget(speedKmh: Float, speedLimitKmh: Int, modifier: Modifier)` (`com.example.carlauncher.ui.navigation.NavAreaWidget`).
- Produces: `@Composable fun MapNavPanel(speedKmh: Float = 0f, speedLimitKmh: Int = 50, modifier: Modifier = Modifier)` — the only symbol Task 2 needs.

- [ ] **Step 1: Write the state model and long-press gesture detector**

Create `app/src/main/java/com/example/carlauncher/ui/launcher/MapNavPanel.kt` with this content:

```kotlin
package com.example.carlauncher.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.navigation.NavRepository
import com.example.carlauncher.ui.map.MapWidget
import com.example.carlauncher.ui.navigation.NavAreaWidget
import com.example.carlauncher.ui.theme.CarColors
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Which content the left panel shows. MAP is the default when no navigation is active. */
enum class PanelView { MAP, NAV }

/**
 * Active navigation always wins (safety) — otherwise fall back to the user's
 * manual choice for this session, defaulting to MAP if they haven't picked one.
 */
fun resolveEffectiveView(isNavActive: Boolean, manualView: PanelView?): PanelView =
    if (isNavActive) PanelView.NAV else manualView ?: PanelView.MAP

private const val LONG_PRESS_TIMEOUT_MS = 1500L
private val LONG_PRESS_SLOP = 12.dp

/**
 * Fires [onLongPress] after a 1500ms hold with <12dp movement. Never consumes
 * the down event, so taps/drags (map pan-zoom, buttons underneath) still work.
 */
private fun Modifier.detectPanelLongPress(onLongPress: () -> Unit): Modifier =
    this.pointerInput(Unit) {
        val slopPx = LONG_PRESS_SLOP.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val downPosition = down.position
            val longPressFired = try {
                withTimeout(LONG_PRESS_TIMEOUT_MS) {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: return@withTimeout
                        if (!change.pressed) return@withTimeout
                        if ((change.position - downPosition).getDistance() > slopPx) {
                            return@withTimeout
                        }
                    }
                }
                false
            } catch (timeout: TimeoutCancellationException) {
                true
            }
            if (longPressFired) onLongPress()
        }
    }
```

- [ ] **Step 2: Append the picker UI and the public `MapNavPanel` entry point**

Append this to the end of the same file (`app/src/main/java/com/example/carlauncher/ui/launcher/MapNavPanel.kt`):

```kotlin

@Composable
private fun PanelChoiceCard(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) CarColors.Surface2 else CarColors.Surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) CarColors.Accent else CarColors.BorderSoft,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) CarColors.Accent else CarColors.Text2,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = CarColors.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PanelPickerSheet(
    currentView: PanelView,
    onSelect: (PanelView) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CarColors.Surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "ZOBRAZENÍ PANELU",
                color = CarColors.Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PanelChoiceCard(
                    icon = Icons.Default.Map,
                    label = "Mapa",
                    selected = currentView == PanelView.MAP,
                    onClick = { onSelect(PanelView.MAP) },
                    modifier = Modifier.weight(1f),
                )
                PanelChoiceCard(
                    icon = Icons.Default.Navigation,
                    label = "Navigace",
                    selected = currentView == PanelView.NAV,
                    onClick = { onSelect(PanelView.NAV) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Left-panel switcher: shows [MapWidget] by default, or [NavAreaWidget] when
 * navigation is active or manually selected. Long-press opens a picker sheet.
 */
@Composable
fun MapNavPanel(
    speedKmh: Float = 0f,
    speedLimitKmh: Int = 50,
    modifier: Modifier = Modifier,
) {
    var manualView by remember { mutableStateOf<PanelView?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    val effectiveView = resolveEffectiveView(NavRepository.isActive, manualView)

    Box(modifier = modifier.detectPanelLongPress(onLongPress = { showPicker = true })) {
        when (effectiveView) {
            PanelView.MAP -> MapWidget(
                modifier = Modifier.fillMaxSize(),
                onNavigate = { manualView = PanelView.NAV },
            )
            PanelView.NAV -> NavAreaWidget(
                speedKmh = speedKmh,
                speedLimitKmh = speedLimitKmh,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (showPicker) {
        PanelPickerSheet(
            currentView = effectiveView,
            onSelect = { chosen ->
                manualView = chosen
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. If it fails on an unresolved `Map` icon reference, confirm `app/build.gradle.kts` still has `implementation("androidx.compose.material:material-icons-extended")` (it does as of this plan being written — do not add a second dependency).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/ui/launcher/MapNavPanel.kt
git commit -m "$(cat <<'EOF'
feat(launcher): add MapNavPanel long-press switcher between map and nav

New MapNavPanel composable resolves MAP vs NAV via a pure function of
NavRepository.isActive and an in-memory manual choice, with a custom
pointer-input long-press (1500ms, 12dp slop) that doesn't consume the
down event so map pan/zoom and existing buttons keep working. Not yet
wired into LauncherScreen.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Wire `MapNavPanel` into `LauncherScreen` and verify end-to-end

**Files:**
- Modify: `app/src/main/java/com/example/carlauncher/ui/launcher/LauncherScreen.kt:32` (import), `:76-83` (call site)

**Interfaces:**
- Consumes: `MapNavPanel(speedKmh: Float, speedLimitKmh: Int, modifier: Modifier)` from Task 1 (same package, no import needed).

- [ ] **Step 1: Remove the now-unused `NavAreaWidget` import**

In `app/src/main/java/com/example/carlauncher/ui/launcher/LauncherScreen.kt`, delete line 32:

```kotlin
import com.example.carlauncher.ui.navigation.NavAreaWidget
```

(`LauncherScreen` no longer references `NavAreaWidget` directly — `MapNavPanel` does, from the same `ui.launcher` package, so no new import is needed for it.)

- [ ] **Step 2: Replace the `NavAreaWidget` call with `MapNavPanel`**

In the same file, replace:

```kotlin
                // Left — nav area ~65% width
                NavAreaWidget(
                    speedKmh = location?.speedKmh ?: 0f,
                    speedLimitKmh = speedLimit,
                    modifier = Modifier
                        .weight(1.85f)
                        .fillMaxHeight()
                )
```

with:

```kotlin
                // Left — map/nav area ~65% width, long-press to switch (MapNavPanel)
                MapNavPanel(
                    speedKmh = location?.speedKmh ?: 0f,
                    speedLimitKmh = speedLimit,
                    modifier = Modifier
                        .weight(1.85f)
                        .fillMaxHeight()
                )
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Build, install, and manually verify on emulator or device**

Run:
```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`, produces `app/build/outputs/apk/debug/app-debug.apk`.

Install (PowerShell, absolute path per project convention):
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r "C:\Users\David\carLauncher\CarLauncher\.claude\worktrees\sleepy-mclean-db2994\app\build\outputs\apk\debug\app-debug.apk"
```
Expected: `Success`. Use an API 34 x86_64 emulator (not API 35+) or the physical Lenovo tablet per CLAUDE.md.

Then manually walk through this checklist on-device:
1. Launch the app — the left panel shows the live map (`MapWidget`), not the old landing screen. (Confirms `MAP` is now the default.)
2. Pan/zoom the map with normal touch gestures — still works exactly as before wiring. (Confirms the long-press detector doesn't consume touch events.)
3. Tap and hold anywhere on the left panel for ~1.5s without moving your finger — a bottom sheet appears with "Mapa" and "Navigace" cards.
4. Tap "Navigace" in the sheet — the panel switches to the nav landing screen (`NavLanding`, "Kde jedete?"). Long-press again, tap "Mapa" — panel switches back to the map.
5. Tap the green "Navigovat" button on the map — the panel switches to the nav landing screen (same as step 4's destination), confirming the `onNavigate` wiring.
6. If a real or test navigation notification is active (e.g., start turn-by-turn in Google Maps or Mapy.cz per the existing notification-listener setup), confirm the panel shows `NavWidget` regardless of whatever you last manually picked, and that it stays on `NavWidget` even if you long-press and pick "Mapa" (safety priority — the picker sheet closes but the panel snaps right back to nav).

Expected: all six checks pass. If check 2 fails (map stops panning after this change), the long-press detector is consuming events it shouldn't — re-check that `down.consume()` / `change.consume()` is never called anywhere in `detectPanelLongPress`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/ui/launcher/LauncherScreen.kt
git commit -m "$(cat <<'EOF'
feat(launcher): wire MapNavPanel into LauncherScreen

Map is now the default left-panel view; navigation instructions still
take over automatically when a nav notification is active. Long-press
lets the driver switch manually. NavAreaWidget/NavWidget/NavRepository
are unchanged — only the call site moved.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```
