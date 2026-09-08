package com.example.carlauncher.data.incident

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PlateDetector] backed by ML Kit Text Recognition v2 (bundled Latin model — no network,
 * no Play Services download). Recognized lines are screened by [PlateRegex] plus simple
 * geometry checks (plates are wide, short, and a small fraction of the frame).
 *
 * Runs synchronously on the caller's analysis thread via [Tasks.await]; the caller
 * ([PlateAnalyzer]) throttles to every N-th frame and guards re-entrancy.
 */
@Singleton
class MlKitPlateDetector @Inject constructor() : PlateDetector {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun detect(image: InputImage): List<PlateDetection> {
        val w = image.width.toFloat()
        val h = image.height.toFloat()
        if (w <= 0f || h <= 0f) return emptyList()

        val recognized = try {
            Tasks.await(recognizer.process(image))
        } catch (e: Exception) {
            Log.w(TAG, "OCR failed: ${e.message}")
            return emptyList()
        }

        val out = ArrayList<PlateDetection>()
        for (block in recognized.textBlocks) {
            for (line in block.lines) {
                if (!PlateRegex.looksLikePlate(line.text)) continue
                val r = line.boundingBox ?: continue
                val bw = r.width().toFloat()
                val bh = r.height().toFloat()
                if (bw <= 0f || bh <= 0f) continue

                val aspect = bw / bh
                if (aspect < MIN_ASPECT || aspect > MAX_ASPECT) continue
                if ((bw * bh) / (w * h) > MAX_AREA_FRACTION) continue

                out += PlateDetection(
                    text = PlateRegex.normalize(line.text),
                    box = PlateBox(
                        left = (r.left / w).coerceIn(0f, 1f),
                        top = (r.top / h).coerceIn(0f, 1f),
                        right = (r.right / w).coerceIn(0f, 1f),
                        bottom = (r.bottom / h).coerceIn(0f, 1f),
                    ),
                )
            }
        }
        return out
    }

    override fun close() {
        runCatching { recognizer.close() }
    }

    companion object {
        private const val TAG = "MlKitPlateDetector"
        private const val MIN_ASPECT = 1.8f
        private const val MAX_ASPECT = 6.5f
        private const val MAX_AREA_FRACTION = 0.25f
    }
}
