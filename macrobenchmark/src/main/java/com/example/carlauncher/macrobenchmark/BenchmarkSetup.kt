package com.example.carlauncher.macrobenchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

/**
 * applicationId of the app variant under test. The baselineprofile plugin passes it as the
 * `targetAppId` instrumentation argument (nonMinifiedRelease = no suffix); our own
 * connectedBenchmarkAndroidTest runs fall back to the "benchmark" build type's id.
 */
val TARGET_PACKAGE: String =
    InstrumentationRegistry.getArguments().getString("targetAppId")
        ?: "com.example.carlauncher.benchmark"

/**
 * MainActivity asks for location + calendar on every start; an un-granted permission would put
 * a system dialog over the app and ruin both startup and frame numbers. Granting from the shell
 * makes the request return immediately without UI.
 */
fun MacrobenchmarkScope.grantRuntimePermissions() {
    listOf(
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.READ_CALENDAR",
        "android.permission.POST_NOTIFICATIONS",
    ).forEach { device.executeShellCommand("pm grant $packageName $it") }
}

/** Starts MainActivity and waits until the first frame is drawn and the UI is idle. */
fun MacrobenchmarkScope.startLauncher() {
    grantRuntimePermissions()
    startActivityAndWait()
    device.waitForIdle()
}

// User journeys shared by FrameTimingBenchmark (measured) and BaselineProfileGenerator
// (profiled). Coordinates are fractions of the screen, assuming the default Classic layout
// with the dock at the bottom: map panel = left ~65 %, widget column = right ~35 %.

/** HorizontalPager: Launcher → Widgets → Trip → Widgets → Launcher. */
fun MacrobenchmarkScope.swipeThroughPages() {
    val w = device.displayWidth
    val y = device.displayHeight * 45 / 100
    // Start on the right column (page 0) so the swipe never begins on the map, which
    // disables pager scrolling while touched (MapNavPanel.trackTouchActive).
    repeat(2) { swipeAndSettle(w * 90 / 100, y, w * 10 / 100, y) }
    repeat(2) { swipeAndSettle(w * 10 / 100, y, w * 90 / 100, y) }
}

/** Panning the Mapbox map on page 0 (3D Standard style, pitch 45°). */
fun MacrobenchmarkScope.panMap() {
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

/** Opening the AppDrawer overlay (fade + slide), flinging through the app grid, closing it. */
fun MacrobenchmarkScope.openAndScrollAppDrawer() {
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

private fun MacrobenchmarkScope.swipeAndSettle(x1: Int, y1: Int, x2: Int, y2: Int) {
    device.swipe(x1, y1, x2, y2, 20)
    device.waitForIdle()
    Thread.sleep(300)
}
