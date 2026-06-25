package com.example.carlauncher.data.location

import android.location.Location
import com.example.carlauncher.data.model.VehicleDisplayLocation
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationProcessor @Inject constructor() {

    data class ProcessResult(
        val display: VehicleDisplayLocation,
        val rawLat: Double,
        val rawLng: Double,
        val rawSpeedKmh: Float,
        val rawBearingDeg: Float,
        val gpsBearingAccDeg: Float,
        val bearingSource: String,
        val bearingChangeDeg: Float,
        val kalmanGain: Double,
        val kalmanVarianceSqM: Double,
        val dtMs: Long
    )

    private val kalmanFilter = KalmanFilter()
    private val speedBuffer = ArrayDeque<Float>(3)
    private var lastBearingDeg: Float = 0f
    private var lastTimestampMs: Long = 0L

    fun process(location: Location): ProcessResult {
        val dtMs = if (lastTimestampMs == 0L) 0L else location.time - lastTimestampMs
        lastTimestampMs = location.time

        val smoothed = kalmanFilter.process(
            lat = location.latitude,
            lng = location.longitude,
            accuracy = location.accuracy,
            timestamp = location.time
        )

        val rawSpeedKmh = location.speed * 3.6f
        if (speedBuffer.size >= 3) speedBuffer.removeFirst()
        speedBuffer.addLast(rawSpeedKmh)
        val smoothedSpeed = speedBuffer.average().toFloat()

        val rawBearing = location.bearing
        val bearingChangeDeg = rawBearing - lastBearingDeg
        lastBearingDeg = rawBearing

        val gpsBearingAccDeg = if (location.hasBearingAccuracy()) location.bearingAccuracyDegrees else 0f

        val display = VehicleDisplayLocation(
            lat = smoothed[0],
            lng = smoothed[1],
            speedKmh = smoothedSpeed,
            bearingDeg = rawBearing,
            accuracyM = location.accuracy,
            timestamp = location.time
        )

        return ProcessResult(
            display = display,
            rawLat = location.latitude,
            rawLng = location.longitude,
            rawSpeedKmh = rawSpeedKmh,
            rawBearingDeg = rawBearing,
            gpsBearingAccDeg = gpsBearingAccDeg,
            bearingSource = "GPS",
            bearingChangeDeg = bearingChangeDeg,
            kalmanGain = kalmanFilter.lastGain,
            kalmanVarianceSqM = kalmanFilter.lastVariance,
            dtMs = dtMs
        )
    }

    fun reset() {
        kalmanFilter.reset()
        speedBuffer.clear()
        lastBearingDeg = 0f
        lastTimestampMs = 0L
    }
}
