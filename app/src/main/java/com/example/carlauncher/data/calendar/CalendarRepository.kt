package com.example.carlauncher.data.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class CalendarEvent(val timeLabel: String, val title: String, val color: Int)

@Singleton
class CalendarRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun todayEvents(): List<CalendarEvent> {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED
        Log.d("CalendarRepo", "READ_CALENDAR granted=$granted")
        if (!granted) return emptyList()

        val now = Calendar.getInstance()
        val startMs = now.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val endMs = startMs + 24L * 60 * 60 * 1000
        Log.d("CalendarRepo", "querying range $startMs – $endMs")

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(startMs.toString())
            .appendPath(endMs.toString())
            .build()

        val projection = arrayOf(
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.CALENDAR_COLOR,
            CalendarContract.Instances.ALL_DAY,
        )

        val events = mutableListOf<CalendarEvent>()
        try {
            val cursor = context.contentResolver.query(
                uri, projection,
                null, null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )
            Log.d("CalendarRepo", "cursor=$cursor count=${cursor?.count}")
            if (cursor == null) return emptyList()

            cursor.use {
                val beginIdx  = it.getColumnIndex(CalendarContract.Instances.BEGIN)
                val titleIdx  = it.getColumnIndex(CalendarContract.Instances.TITLE)
                val colorIdx  = it.getColumnIndex(CalendarContract.Instances.CALENDAR_COLOR)
                val allDayIdx = it.getColumnIndex(CalendarContract.Instances.ALL_DAY)

                Log.d("CalendarRepo", "columns: begin=$beginIdx title=$titleIdx color=$colorIdx allDay=$allDayIdx")

                while (it.moveToNext() && events.size < 3) {
                    val allDay = if (allDayIdx >= 0) it.getInt(allDayIdx) == 1 else false
                    val begin  = if (beginIdx  >= 0) it.getLong(beginIdx)  else 0L
                    val title  = if (titleIdx  >= 0) it.getString(titleIdx) else null
                    val color  = if (colorIdx  >= 0) it.getInt(colorIdx)   else 0xFF60A5FA.toInt()
                    if (title.isNullOrBlank()) continue
                    val label  = if (allDay) "celý den" else timeFmt.format(begin)
                    Log.d("CalendarRepo", "event: label=$label title=$title")
                    events += CalendarEvent(timeLabel = label, title = title, color = color)
                }
            }
        } catch (e: Exception) {
            Log.e("CalendarRepo", "query failed: ${e.message}", e)
        }
        Log.d("CalendarRepo", "returning ${events.size} events")
        return events
    }
}
