package com.example.carlauncher.ui.incident

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.carlauncher.data.incident.incidentDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Persists the user-chosen position of the floating Incident Recorder button.
 * Position is stored as top-left dp offsets; `null` = not set yet (use default placement).
 */
@HiltViewModel
class IncidentButtonViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _pos = MutableStateFlow<Pair<Float, Float>?>(null)

    /** (xDp, yDp) top-left offset, or null until loaded / never set. */
    val pos: StateFlow<Pair<Float, Float>?> = _pos.asStateFlow()

    init {
        viewModelScope.launch {
            context.incidentDataStore.data
                .map { prefs ->
                    val x = prefs[KEY_X]
                    val y = prefs[KEY_Y]
                    if (x != null && y != null) x to y else null
                }
                .collect { _pos.value = it }
        }
    }

    fun save(xDp: Float, yDp: Float) {
        viewModelScope.launch {
            context.incidentDataStore.edit { prefs ->
                prefs[KEY_X] = xDp
                prefs[KEY_Y] = yDp
            }
        }
    }

    private companion object {
        val KEY_X = floatPreferencesKey("fab_x_dp")
        val KEY_Y = floatPreferencesKey("fab_y_dp")
    }
}
