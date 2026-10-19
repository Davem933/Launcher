package com.example.carlauncher.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's Baseline Profile (+ startup profile for DEX layout) on the tablet:
 *
 *   ./gradlew :app:generateBaselineProfile
 *
 * Output: app/src/main/generated/baselineProfiles/{baseline,startup}-prof.txt — commit it.
 * Covers the hot paths the driver hits: cold start, page swipes, AppDrawer open + scroll.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startLauncher()
        // Mapbox style + first tiles, so the map's own init code is in the profile too.
        Thread.sleep(3_000)
        swipeThroughPages()
        openAndScrollAppDrawer()
    }
}
