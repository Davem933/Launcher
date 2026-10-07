package com.example.carlauncher.data.notes

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Enumy ukládá Room 2.6 sám jako TEXT (název konstanty) — žádné TypeConvertery. */
@Entity(tableName = "voice_notes")
data class VoiceNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val durationMs: Long,
    val status: NoteStatus = NoteStatus.PENDING,
    /** null po úspěšném zpracování (soubor smazán). */
    val audioPath: String?,
    /** Výstup Whisperu — při opakování se přepis znovu nevolá. */
    val rawTranscript: String? = null,
    val title: String? = null,
    val text: String? = null,
    val category: NoteCategory? = null,
    val errorMessage: String? = null,
    val attempts: Int = 0,
)
