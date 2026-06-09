package com.example.carlauncher.ui.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.carlauncher.data.location.LocationRepository
import com.example.carlauncher.data.model.VehicleDisplayLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LauncherViewModel @Inject constructor(
    private val repository: LocationRepository
) : ViewModel() {

    val vehicleLocation: StateFlow<VehicleDisplayLocation?> = repository.vehicleLocation

    init {
        viewModelScope.launch { repository.startTracking() }
    }

    override fun onCleared() {
        repository.stopTracking()
    }
}
