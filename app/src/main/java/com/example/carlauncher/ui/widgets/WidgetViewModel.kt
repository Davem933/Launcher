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
private const val HOST_ID = 1337

@HiltViewModel
class WidgetViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    val appWidgetHost = AppWidgetHost(context, HOST_ID)
    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)

    private val _widgetIds = MutableStateFlow<List<Int>>(emptyList())
    val widgetIds: StateFlow<List<Int>> = _widgetIds.asStateFlow()

    init {
        appWidgetHost.startListening()
        viewModelScope.launch {
            context.widgetDataStore.data.collect { prefs ->
                val raw = prefs[WIDGET_IDS_KEY] ?: ""
                _widgetIds.value = if (raw.isEmpty()) emptyList()
                else raw.split(",").mapNotNull { it.toIntOrNull() }
            }
        }
    }

    fun allocateWidgetId(): Int = appWidgetHost.allocateAppWidgetId()

    fun addWidget(appWidgetId: Int) {
        val current = _widgetIds.value.toMutableList()
        if (!current.contains(appWidgetId)) current.add(appWidgetId)
        viewModelScope.launch { persist(current) }
    }

    fun removeWidget(appWidgetId: Int) {
        appWidgetHost.deleteAppWidgetId(appWidgetId)
        val current = _widgetIds.value.toMutableList()
        current.remove(appWidgetId)
        viewModelScope.launch { persist(current) }
    }

    private suspend fun persist(ids: List<Int>) {
        context.widgetDataStore.edit { it[WIDGET_IDS_KEY] = ids.joinToString(",") }
    }

    override fun onCleared() {
        super.onCleared()
        appWidgetHost.stopListening()
    }
}
