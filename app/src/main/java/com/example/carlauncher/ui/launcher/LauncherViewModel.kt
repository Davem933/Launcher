package com.example.carlauncher.ui.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.carlauncher.data.location.LocationRepository
import com.example.carlauncher.data.model.VehicleDisplayLocation
import com.example.carlauncher.data.speedlimit.SpeedLimitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class LauncherViewModel @Inject constructor(
    private val repository: LocationRepository,
    private val speedLimitRepository: SpeedLimitRepository,
) : ViewModel() {

    val vehicleLocation: StateFlow<VehicleDisplayLocation?> = repository.vehicleLocation
    val speedLimit: StateFlow<Int> = speedLimitRepository.speedLimit

    private val _isGpsLogging   = MutableStateFlow(false)
    val isGpsLogging: StateFlow<Boolean> = _isGpsLogging.asStateFlow()

    private val _isGpxReplaying = MutableStateFlow(false)
    val isGpxReplaying: StateFlow<Boolean> = _isGpxReplaying.asStateFlow()

    fun toggleGpsLogging() { _isGpsLogging.value = !_isGpsLogging.value }
    fun startGpxReplay(file: File, multiplier: Float) { _isGpxReplaying.value = true }
    fun stopGpxReplay() { _isGpxReplaying.value = false }

    init {
        repository.startTracking()
        viewModelScope.launch {
            repository.vehicleLocation.collect { loc ->
                loc ?: return@collect
                speedLimitRepository.updateIfMoved(loc.lat, loc.lng)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopTracking()
    }
}
