package com.example.carlauncher.data.incident

import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage

/**
 * [ImageAnalysis.Analyzer] that runs plate detection on every [DETECT_EVERY_N_FRAMES]-th
 * frame only (mid-range tablet budget) and drops frames while an inference is in flight.
 *
 * Runs on a dedicated single-thread executor supplied by [IncidentRecorder]. Never touches
 * Compose state — results are handed back through [onResult], which forwards them to a
 * `MutableStateFlow`.
 */
class PlateAnalyzer(
    private val detector: PlateDetector,
    private val onResult: (boxes: List<PlateBox>, records: List<PlateDetectionRecord>) -> Unit,
) : ImageAnalysis.Analyzer {

    private var frameCounter = 0L

    @Volatile
    private var busy = false

    @ExperimentalGetImage
    override fun analyze(image: ImageProxy) {
        val n = frameCounter++
        if (busy || n % DETECT_EVERY_N_FRAMES != 0L) {
            image.close()
            return
        }

        val media = image.image
        if (media == null) {
            image.close()
            return
        }

        busy = true
        try {
            val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
            val detections = detector.detect(input)
            if (detections.isNotEmpty()) {
                val now = System.currentTimeMillis()
                onResult(
                    detections.map { it.box },
                    detections.map { PlateDetectionRecord(now, it.text, it.box) },
                )
            } else {
                onResult(emptyList(), emptyList())
            }
        } catch (e: Exception) {
            Log.w(TAG, "analyze failed: ${e.message}")
        } finally {
            busy = false
            image.close()
        }
    }

    companion object {
        private const val TAG = "PlateAnalyzer"
        const val DETECT_EVERY_N_FRAMES = 8L
    }
}
