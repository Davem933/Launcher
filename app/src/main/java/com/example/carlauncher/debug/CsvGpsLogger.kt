package com.example.carlauncher.debug

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.carlauncher.data.location.LocationProcessor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvGpsLogger @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var writer: BufferedWriter? = null
    private var outputStream: OutputStream? = null
    private var isLogging = false

    fun startLogging(): Boolean {
        if (isLogging) return true
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "gps_log_$timestamp.csv"

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            ) ?: throw IllegalStateException("MediaStore insert returned null")

            outputStream = context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Cannot open output stream for $uri")
            writer = BufferedWriter(OutputStreamWriter(outputStream!!))
            // 16 columns — raw vs smoothed lets you spot Kalman drift off-road;
            // bearingSource/kalmanGain diagnose missed curves and roundabouts
            writer!!.write(
                "timestamp," +
                "rawLat,rawLng,smoothLat,smoothLng," +
                "rawSpeedKmh,smoothSpeedKmh," +
                "rawBearingDeg,smoothBearingDeg,gpsBearingAccDeg," +
                "bearingSource,bearingChangeDeg," +
                "accuracyM,kalmanGain,kalmanVarianceSqM,dtMs\n"
            )
            writer!!.flush()
            isLogging = true
            Log.d("CsvGpsLogger", "Logging started → Downloads/$fileName")
            true
        } catch (e: Exception) {
            Log.e("CsvGpsLogger", "Failed to start logging: ${e.message}")
            writer = null
            outputStream = null
            false
        }
    }

    fun log(r: LocationProcessor.ProcessResult) {
        if (!isLogging) return
        try {
            writer?.write(
                "${r.display.timestamp}," +
                "${r.rawLat},${r.rawLng},${r.display.lat},${r.display.lng}," +
                "${r.rawSpeedKmh},${r.display.speedKmh}," +
                "${r.rawBearingDeg},${r.display.bearingDeg},${r.gpsBearingAccDeg}," +
                "${r.bearingSource},${r.bearingChangeDeg}," +
                "${r.display.accuracyM},${r.kalmanGain},${r.kalmanVarianceSqM},${r.dtMs}\n"
            )
            writer?.flush()
        } catch (e: Exception) {
            Log.e("CsvGpsLogger", "Log write failed: ${e.message}")
        }
    }

    fun stopLogging() {
        try {
            writer?.close()
            outputStream?.close()
        } catch (_: Exception) {}
        writer = null
        outputStream = null
        isLogging = false
        Log.d("CsvGpsLogger", "Logging stopped")
    }

    fun isActive(): Boolean = isLogging
}
