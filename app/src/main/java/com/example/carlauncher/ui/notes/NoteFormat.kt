package com.example.carlauncher.ui.notes

import androidx.compose.ui.graphics.Color
import com.example.carlauncher.data.notes.NoteCategory
import com.example.carlauncher.data.notes.NoteStatus
import com.example.carlauncher.data.notes.VoiceNoteEntity
import com.example.carlauncher.ui.theme.CarColors
import java.util.Calendar
import java.util.TimeZone

/** Dnes → "HH:mm", jindy → "d. M." */
fun formatNoteTime(timestamp: Long, now: Long, zone: TimeZone = TimeZone.getDefault()): String {
    val t = Calendar.getInstance(zone).apply { timeInMillis = timestamp }
    val n = Calendar.getInstance(zone).apply { timeInMillis = now }
    val sameDay = t.get(Calendar.YEAR) == n.get(Calendar.YEAR) &&
        t.get(Calendar.DAY_OF_YEAR) == n.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) {
        "%02d:%02d".format(t.get(Calendar.HOUR_OF_DAY), t.get(Calendar.MINUTE))
    } else {
        "${t.get(Calendar.DAY_OF_MONTH)}. ${t.get(Calendar.MONTH) + 1}."
    }
}

fun formatDuration(ms: Long): String {
    val s = ms / 1_000
    return "%d:%02d".format(s / 60, s % 60)
}

fun noteRowTitle(note: VoiceNoteEntity): String = when (note.status) {
    NoteStatus.PENDING, NoteStatus.PROCESSING -> "⏳ Zpracovávám…"
    NoteStatus.FAILED -> "⚠ Chyba zpracování"
    NoteStatus.DONE -> "${(note.category ?: NoteCategory.OTHER).emoji} ${note.title.orEmpty()}"
}

val NoteCategory.color: Color
    get() = when (this) {
        NoteCategory.IDEA -> CarColors.Warn
        NoteCategory.TASK -> CarColors.Go
        NoteCategory.SHOPPING -> CarColors.Accent
        NoteCategory.CAR -> CarColors.Danger
        NoteCategory.OTHER -> CarColors.Text3
    }
