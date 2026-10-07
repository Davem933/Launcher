package com.example.carlauncher.data.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SilenceDetectorTest {
    private val quiet = 100
    private val loud = 5_000

    @Test fun stopsAfterFiveSecondsOfSilence() {
        val d = SilenceDetector().apply { start(0) }
        assertFalse(d.onSample(quiet, 4_800).shouldStop)
        assertTrue(d.onSample(quiet, 5_000).shouldStop)
    }

    @Test fun loudSampleResetsSilence() {
        val d = SilenceDetector().apply { start(0) }
        d.onSample(loud, 3_000)
        val beforeLimit = d.onSample(quiet, 7_900)
        assertEquals(4_900, beforeLimit.silenceMs)
        assertFalse(beforeLimit.shouldStop)
        assertTrue(d.onSample(quiet, 8_000).shouldStop)
    }

    @Test fun stopsAtMaxDurationEvenWhenTalking() {
        val d = SilenceDetector().apply { start(0) }
        for (t in 1_000L..119_000L step 1_000L) {
            assertFalse("t=$t", d.onSample(loud, t).shouldStop)
        }
        val last = d.onSample(loud, 120_000)
        assertEquals(120_000, last.elapsedMs)
        assertTrue(last.shouldStop)
    }
}
