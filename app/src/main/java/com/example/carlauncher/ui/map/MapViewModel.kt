package com.example.carlauncher.ui.map

import androidx.lifecycle.ViewModel
import com.example.carlauncher.data.location.LocationRepository
import com.example.carlauncher.data.model.VehicleDisplayLocation
import com.example.carlauncher.data.speedlimit.SpeedLimitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    repository: LocationRepository,
    speedLimitRepository: SpeedLimitRepository,
) : ViewModel() {

    val vehicleLocation: StateFlow<VehicleDisplayLocation?> = repository.vehicleLocation
    val speedLimit: StateFlow<Int> = speedLimitRepository.speedLimit

    private val _routePolyline = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val routePolyline: StateFlow<List<Pair<Double, Double>>> = _routePolyline.asStateFlow()

    fun setRoutePolyline(points: List<Pair<Double, Double>>) { _routePolyline.value = points }
    fun clearRoutePolyline() { _routePolyline.value = emptyList() }
}
