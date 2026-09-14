package com.example.carlauncher.data.poi

import android.util.Log
import com.example.carlauncher.data.model.Parking
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parking lots specifically — Mapbox's own Standard-style POI data is sparse for parking in this
 * area (spot-checked against a location where a competing nav app clearly shows several), while
 * OSM/Overpass has much denser community-mapped parking coverage. Fetches only "amenity=parking",
 * not the broader POI set the old (removed) PoiRepository covered.
 */
@Singleton
class ParkingRepository @Inject constructor() {

    private val endpoints = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter"
    )

    private val radiusM = 800

    suspend fun fetchParking(lat: Double, lng: Double): List<Parking> {
        val query = buildQuery(lat, lng)
        for (url in endpoints) {
            try {
                val response = post(url, query)
                val parking = parse(response)
                Log.d(TAG, "Fetched ${parking.size} parking lots from $url")
                return parking
            } catch (e: Exception) {
                Log.w(TAG, "Failed $url: ${e.message}")
            }
        }
        return emptyList()
    }

    private fun buildQuery(lat: Double, lng: Double): String {
        val latS = String.format(Locale.US, "%.6f", lat)
        val lngS = String.format(Locale.US, "%.6f", lng)
        // Parking lots are usually mapped as ways (areas) in OSM, not just nodes (points) — a
        // node-only query misses most real-world parking. "out center" gives ways/relations a
        // computed center point we can use directly as a marker location.
        return """
            [out:json][timeout:10];
            (
              node["amenity"="parking"](around:$radiusM,$latS,$lngS);
              way["amenity"="parking"](around:$radiusM,$latS,$lngS);
              relation["amenity"="parking"](around:$radiusM,$latS,$lngS);
            );
            out center;
        """.trimIndent()
    }

    private fun parse(json: String): List<Parking> {
        val elements = JSONObject(json).getJSONArray("elements")
        val parking = mutableListOf<Parking>()
        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            val (lat, lng) = when (el.optString("type")) {
                "node" -> el.getDouble("lat") to el.getDouble("lon")
                "way", "relation" -> {
                    val center = el.optJSONObject("center") ?: continue
                    center.getDouble("lat") to center.getDouble("lon")
                }
                else -> continue
            }
            parking += Parking(id = el.getLong("id"), lat = lat, lng = lng)
        }
        return parking
    }

    private fun post(url: String, query: String): String {
        val body = "data=" + URLEncoder.encode(query, "UTF-8")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return conn.inputStream.use { it.reader().readText() }
    }

    private companion object {
        const val TAG = "ParkingRepository"
    }
}
