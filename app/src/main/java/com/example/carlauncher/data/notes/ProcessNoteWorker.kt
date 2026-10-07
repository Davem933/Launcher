package com.example.carlauncher.data.notes

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Tenký obal: veškerá logika je v [NoteProcessor] (unit-testovaný). */
@HiltWorker
class ProcessNoteWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val processor: NoteProcessor,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_NOTE_ID, -1L)
        if (id < 0) return Result.failure()
        return when (processor.process(id)) {
            ProcessOutcome.RETRY -> Result.retry()
            ProcessOutcome.DONE, ProcessOutcome.DELETED, ProcessOutcome.FAILED -> Result.success()
        }
    }

    companion object {
        const val KEY_NOTE_ID = "note_id"
    }
}
