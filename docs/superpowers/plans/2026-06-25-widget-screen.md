# Widget Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a second launcher page (WidgetScreen) with snap-to-grid Android system widget hosting, reachable by swiping left from the main launcher.

**Architecture:** `MainActivity` is wrapped in a `HorizontalPager` (2 pages). Page 0 = existing `LauncherScreen` (untouched). Page 1 = new `WidgetScreen` with `AppWidgetHost`-backed widget canvas. Widgets are stored in a dedicated DataStore (`widget_prefs`). `WidgetViewModel` holds the `AppWidgetHost` singleton and exposes `StateFlow<List<WidgetSlot>>`.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, `androidx.compose.foundation.pager`, `AppWidgetHost` / `AppWidgetManager` (Android framework), `AndroidView`, DataStore Preferences.

## Global Constraints

- minSdk 31, compileSdk/targetSdk 36
- Landscape-only, fullscreen (no system bars)
- Dark theme only (`CarColors` tokens: `Bg #0D0E12`, `Surface #161820`, `Text2`, `BorderSoft`)
- `LauncherScreen.kt` must not be modified
- No new dependencies — only Android framework + libraries already in `build.gradle`
- Keep all new files under 500 lines
- DataStore key: `"widget_slots_v1"`, separator `|`, field separator `:`

---

### Task 1: WidgetSlot data class + serialization

**Files:**
- Create: `app/src/main/java/com/example/carlauncher/data/model/WidgetSlot.kt`
- Create: `app/src/test/java/com/example/carlauncher/data/model/WidgetSlotTest.kt`

**Interfaces:**
- Produces: `WidgetSlot(appWidgetId, col, row, colSpan, rowSpan)`, `WidgetSlot.serialize(): String`, `String.toWidgetSlot(): WidgetSlot?`, `List<WidgetSlot>.serializeAll(): String`, `String.toWidgetSlots(): List<WidgetSlot>` — used by Task 2 and Task 3.

- [ ] **Step 1: Create the data class and serialization functions**

```kotlin
// app/src/main/java/com/example/carlauncher/data/model/WidgetSlot.kt
package com.example.carlauncher.data.model

data class WidgetSlot(
    val appWidgetId: Int,
    val col: Int,
    val row: Int,
    val colSpan: Int,
    val rowSpan: Int
)

fun WidgetSlot.serialize(): String = "$appWidgetId:$col:$row:$colSpan:$rowSpan"

fun String.toWidgetSlot(): WidgetSlot? = runCatching {
    val p = split(":")
    require(p.size == 5)
    WidgetSlot(p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4].toInt())
}.getOrNull()

fun List<WidgetSlot>.serializeAll(): String = joinToString("|") { it.serialize() }

fun String.toWidgetSlots(): List<WidgetSlot> =
    if (isBlank()) emptyList()
    else split("|").mapNotNull { it.toWidgetSlot() }
```

- [ ] **Step 2: Write unit tests**

```kotlin
// app/src/test/java/com/example/carlauncher/data/model/WidgetSlotTest.kt
package com.example.carlauncher.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetSlotTest {

    @Test
    fun `serialize round-trips single slot`() {
        val slot = WidgetSlot(42, 1, 2, 2, 1)
        assertEquals(slot, slot.serialize().toWidgetSlot())
    }

    @Test
    fun `serializeAll round-trips list`() {
        val slots = listOf(WidgetSlot(1, 0, 0, 2, 1), WidgetSlot(7, 2, 1, 2, 2))
        assertEquals(slots, slots.serializeAll().toWidgetSlots())
    }

    @Test
    fun `blank string returns empty list`() {
        assertEquals(emptyList<WidgetSlot>(), "".toWidgetSlots())
    }

    @Test
    fun `malformed entry returns null`() {
        assertNull("bad:data".toWidgetSlot())
    }

    @Test
    fun `malformed entries are skipped in list`() {
        val raw = "42:0:0:2:1|bad|7:2:1:1:1"
        val result = raw.toWidgetSlots()
        assertEquals(2, result.size)
        assertEquals(42, result[0].appWidgetId)
        assertEquals(7, result[1].appWidgetId)
    }
}
```

- [ ] **Step 3: Run tests**

```bash
./gradlew test --tests "com.example.carlauncher.data.model.WidgetSlotTest"
```

Expected: 5 tests pass, 0 failures.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/data/model/WidgetSlot.kt \
        app/src/test/java/com/example/carlauncher/data/model/WidgetSlotTest.kt
git commit -m "feat: add WidgetSlot data class with serialization"
```

---

### Task 2: WidgetDataStoreExt + firstFreeCell logic

**Files:**
- Create: `app/src/main/java/com/example/carlauncher/data/widgets/WidgetDataStoreExt.kt`
- Create: `app/src/test/java/com/example/carlauncher/data/widgets/GridUtilTest.kt`

**Interfaces:**
- Consumes: `WidgetSlot`, `serializeAll()`, `toWidgetSlots()` from Task 1
- Produces: `Context.widgetDataStore`, `DataStore<Preferences>.readWidgetSlots(): List<WidgetSlot>`, `DataStore<Preferences>.writeWidgetSlots(slots)`, `firstFreeCell(slots, colSpan, rowSpan): Pair<Int,Int>?` — used by Task 3

- [ ] **Step 1: Create the DataStore extension file**

```kotlin
// app/src/main/java/com/example/carlauncher/data/widgets/WidgetDataStoreExt.kt
package com.example.carlauncher.data.widgets

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.carlauncher.data.model.WidgetSlot
import com.example.carlauncher.data.model.serializeAll
import com.example.carlauncher.data.model.toWidgetSlots
import kotlinx.coroutines.flow.first

val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "widget_prefs")

internal val WIDGET_KEY = stringPreferencesKey("widget_slots_v1")

const val GRID_COLS = 4
const val GRID_ROWS = 3

suspend fun DataStore<Preferences>.readWidgetSlots(): List<WidgetSlot> =
    data.first()[WIDGET_KEY].orEmpty().toWidgetSlots()

suspend fun DataStore<Preferences>.writeWidgetSlots(slots: List<WidgetSlot>) {
    edit { it[WIDGET_KEY] = slots.serializeAll() }
}

fun firstFreeCell(slots: List<WidgetSlot>, colSpan: Int, rowSpan: Int): Pair<Int, Int>? {
    for (row in 0..(GRID_ROWS - rowSpan)) {
        for (col in 0..(GRID_COLS - colSpan)) {
            val free = slots.none { s ->
                col < s.col + s.colSpan &&
                col + colSpan > s.col &&
                row < s.row + s.rowSpan &&
                row + rowSpan > s.row
            }
            if (free) return col to row
        }
    }
    return null
}
```

- [ ] **Step 2: Write unit tests for firstFreeCell**

```kotlin
// app/src/test/java/com/example/carlauncher/data/widgets/GridUtilTest.kt
package com.example.carlauncher.data.widgets

import com.example.carlauncher.data.model.WidgetSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GridUtilTest {

    @Test
    fun `firstFreeCell returns 0,0 when grid is empty`() {
        assertEquals(0 to 0, firstFreeCell(emptyList(), 2, 1))
    }

    @Test
    fun `firstFreeCell skips occupied top-left area`() {
        val occupied = listOf(WidgetSlot(1, 0, 0, 2, 1))
        assertEquals(2 to 0, firstFreeCell(occupied, 2, 1))
    }

    @Test
    fun `firstFreeCell wraps to next row when row is full`() {
        val occupied = listOf(
            WidgetSlot(1, 0, 0, 2, 1),
            WidgetSlot(2, 2, 0, 2, 1)
        )
        assertEquals(0 to 1, firstFreeCell(occupied, 2, 1))
    }

    @Test
    fun `firstFreeCell returns null when no room for given span`() {
        val slots = (0 until GRID_COLS).flatMap { col ->
            (0 until GRID_ROWS).map { row -> WidgetSlot(col * GRID_ROWS + row, col, row, 1, 1) }
        }
        assertNull(firstFreeCell(slots, 1, 1))
    }

    @Test
    fun `firstFreeCell respects colSpan boundary`() {
        // col 3 is free but only 1 col wide — a 2-wide widget can't fit there
        val occupied = listOf(WidgetSlot(1, 0, 0, 3, 1))
        // Next free start for 2-wide: row 1, col 0
        assertEquals(0 to 1, firstFreeCell(occupied, 2, 1))
    }
}
```

- [ ] **Step 3: Run tests**

```bash
./gradlew test --tests "com.example.carlauncher.data.widgets.GridUtilTest"
```

Expected: 5 tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/data/widgets/WidgetDataStoreExt.kt \
        app/src/test/java/com/example/carlauncher/data/widgets/GridUtilTest.kt
git commit -m "feat: add widget DataStore helpers and grid placement logic"
```

---

### Task 3: WidgetViewModel

**Files:**
- Create: `app/src/main/java/com/example/carlauncher/ui/widgets/WidgetViewModel.kt`

**Interfaces:**
- Consumes: `WidgetSlot`, `Context.widgetDataStore`, `readWidgetSlots()`, `writeWidgetSlots()`, `firstFreeCell()` from Tasks 1–2
- Produces: `WidgetViewModel` with `appWidgetHost: AppWidgetHost`, `appWidgetManager: AppWidgetManager`, `slots: StateFlow<List<WidgetSlot>>`, `allocateWidgetId(): Int`, `addWidget(appWidgetId: Int)`, `removeWidget(appWidgetId: Int)`, `moveWidget(appWidgetId: Int, col: Int, row: Int)` — used by Tasks 4–6.

- [ ] **Step 1: Create WidgetViewModel**

```kotlin
// app/src/main/java/com/example/carlauncher/ui/widgets/WidgetViewModel.kt
package com.example.carlauncher.ui.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.carlauncher.data.model.WidgetSlot
import com.example.carlauncher.data.widgets.firstFreeCell
import com.example.carlauncher.data.widgets.readWidgetSlots
import com.example.carlauncher.data.widgets.widgetDataStore
import com.example.carlauncher.data.widgets.writeWidgetSlots
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WidgetViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    val appWidgetHost: AppWidgetHost = AppWidgetHost(context, HOST_ID)
    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)

    private val _slots = MutableStateFlow<List<WidgetSlot>>(emptyList())
    val slots: StateFlow<List<WidgetSlot>> = _slots.asStateFlow()

    init {
        viewModelScope.launch {
            _slots.value = context.widgetDataStore.readWidgetSlots()
        }
    }

    fun allocateWidgetId(): Int = appWidgetHost.allocateAppWidgetId()

    fun addWidget(appWidgetId: Int) {
        val (col, row) = firstFreeCell(_slots.value, 2, 1) ?: (0 to 0)
        persist(_slots.value + WidgetSlot(appWidgetId, col, row, 2, 1))
    }

    fun removeWidget(appWidgetId: Int) {
        appWidgetHost.deleteAppWidgetId(appWidgetId)
        persist(_slots.value.filter { it.appWidgetId != appWidgetId })
    }

    fun moveWidget(appWidgetId: Int, col: Int, row: Int) {
        persist(_slots.value.map {
            if (it.appWidgetId == appWidgetId) it.copy(col = col, row = row) else it
        })
    }

    private fun persist(slots: List<WidgetSlot>) {
        _slots.value = slots
        viewModelScope.launch { context.widgetDataStore.writeWidgetSlots(slots) }
    }

    companion object {
        const val HOST_ID = 1001
    }
}
```

- [ ] **Step 2: Verify the project builds**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL, no compilation errors.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/ui/widgets/WidgetViewModel.kt
git commit -m "feat: add WidgetViewModel with AppWidgetHost and DataStore persistence"
```

---

### Task 4: WidgetCanvas composable

**Files:**
- Create: `app/src/main/java/com/example/carlauncher/ui/widgets/WidgetCanvas.kt`

**Interfaces:**
- Consumes: `WidgetSlot` from Task 1, `GRID_COLS`, `GRID_ROWS` from Task 2, `AppWidgetHost`, `AppWidgetManager` from Task 3
- Produces: `WidgetCanvas(slots, appWidgetHost, appWidgetManager, onAddWidget, onRemoveWidget, onMoveWidget, modifier)` — used by Task 5

- [ ] **Step 1: Create WidgetCanvas.kt**

```kotlin
// app/src/main/java/com/example/carlauncher/ui/widgets/WidgetCanvas.kt
package com.example.carlauncher.ui.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.carlauncher.data.model.WidgetSlot
import com.example.carlauncher.data.widgets.GRID_COLS
import com.example.carlauncher.data.widgets.GRID_ROWS
import com.example.carlauncher.ui.theme.CarColors
import kotlin.math.roundToInt

@Composable
fun WidgetCanvas(
    slots: List<WidgetSlot>,
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager,
    onAddWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onMoveWidget: (appWidgetId: Int, col: Int, row: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var editMode by remember { mutableStateOf(false) }
    var canvasWidthPx by remember { mutableStateOf(0) }
    var canvasHeightPx by remember { mutableStateOf(0) }

    if (slots.isEmpty() && !editMode) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            AddWidgetButton(onClick = onAddWidget)
        }
        return
    }

    Box(
        modifier = modifier
            .onSizeChanged { canvasWidthPx = it.width; canvasHeightPx = it.height }
            .pointerInput(Unit) { detectTapGestures { editMode = false } }
    ) {
        val cellWidthPx = canvasWidthPx.toFloat() / GRID_COLS
        val cellHeightPx = canvasHeightPx.toFloat() / GRID_ROWS

        slots.forEach { slot ->
            key(slot.appWidgetId) {
                WidgetItem(
                    slot = slot,
                    cellWidthPx = cellWidthPx,
                    cellHeightPx = cellHeightPx,
                    editMode = editMode,
                    appWidgetHost = appWidgetHost,
                    appWidgetManager = appWidgetManager,
                    onLongPress = { editMode = true },
                    onRemove = { onRemoveWidget(slot.appWidgetId) },
                    onMove = { col, row -> onMoveWidget(slot.appWidgetId, col, row) }
                )
            }
        }

        if (editMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)  // note: need import for padding
            ) {
                AddWidgetButton(onClick = { editMode = false; onAddWidget() })
            }
        }
    }
}

@Composable
private fun WidgetItem(
    slot: WidgetSlot,
    cellWidthPx: Float,
    cellHeightPx: Float,
    editMode: Boolean,
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager,
    onLongPress: () -> Unit,
    onRemove: () -> Unit,
    onMove: (col: Int, row: Int) -> Unit
) {
    val context = LocalContext.current
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    val xPx = (slot.col * cellWidthPx + dragOffsetX).roundToInt()
    val yPx = (slot.row * cellHeightPx + dragOffsetY).roundToInt()
    val widthPx = (slot.colSpan * cellWidthPx).roundToInt()
    val heightPx = (slot.rowSpan * cellHeightPx).roundToInt()

    Box(
        modifier = Modifier
            .offset { IntOffset(xPx, yPx) }
            // size in pixels via layout
            .then(
                Modifier.size(
                    width = with(androidx.compose.ui.platform.LocalDensity.current) { widthPx.toDp() },
                    height = with(androidx.compose.ui.platform.LocalDensity.current) { heightPx.toDp() }
                )
            )
            .rotate(if (editMode) 2f else 0f)
            .pointerInput(editMode) {
                if (!editMode) {
                    detectTapGestures(onLongPress = { onLongPress() })
                } else {
                    detectDragGestures(
                        onDragEnd = {
                            val newCol = ((slot.col * cellWidthPx + dragOffsetX) / cellWidthPx)
                                .roundToInt().coerceIn(0, GRID_COLS - slot.colSpan)
                            val newRow = ((slot.row * cellHeightPx + dragOffsetY) / cellHeightPx)
                                .roundToInt().coerceIn(0, GRID_ROWS - slot.rowSpan)
                            onMove(newCol, newRow)
                            dragOffsetX = 0f
                            dragOffsetY = 0f
                        },
                        onDragCancel = { dragOffsetX = 0f; dragOffsetY = 0f },
                        onDrag = { _, amount ->
                            dragOffsetX += amount.x
                            dragOffsetY += amount.y
                        }
                    )
                }
            }
    ) {
        val info = remember(slot.appWidgetId) {
            appWidgetManager.getAppWidgetInfo(slot.appWidgetId)
        }
        AndroidView(
            factory = { ctx ->
                appWidgetHost.createView(ctx, slot.appWidgetId, info) as AppWidgetHostView
            },
            modifier = Modifier.fillMaxSize()
        )

        if (editMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF3B30))
                    .pointerInput(Unit) { detectTapGestures(onTap = { onRemove() }) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Odebrat widget",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun AddWidgetButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(CarColors.Surface)
            .border(1.dp, CarColors.BorderSoft, CircleShape)
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Přidat widget",
            tint = CarColors.Text2,
            modifier = Modifier.size(32.dp)
        )
    }
}
```

> **Note on touch events:** `AndroidView` (AppWidgetHostView) consumes touch events in normal mode. In edit mode the overlay Box intercepts touches before they reach the widget. If a widget intercepts `ACTION_DOWN` before Compose processes it, drag may require wrapping in `DisposableEffect` to disable widget touch in edit mode — test this during manual verification.

- [ ] **Step 2: Fix any missing imports and build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL. If `LocalDensity` is used inside a `pointerInput` lambda (which is not `@Composable`), refactor `widthPx`/`heightPx` to be computed in the composable scope before the `Modifier.size` call.

**Fix if density error:** Replace the `Modifier.size(...)` call with density computed outside:

```kotlin
val density = LocalDensity.current
val widthDp = with(density) { widthPx.toDp() }
val heightDp = with(density) { heightPx.toDp() }
// then: .size(widthDp, heightDp)
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/ui/widgets/WidgetCanvas.kt
git commit -m "feat: add WidgetCanvas with snap-to-grid drag and empty state"
```

---

### Task 5: WidgetScreen composable

**Files:**
- Create: `app/src/main/java/com/example/carlauncher/ui/widgets/WidgetScreen.kt`

**Interfaces:**
- Consumes: `WidgetCanvas` from Task 4, `WidgetViewModel` from Task 3, `StatusBar` from `ui/launcher/StatusBar.kt`, `DockBar` from `ui/dock/DockBar.kt`
- Produces: `WidgetScreen(onLaunchSplitScreen, onAddWidget, modifier)` — used by Task 6

- [ ] **Step 1: Create WidgetScreen.kt**

```kotlin
// app/src/main/java/com/example/carlauncher/ui/widgets/WidgetScreen.kt
package com.example.carlauncher.ui.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.ui.dock.DockBar
import com.example.carlauncher.ui.launcher.StatusBar

@Composable
fun WidgetScreen(
    onLaunchSplitScreen: (pkg1: String, pkg2: String) -> Unit,
    onAddWidget: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WidgetViewModel = hiltViewModel()
) {
    val slots by viewModel.slots.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.appWidgetHost.startListening()
                Lifecycle.Event.ON_STOP  -> viewModel.appWidgetHost.stopListening()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        StatusBar(
            gpsFix = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        )
        WidgetCanvas(
            slots = slots,
            appWidgetHost = viewModel.appWidgetHost,
            appWidgetManager = viewModel.appWidgetManager,
            onAddWidget = onAddWidget,
            onRemoveWidget = viewModel::removeWidget,
            onMoveWidget = viewModel::moveWidget,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        DockBar(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 8.dp),
            onLaunchSplitScreen = onLaunchSplitScreen
        )
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/ui/widgets/WidgetScreen.kt
git commit -m "feat: add WidgetScreen with lifecycle-bound AppWidgetHost"
```

---

### Task 6: MainActivity — HorizontalPager + widget picker

**Files:**
- Modify: `app/src/main/java/com/example/carlauncher/MainActivity.kt`

**Interfaces:**
- Consumes: `WidgetScreen(onLaunchSplitScreen, onAddWidget)` from Task 5, `WidgetViewModel` from Task 3
- Produces: Working 2-page launcher with page indicator dots and full widget picker flow

- [ ] **Step 1: Read the current MainActivity before editing**

Read `app/src/main/java/com/example/carlauncher/MainActivity.kt` (already done above — it is 98 lines, ends at line 98).

- [ ] **Step 2: Rewrite MainActivity with HorizontalPager + widget picker**

Replace the full file content with:

```kotlin
package com.example.carlauncher

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.carlauncher.ui.launcher.LauncherScreen
import com.example.carlauncher.ui.theme.CarLauncherTheme
import com.example.carlauncher.ui.widgets.WidgetScreen
import com.example.carlauncher.ui.widgets.WidgetViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val widgetViewModel: WidgetViewModel by viewModels()

    // Tracks the widget ID currently being bound / configured
    private var pendingWidgetId by mutableIntStateOf(-1)

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* LocationRepository handles SecurityException on denial */ }

    // Step 1: user picks a widget from system chooser
    private val widgetPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val id = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
            if (id != -1) configureOrAdd(id)
        } else if (pendingWidgetId != -1) {
            widgetViewModel.appWidgetHost.deleteAppWidgetId(pendingWidgetId)
            pendingWidgetId = -1
        }
    }

    // Step 2 (optional): widget requires its own configuration Activity
    private val widgetConfigureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && pendingWidgetId != -1) {
            widgetViewModel.addWidget(pendingWidgetId)
        } else if (pendingWidgetId != -1) {
            widgetViewModel.appWidgetHost.deleteAppWidgetId(pendingWidgetId)
        }
        pendingWidgetId = -1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        setContent {
            CarLauncherTheme {
                val pagerState = rememberPagerState(pageCount = { 2 })

                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (page) {
                            0 -> LauncherScreen(
                                onLaunchSplitScreen = { pkg1, pkg2 -> launchSplitScreen(pkg1, pkg2) }
                            )
                            1 -> WidgetScreen(
                                onLaunchSplitScreen = { pkg1, pkg2 -> launchSplitScreen(pkg1, pkg2) },
                                onAddWidget = { launchWidgetPicker() }
                            )
                        }
                    }

                    // Page indicator — 2 dots above DockBar
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 100.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        repeat(2) { i ->
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (pagerState.currentPage == i)
                                            Color.White
                                        else
                                            Color.White.copy(alpha = 0.35f)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun launchWidgetPicker() {
        pendingWidgetId = widgetViewModel.allocateWidgetId()
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
        }
        widgetPickerLauncher.launch(intent)
    }

    private fun configureOrAdd(appWidgetId: Int) {
        pendingWidgetId = appWidgetId
        val info = widgetViewModel.appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (info?.configure != null) {
            val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = info.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            widgetConfigureLauncher.launch(configIntent)
        } else {
            widgetViewModel.addWidget(appWidgetId)
            pendingWidgetId = -1
        }
    }

    // ── Split screen ──────────────────────────────────────────────────────────

    private fun launchSplitScreen(pkg1: String, pkg2: String) {
        val opened = launchPackage(pkg1)
        if (!opened) {
            Toast.makeText(this, "Aplikaci nelze spustit", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            delay(650L)
            launchPackageAdjacent(pkg2)
        }
    }

    private fun launchPackage(packageName: String): Boolean {
        return runCatching {
            val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            Log.d("SplitScreen", "launched: $packageName")
            true
        }.getOrDefault(false)
    }

    private fun launchPackageAdjacent(packageName: String): Boolean {
        return runCatching {
            val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
                intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            startActivity(intent)
            Log.d("SplitScreen", "launched adjacent: $packageName")
            true
        }.getOrDefault(false)
    }
}
```

- [ ] **Step 3: Build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL. Common issue: `mutableIntStateOf` requires `androidx.compose.runtime.getValue/setValue` imports — verify they are present in the import block above.

- [ ] **Step 4: Install and manually verify**

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Verification checklist:**
- [ ] Main launcher (page 0) loads and works identically to before
- [ ] Swipe left → WidgetScreen (page 1) with StatusBar + empty "+" button + DockBar
- [ ] Page indicator dots visible on both pages, active dot changes on swipe
- [ ] Tap "+" → system widget picker opens
- [ ] Select a widget → widget appears on canvas at grid position 0:0
- [ ] Swipe back to page 0 → launcher works normally (GPS, map, music)
- [ ] Long press widget → widget tilts 2° (edit mode)
- [ ] Drag widget → snaps to new grid cell on release
- [ ] Red "×" button visible in edit mode → tap removes widget
- [ ] Kill app and reopen → widgets persist on page 2
- [ ] DockBar split-screen launch works from page 2

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/MainActivity.kt
git commit -m "feat: widget screen - HorizontalPager, widget picker, page indicator"
```

---

## Self-Review

**Spec coverage check:**
- ✅ HorizontalPager 2-page navigation
- ✅ WidgetScreen with StatusBar + WidgetCanvas + DockBar
- ✅ Snap-to-grid 4×3
- ✅ AppWidgetHost lifecycle (startListening/stopListening via DisposableEffect)
- ✅ Empty state "+" button
- ✅ Widget picker via ACTION_APPWIDGET_PICK
- ✅ Widget configuration via ACTION_APPWIDGET_CONFIGURE
- ✅ registerForActivityResult in MainActivity
- ✅ DataStore persistence (widget_slots_v1)
- ✅ Long press → edit mode → drag → snap
- ✅ Remove button in edit mode
- ✅ LauncherScreen.kt untouched
- ✅ Page indicator dots
- ⏭ Resize handles — explicitly deferred to v2 per spec

**Type consistency:**
- `WidgetSlot` defined in Task 1, consumed in Tasks 2–5 ✅
- `firstFreeCell` defined in Task 2, called in Task 3 ✅
- `WidgetViewModel.addWidget(appWidgetId: Int)` defined in Task 3, called in Task 6 ✅
- `WidgetViewModel.removeWidget(appWidgetId: Int)` defined in Task 3, called in Task 4 ✅
- `WidgetViewModel.moveWidget(appWidgetId: Int, col: Int, row: Int)` defined in Task 3, called in Task 4 ✅
- `WidgetScreen(onLaunchSplitScreen, onAddWidget)` defined in Task 5, called in Task 6 ✅
- `WidgetCanvas(slots, appWidgetHost, appWidgetManager, onAddWidget, onRemoveWidget, onMoveWidget)` defined in Task 4, called in Task 5 ✅
