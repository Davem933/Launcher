package com.example.carlauncher.data.notes

import java.io.File
import javax.inject.Inject

enum class ProcessOutcome { DONE, DELETED, RETRY, FAILED }

/**
 * Zpracování jedné poznámky: Whisper → LLM → uložit → smazat audio. Čistá logika bez Androidu,
 * ProcessNoteWorker jen mapuje [ProcessOutcome] na WorkManager Result.
 */
class NoteProcessor @Inject constructor(
    private val dao: VoiceNoteDao,
    private val ai: NoteAi,
) {
    suspend fun process(noteId: Long): ProcessOutcome {
        val note = dao.getById(noteId) ?: return ProcessOutcome.DELETED
        if (note.status == NoteStatus.DONE) return ProcessOutcome.DONE

        var current = note.copy(
            status = NoteStatus.PROCESSING,
            attempts = note.attempts + 1,
            errorMessage = null,
        )
        dao.update(current)

        return try {
            val raw = current.rawTranscript ?: run {
                val audio = current.audioPath?.let(::File)?.takeIf { it.exists() }
                    ?: return fail(current, "Nahrávka chybí")
                ai.transcribe(audio).also {
                    current = current.copy(rawTranscript = it)
                    dao.update(current)
                }
            }
            if (raw.isBlank()) {
                deleteAudio(current)
                dao.deleteById(noteId)
                return ProcessOutcome.DELETED
            }
            val refined = ai.refine(raw)
            deleteAudio(current)
            dao.update(
                current.copy(
                    status = NoteStatus.DONE,
                    title = refined.title,
                    text = refined.text,
                    category = refined.category,
                    audioPath = null,
                ),
            )
            ProcessOutcome.DONE
        } catch (e: GroqError) {
            if (e.isRetryable && current.attempts < MAX_ATTEMPTS) {
                dao.update(current.copy(status = NoteStatus.PENDING))
                ProcessOutcome.RETRY
            } else {
                fail(current, e.message ?: "Chyba zpracování")
            }
        }
    }

    private suspend fun fail(note: VoiceNoteEntity, message: String): ProcessOutcome {
        dao.update(note.copy(status = NoteStatus.FAILED, errorMessage = message))
        return ProcessOutcome.FAILED
    }

    private fun deleteAudio(note: VoiceNoteEntity) {
        note.audioPath?.let { File(it).delete() }
    }

    companion object {
        const val MAX_ATTEMPTS = 5
    }
}
