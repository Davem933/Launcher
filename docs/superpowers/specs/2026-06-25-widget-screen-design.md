# Widget Screen — Design Spec
**Date:** 2026-06-25  
**Status:** Approved

---

## 1. Overview

Add a second launcher page (WidgetScreen) accessible via swipe left from the main launcher. The screen hosts standard Android system widgets (AppWidgetHost) with free snap-to-grid placement. The main LauncherScreen is not modified.

---

## 2. Navigation

`MainActivity.setContent` wraps both screens in a `HorizontalPager` (Compose Foundation):

```
CarLauncherTheme
└── HorizontalPager(pageCount = 2, userScrollEnabled = true)
    ├── Page 0: LauncherScreen (unchanged)
    └── Page 1: WidgetScreen
```

- Page indicator: 2 semi-transparent dots rendered as an overlay on both pages, centered horizontally, positioned just above the DockBar (bottom of the content area).
- `onLaunchSplitScreen` lambda is passed to both pages — DockBar is available on both.

---

## 3. WidgetScreen Layout

```
WidgetScreen (Column, fillMaxSize, bg = CarColors.Bg)
├── StatusBar (44 dp) — same composable as LauncherScreen
├── WidgetCanvas (weight 1f)
│   ├── [AppWidgetHostView items at grid offsets]
│   └── [Empty state: "+" button centered]
└── DockBar (88 dp) — same composable, own ViewModel instance
```

---

## 4. WidgetCanvas — Snap-to-Grid

**Grid:** 4 columns × 3 rows over the available canvas area (after StatusBar + DockBar).  
Approximate cell size at 1143×686 dp target: ~240 dp wide × ~150 dp tall.

**Widget placement:**
- Each widget occupies N columns × M rows (min 1×1, max 4×3).
- Position stored as `(col, row, colSpan, rowSpan)` — all integer grid coordinates.
- On drag, the widget snaps to the nearest free grid cell on release.

**Drag interaction:**
- Long press on a widget enters edit mode: all widgets show wiggle animation (same `Animatable` pattern as DockBar slots).
- User drags widget to new position — preview shows target cell highlighted.
- Release snaps to nearest valid (non-overlapping) cell.
- Tap outside widgets or press back to exit edit mode.

**Resize interaction:**
- In edit mode, each widget shows a resize handle at bottom-right corner.
- Dragging the handle expands/contracts widget by grid cells.

**Empty state:**
- When no widgets are present, a single centered "+" button is shown (style: 72 dp circle, `CarColors.Surface` fill, `CarColors.Text2` icon, `CarColors.BorderSoft` border).
- Tapping "+" launches the widget picker.

---

## 5. AppWidgetHost Lifecycle

| Event | Action |
|-------|--------|
| `onStart` | `appWidgetHost.startListening()` |
| `onStop` | `appWidgetHost.stopListening()` |
| Widget removed | `appWidgetHost.deleteAppWidgetId(id)` |

- `HOST_ID = 1001` (fixed constant, avoids collision with other hosts).
- `AppWidgetHost` is created once and held in `WidgetViewModel` (survives recomposition).
- Lifecycle binding via `DisposableEffect(lifecycleOwner)` in `WidgetScreen`.

---

## 6. Adding a Widget

1. User taps "+" (or long-press on empty cell in edit mode).
2. `AppWidgetManager.ACTION_APPWIDGET_PICK` intent launched via `registerForActivityResult` in `MainActivity`.
3. System returns selected `appWidgetId`.
4. If widget requires configuration → `ACTION_APPWIDGET_CONFIGURE` intent launched automatically.
5. After configuration completes → widget added to first free grid cell and persisted.

`registerForActivityResult` contracts are registered in `MainActivity`, callbacks communicated to `WidgetViewModel` via a shared flow or direct call.

---

## 7. Persistence — DataStore

**Key:** `widget_slots_v1` (`stringPreferencesKey`)  
**Format:** pipe-separated entries, each `appWidgetId:col:row:colSpan:rowSpan`

Example:
```
42:0:0:2:1|87:2:1:2:2
```

- Empty string = no widgets.
- `WidgetDataStoreExt.kt` — extension on `DataStore<Preferences>`, mirrors pattern of `DockDataStoreExt.kt`.
- On delete: remove entry from DataStore + call `deleteAppWidgetId`.

---

## 8. ViewModel

**`WidgetViewModel`** (`@HiltViewModel`):
- `StateFlow<List<WidgetSlot>>` — current widget placements.
- `addWidget(appWidgetId, col, row, colSpan, rowSpan)` — persists + updates state.
- `removeWidget(appWidgetId)` — deletes id, persists.
- `moveWidget(appWidgetId, newCol, newRow)` — updates position, persists.
- `resizeWidget(appWidgetId, colSpan, rowSpan)` — updates span, persists.
- Holds `AppWidgetHost` instance (survives recomposition).

**`WidgetSlot` data class:**
```kotlin
data class WidgetSlot(
    val appWidgetId: Int,
    val col: Int,
    val row: Int,
    val colSpan: Int,
    val rowSpan: Int
)
```

---

## 9. New Files

| File | Purpose |
|------|---------|
| `ui/widgets/WidgetScreen.kt` | Root composable, Column layout |
| `ui/widgets/WidgetCanvas.kt` | Grid canvas, drag & drop, AppWidgetHostView hosting |
| `ui/widgets/WidgetViewModel.kt` | State, AppWidgetHost, persistence calls |
| `data/widgets/WidgetDataStoreExt.kt` | DataStore read/write helpers |
| `data/model/WidgetSlot.kt` | WidgetSlot data class + serialization |

---

## 10. MainActivity Changes (minimal)

- Wrap existing `LauncherScreen(...)` call in `HorizontalPager`.
- Add `registerForActivityResult` for widget picker + configuration.
- Add `registerForActivityResult` for widget configuration (`ACTION_APPWIDGET_CONFIGURE`).
- Pass `AppWidgetHost` reference to `WidgetScreen` (or inject via Hilt).

**LauncherScreen.kt — zero changes.**

---

## 11. Out of Scope

- Widget resize handles (deferred to v2 — add after basic placement works)
- Multiple widget pages / infinite scroll
- Widget overlap detection UI (first-free-cell placement avoids overlaps automatically)
