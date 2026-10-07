package com.example.carlauncher.data.notes

import java.io.File

/** AI zpracování poznámky. Hází [GroqError]. Rozhraní kvůli fake implementaci v testech. */
interface NoteAi {
    suspend fun transcribe(audio: File): String
    suspend fun refine(rawTranscript: String): RefinedNote
}
