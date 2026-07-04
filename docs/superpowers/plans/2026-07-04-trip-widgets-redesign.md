# Trip Widgets Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign `TripComputerWidget` and `JizdniDenikWidget` (3. stránka / `TripScreen.kt`) so they fill their full allocated height with a richer layout, using only data `TripViewModel` already exposes.

**Architecture:** Pure Compose UI change in two existing widget files. No new data sources — `TripComputerWidget` switches from a two-row stat layout to a 2×2 icon-grid; `JizdniDenikWidget` gains a `Canvas`-drawn weekly distance bar chart (same drawing pattern as `SystemControlsWidget`) and expands its trip history from 2 to 4 rows with a small location icon per row.

**Tech Stack:** Jetpack Compose, Material3, `androidx.compose.material:material-icons-extended` (already a dependency — no new deps needed).

## Global Constraints

- Reuse `CarColors` tokens for all colors — no new hardcoded hex values (existing hardcoded `Color(0xFF2A2C35)` borders get replaced with `CarColors.BorderSoft`).
- No changes to `TripEntity`, `TripDetector`, `TripRepository`, or `TripViewModel` — this is a UI-only redesign (see spec: [2026-07-04-trip-widgets-redesign.md](../specs/2026-07-04-trip-widgets-redesign.md)).
- No changes to `TripScreen.kt` layout (the `Row(...).weight(1f)` container is already correct — the fix is inside the widgets).
- This project has no Compose UI test scaffolding, so verification is build success + on-device screenshot comparison against the approved mockup, not unit tests.

---

### Task 1: TripComputerWidget — 2×2 icon-grid stat layout

**Files:**
- Modify: `app/src/main/java/com/example/carlauncher/ui/widgets/TripComputerWidget.kt` (full replace)

**Interfaces:**
- Consumes: `TripViewModel.liveTrip: StateFlow<LiveTripState>` (unchanged), `LiveTripState.Active(distanceKm, durationSec, avgSpeedKmh, maxSpeedKmh)`, `LiveTripState.Idle(lastTrip: TripEntity?)`, `TripEntity(distanceKm, avgSpeedKmh, maxSpeedKmh, startTime, endTime, startAddress, endAddress)` — all pre-existing, no signature changes.
- Produces: `TripComputerWidget(modifier, viewModel)` composable — same public signature as before, safe to call unchanged from `TripScreen.kt`.

- [ ] **Step 1: Replace the file contents**

Write this complete file to `app/src/main/java/com/example/carlauncher/ui/widgets/TripComputerWidget.kt`:

```kotlin
package com.example.carlauncher.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.data.trip.LiveTripState
import com.example.carlauncher.data.trip.TripEntity
import com.example.carlauncher.ui.theme.CarColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun TripComputerWidget(
    modifier: Modifier = Modifier,
    viewModel: TripViewModel = hiltViewModel()
) {
    val liveTrip by viewModel.liveTrip.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(CarColors.Surface)
            .border(1.dp, CarColors.BorderSoft, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        when (val state = liveTrip) {
            is LiveTripState.Active -> ActiveTrip(state)
            is LiveTripState.Idle   -> IdleTrip(state.lastTrip)
        }
    }
}

@Composable
private fun ColumnScope.ActiveTrip(state: LiveTripState.Active) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(8.dp)) { drawCircle(color = CarColors.Go) }
        Text(
            text = "  JÍZDA PROBÍHÁ",
            color = CarColors.Go,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatDuration(state.durationSec),
            color = CarColors.Go,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
    Spacer(Modifier.height(12.dp))
    StatGrid(
        distanceKm  = state.distanceKm,
        avgSpeedKmh = state.avgSpeedKmh,
        maxSpeedKmh = state.maxSpeedKmh,
        durationSec = state.durationSec,
        modifier    = Modifier.weight(1f)
    )
}

@Composable
private fun ColumnScope.IdleTrip(lastTrip: TripEntity?) {
    if (lastTrip == null) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Žádná jízda", color = CarColors.Text2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(text = "Start detekován automaticky", color = CarColors.Text3, fontSize = 11.sp)
            }
        }
        return
    }
    Text(
        text = "Poslední jízda",
        color = CarColors.Text2,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
    )
    Text(text = relativeDate(lastTrip.startTime), color = CarColors.Text2, fontSize = 11.sp)
    Spacer(Modifier.height(12.dp))
    StatGrid(
        distanceKm  = lastTrip.distanceKm,
        avgSpeedKmh = lastTrip.avgSpeedKmh,
        maxSpeedKmh = lastTrip.maxSpeedKmh,
        durationSec = (lastTrip.endTime - lastTrip.startTime) / 1000,
        modifier    = Modifier.weight(1f)
    )
}

@Composable
private fun StatGrid(
    distanceKm: Float,
    avgSpeedKmh: Float,
    maxSpeedKmh: Float,
    durationSec: Long,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Icons.Filled.Route, "%.1f km".format(distanceKm), "vzdálenost", CarColors.Accent, Modifier.weight(1f).fillMaxHeight())
            StatCard(Icons.Filled.Speed, "${avgSpeedKmh.toInt()} km/h", "průměr", CarColors.Go, Modifier.weight(1f).fillMaxHeight())
        }
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Icons.Filled.TrendingUp, "${maxSpeedKmh.toInt()} km/h", "maximum", CarColors.Warn, Modifier.weight(1f).fillMaxHeight())
            StatCard(Icons.Filled.Timer, formatDuration(durationSec), "trvání", CarColors.Text2, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CarColors.Surface2)
            .padding(12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(text = value, color = CarColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = CarColors.Text2, fontSize = 11.sp)
    }
}

private fun formatDuration(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

private fun relativeDate(epochMs: Long): String {
    val diffMs = System.currentTimeMillis() - epochMs
    return when {
        diffMs < TimeUnit.HOURS.toMillis(1) -> "před méně než hodinou"
        diffMs < TimeUnit.DAYS.toMillis(1)  -> "dnes"
        diffMs < TimeUnit.DAYS.toMillis(2)  -> "včera"
        else -> SimpleDateFormat("d. M. yyyy", Locale("cs")).format(Date(epochMs))
    }
}
```

- [ ] **Step 2: Build**

Run: `cd app/.. && ./gradlew assembleDebug` (from repo root)
Expected: `BUILD SUCCESSFUL`. If it fails with `Unresolved reference` on `Route`/`Speed`/`Timer`/`TrendingUp`, confirm `app/build.gradle.kts` still has `implementation("androidx.compose.material:material-icons-extended")` (it does — no action needed, just a sanity check).

- [ ] **Step 3: Install and visually verify**

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices
& $adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

Open CarLauncher, swipe to the 3rd page (Trip screen). Confirm:
- The left card (Trip Computer) now fills the full card height with a 2×2 grid of icon stat cards (no more empty space below two plain rows).
- Icons are colored (blue/green/amber/gray) and visible, not blank boxes.
- If no trip has ever been recorded, the empty state ("Žádná jízda") is centered in the card, not stuck at the top with empty space below.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/ui/widgets/TripComputerWidget.kt
git commit -m "feat: redesign TripComputerWidget as 2x2 icon stat grid"
```

---

### Task 2: JizdniDenikWidget — weekly bar chart + expanded history

**Files:**
- Modify: `app/src/main/java/com/example/carlauncher/ui/widgets/JizdniDenikWidget.kt` (full replace)

**Interfaces:**
- Consumes: `TripViewModel.lastTrips: StateFlow<List<TripEntity>>`, `TripViewModel.tripsThisWeek: StateFlow<List<TripEntity>>`, `TripViewModel.exportCsv(context: Context)` — all pre-existing, no signature changes. `TripEntity` fields used: `distanceKm: Float`, `startTime: Long`, `endAddress: String`, `startAddress: String`.
- Produces: `JizdniDenikWidget(modifier, onOpenHistory, viewModel)` composable — same public signature as before, safe to call unchanged from `TripScreen.kt`.

- [ ] **Step 1: Replace the file contents**

Write this complete file to `app/src/main/java/com/example/carlauncher/ui/widgets/JizdniDenikWidget.kt`:

```kotlin
package com.example.carlauncher.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.data.trip.TripEntity
import com.example.carlauncher.ui.theme.CarColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun JizdniDenikWidget(
    modifier: Modifier = Modifier,
    onOpenHistory: () -> Unit = {},
    viewModel: TripViewModel = hiltViewModel()
) {
    val context   = LocalContext.current
    val lastTrips by viewModel.lastTrips.collectAsStateWithLifecycle()
    val weekTrips by viewModel.tripsThisWeek.collectAsStateWithLifecycle()

    val weekKm      = weekTrips.sumOf { it.distanceKm.toDouble() }.toFloat()
    val weekCount   = weekTrips.size
    val recentTrips = lastTrips.take(4)
    val distances   = remember(weekTrips) { weeklyDistances(weekTrips) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(CarColors.Surface)
            .border(1.dp, CarColors.BorderSoft, RoundedCornerShape(16.dp))
            .clickable { onOpenHistory() }
            .padding(14.dp)
    ) {
        Text(text = "Tento týden", color = CarColors.Text2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = "%.0f km  ·  %d jízd".format(weekKm, weekCount),
            color = CarColors.Text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(10.dp))
        WeekBarChart(distances = distances, modifier = Modifier.weight(1f))

        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = CarColors.BorderSoft)

        Column(modifier = Modifier.weight(1f)) {
            if (recentTrips.isEmpty()) {
                Text(text = "Zatím žádné jízdy", color = CarColors.Text2, fontSize = 11.sp)
            } else {
                recentTrips.forEach { trip -> RecentTripRow(trip) }
            }
        }

        Button(
            onClick = { viewModel.exportCsv(context) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CarColors.Surface3),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(text = "Exportovat CSV", color = CarColors.Text, fontSize = 12.sp)
        }
    }
}

@Composable
private fun WeekBarChart(distances: FloatArray, modifier: Modifier = Modifier) {
    val maxDist  = remember(distances) { distances.maxOrNull()?.takeIf { it > 0f } ?: 1f }
    val todayIdx = remember { todayMondayIndex() }
    val dayLabels = listOf("Po", "Út", "St", "Čt", "Pá", "So", "Ne")
    val accent  = CarColors.Accent
    val neutral = CarColors.Surface3

    Column(modifier = modifier) {
        Text(
            text = "KM PO DNECH",
            color = CarColors.Text3,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.08.em,
        )
        Spacer(Modifier.height(8.dp))
        Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val barCount  = distances.size
            val gap       = 6.dp.toPx()
            val barWidth  = (size.width - gap * (barCount - 1)) / barCount
            distances.forEachIndexed { i, dist ->
                val fraction   = (dist / maxDist).coerceIn(0f, 1f)
                val barHeight  = (size.height * fraction).coerceAtLeast(3.dp.toPx())
                val x          = i * (barWidth + gap)
                drawRoundRect(
                    color      = if (i == todayIdx) accent else neutral,
                    topLeft    = Offset(x, size.height - barHeight),
                    size       = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            dayLabels.forEachIndexed { i, label ->
                Text(
                    text = label,
                    color = if (i == todayIdx) CarColors.Accent else CarColors.Text3,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Index 0=Po..6=Ne, summed distanceKm per weekday for the given trips. */
private fun weeklyDistances(trips: List<TripEntity>): FloatArray {
    val result = FloatArray(7)
    trips.forEach { trip ->
        val cal = Calendar.getInstance().apply { timeInMillis = trip.startTime }
        val dow = cal.get(Calendar.DAY_OF_WEEK) // Sunday=1..Saturday=7
        val idx = (dow + 5) % 7                 // remap to Monday=0..Sunday=6
        result[idx] += trip.distanceKm
    }
    return result
}

private fun todayMondayIndex(): Int {
    val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    return (dow + 5) % 7
}

@Composable
private fun RecentTripRow(trip: TripEntity) {
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dateLabel = when {
        isToday(trip.startTime)     -> "Dnes"
        isYesterday(trip.startTime) -> "Včera"
        else -> SimpleDateFormat("d. M.", Locale("cs")).format(Date(trip.startTime))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CarColors.Surface3),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = CarColors.Text2,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$dateLabel ${timeFmt.format(Date(trip.startTime))}",
                color = CarColors.Text2,
                fontSize = 10.sp
            )
            val dest = trip.endAddress.ifEmpty { trip.startAddress.ifEmpty { "?" } }
            Text(text = dest.take(30), color = CarColors.Text, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        Text(
            text = "%.1f km".format(trip.distanceKm),
            color = CarColors.Text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun isToday(epochMs: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = epochMs }
    val c2 = Calendar.getInstance()
    return c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR) &&
           c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
}

private fun isYesterday(epochMs: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = epochMs }
    val c2 = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    return c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR) &&
           c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
}
```

- [ ] **Step 2: Build**

Run: `./gradlew assembleDebug` (from repo root)
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Install and visually verify**

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

Open CarLauncher, swipe to the 3rd page. Confirm:
- Right card (Jízdní deník) fills the full height: week summary at top, a 7-bar chart (Po–Ne) in the middle with today's bar highlighted in accent blue, up to 4 recent trips below with a small location-pin icon each, Export CSV button at the bottom.
- If there are zero trips this week, all 7 bars render as thin flat lines (not crashes, not `NaN` heights) — `weeklyDistances` returning all-zero is handled by `maxDist` falling back to `1f`.
- Take a screenshot (`adb shell screencap`) and compare visually against both cards side by side — confirm no large empty black area remains below either card.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/ui/widgets/JizdniDenikWidget.kt
git commit -m "feat: add weekly bar chart and expand trip history in JizdniDenikWidget"
```

---

## Final verification (after both tasks)

1. `./gradlew assembleDebug` succeeds with both files changed together.
2. Install on the physical tablet (not just emulator) — confirm layout looks correct on the real 10" landscape screen, not just the emulator's aspect ratio.
3. Compare against the approved mockup (`layout-v1.html` from the brainstorming session) — same structure: icon grid left, bar chart + history right.
4. Confirm no regressions: `TripScreen.kt` still shows `DockBar` below, tapping the Jízdní deník card still opens `TripHistoryScreen`, "Exportovat CSV" still triggers the share sheet.
