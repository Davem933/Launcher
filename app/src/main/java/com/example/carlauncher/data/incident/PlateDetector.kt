package com.example.carlauncher.data.incident

import com.google.mlkit.vision.common.InputImage

/**
 * On-device license-plate detector abstraction. The shipping implementation is
 * [MlKitPlateDetector] (offline OCR + [PlateRegex] filter). A TFLite YOLO plate detector can
 * replace it later by binding a different implementation in `di/IncidentBindsModule.kt`,
 * with no change to [IncidentRecorder] or the UI.
 *
 * [detect] is called from a single background analysis thread and may block on inference.
 */
interface PlateDetector {

    /**
     * @param image frame already rotated to display orientation; boxes returned are in
     *   normalized [0,1] coordinates relative to [InputImage.getWidth]/[InputImage.getHeight].
     */
    fun detect(image: InputImage): List<PlateDetection>

    /** Release native resources. */
    fun close()
}
