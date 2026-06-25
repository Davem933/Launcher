package com.example.carlauncher.data.poi

import android.util.Log
import com.example.carlauncher.data.model.Poi
import com.example.carlauncher.data.model.PoiType
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PoiRepository @Inject constructor() {

    private val endpoints = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter"
    )

    suspend fun fetchPois(lat: Double, lng: Double): List<Poi> {
        val query = buildQuery(lat, lng)
        for (url in endpoints) {
            try {
                val response = post(url, query)
                val pois = parse(response)
                Log.d(TAG, "Fetched ${pois.size} POIs from $url")
                return pois
            } catch (e: Exception) {
                Log.w(TAG, "Failed $url: ${e.message}")
            }
        }
        return emptyList()
    }

    private fun buildQuery(lat: Double, lng: Double): String {
        val latS = String.format(Locale.US, "%.6f", lat)
        val lngS = String.format(Locale.US, "%.6f", lng)
        return buildString {
            append("[out:json][timeout:10];(")
            // exact-match types
            listOf(PoiType.FUEL, PoiType.PARKING, PoiType.HOSPITAL).forEach { type ->
                append("""node["amenity"="${type.osmAmenity}"](around:${type.radiusM},$latS,$lngS);""")
            }
            // regex type (cafe|restaurant)
            append("""node["amenity"~"${PoiType.RESTAURANT.osmAmenity}"](around:${PoiType.RESTAURANT.radiusM},$latS,$lngS);""")
            append(");out body;")
        }
    }

    private fun parse(json: String): List<Poi> {
        val elements = JSONObject(json).getJSONArray("elements")
        val pois = mutableListOf<Poi>()
        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            if (el.optString("type") != "node") continue
            val tags = el.optJSONObject("tags") ?: continue
            val amenity = tags.optString("amenity")
            if (amenity.isEmpty()) continue
            val type = amenityToType(amenity) ?: continue
            val rawName = tags.optString("name")
            pois += Poi(
                id = el.getLong("id"),
                lat = el.getDouble("lat"),
                lng = el.getDouble("lon"),
                type = type,
                name = if (rawName.isEmpty()) null else rawName
            )
        }
        return pois
    }

    private fun amenityToType(amenity: String): PoiType? = when (amenity) {
        "fuel"              -> PoiType.FUEL
        "parking"           -> PoiType.PARKING
        "cafe", "restaurant"-> PoiType.RESTAURANT
        "hospital"          -> PoiType.HOSPITAL
        else                -> null
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
        const val TAG = "PoiRepository"
    }
}
