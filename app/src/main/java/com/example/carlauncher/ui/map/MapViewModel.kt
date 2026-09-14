package com.example.carlauncher.ui.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.carlauncher.data.location.LocationRepository
import com.example.carlauncher.data.model.Poi
import com.example.carlauncher.data.model.VehicleDisplayLocation
import com.example.carlauncher.data.poi.PoiRepository
import com.example.carlauncher.data.poi.PoiUseCase
import com.example.carlauncher.data.speedlimit.SpeedLimitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    repository: LocationRepository,
    private val poiRepository: PoiRepository,
    speedLimitRepository: SpeedLimitRepository,
) : ViewModel() {

    val vehicleLocation: StateFlow<VehicleDisplayLocation?> = repository.vehicleLocation
    val speedLimit: StateFlow<Int> = speedLimitRepository.speedLimit

    private val _nearbyPois = MutableStateFlow<List<Poi>>(emptyList())
    val nearbyPois: StateFlow<List<Poi>> = _nearbyPois.asStateFlow()

    private val _routePolyline = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val routePolyline: StateFlow<List<Pair<Double, Double>>> = _routePolyline.asStateFlow()

    fun setRoutePolyline(points: List<Pair<Double, Double>>) { _routePolyline.value = points }
    fun clearRoutePolyline() { _routePolyline.value = emptyList() }

    private val poiUseCase = PoiUseCase()

    init {
        viewModelScope.launch {
            repository.vehicleLocation.collect { fix ->
                fix ?: return@collect
                if (poiUseCase.shouldFetch(fix.lat, fix.lng)) {
                    poiUseCase.recordQuery(fix.lat, fix.lng)
                    val lat = fix.lat; val lng = fix.lng
                    viewModelScope.launch(Dispatchers.IO) {
                        val pois = poiRepository.fetchPois(lat, lng)
                        Log.d("MapViewModel", "POI fetch done: ${pois.size} POIs")
                        _nearbyPois.value = pois
                    }
                }
            }
        }
    }
}
