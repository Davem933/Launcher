package com.example.carlauncher.data.incident

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Owns the CameraX pipeline for the Incident Recorder: [Preview] (live view only),
 * [VideoCapture] (pristine `.mp4`, no audio) and [ImageAnalysis] (throttled plate detection).
 *
 * The overlay is drawn by Compose over the [Preview] surface and is never routed into any
 * use case, so the recorded video is unmodified — usable as evidence.
 *
 * GPS points and plate detections are buffered in memory during a recording and flushed to
 * the sidecar JSON in [VideoRecordEvent.Finalize], guaranteeing the `.json` sits next to a
 * fully-muxed `.mp4`.
 */
@Singleton
class IncidentRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val plateDetector: PlateDetector,
    private val locationSource: IncidentLocationSource,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<IncidentUiState>(IncidentUiState.Idle)
    val state: StateFlow<IncidentUiState> = _state.asStateFlow()

    /** Latest plate boxes for the live overlay (updated between recordings too). */
    private val _plateBoxes = MutableStateFlow<List<PlateBox>>(emptyList())
    val plateBoxes: StateFlow<List<PlateBox>> = _plateBoxes.asStateFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var analysisExecutor: ExecutorService? = null

    private var recording: Recording? = null
    private var session: IncidentSession? = null
    private var gpsJob: Job? = null
    private var pendingTeardown = false
    private var bound = false

    @Volatile
    private var lastFix: IncidentGpsFix? = null

    val isRecording: Boolean
        get() = _state.value is IncidentUiState.Recording

    val isBound: Boolean
        get() = bound

    // ── Camera lifecycle ──────────────────────────────────────────────────────

    suspend fun bind(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        if (bound) {
            // Re-request the surface (toggle via null) so the preview reconnects after the
            // Activity was stopped/resumed — re-setting the same provider can be a no-op.
            withContext(Dispatchers.Main) {
                preview?.let {
                    it.surfaceProvider = null
                    it.surfaceProvider = surfaceProvider
                }
            }
            return
        }

        val provider = awaitProvider()
        cameraProvider = provider

        val exec = Executors.newSingleThreadExecutor()
        analysisExecutor = exec

        val previewUC = Preview.Builder().build().also { it.surfaceProvider = surfaceProvider }

        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(
                    Quality.HD,
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                ),
            )
            .build()
        val videoUC = VideoCapture.withOutput(recorder)

        val analysisUC = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1280, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER,
                        ),
                    )
                    .build(),
            )
            .build()
            .also { analysis ->
                analysis.setAnalyzer(
                    exec,
                    PlateAnalyzer(plateDetector) { boxes, records ->
                        _plateBoxes.value = boxes
                        val s = session
                        if (s != null && records.isNotEmpty()) {
                            synchronized(s) { s.plates.addAll(records) }
                        }
                    },
                )
            }

        preview = previewUC
        videoCapture = videoUC
        imageAnalysis = analysisUC

        withContext(Dispatchers.Main) {
            provider.unbindAll()
            try {
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    previewUC, videoUC, analysisUC,
                )
                bound = true
            } catch (e: Exception) {
                Log.w(TAG, "3-use-case bind failed, retrying Preview+VideoCapture: ${e.message}")
                runCatching { analysisUC.clearAnalyzer() }
                imageAnalysis = null
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        previewUC, videoUC,
                    )
                    bound = true
                } catch (e2: Exception) {
                    Log.e(TAG, "camera bind failed: ${e2.message}")
                    _state.value = IncidentUiState.Error("Kameru se nepodařilo spustit")
                }
            }
        }
    }

    fun unbind() {
        if (recording != null) {
            // Stop first so Finalize writes the sidecar next to a complete .mp4,
            // then tear the camera down from the Finalize callback.
            pendingTeardown = true
            runCatching { recording?.stop() }
            return
        }
        teardown()
    }

    private fun teardown() {
        gpsJob?.cancel()
        gpsJob = null
        recording = null
        session = null
        runCatching { imageAnalysis?.clearAnalyzer() }
        runCatching { cameraProvider?.unbindAll() }
        analysisExecutor?.shutdown()
        analysisExecutor = null
        cameraProvider = null
        preview = null
        videoCapture = null
        imageAnalysis = null
        bound = false
        pendingTeardown = false
        _plateBoxes.value = emptyList()
        if (_state.value is IncidentUiState.Recording) _state.value = IncidentUiState.Idle
    }

    // ── Recording ─────────────────────────────────────────────────────────────

    fun start() {
        if (isRecording) return
        val vc = videoCapture
        if (vc == null || !bound) {
            _state.value = IncidentUiState.Error("Kamera není připravená")
            return
        }
        if (!IncidentFiles.hasEnoughSpace(context)) {
            _state.value = IncidentUiState.Error("Nedostatek místa v úložišti")
            return
        }

        val video = IncidentFiles.videoFile(context)
        val s = IncidentSession(
            startedAt = System.currentTimeMillis(),
            videoFile = video,
            jsonFile = IncidentFiles.jsonFor(video),
        )
        session = s
        startGpsSampling(s)

        val options = FileOutputOptions.Builder(video).build()
        recording = vc.output
            .prepareRecording(context, options) // no .withAudioEnabled() — video only
            .start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        _state.value = IncidentUiState.Recording(
                            elapsedMs = 0L,
                            fix = lastFix,
                            plates = _plateBoxes.value,
                            fileName = video.name,
                        )
                    }

                    is VideoRecordEvent.Status -> {
                        val cur = _state.value
                        if (cur is IncidentUiState.Recording) {
                            _state.value = cur.copy(
                                elapsedMs = System.currentTimeMillis() - s.startedAt,
                                fix = lastFix,
                                plates = _plateBoxes.value,
                            )
                        }
                    }

                    is VideoRecordEvent.Finalize -> {
                        s.stoppedAt = System.currentTimeMillis()
                        gpsJob?.cancel()
                        gpsJob = null
                        IncidentJsonWriter.write(s)
                        recording = null
                        session = null
                        if (event.hasError()) {
                            Log.w(TAG, "finalize error=${event.error}")
                        }
                        _state.value = IncidentUiState.Idle
                        if (pendingTeardown) teardown()
                    }
                }
            }
    }

    fun stop() {
        runCatching { recording?.stop() }
    }

    private fun startGpsSampling(s: IncidentSession) {
        gpsJob?.cancel()
        gpsJob = scope.launch {
            locationSource.fix.collect { fix ->
                fix ?: return@collect
                lastFix = fix
                synchronized(s) {
                    s.gps.add(
                        IncidentGpsPoint(
                            t = fix.timestamp,
                            lat = fix.lat,
                            lon = fix.lon,
                            accM = fix.accM,
                            speedMps = fix.speedMps,
                            bearingDeg = fix.bearingDeg,
                            provider = fix.provider,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun awaitProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                try {
                    cont.resume(future.get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(context))
        }

    companion object {
        private const val TAG = "IncidentRecorder"
    }
}
