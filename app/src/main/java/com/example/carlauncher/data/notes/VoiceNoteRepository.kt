package com.example.carlauncher.data.notes

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Jediný vstupní bod UI pro hlasové poznámky. Ukládání po nahrání běží v app-level scope,
 * takže poznámka se neztratí, ani když se Trip stránka mezitím disposuje.
 */
@Singleton
class VoiceNoteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: VoiceNoteDao,
    private val recorder: VoiceRecorder,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioDir = File(context.filesDir, AUDIO_DIR)

    // Líně: repository se injektuje do CarLauncherApp ve stejném kroku jako HiltWorkerFactory —
    // WorkManager.getInstance() v konstruktoru by sáhl na workManagerConfiguration dřív,
    // než je workerFactory nastavená.
    private val workManager by lazy { WorkManager.getInstance(context) }

    val notes: Flow<List<VoiceNoteEntity>> = dao.observeAll()
    val recorderState: StateFlow<RecorderState> = recorder.state

    fun startRecording(): Boolean =
        recorder.start(audioDir) { result -> scope.launch { saveRecording(result) } }

    fun stopRecording() = recorder.stop()

    private suspend fun saveRecording(result: RecordingResult) {
        if (result.durationMs < MIN_DURATION_MS) {
            result.file.delete()
            return
        }
        val id = dao.insert(
            VoiceNoteEntity(
                createdAt = result.startedAt,
                durationMs = result.durationMs,
                audioPath = result.file.absolutePath,
            ),
        )
        enqueue(id, ExistingWorkPolicy.KEEP)
    }

    suspend fun retry(id: Long) {
        val note = dao.getById(id) ?: return
        dao.update(note.copy(status = NoteStatus.PENDING, attempts = 0, errorMessage = null))
        enqueue(id, ExistingWorkPolicy.REPLACE)
    }

    suspend fun delete(id: Long) {
        workManager.cancelUniqueWork(workName(id))
        dao.getById(id)?.audioPath?.let { File(it).delete() }
        dao.deleteById(id)
    }

    suspend fun setCompleted(id: Long, completed: Boolean) = dao.setCompleted(id, completed)

    /** Smaže osiřelé nahrávky a znovu zařadí rozpracované poznámky (proces mohl být zabit). */
    fun cleanupOnStartup() {
        scope.launch {
            val all = dao.getAll()
            val files = audioDir.listFiles()?.toList().orEmpty()
            findOrphanAudio(files, all.mapNotNull { it.audioPath }.toSet()).forEach { it.delete() }
            all.filter { it.status == NoteStatus.PENDING || it.status == NoteStatus.PROCESSING }
                .forEach { note ->
                    if (note.status == NoteStatus.PROCESSING) {
                        dao.update(note.copy(status = NoteStatus.PENDING))
                    }
                    enqueue(note.id, ExistingWorkPolicy.KEEP)
                }
        }
    }

    private fun enqueue(id: Long, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<ProcessNoteWorker>()
            .setInputData(workDataOf(ProcessNoteWorker.KEY_NOTE_ID to id))
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(workName(id), policy, request)
    }

    companion object {
        const val AUDIO_DIR = "voice_notes"
        const val MIN_DURATION_MS = 1_000L
        private const val BACKOFF_SECONDS = 30L
        fun workName(id: Long) = "note-$id"
    }
}
