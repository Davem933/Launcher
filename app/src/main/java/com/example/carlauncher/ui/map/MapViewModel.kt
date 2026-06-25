package com.example.carlauncher.ui.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.carlauncher.data.location.LocationRepository
import com.example.carlauncher.data.model.Poi
import com.example.carlauncher.data.model.VehicleDisplayLocation
import com.example.carlauncher.data.poi.PoiRepository
import com.example.carlauncher.data.poi.PoiUseCase
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
    private val poiRepository: PoiRepository
) : ViewModel() {

    val vehicleLocation: StateFlow<VehicleDisplayLocation?> = repository.vehicleLocation

    // Single source of truth for which tile backend MapWidget should load.
    val tileSource: TileSource = TileConfig.ACTIVE

    private val _nearbyPois = MutableStateFlow<List<Poi>>(emptyList())
    val nearbyPois: StateFlow<List<Poi>> = _nearbyPois.asStateFlow()

    private val poiUseCase = PoiUseCase()

    init {
        viewModelScope.launch {
            repository.vehicleLocation.collect { fix ->
                fix ?: return@collect
                if (poiUseCase.shouldFetch(fix.lat, fix.lng)) {
                    poiUseCase.recordQuery(fix.lat, fix.lng)
                    val lat = fix.lat; val lng = fix.lng
                    Log.d("MapViewModel", "POI fetch triggered at $lat,$lng")
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
