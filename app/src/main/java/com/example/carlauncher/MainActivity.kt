package com.example.carlauncher

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import com.example.carlauncher.ui.dock.DockScaffold
import com.example.carlauncher.ui.dock.DockViewModel
import com.example.carlauncher.ui.launcher.AppDrawer
import com.example.carlauncher.ui.settings.LauncherSettingsScreen
import com.example.carlauncher.ui.theme.CarColors
import com.example.carlauncher.ui.incident.IncidentFab
import com.example.carlauncher.ui.incident.IncidentRecorderScreen
import com.example.carlauncher.ui.launcher.LauncherScreen
import com.example.carlauncher.ui.aurora.AuroraLauncherScreen
import com.example.carlauncher.ui.aurora.AuroraFloatingDockInset
import com.example.carlauncher.ui.aurora.AuroraMapScreen
import com.example.carlauncher.data.model.DockPosition
import com.example.carlauncher.data.model.LauncherStyle
import com.example.carlauncher.ui.theme.CarLauncherTheme
import com.example.carlauncher.ui.theme.DeviceScaleProvider
import com.example.carlauncher.ui.widgets.TripScreen
import com.example.carlauncher.ui.widgets.WidgetScreen
import com.example.carlauncher.ui.widgets.WidgetViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** After start-up (first frame, map style), compose the Trip page too — see HorizontalPager. */
private const val PRELOAD_ALL_PAGES_DELAY_MS = 2_000L

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
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_CALENDAR,
            )
        )

        setContent {
            val themeVm = hiltViewModel<ThemeViewModel>()
            val isDarkSensor by themeVm.isDarkTheme.collectAsStateWithLifecycle()
            // If no physical light sensor, defer to Android system dark-mode schedule.
            val isDark = if (themeVm.hasSensor) isDarkSensor else isSystemInDarkTheme()

            CarLauncherTheme(isDark = isDark) {
                DeviceScaleProvider {
                    val pagerState = rememberPagerState(pageCount = { 3 })
                    // Start with only page 0 + 1 composed (faster first frame), then keep all
                    // three pages composed. With 1 for good, reaching the Trip page disposed
                    // page 0 (MapView teardown ~40 ms) and swiping back rebuilt it — the P99
                    // spikes in pagerSwipe. Deferred so the Trip page (Room, voice notes) isn't
                    // part of the cold-start frame.
                    var preloadAllPages by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(PRELOAD_ALL_PAGES_DELAY_MS)
                        preloadAllPages = true
                    }
                    val incidentEnabled = BuildConfig.INCIDENT_RECORDER_ENABLED
                    var showIncidentRecorder by remember { mutableStateOf(false) }
                    // Disabled for the duration of any touch on the map panel (see MapNavPanel's
                    // trackTouchActive) — otherwise a left/right (or diagonal) pan on the map gets
                    // hijacked into a page swipe, since Pager has no way to negotiate gesture
                    // ownership with the native MapView's own gesture detector on its own.
                    var mapTouchActive by remember { mutableStateOf(false) }
                    // Same Activity-scoped instance DockBar uses.
                    val dockVm = hiltViewModel<DockViewModel>()
                    val showAppDrawer by dockVm.showAppDrawer.collectAsStateWithLifecycle()
                    val showSettings by dockVm.showSettings.collectAsStateWithLifecycle()
                    val launcherStyle by dockVm.style.collectAsStateWithLifecycle()
                    val dockPosition by dockVm.position.collectAsStateWithLifecycle()
                    // Aurora mapa + bottom dock: the dock floats over the pages (DockScaffold).
                    val dockFloating = launcherStyle == LauncherStyle.AURORA_MAP &&
                        dockPosition == DockPosition.BOTTOM
                    val nonMapPagePadding = if (dockFloating) AuroraFloatingDockInset else 0.dp

                    Box(modifier = Modifier.fillMaxSize()) {
                        // Dock sits OUTSIDE the pager so it stays fixed while the pages swipe.
                        DockScaffold(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (launcherStyle.isAurora) Color.Black else CarColors.Bg
                                ),
                            onLaunchSplitScreen = { pkg1, pkg2 -> launchSplitScreen(pkg1, pkg2) }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize(),
                                    beyondViewportPageCount = if (preloadAllPages) 2 else 1,
                                    userScrollEnabled = !mapTouchActive,
                                ) { page ->
                                    when (page) {
                                        0 -> when (launcherStyle) {
                                            LauncherStyle.CLASSIC -> LauncherScreen(
                                                isDark = isDark,
                                                onMapTouchActiveChanged = { active -> mapTouchActive = active }
                                            )
                                            LauncherStyle.AURORA -> AuroraLauncherScreen(
                                                onMapTouchActiveChanged = { active -> mapTouchActive = active }
                                            )
                                            LauncherStyle.AURORA_MAP -> AuroraMapScreen(
                                                dockFloating = dockFloating,
                                                onMapTouchActiveChanged = { active -> mapTouchActive = active }
                                            )
                                        }
                                        1 -> Box(Modifier.padding(bottom = nonMapPagePadding)) {
                                            WidgetScreen(
                                                onAddWidget = { slotIndex -> launchWidgetPicker(slotIndex) }
                                            )
                                        }
                                        2 -> Box(Modifier.padding(bottom = nonMapPagePadding)) {
                                            TripScreen()
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    repeat(3) { i ->
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

                        // AppDrawer / Settings — fullscreen overlays above pager AND dock
                        AnimatedVisibility(
                            visible = showAppDrawer,
                            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 8 },
                            exit  = fadeOut(tween(150))
                        ) {
                            AppDrawer(
                                onAppClick = { packageName ->
                                    dockVm.closeAppDrawer()
                                    dockVm.launchApp(packageName)
                                },
                                onOpenSettings = { dockVm.openSettings() },
                                onDismiss = { dockVm.closeAppDrawer() }
                            )
                        }
                        AnimatedVisibility(
                            visible = showSettings,
                            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 8 },
                            exit  = fadeOut(tween(150))
                        ) {
                            LauncherSettingsScreen(onDismiss = { dockVm.closeSettings() })
                        }

                        // Incident Recorder — draggable floating entry point (feature-flagged)
                        if (incidentEnabled && !showIncidentRecorder) {
                            IncidentFab(onOpen = { showIncidentRecorder = true })
                        }

                        if (incidentEnabled && showIncidentRecorder) {
                            IncidentRecorderScreen(onClose = { showIncidentRecorder = false })
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
