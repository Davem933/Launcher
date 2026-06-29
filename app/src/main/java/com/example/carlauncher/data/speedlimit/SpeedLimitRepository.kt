package com.example.carlauncher.data.speedlimit

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeedLimitRepository @Inject constructor() {

    private val _speedLimit = MutableStateFlow(50)
    val speedLimit: StateFlow<Int> = _speedLimit.asStateFlow()

    private var lastLat = Double.NaN
    private var lastLon = Double.NaN

    suspend fun updateIfMoved(lat: Double, lon: Double) {
        val dlat = lat - lastLat
        val dlon = lon - lastLon
        if (!lastLat.isNaN() && (dlat * dlat + dlon * dlon) < 0.000004) return // < ~200m
        lastLat = lat
        lastLon = lon
        withContext(Dispatchers.IO) {
            runCatching { _speedLimit.value = queryNominatim(lat, lon) }
                .onFailure { Log.w("SpeedLimit", "Nominatim query failed: ${it.message}") }
        }
    }

    private fun queryNominatim(lat: Double, lon: Double): Int {
        val url = URL("https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "CarLauncher/1.0")
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        return try {
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val addr = json.optJSONObject("address") ?: return 90
            val urban = listOf("city", "town", "village", "suburb", "city_district", "borough")
                .any { addr.optString(it).isNotEmpty() }
            val limit = if (urban) 50 else 90
            Log.d("SpeedLimit", "limit=$limit urban=$urban lat=$lat lon=$lon")
            limit
        } finally {
            conn.disconnect()
        }
    }
}
