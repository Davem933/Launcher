package com.example.carlauncher.debug

import android.util.Log
import com.example.carlauncher.data.location.LocationRepository
import com.example.carlauncher.data.model.VehicleDisplayLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GpxReplayService @Inject constructor(
    private val locationRepository: LocationRepository
) {
    private var replayJob: Job? = null
    var isReplaying: Boolean = false
        private set

    fun startReplay(gpxFile: File, speedMultiplier: Float = 1f, scope: CoroutineScope) {
        val points = parseGpx(gpxFile)
        if (points.isEmpty()) {
            Log.e("GpxReplay", "No points in GPX file")
            return
        }
        Log.d("GpxReplay", "Starting replay: ${points.size} points, speed ${speedMultiplier}x")
        isReplaying = true
        replayJob = scope.launch(Dispatchers.IO) {
            for (i in points.indices) {
                if (!isActive) break
                val point = points[i]
                val bearing = if (i < points.size - 1) calculateBearing(point, points[i + 1]) else 0f
                locationRepository.injectDebugLocation(
                    VehicleDisplayLocation(
                        lat       = point.lat,
                        lng       = point.lng,
                        speedKmh  = point.speed ?: 50f,
                        bearingDeg = bearing,
                        accuracyM = 5f,
                        timestamp = System.currentTimeMillis()
                    )
                )
                val delayMs = if (i < points.size - 1) {
                    val timeDiff = points[i + 1].timeMs - point.timeMs
                    (timeDiff / speedMultiplier).toLong().coerceIn(100L, 2000L)
                } else 500L
                delay(delayMs)
            }
            isReplaying = false
            Log.d("GpxReplay", "Replay finished")
        }
    }

    fun stopReplay() {
        replayJob?.cancel()
        isReplaying = false
    }

    private data class GpxPoint(val lat: Double, val lng: Double, val timeMs: Long, val speed: Float?)

    private fun parseGpx(file: File): List<GpxPoint> {
        val points = mutableListOf<GpxPoint>()
        try {
            val factory = javax.xml.parsers.SAXParserFactory.newInstance()
            val parser = factory.newSAXParser()
            val handler = object : org.xml.sax.helpers.DefaultHandler() {
                var inTrkpt = false
                var lat = 0.0; var lng = 0.0
                var timeMs = 0L; var speed: Float? = null
                var currentTag = ""
                val textBuffer = StringBuilder()

                override fun startElement(uri: String, local: String, qName: String, attrs: org.xml.sax.Attributes) {
                    currentTag = qName
                    textBuffer.clear()
                    if (qName == "trkpt") {
                        inTrkpt = true
                        lat   = attrs.getValue("lat")?.toDoubleOrNull() ?: 0.0
                        lng   = attrs.getValue("lon")?.toDoubleOrNull() ?: 0.0
                        speed = null
                    }
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (inTrkpt) textBuffer.append(ch, start, length)
                }

                override fun endElement(uri: String, local: String, qName: String) {
                    if (!inTrkpt) return
                    when (qName) {
                        "time" -> {
                            try {
                                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                                timeMs = sdf.parse(textBuffer.toString())?.time ?: 0L
                            } catch (_: Exception) { timeMs = System.currentTimeMillis() }
                        }
                        "speed"  -> speed = textBuffer.toString().toFloatOrNull()?.times(3.6f)
                        "trkpt"  -> { points.add(GpxPoint(lat, lng, timeMs, speed)); inTrkpt = false }
                    }
                    textBuffer.clear()
                }
            }
            parser.parse(file, handler)
        } catch (e: Exception) {
            Log.e("GpxReplay", "GPX parse error: ${e.message}")
        }
        return points
    }

    private fun calculateBearing(from: GpxPoint, to: GpxPoint): Float {
        val lat1  = Math.toRadians(from.lat)
        val lat2  = Math.toRadians(to.lat)
        val dLng  = Math.toRadians(to.lng - from.lng)
        val y     = Math.sin(dLng) * Math.cos(lat2)
        val x     = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng)
        return ((Math.toDegrees(Math.atan2(y, x)) + 360) % 360).toFloat()
    }
}
