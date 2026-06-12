package com.example.carlauncher.data.location

class KalmanFilter {

    // Q = 0.8 m/s — balanced smoothing: enough to filter GPS jitter, responsive enough for driving
    // (was 3.0 — caused too many marker updates because raw GPS noise passed through)
    private val qMetersPerSecond = 0.8

    private var variance: Double = -1.0
    private var filteredLat: Double = 0.0
    private var filteredLng: Double = 0.0
    private var lastTimestamp: Long = 0L

    // Pre-allocated output — eliminates ~500 Pair<Double,Double> allocations per minute
    private val output = DoubleArray(2)

    fun process(lat: Double, lng: Double, accuracy: Float, timestamp: Long): DoubleArray {
        if (variance < 0) {
            filteredLat = lat
            filteredLng = lng
            variance = accuracy.toDouble() * accuracy.toDouble()
            lastTimestamp = timestamp
            output[0] = lat
            output[1] = lng
            return output
        }

        val dtSeconds = ((timestamp - lastTimestamp) / 1000.0).coerceAtLeast(0.0)
        lastTimestamp = timestamp

        // Predict: grow variance by process noise
        variance += dtSeconds * qMetersPerSecond * qMetersPerSecond

        // Update: compute Kalman gain
        val measurementVariance = accuracy.toDouble() * accuracy.toDouble()
        val gain = variance / (variance + measurementVariance)

        filteredLat += gain * (lat - filteredLat)
        filteredLng += gain * (lng - filteredLng)
        variance *= (1.0 - gain)

        output[0] = filteredLat
        output[1] = filteredLng
        return output
    }

    fun reset() {
        variance = -1.0
        lastTimestamp = 0L
    }
}
