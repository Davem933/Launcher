package com.example.carlauncher.data.incident

import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Serializes an [IncidentSession] to its sidecar JSON, written next to the finalized `.mp4`
 * with the same basename. Uses `org.json` (Android SDK) — no serialization dependency, in
 * keeping with the rest of the codebase.
 *
 * The `.mp4` itself is never modified; this file carries all overlay-equivalent metadata.
 */
object IncidentJsonWriter {

    const val SCHEMA_VERSION = 1

    private fun isoFormat() =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())

    fun write(session: IncidentSession) {
        try {
            val root = JSONObject().apply {
                put("schemaVersion", SCHEMA_VERSION)
                put("video", session.videoFile.name)
                put("startedAt", session.startedAt)
                put("stoppedAt", session.stoppedAt)
                put("startedAtLocal", isoFormat().format(Date(session.startedAt)))
                put("stoppedAtLocal", isoFormat().format(Date(session.stoppedAt)))
                put("device", JSONObject().apply {
                    put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
                    put("androidSdk", Build.VERSION.SDK_INT)
                })
                put("gps", gpsArray(session))
                put("plates", platesArray(session))
            }
            session.jsonFile.writeText(root.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write sidecar ${session.jsonFile.name}: ${e.message}")
        }
    }

    private fun gpsArray(session: IncidentSession): JSONArray {
        val arr = JSONArray()
        synchronized(session) {
            for (p in session.gps) {
                arr.put(JSONObject().apply {
                    put("t", p.t)
                    put("lat", p.lat)
                    put("lon", p.lon)
                    put("accM", p.accM.toDouble())
                    put("speedMps", p.speedMps.toDouble())
                    put("bearingDeg", p.bearingDeg.toDouble())
                    put("provider", p.provider)
                })
            }
        }
        return arr
    }

    private fun platesArray(session: IncidentSession): JSONArray {
        val arr = JSONArray()
        synchronized(session) {
            for (d in session.plates) {
                arr.put(JSONObject().apply {
                    put("t", d.t)
                    put("text", d.text)
                    put("box", JSONObject().apply {
                        put("left", d.box.left.toDouble())
                        put("top", d.box.top.toDouble())
                        put("right", d.box.right.toDouble())
                        put("bottom", d.box.bottom.toDouble())
                    })
                })
            }
        }
        return arr
    }

    private const val TAG = "IncidentJsonWriter"
}
