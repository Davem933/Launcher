package com.example.carlauncher.ui.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.carlauncher.data.location.LocationRepository
import com.example.carlauncher.data.model.Parking
import com.example.carlauncher.data.model.VehicleDisplayLocation
import com.example.carlauncher.data.poi.ParkingFetchThrottle
import com.example.carlauncher.data.poi.ParkingRepository
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
    private val parkingRepository: ParkingRepository,
) : ViewModel() {

    val vehicleLocation: StateFlow<VehicleDisplayLocation?> = repository.vehicleLocation

    private val _nearbyParking = MutableStateFlow<List<Parking>>(emptyList())
    val nearbyParking: StateFlow<List<Parking>> = _nearbyParking.asStateFlow()

    private val _routePolyline = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val routePolyline: StateFlow<List<Pair<Double, Double>>> = _routePolyline.asStateFlow()

    fun setRoutePolyline(points: List<Pair<Double, Double>>) { _routePolyline.value = points }
    fun clearRoutePolyline() { _routePolyline.value = emptyList() }

    private val parkingThrottle = ParkingFetchThrottle()

    init {
        viewModelScope.launch {
            repository.vehicleLocation.collect { fix ->
                fix ?: return@collect
                if (parkingThrottle.shouldFetch(fix.lat, fix.lng)) {
                    parkingThrottle.recordQuery(fix.lat, fix.lng)
                    val lat = fix.lat; val lng = fix.lng
                    viewModelScope.launch(Dispatchers.IO) {
                        val parking = parkingRepository.fetchParking(lat, lng)
                        Log.d("MapViewModel", "Parking fetch done: ${parking.size} lots")
                        _nearbyParking.value = parking
                    }
                }
            }
        }
    }
}
