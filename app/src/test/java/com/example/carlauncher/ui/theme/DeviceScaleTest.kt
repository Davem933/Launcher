package com.example.carlauncher.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceScaleTest {

    private val eps = 0.001f

    @Test
    fun reference_device_is_exactly_one() {
        assertEquals(1f, computeScale(1143f, 686f), eps)
    }

    @Test
    fun near_reference_snaps_to_one() {
        // M10 s mírně jinými reálnými dp (+0,8 %) nesmí dostat škálu ≠ 1.0
        assertEquals(1f, computeScale(1152f, 691f), eps)
        assertEquals(1f, computeScale(1110f, 670f), eps)
    }

    @Test
    fun small_16_10_tablet_is_clamped_to_min() {
        // 8" 16:10 ≈ 962×601 dp → min(0,842; 0,876) = 0,842 → ořez na 0,85
        assertEquals(MIN_SCALE, computeScale(962f, 601f), eps)
    }

    @Test
    fun four_by_three_uses_the_limiting_width() {
        // 1024×768 dp → min(0,896; 1,120) = 0,896 (výška není omezující)
        assertEquals(1024f / 1143f, computeScale(1024f, 768f), eps)
    }

    @Test
    fun large_tablet_scales_up_proportionally() {
        // 1480×924 dp → min(1,295; 1,347) = 1,295
        assertEquals(1480f / 1143f, computeScale(1480f, 924f), eps)
    }

    @Test
    fun extreme_large_is_clamped_to_max() {
        assertEquals(MAX_SCALE, computeScale(3000f, 2000f), eps)
    }

    @Test
    fun extreme_small_is_clamped_to_min() {
        assertEquals(MIN_SCALE, computeScale(400f, 300f), eps)
    }
}
