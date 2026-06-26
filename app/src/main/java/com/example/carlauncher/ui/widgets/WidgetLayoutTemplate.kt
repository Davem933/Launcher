package com.example.carlauncher.ui.widgets

/**
 * Defines how the widget grid is divided.
 * All templates use a 2-column LazyVerticalGrid; span controls width per slot.
 *
 * Slot index mapping:
 *   GRID_2X2          : 0 1 / 2 3   (each span=1)
 *   WIDE_TOP_TWO_BOTTOM: 0   / 1 2  (slot 0 span=2, slots 1-2 span=1)
 *   TWO_WIDE_ROWS     : 0   / 1     (each span=2)
 */
enum class WidgetLayoutTemplate(
    val slotCount: Int,
    val spans: List<Int>          // column span (1 or 2) per slot index
) {
    GRID_2X2(
        slotCount = 4,
        spans = listOf(1, 1, 1, 1)
    ),
    WIDE_TOP_TWO_BOTTOM(
        slotCount = 3,
        spans = listOf(2, 1, 1)
    ),
    TWO_WIDE_ROWS(
        slotCount = 2,
        spans = listOf(2, 2)
    );

    fun labelCs(): String = when (this) {
        GRID_2X2             -> "2 × 2"
        WIDE_TOP_TWO_BOTTOM  -> "Velký + 2 malé"
        TWO_WIDE_ROWS        -> "2 řádky"
    }
}
