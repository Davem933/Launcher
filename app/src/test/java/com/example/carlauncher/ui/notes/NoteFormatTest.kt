package com.example.carlauncher.ui.notes

import com.example.carlauncher.data.notes.NoteCategory
import com.example.carlauncher.data.notes.NoteStatus
import com.example.carlauncher.data.notes.VoiceNoteEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class NoteFormatTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val now = 1_759_838_400_000L // 2025-10-07 12:00 UTC

    @Test fun sameDay_showsHoursAndMinutes() {
        assertEquals("09:05", formatNoteTime(now - (2 * 60 + 55) * 60_000L, now, utc))
    }

    @Test fun otherDay_showsDayAndMonth() {
        assertEquals("6. 10.", formatNoteTime(now - 24 * 3_600_000L, now, utc))
    }

    @Test fun duration_isMinutesAndSeconds() {
        assertEquals("0:07", formatDuration(7_400))
        assertEquals("2:00", formatDuration(120_000))
    }

    @Test fun rowTitle_dependsOnStatus() {
        val base = VoiceNoteEntity(createdAt = 0, durationMs = 0, audioPath = null)
        assertEquals("⏳ Zpracovávám…", noteRowTitle(base.copy(status = NoteStatus.PENDING)))
        assertEquals("⏳ Zpracovávám…", noteRowTitle(base.copy(status = NoteStatus.PROCESSING)))
        assertEquals("⚠ Chyba zpracování", noteRowTitle(base.copy(status = NoteStatus.FAILED)))
        assertEquals(
            "✅ Servis",
            noteRowTitle(base.copy(status = NoteStatus.DONE, title = "Servis", category = NoteCategory.TASK)),
        )
    }
}
