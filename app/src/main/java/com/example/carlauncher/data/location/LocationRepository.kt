package com.example.carlauncher.data.location

import android.os.HandlerThread
import android.util.Log
import com.example.carlauncher.data.model.VehicleDisplayLocation
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    private val fusedClient: FusedLocationProviderClient,
    private val processor: LocationProcessor
) {
    private val _vehicleLocation = MutableStateFlow<VehicleDisplayLocation?>(null)
    val vehicleLocation: StateFlow<VehicleDisplayLocation?> = _vehicleLocation.asStateFlow()

    // Dedicated background thread for location callbacks — keeps Kalman filter off Main thread
    private val locationThread = HandlerThread("location-thread").also { it.start() }

    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500L)
        .setMinUpdateDistanceMeters(0f)
        .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                // Runs on locationThread (background) — safe to do Kalman processing here
                _vehicleLocation.value = processor.process(location)
            }
        }
    }

    private var isTracking = false

    fun startTracking() {
        if (isTracking) return
        try {
            fusedClient.requestLocationUpdates(locationRequest, locationCallback, locationThread.looper)
            isTracking = true
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission not granted: ${e.message}")
        }
    }

    fun stopTracking() {
        if (!isTracking) return
        fusedClient.removeLocationUpdates(locationCallback)
        processor.reset()
        isTracking = false
    }

    companion object {
        private const val TAG = "LocationRepository"
    }
}
