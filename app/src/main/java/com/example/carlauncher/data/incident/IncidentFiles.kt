package com.example.carlauncher.data.incident

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Resolves the app-specific external `incidents/` directory and the timestamped
 * `incident_<yyyyMMdd_HHmmss>.mp4` / `.json` file pair. App-specific external storage needs
 * no runtime permission on minSdk 31 and is pullable over USB/adb for evidence handoff.
 */
object IncidentFiles {

    private const val DIR = "incidents"

    /** Refuse to start a recording below this much free space. */
    const val MIN_FREE_BYTES = 500L * 1024 * 1024

    private fun stampFormat() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    fun dir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, DIR).apply { if (!exists()) mkdirs() }
    }

    fun videoFile(context: Context): File =
        File(dir(context), "incident_${stampFormat().format(Date())}.mp4")

    fun jsonFor(video: File): File =
        File(video.parentFile, video.nameWithoutExtension + ".json")

    fun hasEnoughSpace(context: Context): Boolean =
        dir(context).usableSpace > MIN_FREE_BYTES

    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = if (file.extension.equals("mp4", ignoreCase = true)) "video/mp4" else "application/json"
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
