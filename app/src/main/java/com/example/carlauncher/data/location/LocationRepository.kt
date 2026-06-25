package com.example.carlauncher.data.location

import android.os.HandlerThread
import android.util.Log
import com.example.carlauncher.BuildConfig
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

    private val _debugSnapshot = MutableStateFlow<LocationProcessor.ProcessResult?>(null)
    val debugSnapshot: StateFlow<LocationProcessor.ProcessResult?> = _debugSnapshot.asStateFlow()

    // Dedicated background thread for location callbacks — keeps Kalman filter off Main thread
    private val locationThread = HandlerThread("location-thread").also { it.start() }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                val processed = processor.process(location)
                _vehicleLocation.value = processed.display
                _debugSnapshot.value = processed
            }
        }
    }

    private var isTracking = false

    private fun buildRequest(): LocationRequest =
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateDistanceMeters(0f)
            .build()

    fun startTracking() {
        if (isTracking) return
        try {
            fusedClient.requestLocationUpdates(
                buildRequest(), locationCallback, locationThread.looper
            )
            isTracking = true
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission not granted: ${e.message}")
        }
    }

    fun injectDebugLocation(location: VehicleDisplayLocation) {
        if (!BuildConfig.DEBUG) return
        _vehicleLocation.value = location
        // Build a minimal snapshot so CSV logger works during GPX replay.
        // Raw fields mirror the smoothed values — GPX provides no raw sensor data.
        _debugSnapshot.value = LocationProcessor.ProcessResult(
            display          = location,
            rawLat           = location.lat,
            rawLng           = location.lng,
            rawSpeedKmh      = location.speedKmh,
            rawBearingDeg    = location.bearingDeg,
            gpsBearingAccDeg = 0f,
            bearingSource    = "GPX",
            bearingChangeDeg = 0f,
            kalmanGain       = 0.0,
            kalmanVarianceSqM = 0.0,
            dtMs             = 0L
        )
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
