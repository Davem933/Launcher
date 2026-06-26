package com.example.carlauncher.ui.widgets

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

/** One slot in the grid — holds an ordered list of widget IDs (the "stack"). */
data class WidgetStack(val widgetIds: List<Int> = emptyList())

private val Context.widgetDataStore by preferencesDataStore(name = "widgets")
private val STACKS_KEY   = stringPreferencesKey("widget_stacks")
private val TEMPLATE_KEY = stringPreferencesKey("layout_template")
private const val HOST_ID = 1337

@HiltViewModel
class WidgetViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    val appWidgetHost    = LongPressWidgetHost(context, HOST_ID)
    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)

    private val _template = MutableStateFlow(WidgetLayoutTemplate.GRID_2X2)
    val template: StateFlow<WidgetLayoutTemplate> = _template.asStateFlow()

    /** One WidgetStack per slot; size always equals template.slotCount. */
    private val _stacks = MutableStateFlow<List<WidgetStack>>(emptyList())
    val stacks: StateFlow<List<WidgetStack>> = _stacks.asStateFlow()

    init {
        appWidgetHost.startListening()
        viewModelScope.launch {
            context.widgetDataStore.data.collect { prefs ->
                val tmpl = WidgetLayoutTemplate.entries
                    .firstOrNull { it.name == (prefs[TEMPLATE_KEY] ?: "") }
                    ?: WidgetLayoutTemplate.GRID_2X2
                _template.value = tmpl
                _stacks.value = deserializeStacks(prefs[STACKS_KEY] ?: "", tmpl.slotCount)
            }
        }
    }

    fun allocateWidgetId(): Int = appWidgetHost.allocateAppWidgetId()

    /** Add a widget to a specific slot's stack. */
    fun addWidgetToSlot(slotIndex: Int, widgetId: Int) {
        val current = _stacks.value.toMutableList()
        if (slotIndex !in current.indices) return
        val slot = current[slotIndex]
        if (widgetId in slot.widgetIds) return
        current[slotIndex] = slot.copy(widgetIds = slot.widgetIds + widgetId)
        viewModelScope.launch { persist(current) }
    }

    /** Remove one widget from a slot's stack; unbinds the widget ID. */
    fun removeWidgetFromStack(slotIndex: Int, widgetId: Int) {
        appWidgetHost.deleteAppWidgetId(widgetId)
        val current = _stacks.value.toMutableList()
        if (slotIndex !in current.indices) return
        val slot = current[slotIndex]
        current[slotIndex] = slot.copy(widgetIds = slot.widgetIds - widgetId)
        viewModelScope.launch { persist(current) }
    }

    fun setTemplate(tmpl: WidgetLayoutTemplate) {
        val current = _stacks.value
        if (current.size > tmpl.slotCount) {
            current.drop(tmpl.slotCount).forEach { stack ->
                stack.widgetIds.forEach { appWidgetHost.deleteAppWidgetId(it) }
            }
        }
        val adjusted = List(tmpl.slotCount) { i -> current.getOrElse(i) { WidgetStack() } }
        _stacks.value = adjusted
        viewModelScope.launch { persist(adjusted, tmpl) }
    }

    private suspend fun persist(
        stacks: List<WidgetStack>,
        tmpl: WidgetLayoutTemplate = _template.value
    ) {
        context.widgetDataStore.edit { prefs ->
            prefs[STACKS_KEY]   = serializeStacks(stacks)
            prefs[TEMPLATE_KEY] = tmpl.name
        }
    }

    override fun onCleared() {
        super.onCleared()
        appWidgetHost.stopListening()
    }
}

// ── Serialization ─────────────────────────────────────────────────────────────
// Format: slots separated by ";", widget IDs within each slot by "|"
// Example: "123|456;;789" → slot0=[123,456]  slot1=[]  slot2=[789]

private fun serializeStacks(stacks: List<WidgetStack>): String =
    stacks.joinToString(";") { it.widgetIds.joinToString("|") }

private fun deserializeStacks(raw: String, slotCount: Int): List<WidgetStack> {
    val parts = if (raw.isEmpty()) emptyList() else raw.split(";")
    return List(slotCount) { i ->
        val ids = parts.getOrNull(i)
            ?.split("|")
            ?.mapNotNull { it.toIntOrNull() }
            .orEmpty()
        WidgetStack(ids)
    }
}
