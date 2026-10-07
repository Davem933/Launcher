package com.example.carlauncher.data.notes

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class NoteProcessorTest {
    @get:Rule val tmp = TemporaryFolder()

    private class FakeAi : NoteAi {
        var transcribeCalls = 0
        var transcribe: () -> String = { "zavolat ehm do servisu" }
        var refine: (String) -> RefinedNote = { RefinedNote("Servis", "Zavolat do servisu.", NoteCategory.TASK) }
        override suspend fun transcribe(audio: File): String { transcribeCalls++; return transcribe() }
        override suspend fun refine(rawTranscript: String) = refine.invoke(rawTranscript)
    }

    private val dao = FakeVoiceNoteDao()
    private val ai = FakeAi()
    private val processor = NoteProcessor(dao, ai)

    private suspend fun insertNote(
        attempts: Int = 0,
        rawTranscript: String? = null,
    ): Pair<Long, File> {
        val audio = tmp.newFile().apply { writeBytes(byteArrayOf(1)) }
        val id = dao.insert(
            VoiceNoteEntity(
                createdAt = 1L, durationMs = 3_000, audioPath = audio.absolutePath,
                attempts = attempts, rawTranscript = rawTranscript,
            ),
        )
        return id to audio
    }

    @Test fun happyPath_storesRefinedNoteAndDeletesAudio() = runTest {
        val (id, audio) = insertNote()

        assertEquals(ProcessOutcome.DONE, processor.process(id))

        val n = dao.rows.getValue(id)
        assertEquals(NoteStatus.DONE, n.status)
        assertEquals("Servis", n.title)
        assertEquals("Zavolat do servisu.", n.text)
        assertEquals(NoteCategory.TASK, n.category)
        assertEquals("zavolat ehm do servisu", n.rawTranscript)
        assertNull(n.audioPath)
        assertFalse(audio.exists())
    }

    @Test fun networkError_retriesAndKeepsPending() = runTest {
        val (id, audio) = insertNote()
        ai.transcribe = { throw GroqError.Network(IOException("offline")) }

        assertEquals(ProcessOutcome.RETRY, processor.process(id))

        val n = dao.rows.getValue(id)
        assertEquals(NoteStatus.PENDING, n.status)
        assertEquals(1, n.attempts)
        assertTrue(audio.exists())
    }

    @Test fun authError_failsAndKeepsAudio() = runTest {
        val (id, audio) = insertNote()
        ai.transcribe = { throw GroqError.Auth(401) }

        assertEquals(ProcessOutcome.FAILED, processor.process(id))

        val n = dao.rows.getValue(id)
        assertEquals(NoteStatus.FAILED, n.status)
        assertEquals("Neplatný Groq API klíč (401)", n.errorMessage)
        assertTrue(audio.exists())
    }

    @Test fun retryableErrorOnFifthAttempt_fails() = runTest {
        val (id, _) = insertNote(attempts = NoteProcessor.MAX_ATTEMPTS - 1)
        ai.transcribe = { throw GroqError.Server(503) }

        assertEquals(ProcessOutcome.FAILED, processor.process(id))
        assertEquals(NoteStatus.FAILED, dao.rows.getValue(id).status)
    }

    @Test fun existingTranscript_skipsTranscription() = runTest {
        val (id, _) = insertNote(rawTranscript = "už přepsáno")
        var refinedInput: String? = null
        ai.refine = { refinedInput = it; RefinedNote("A", "B", NoteCategory.IDEA) }

        assertEquals(ProcessOutcome.DONE, processor.process(id))
        assertEquals(0, ai.transcribeCalls)
        assertEquals("už přepsáno", refinedInput)
    }

    @Test fun refineFailure_keepsTranscriptForNextAttempt() = runTest {
        val (id, _) = insertNote()
        ai.refine = { throw GroqError.RateLimited() }

        assertEquals(ProcessOutcome.RETRY, processor.process(id))
        assertEquals("zavolat ehm do servisu", dao.rows.getValue(id).rawTranscript)
    }

    @Test fun blankTranscript_deletesNoteAndAudio() = runTest {
        val (id, audio) = insertNote()
        ai.transcribe = { "   " }

        assertEquals(ProcessOutcome.DELETED, processor.process(id))
        assertFalse(dao.rows.containsKey(id))
        assertFalse(audio.exists())
    }

    @Test fun missingNote_isDeleted() = runTest {
        assertEquals(ProcessOutcome.DELETED, processor.process(42))
    }

    @Test fun missingAudioFile_fails() = runTest {
        val (id, audio) = insertNote()
        audio.delete()

        assertEquals(ProcessOutcome.FAILED, processor.process(id))
        assertEquals("Nahrávka chybí", dao.rows.getValue(id).errorMessage)
    }
}
