package com.example.carlauncher.data.trip

import com.example.carlauncher.data.location.LocationRepository
import com.example.carlauncher.data.model.VehicleDisplayLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

private const val SPEED_START_KMH = 5f
private const val START_SAMPLES = 10   // 10s @ 1Hz → jízda začala
private const val STOP_SECONDS = 30L   // 30s @ 0 km/h → jízda skončila

@Singleton
class TripDetector @Inject constructor(
    private val locationRepository: LocationRepository,
    private val tripRepository: TripRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var collectJob: Job? = null

    private val _state = MutableStateFlow<LiveTripState>(LiveTripState.Idle())
    val state: StateFlow<LiveTripState> = _state.asStateFlow()

    private val speedBuffer = ArrayDeque<Float>(START_SAMPLES)

    private var tripStartTime = 0L
    private var startLat = 0.0
    private var startLng = 0.0
    private var prevLat = 0.0
    private var prevLng = 0.0
    private var totalDistanceKm = 0f
    private var maxSpeedKmh = 0f
    private var stopCounter = 0L

    fun start() {
        if (collectJob?.isActive == true) return
        collectJob = scope.launch {
            locationRepository.vehicleLocation.collect { loc ->
                loc ?: return@collect
                onLocation(loc)
            }
        }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
    }

    private suspend fun onLocation(loc: VehicleDisplayLocation) {
        when (_state.value) {
            is LiveTripState.Idle   -> handleIdle(loc.speedKmh, loc)
            is LiveTripState.Active -> handleActive(loc.speedKmh, loc)
        }
    }

    private fun handleIdle(speed: Float, loc: VehicleDisplayLocation) {
        if (speedBuffer.size >= START_SAMPLES) speedBuffer.removeFirst()
        speedBuffer.addLast(speed)
        if (speedBuffer.size == START_SAMPLES && speedBuffer.all { it > SPEED_START_KMH }) {
            tripStartTime = System.currentTimeMillis()
            startLat = loc.lat; startLng = loc.lng
            prevLat = loc.lat;  prevLng = loc.lng
            totalDistanceKm = 0f; maxSpeedKmh = speed; stopCounter = 0
            speedBuffer.clear()
            _state.value = LiveTripState.Active(0f, 0L, speed, speed)
        }
    }

    private suspend fun handleActive(speed: Float, loc: VehicleDisplayLocation) {
        totalDistanceKm += haversineKm(prevLat, prevLng, loc.lat, loc.lng)
        prevLat = loc.lat; prevLng = loc.lng

        if (speed > maxSpeedKmh) maxSpeedKmh = speed

        val durationSec = (System.currentTimeMillis() - tripStartTime) / 1000L
        val avgSpeed = if (durationSec > 0) (totalDistanceKm / durationSec * 3600f) else 0f

        if (speed < 1f) {
            stopCounter++
            if (stopCounter >= STOP_SECONDS) {
                finishTrip(loc.lat, loc.lng, durationSec)
                return
            }
        } else {
            stopCounter = 0
        }

        _state.value = LiveTripState.Active(
            distanceKm  = totalDistanceKm,
            durationSec = durationSec,
            avgSpeedKmh = avgSpeed,
            maxSpeedKmh = maxSpeedKmh
        )
    }

    private suspend fun finishTrip(endLat: Double, endLng: Double, durationSec: Long) {
        val endTime = System.currentTimeMillis()
        val avgSpeed = if (durationSec > 0) (totalDistanceKm / durationSec * 3600f) else 0f

        val startAddr = reverseGeocode(startLat, startLng)
        val endAddr   = reverseGeocode(endLat, endLng)

        val finished = TripEntity(
            startTime    = tripStartTime,
            endTime      = endTime,
            distanceKm   = totalDistanceKm,
            avgSpeedKmh  = avgSpeed,
            maxSpeedKmh  = maxSpeedKmh,
            startAddress = startAddr,
            endAddress   = endAddr
        )
        tripRepository.save(finished)
        _state.value = LiveTripState.Idle(lastTrip = finished)
        speedBuffer.clear()
    }

    private suspend fun reverseGeocode(lat: Double, lng: Double): String = try {
        val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lng"
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.setRequestProperty("User-Agent", "CarLauncher/1.0")
        conn.connectTimeout = 3000; conn.readTimeout = 3000
        val json = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val road = Regex("\"road\":\"([^\"]+)\"").find(json)?.groupValues?.get(1)
        val city = Regex("\"city\":\"([^\"]+)\"|\"town\":\"([^\"]+)\"|\"village\":\"([^\"]+)\"")
            .find(json)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }
        listOfNotNull(road, city).joinToString(", ").ifEmpty { "Neznámé místo" }
    } catch (_: Exception) { "" }

    private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return (R * 2 * asin(sqrt(a))).toFloat()
    }
}
