package com.example.carlauncher.data.location

import android.location.Location
import com.example.carlauncher.data.model.VehicleDisplayLocation
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationProcessor @Inject constructor() {

    private val kalmanFilter = KalmanFilter()
    private val speedBuffer = ArrayDeque<Float>(3)

    fun process(location: Location): VehicleDisplayLocation {
        val (smoothLat, smoothLng) = kalmanFilter.process(
            lat = location.latitude,
            lng = location.longitude,
            accuracy = location.accuracy,
            timestamp = location.time
        )

        val rawSpeedKmh = location.speed * 3.6f
        if (speedBuffer.size >= 3) speedBuffer.removeFirst()
        speedBuffer.addLast(rawSpeedKmh)
        val smoothedSpeed = speedBuffer.average().toFloat()

        return VehicleDisplayLocation(
            lat = smoothLat,
            lng = smoothLng,
            speedKmh = smoothedSpeed,
            bearingDeg = location.bearing,
            accuracyM = location.accuracy,
            timestamp = location.time
        )
    }

    fun reset() {
        kalmanFilter.reset()
        speedBuffer.clear()
    }
}
