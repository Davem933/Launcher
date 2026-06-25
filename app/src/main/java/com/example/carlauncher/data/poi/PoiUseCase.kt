package com.example.carlauncher.data.poi

class PoiUseCase {

    private var lastQueryLat = 0.0
    private var lastQueryLng = 0.0
    private var lastQueryMs = 0L

    fun shouldFetch(lat: Double, lng: Double): Boolean {
        val now = System.currentTimeMillis()
        if (lastQueryMs == 0L) return true                          // first fix — always fetch
        if (now - lastQueryMs < MIN_INTERVAL_MS) return false       // rate limit
        return distanceM(lastQueryLat, lastQueryLng, lat, lng) >= MIN_DIST_M
    }

    fun recordQuery(lat: Double, lng: Double) {
        lastQueryLat = lat
        lastQueryLng = lng
        lastQueryMs = System.currentTimeMillis()
    }

    private fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val result = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lng1, lat2, lng2, result)
        return result[0]
    }

    private companion object {
        const val MIN_DIST_M = 1500f
        const val MIN_INTERVAL_MS = 30_000L
    }
}
