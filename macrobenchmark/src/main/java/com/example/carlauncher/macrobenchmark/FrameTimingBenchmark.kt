package com.example.carlauncher.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoothness (frameDurationCpuMs / frameOverrunMs percentiles) of the interactions the driver
 * actually does. Coordinates are fractions of the screen, assuming the default Classic layout
 * with the dock at the bottom: map panel = left ~65 %, widget column = right ~35 %.
 *
 * Mapbox tiles come from the network, so results depend on connectivity — keep the tablet on
 * the same Wi-Fi between runs that you want to compare.
 */
@RunWith(AndroidJUnit4::class)
class FrameTimingBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    /** HorizontalPager: Launcher → Widgets → Trip → Widgets → Launcher. */
    @Test
    fun pagerSwipe() = measureFrames {
        val w = device.displayWidth
        val y = device.displayHeight * 45 / 100
        // Start on the right column (page 0) so the swipe never begins on the map, which
        // disables pager scrolling while touched (MapNavPanel.trackTouchActive).
        repeat(2) { swipeAndSettle(w * 90 / 100, y, w * 10 / 100, y) }
        repeat(2) { swipeAndSettle(w * 10 / 100, y, w * 90 / 100, y) }
    }

    /** Panning the Mapbox map on page 0 (3D Standard style, pitch 45°). */
    @Test
    fun mapPan() = measureFrames {
        val w = device.displayWidth
        val h = device.displayHeight
        val cy = h * 40 / 100
        repeat(2) {
            swipeAndSettle(w * 50 / 100, cy, w * 15 / 100, cy)
            swipeAndSettle(w * 15 / 100, cy, w * 50 / 100, cy)
            swipeAndSettle(w * 32 / 100, h * 20 / 100, w * 32 / 100, h * 65 / 100)
            swipeAndSettle(w * 32 / 100, h * 65 / 100, w * 32 / 100, h * 20 / 100)
        }
    }

    /** Opening the AppDrawer overlay (fade + slide) and flinging through the app grid. */
    @Test
    fun appDrawerOpenAndScroll() = measureFrames {
        device.findObject(By.desc("Všechny aplikace")).click()
        // The settings tile is always the first grid item, so it marks "drawer is up".
        device.wait(Until.hasObject(By.text("Nastavení")), 5_000)
        device.waitForIdle()

        val grid = device.findObject(By.scrollable(true))
        if (grid != null) {
            grid.setGestureMargin(device.displayWidth / 10)
            repeat(2) {
                grid.fling(Direction.DOWN)
                device.waitForIdle()
                grid.fling(Direction.UP)
                device.waitForIdle()
            }
        }

        device.findObject(By.desc("Zavřít"))?.click()
        device.wait(Until.gone(By.desc("Zavřít")), 5_000)
        device.waitForIdle()
    }

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

    private fun MacrobenchmarkScope.swipeAndSettle(x1: Int, y1: Int, x2: Int, y2: Int) {
        device.swipe(x1, y1, x2, y2, 20)
        device.waitForIdle()
        Thread.sleep(300)
    }
}
