package com.example.carlauncher.macrobenchmark

import androidx.benchmark.macro.MacrobenchmarkScope

/** applicationId of the app's "benchmark" build type (see app/build.gradle.kts). */
const val TARGET_PACKAGE = "com.example.carlauncher.benchmark"

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
