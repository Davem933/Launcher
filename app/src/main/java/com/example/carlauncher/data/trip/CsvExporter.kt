package com.example.carlauncher.data.trip

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun share(context: Context, trips: List<TripEntity>) {
        val file = File(context.filesDir, "trips_export.csv")
        file.bufferedWriter().use { w ->
            w.write("datum,cas_start,cas_konec,trvani,km,prumerna_rychlost,max_rychlost,odkud,kam\n")
            trips.forEach { t ->
                val date  = dateFmt.format(Date(t.startTime))
                val start = timeFmt.format(Date(t.startTime))
                val end   = timeFmt.format(Date(t.endTime))
                val dur   = formatDuration((t.endTime - t.startTime) / 1000)
                val km    = "%.1f".format(t.distanceKm)
                val avg   = t.avgSpeedKmh.toInt()
                val max   = t.maxSpeedKmh.toInt()
                val from  = t.startAddress.replace(",", ";")
                val to    = t.endAddress.replace(",", ";")
                w.write("$date,$start,$end,$dur,$km,$avg,$max,$from,$to\n")
            }
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Exportovat jízdy").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun formatDuration(totalSec: Long): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        return "%02d:%02d".format(h, m)
    }
}
