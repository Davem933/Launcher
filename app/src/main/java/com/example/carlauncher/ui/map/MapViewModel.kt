package com.example.carlauncher.ui.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.carlauncher.data.location.LocationRepository
import com.example.carlauncher.data.map.PmtilesHttpServer
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
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    repository: LocationRepository,
    private val poiRepository: PoiRepository
) : ViewModel() {

    val vehicleLocation: StateFlow<VehicleDisplayLocation?> = repository.vehicleLocation

    val tileSource: TileSource = TileConfig.ACTIVE

    private val _nearbyPois = MutableStateFlow<List<Poi>>(emptyList())
    val nearbyPois: StateFlow<List<Poi>> = _nearbyPois.asStateFlow()

    private val _routePolyline = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val routePolyline: StateFlow<List<Pair<Double, Double>>> = _routePolyline.asStateFlow()

    fun setRoutePolyline(points: List<Pair<Double, Double>>) { _routePolyline.value = points }
    fun clearRoutePolyline() { _routePolyline.value = emptyList() }

    private var pmtilesServer: PmtilesHttpServer? = null

    private val poiUseCase = PoiUseCase()

    init {
        if (tileSource == TileSource.PMTILES) {
            val filePath = TileConfig.PMTILES.removePrefix("pmtiles://")
            val file = File(filePath)
            if (file.exists()) {
                try {
                    pmtilesServer = PmtilesHttpServer(file).also { it.start() }
                    Log.d("MapViewModel", "PMTiles server started on :8888, file=$filePath")
                } catch (e: Exception) {
                    Log.e("MapViewModel", "PMTiles server failed to start: ${e.message}")
                }
            } else {
                Log.w("MapViewModel", "PMTiles file not found: $filePath")
            }
        }

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

    override fun onCleared() {
        pmtilesServer?.stop()
        super.onCleared()
    }
}
