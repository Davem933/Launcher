package com.example.carlauncher.data.notes

import android.content.Context
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RecorderState {
    data object Idle : RecorderState
    data class Recording(val amplitude: Int, val elapsedMs: Long, val silenceMs: Long) : RecorderState
}

data class RecordingResult(val file: File, val startedAt: Long, val durationMs: Long)

/**
 * Nahrává AAC/.m4a z mikrofonu. Každých [POLL_INTERVAL_MS] čte amplitudu a nechá
 * [SilenceDetector] rozhodnout o auto-stopu. O Groqu ani DB nic neví — výsledek předá
 * callbackem `onFinished` (jen když MediaRecorder zapsal platný soubor).
 */
@Singleton
class VoiceRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt = 0L
    private var pollJob: Job? = null
    private var onFinished: ((RecordingResult) -> Unit)? = null

    fun start(outputDir: File, onFinished: (RecordingResult) -> Unit): Boolean {
        if (recorder != null) return false
        outputDir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(outputDir, "note_$stamp.m4a")
        val r = MediaRecorder(context)
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(16_000)
            r.setAudioEncodingBitRate(32_000)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
        } catch (e: Exception) {
            Log.w(TAG, "Start nahrávání selhal", e)
            r.release()
            file.delete()
            return false
        }
        recorder = r
        outputFile = file
        startedAt = System.currentTimeMillis()
        this.onFinished = onFinished

        val detector = SilenceDetector().apply { start(SystemClock.elapsedRealtime()) }
        pollJob = scope.launch {
            while (isActive) {
                val amplitude = r.maxAmplitude
                val status = detector.onSample(amplitude, SystemClock.elapsedRealtime())
                _state.value = RecorderState.Recording(amplitude, status.elapsedMs, status.silenceMs)
                if (status.shouldStop) {
                    stop()
                    break
                }
                delay(POLL_INTERVAL_MS)
            }
        }
        return true
    }

    fun stop() {
        val r = recorder ?: return
        val file = outputFile ?: return
        pollJob?.cancel()
        pollJob = null
        val duration = System.currentTimeMillis() - startedAt
        // stop() hází RuntimeException, když se nezapsala žádná platná data (příliš krátké)
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        recorder = null
        outputFile = null
        _state.value = RecorderState.Idle
        val callback = onFinished
        onFinished = null
        if (ok) callback?.invoke(RecordingResult(file, startedAt, duration)) else file.delete()
    }

    companion object {
        private const val TAG = "VoiceNotes"
        private const val POLL_INTERVAL_MS = 200L
    }
}
