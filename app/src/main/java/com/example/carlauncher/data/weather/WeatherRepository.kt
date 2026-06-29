package com.example.carlauncher.data.weather

import com.example.carlauncher.data.location.LocationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class WeatherData(val tempC: Int, val code: Int, val condition: String)

@Singleton
class WeatherRepository @Inject constructor(
    private val locationRepository: LocationRepository,
) {
    suspend fun fetch(): WeatherData = withContext(Dispatchers.IO) {
        val loc = locationRepository.vehicleLocation.value
        val lat = loc?.lat ?: 50.0796
        val lon = loc?.lng ?: 14.4252

        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weathercode" +
            "&timezone=auto"
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout    = 5_000
        try {
            val json    = conn.inputStream.bufferedReader().readText()
            val current = JSONObject(json).getJSONObject("current")
            val temp    = current.getDouble("temperature_2m").toInt()
            val code    = current.getInt("weathercode")
            WeatherData(tempC = temp, code = code, condition = wmoCondition(code))
        } finally {
            conn.disconnect()
        }
    }

    private fun wmoCondition(code: Int): String = when (code) {
        0            -> "Jasno"
        1, 2, 3      -> "Polojasno"
        45, 48       -> "Mlha"
        51, 53, 55   -> "Mrholení"
        61, 63, 65   -> "Déšť"
        66, 67       -> "Mrznoucí déšť"
        71, 73, 75   -> "Sněžení"
        77           -> "Sněhové zrno"
        80, 81, 82   -> "Přeháňky"
        85, 86       -> "Sněhové přeháňky"
        95           -> "Bouřka"
        96, 99       -> "Bouřka s krupobitím"
        else         -> "Neznámo"
    }
}
