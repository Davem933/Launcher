package com.example.carlauncher.data.incident

import java.io.File

/**
 * Incident Recorder domain models.
 *
 * All timestamps are epoch milliseconds (`System.currentTimeMillis()`), matching the rest
 * of the codebase (`VehicleDisplayLocation.timestamp`, `TripEntity.startTime`).
 *
 * The overlay drawn from these models is a live Compose layer only — it is never fed to any
 * CameraX use case, so the recorded `.mp4` stays pristine for evidentiary use.
 */

/** Live GPS value shown in the overlay. */
data class IncidentGpsFix(
    val timestamp: Long,
    val lat: Double,
    val lon: Double,
    val accM: Float,
    val speedMps: Float,
    val bearingDeg: Float,
    val provider: String,
)

/** One GPS sample logged into a session's time series. */
data class IncidentGpsPoint(
    val t: Long,
    val lat: Double,
    val lon: Double,
    val accM: Float,
    val speedMps: Float,
    val bearingDeg: Float,
    val provider: String,
)

/** Bounding box in normalized [0,1] PreviewView coordinates (origin top-left). */
data class PlateBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** A plate-candidate detection produced by a [PlateDetector]. */
data class PlateDetection(
    val text: String,
    val box: PlateBox,
)

/** A plate detection logged into a session's time series. */
data class PlateDetectionRecord(
    val t: Long,
    val text: String,
    val box: PlateBox,
)

/**
 * Mutable buffer for a single recording session. Held by [IncidentRecorder] while recording,
 * flushed to the sidecar JSON on [android.media.MediaRecorder]-style finalize, then discarded.
 * Guard all list mutations with `synchronized(this)`.
 */
class IncidentSession(
    val startedAt: Long,
    val videoFile: File,
    val jsonFile: File,
    @Volatile var stoppedAt: Long = 0L,
    val gps: MutableList<IncidentGpsPoint> = mutableListOf(),
    val plates: MutableList<PlateDetectionRecord> = mutableListOf(),
)

/** UI state surfaced by [IncidentRecorder] / IncidentViewModel. */
sealed interface IncidentUiState {

    data object Idle : IncidentUiState

    data class Recording(
        val elapsedMs: Long,
        val fix: IncidentGpsFix?,
        val plates: List<PlateBox>,
        val fileName: String,
    ) : IncidentUiState

    data class Error(val message: String) : IncidentUiState
}
