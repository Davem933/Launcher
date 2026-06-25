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
