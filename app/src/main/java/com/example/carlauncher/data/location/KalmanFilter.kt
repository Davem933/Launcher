package com.example.carlauncher.data.location

class KalmanFilter {

    private val qMetersPerSecond = 3.0

    private var variance: Double = -1.0
    private var filteredLat: Double = 0.0
    private var filteredLng: Double = 0.0
    private var lastTimestamp: Long = 0L

    fun process(lat: Double, lng: Double, accuracy: Float, timestamp: Long): Pair<Double, Double> {
        if (variance < 0) {
            filteredLat = lat
            filteredLng = lng
            variance = accuracy.toDouble() * accuracy.toDouble()
            lastTimestamp = timestamp
            return Pair(lat, lng)
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

        return Pair(filteredLat, filteredLng)
    }

    fun reset() {
        variance = -1.0
        lastTimestamp = 0L
    }
}
