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

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                val vehicleLocation = processor.process(location)
                _vehicleLocation.value = vehicleLocation
                // Self-contained adaptive interval: no ViewModel changes needed
                adjustIntervalIfNeeded(vehicleLocation.speedKmh)
            }
        }
    }

    private var isTracking = false

    // Adaptive GPS interval — reduces battery drain when parked
    private enum class IntervalMode { DRIVING, PARKED }
    private var currentMode = IntervalMode.DRIVING

    private fun buildRequest(mode: IntervalMode): LocationRequest {
        val (interval, priority) = when (mode) {
            IntervalMode.DRIVING -> 500L to Priority.PRIORITY_HIGH_ACCURACY
            IntervalMode.PARKED  -> 5000L to Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        return LocationRequest.Builder(priority, interval)
            .setMinUpdateDistanceMeters(0f)
            .build()
    }

    private fun adjustIntervalIfNeeded(speedKmh: Float) {
        val targetMode = if (speedKmh > 5f) IntervalMode.DRIVING else IntervalMode.PARKED
        if (targetMode == currentMode) return
        currentMode = targetMode
        try {
            fusedClient.removeLocationUpdates(locationCallback)
            fusedClient.requestLocationUpdates(
                buildRequest(targetMode), locationCallback, locationThread.looper
            )
            Log.d(TAG, "GPS interval → $targetMode (speed=${speedKmh.toInt()} km/h)")
        } catch (e: SecurityException) {
            Log.w(TAG, "adjustInterval: $e")
        }
    }

    fun startTracking() {
        if (isTracking) return
        try {
            fusedClient.requestLocationUpdates(
                buildRequest(IntervalMode.DRIVING), locationCallback, locationThread.looper
            )
            isTracking = true
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission not granted: ${e.message}")
        }
    }

    fun stopTracking() {
        if (!isTracking) return
        fusedClient.removeLocationUpdates(locationCallback)
        processor.reset()
        currentMode = IntervalMode.DRIVING
        isTracking = false
    }

    companion object {
        private const val TAG = "LocationRepository"
    }
}
