package com.example.carlauncher.data.incident

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.HandlerThread
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GPS source for the Incident Recorder, built on the raw platform [LocationManager]
 * (deliberately NOT `FusedLocationProviderClient` — the feature must not depend on Play
 * Services). Completely independent of [com.example.carlauncher.data.location.LocationRepository]:
 * different API, its own [HandlerThread], its own model, no Kalman filtering.
 *
 * Follows the `LocationRepository.startTracking()` convention of catching [SecurityException]
 * rather than pre-checking the permission.
 */
@Singleton
class IncidentLocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val locationManager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    // Dedicated thread so fixes never land on the main looper.
    private val thread = HandlerThread("incident-gps").also { it.start() }

    private val _fix = MutableStateFlow<IncidentGpsFix?>(null)
    val fix: StateFlow<IncidentGpsFix?> = _fix.asStateFlow()

    private var listener: LocationListener? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (listener != null) return
        val lm = locationManager ?: return

        val l = LocationListener { location -> _fix.value = location.toFix() }
        listener = l

        try {
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                UPDATE_INTERVAL_MS,
                0f,
                l,
                thread.looper,
            )
            // A quick network fix so the overlay is not blank while GPS cold-starts.
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    UPDATE_INTERVAL_MS,
                    0f,
                    l,
                    thread.looper,
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "ACCESS_FINE_LOCATION not granted: ${e.message}")
            listener = null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Location provider unavailable: ${e.message}")
            listener = null
        }
    }

    fun stop() {
        listener?.let { l -> runCatching { locationManager?.removeUpdates(l) } }
        listener = null
        _fix.value = null
    }

    private fun Location.toFix() = IncidentGpsFix(
        timestamp = if (time > 0L) time else System.currentTimeMillis(),
        lat = latitude,
        lon = longitude,
        accM = if (hasAccuracy()) accuracy else 0f,
        speedMps = if (hasSpeed()) speed else 0f,
        bearingDeg = if (hasBearing()) bearing else 0f,
        provider = provider ?: "gps",
    )

    companion object {
        private const val TAG = "IncidentLocation"
        private const val UPDATE_INTERVAL_MS = 1500L
    }
}
