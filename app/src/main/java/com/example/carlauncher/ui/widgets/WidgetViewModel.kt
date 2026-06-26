package com.example.carlauncher.ui.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
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
private val TEMPLATE_KEY = stringPreferencesKey("layout_template")
private const val HOST_ID = 1337

@HiltViewModel
class WidgetViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    val appWidgetHost = AppWidgetHost(context, HOST_ID)
    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)

    private val _widgetIds = MutableStateFlow<List<Int>>(emptyList())
    val widgetIds: StateFlow<List<Int>> = _widgetIds.asStateFlow()

    private val _template = MutableStateFlow(WidgetLayoutTemplate.GRID_2X2)
    val template: StateFlow<WidgetLayoutTemplate> = _template.asStateFlow()

    init {
        appWidgetHost.startListening()
        viewModelScope.launch {
            context.widgetDataStore.data.collect { prefs ->
                val raw = prefs[WIDGET_IDS_KEY] ?: ""
                _widgetIds.value = if (raw.isEmpty()) emptyList()
                else raw.split(",").mapNotNull { it.toIntOrNull() }

                val tmpl = prefs[TEMPLATE_KEY] ?: ""
                _template.value = WidgetLayoutTemplate.entries
                    .firstOrNull { it.name == tmpl } ?: WidgetLayoutTemplate.GRID_2X2
            }
        }
    }

    fun allocateWidgetId(): Int = appWidgetHost.allocateAppWidgetId()

    fun addWidget(appWidgetId: Int) {
        appWidgetHost.startListening()
        val current = _widgetIds.value.toMutableList()
        if (!current.contains(appWidgetId)) {
            current.add(appWidgetId)
            // Ask the provider to push fresh RemoteViews immediately.
            // Without this the host may miss the initial update and show blank.
            val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
            info?.provider?.let { provider ->
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    component = provider
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
                }
                context.sendBroadcast(intent)
            }
        }
        viewModelScope.launch { persist(current) }
    }

    fun removeWidget(appWidgetId: Int) {
        appWidgetHost.deleteAppWidgetId(appWidgetId)
        val current = _widgetIds.value.toMutableList()
        current.remove(appWidgetId)
        viewModelScope.launch { persist(current) }
    }

    fun canAddWidget(): Boolean = _widgetIds.value.size < _template.value.slotCount

    fun setTemplate(tmpl: WidgetLayoutTemplate) {
        val maxSlots = tmpl.slotCount
        val current = _widgetIds.value
        // Unbind widgets that exceed the new template's slot count
        if (current.size > maxSlots) {
            current.drop(maxSlots).forEach { appWidgetHost.deleteAppWidgetId(it) }
        }
        val trimmed = current.take(maxSlots)
        _widgetIds.value = trimmed
        viewModelScope.launch { persist(trimmed, tmpl) }
    }

    private suspend fun persist(ids: List<Int>, tmpl: WidgetLayoutTemplate = _template.value) {
        context.widgetDataStore.edit { prefs ->
            prefs[WIDGET_IDS_KEY] = ids.joinToString(",")
            prefs[TEMPLATE_KEY] = tmpl.name
        }
    }

    override fun onCleared() {
        super.onCleared()
        appWidgetHost.stopListening()
    }
}
