package com.example.carlauncher.data.location

import android.content.Context
import android.util.Log
import com.example.carlauncher.data.model.VehicleDisplayLocation
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedClient: FusedLocationProviderClient,
    private val processor: LocationProcessor
) {
    private val _vehicleLocation = MutableStateFlow<VehicleDisplayLocation?>(null)
    val vehicleLocation: StateFlow<VehicleDisplayLocation?> = _vehicleLocation.asStateFlow()

    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500L)
        .setMinUpdateDistanceMeters(0f)
        .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                _vehicleLocation.value = processor.process(location)
            }
        }
    }

    fun startTracking() {
        try {
            fusedClient.requestLocationUpdates(locationRequest, locationCallback, null)
        } catch (e: SecurityException) {
            Log.w("LocationRepository", "Location permission not granted: ${e.message}")
        }
    }

    fun stopTracking() {
        fusedClient.removeLocationUpdates(locationCallback)
        processor.reset()
    }
}
