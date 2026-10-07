package com.example.carlauncher.data.notes

data class SilenceStatus(val elapsedMs: Long, val silenceMs: Long, val shouldStop: Boolean)

/**
 * Čistá logika auto-stopu nahrávání: ticho = amplituda pod prahem. Hlasitý vzorek nuluje čítač
 * ticha. Stop při [silenceLimitMs] ticha nebo [maxDurationMs] celkem. Čas dodává volající
 * (SystemClock.elapsedRealtime v appce), takže je to testovatelné bez zařízení.
 */
class SilenceDetector(
    private val amplitudeThreshold: Int = DEFAULT_THRESHOLD,
    private val silenceLimitMs: Long = SILENCE_LIMIT_MS,
    private val maxDurationMs: Long = MAX_DURATION_MS,
) {
    private var startedAt = 0L
    private var lastLoudAt = 0L

    fun start(nowMs: Long) {
        startedAt = nowMs
        lastLoudAt = nowMs
    }

    fun onSample(amplitude: Int, nowMs: Long): SilenceStatus {
        if (amplitude >= amplitudeThreshold) lastLoudAt = nowMs
        val elapsed = nowMs - startedAt
        val silence = nowMs - lastLoudAt
        return SilenceStatus(
            elapsedMs = elapsed,
            silenceMs = silence,
            shouldStop = silence >= silenceLimitMs || elapsed >= maxDurationMs,
        )
    }

    companion object {
        /** MediaRecorder.getMaxAmplitude() škála 0..32767; doladit na zařízení. */
        const val DEFAULT_THRESHOLD = 1_500
        const val SILENCE_LIMIT_MS = 5_000L
        const val MAX_DURATION_MS = 120_000L
    }
}
