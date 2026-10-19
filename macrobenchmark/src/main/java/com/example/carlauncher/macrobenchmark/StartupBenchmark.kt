package com.example.carlauncher.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Time to initial display (TTID) of MainActivity.
 *
 * - `None` = freshly installed APK, everything interpreted/JIT — the worst case, what the user
 *   sees right after every `installDebug`/update.
 * - `Partial(Disable, warmup)` = the app after a few runs, once ART has JIT-profiled the hot code
 *   paths — the steady state on the tablet. The gap between the two is roughly what a Baseline
 *   Profile could win back.
 * - `Partial()` = only the shipped Baseline Profile compiled (BaselineProfileMode.Require, no
 *   warm-up) — what the user gets right after install since phase 2. Fails if the APK has no
 *   profile, so a lost baseline-prof.txt can't silently go unnoticed.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartNoCompilation() = startup(StartupMode.COLD, CompilationMode.None())

    @Test
    fun coldStartBaselineProfile() = startup(StartupMode.COLD, CompilationMode.Partial())

    @Test
    fun warmStartBaselineProfile() = startup(StartupMode.WARM, CompilationMode.Partial())

    @Test
    fun coldStartWarmedUp() = startup(StartupMode.COLD, warmedUp())

    @Test
    fun warmStartWarmedUp() = startup(StartupMode.WARM, warmedUp())

    private fun warmedUp() = CompilationMode.Partial(
        baselineProfileMode = BaselineProfileMode.Disable,
        warmupIterations = 3,
    )

    private fun startup(mode: StartupMode, compilation: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilation,
        startupMode = mode,
        iterations = 10,
        setupBlock = {
            pressHome()
            grantRuntimePermissions()
        },
    ) {
        startActivityAndWait()
    }
}
