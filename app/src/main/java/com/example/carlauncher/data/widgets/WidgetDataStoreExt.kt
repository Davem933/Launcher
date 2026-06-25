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

private val WIDGET_KEY = stringPreferencesKey("widget_slots_v1")

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
