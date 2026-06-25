package com.example.carlauncher.ui.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Context.widgetDataStore by preferencesDataStore(name = "widgets")
private val WIDGET_IDS_KEY = stringPreferencesKey("widget_ids")
private val WIDGET_HEIGHTS_KEY = stringPreferencesKey("widget_heights")
private const val HOST_ID = 1337

@HiltViewModel
class WidgetViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    val appWidgetHost = AppWidgetHost(context, HOST_ID)
    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)

    private val _widgetIds = MutableStateFlow<List<Int>>(emptyList())
    val widgetIds: StateFlow<List<Int>> = _widgetIds.asStateFlow()

    // widgetId → height in dp
    private val _widgetHeights = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val widgetHeights: StateFlow<Map<Int, Int>> = _widgetHeights.asStateFlow()

    init {
        appWidgetHost.startListening()
        viewModelScope.launch {
            context.widgetDataStore.data.collect { prefs ->
                val raw = prefs[WIDGET_IDS_KEY] ?: ""
                _widgetIds.value = if (raw.isEmpty()) emptyList()
                else raw.split(",").mapNotNull { it.toIntOrNull() }

                val heightsRaw = prefs[WIDGET_HEIGHTS_KEY] ?: ""
                _widgetHeights.value = if (heightsRaw.isEmpty()) emptyMap()
                else heightsRaw.split(",").mapNotNull { entry ->
                    val parts = entry.split(":")
                    if (parts.size == 2) parts[0].toIntOrNull()?.let { id ->
                        parts[1].toIntOrNull()?.let { h -> id to h }
                    } else null
                }.toMap()
            }
        }
    }

    fun allocateWidgetId(): Int = appWidgetHost.allocateAppWidgetId()

    fun addWidget(appWidgetId: Int) {
        val current = _widgetIds.value.toMutableList()
        if (!current.contains(appWidgetId)) current.add(appWidgetId)
        viewModelScope.launch { persistAll(current, _widgetHeights.value) }
    }

    fun removeWidget(appWidgetId: Int) {
        appWidgetHost.deleteAppWidgetId(appWidgetId)
        val current = _widgetIds.value.toMutableList()
        current.remove(appWidgetId)
        val heights = _widgetHeights.value.toMutableMap().also { it.remove(appWidgetId) }
        viewModelScope.launch { persistAll(current, heights) }
    }

    fun setWidgetHeight(appWidgetId: Int, heightDp: Int) {
        val clamped = heightDp.coerceIn(80, 480)
        val heights = _widgetHeights.value.toMutableMap().also { it[appWidgetId] = clamped }
        _widgetHeights.value = heights
        viewModelScope.launch { persistAll(_widgetIds.value, heights) }
    }

    private suspend fun persistAll(ids: List<Int>, heights: Map<Int, Int>) {
        context.widgetDataStore.edit { prefs ->
            prefs[WIDGET_IDS_KEY] = ids.joinToString(",")
            prefs[WIDGET_HEIGHTS_KEY] = heights.entries.joinToString(",") { "${it.key}:${it.value}" }
        }
    }

    override fun onCleared() {
        super.onCleared()
        appWidgetHost.stopListening()
    }
}
