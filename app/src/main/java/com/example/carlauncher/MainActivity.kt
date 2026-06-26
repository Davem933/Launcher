package com.example.carlauncher

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.ui.theme.ThemeViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.carlauncher.ui.launcher.LauncherScreen
import com.example.carlauncher.ui.theme.CarLauncherTheme
import com.example.carlauncher.ui.widgets.WidgetScreen
import com.example.carlauncher.ui.widgets.WidgetViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val widgetViewModel: WidgetViewModel by viewModels()

    private var pendingWidgetId  by mutableIntStateOf(-1)
    private var pendingSlotIndex by mutableIntStateOf(-1)

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* LocationRepository catches SecurityException on denial */ }

    private val widgetPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val id = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
            if (id != -1) configureOrAdd(id)
        } else if (pendingWidgetId != -1) {
            widgetViewModel.appWidgetHost.deleteAppWidgetId(pendingWidgetId)
            pendingWidgetId  = -1
            pendingSlotIndex = -1
        }
    }

    private val widgetConfigureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && pendingWidgetId != -1 && pendingSlotIndex != -1) {
            widgetViewModel.addWidgetToSlot(pendingSlotIndex, pendingWidgetId)
        } else if (pendingWidgetId != -1) {
            widgetViewModel.appWidgetHost.deleteAppWidgetId(pendingWidgetId)
        }
        pendingWidgetId  = -1
        pendingSlotIndex = -1
    }

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

        // MANAGE_EXTERNAL_STORAGE needed to read PMTiles file from /storage/emulated/0/CarLauncher/
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        }

        setContent {
            val themeVm = hiltViewModel<ThemeViewModel>()
            val isDarkSensor by themeVm.isDarkTheme.collectAsStateWithLifecycle()
            // If no physical light sensor, defer to Android system dark-mode schedule.
            val isDark = if (themeVm.hasSensor) isDarkSensor else isSystemInDarkTheme()

            CarLauncherTheme(isDark = isDark) {
                val pagerState = rememberPagerState(pageCount = { 2 })

                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1
                    ) { page ->
                        when (page) {
                            0 -> LauncherScreen(
                                isDark = isDark,
                                onLaunchSplitScreen = { pkg1, pkg2 -> launchSplitScreen(pkg1, pkg2) }
                            )
                            1 -> WidgetScreen(
                                onLaunchSplitScreen = { pkg1, pkg2 -> launchSplitScreen(pkg1, pkg2) },
                                onAddWidget = { slotIndex -> launchWidgetPicker(slotIndex) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 100.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        repeat(2) { i ->
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (pagerState.currentPage == i) Color.White
                                        else Color.White.copy(alpha = 0.35f)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun launchWidgetPicker(slotIndex: Int) {
        pendingSlotIndex = slotIndex
        pendingWidgetId  = widgetViewModel.allocateWidgetId()
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
        }
        widgetPickerLauncher.launch(intent)
    }

    private fun configureOrAdd(appWidgetId: Int) {
        pendingWidgetId = appWidgetId
        val info = widgetViewModel.appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (info?.configure != null) {
            val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = info.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            widgetConfigureLauncher.launch(configIntent)
        } else {
            widgetViewModel.addWidgetToSlot(pendingSlotIndex, appWidgetId)
            pendingWidgetId  = -1
            pendingSlotIndex = -1
        }
    }

    // ── Split screen ──────────────────────────────────────────────────────────

    private fun launchSplitScreen(pkg1: String, pkg2: String) {
        val opened = launchPackage(pkg1)
        if (!opened) {
            Toast.makeText(this, "Aplikaci nelze spustit", Toast.LENGTH_SHORT).show()
            return
        }
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
