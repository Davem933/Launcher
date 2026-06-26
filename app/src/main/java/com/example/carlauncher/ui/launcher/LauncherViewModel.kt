package com.example.carlauncher.ui.launcher

import androidx.lifecycle.ViewModel
import com.example.carlauncher.data.location.LocationRepository
import com.example.carlauncher.data.model.VehicleDisplayLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject

@HiltViewModel
class LauncherViewModel @Inject constructor(
    private val repository: LocationRepository
) : ViewModel() {

    val vehicleLocation: StateFlow<VehicleDisplayLocation?> = repository.vehicleLocation

    private val _isGpsLogging   = MutableStateFlow(false)
    val isGpsLogging: StateFlow<Boolean> = _isGpsLogging.asStateFlow()

    private val _isGpxReplaying = MutableStateFlow(false)
    val isGpxReplaying: StateFlow<Boolean> = _isGpxReplaying.asStateFlow()

    fun toggleGpsLogging() { _isGpsLogging.value = !_isGpsLogging.value }
    fun startGpxReplay(file: File, multiplier: Float) { _isGpxReplaying.value = true }
    fun stopGpxReplay() { _isGpxReplaying.value = false }

    init {
        repository.startTracking()
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopTracking()
    }
}
