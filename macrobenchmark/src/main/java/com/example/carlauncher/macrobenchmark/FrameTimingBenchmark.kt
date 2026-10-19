package com.example.carlauncher.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoothness (frameDurationCpuMs / frameOverrunMs percentiles) of the interactions the driver
 * actually does (journeys live in BenchmarkSetup.kt, shared with BaselineProfileGenerator).
 *
 * Mapbox tiles come from the network, so results depend on connectivity — keep the tablet on
 * the same Wi-Fi between runs that you want to compare.
 */
@RunWith(AndroidJUnit4::class)
class FrameTimingBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun pagerSwipe() = measureFrames { swipeThroughPages() }

    @Test
    fun mapPan() = measureFrames { panMap() }

    @Test
    fun appDrawerOpenAndScroll() = measureFrames { openAndScrollAppDrawer() }

    private fun measureFrames(block: MacrobenchmarkScope.() -> Unit) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        // Same "used for a while" state as StartupBenchmark.coldStartWarmedUp.
        compilationMode = CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Disable,
            warmupIterations = 3,
        ),
        // Not StartupMode.COLD: that kills the process *after* setupBlock, i.e. it would throw
        // away the launch below right before measuring. Kill it ourselves first instead, so
        // every iteration starts on page 0 with no overlay open.
        startupMode = null,
        iterations = 5,
        setupBlock = {
            killProcess()
            pressHome()
            startLauncher()
            // Give the Mapbox style + first tiles time to load, so we measure interaction,
            // not the tail of startup.
            Thread.sleep(3_000)
        },
        measureBlock = block,
    )
}
