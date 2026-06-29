package com.example.carlauncher.data.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
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
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED) return emptyList()

        val now = Calendar.getInstance()
        val startMs = now.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val endMs = startMs + 24L * 60 * 60 * 1000

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
        val cursor = context.contentResolver.query(
            uri, projection,
            null, null,
            "${CalendarContract.Instances.BEGIN} ASC"
        ) ?: return emptyList()

        cursor.use {
            val beginIdx  = it.getColumnIndex(CalendarContract.Instances.BEGIN)
            val titleIdx  = it.getColumnIndex(CalendarContract.Instances.TITLE)
            val colorIdx  = it.getColumnIndex(CalendarContract.Instances.CALENDAR_COLOR)
            val allDayIdx = it.getColumnIndex(CalendarContract.Instances.ALL_DAY)

            while (it.moveToNext() && events.size < 3) {
                val allDay = it.getInt(allDayIdx) == 1
                val begin  = it.getLong(beginIdx)
                val title  = it.getString(titleIdx) ?: continue
                val color  = it.getInt(colorIdx)
                val label  = if (allDay) "celý den" else timeFmt.format(begin)
                events += CalendarEvent(timeLabel = label, title = title, color = color)
            }
        }
        return events
    }
}
