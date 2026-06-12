package com.example.carlauncher

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.carlauncher.ui.launcher.LauncherScreen
import com.example.carlauncher.ui.theme.CarLauncherTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* LauncherViewModel starts tracking after permission; LocationRepository
           catches SecurityException if denied — no further action needed here */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        setContent {
            CarLauncherTheme {
                LauncherScreen(
                    onLaunchSplitScreen = { pkg1, pkg2 -> launchSplitScreen(pkg1, pkg2) }
                )
            }
        }
    }

    // ── Split screen ──────────────────────────────────────────────────────────
    // Must be called from Activity (not application Context) for
    // FLAG_ACTIVITY_LAUNCH_ADJACENT to work correctly.

    private fun launchSplitScreen(pkg1: String, pkg2: String) {
        val opened = launchPackage(pkg1)
        if (!opened) {
            Toast.makeText(this, "Aplikaci nelze spustit", Toast.LENGTH_SHORT).show()
            return
        }
        // lifecycleScope — auto-cancelled if Activity is destroyed before the delay fires
        lifecycleScope.launch {
            delay(650L)
            launchPackageAdjacent(pkg2)
        }
    }

    private fun launchPackage(packageName: String): Boolean {
        return runCatching {
            val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            Log.d("SplitScreen", "launched: $packageName")
            true
        }.getOrDefault(false)
    }

    private fun launchPackageAdjacent(packageName: String): Boolean {
        return runCatching {
            val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
                intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            startActivity(intent)
            Log.d("SplitScreen", "launched adjacent: $packageName")
            true
        }.getOrDefault(false)
    }
}
