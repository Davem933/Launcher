package com.example.carlauncher.ui.launcher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.BuildConfig
import com.example.carlauncher.debug.DebugPanel
import com.example.carlauncher.debug.GpsDebugOverlay
import com.example.carlauncher.ui.dock.DockBar
import com.example.carlauncher.ui.dock.DockViewModel
import com.example.carlauncher.ui.navigation.NavAreaWidget
import com.example.carlauncher.ui.music.MusicWidget
import com.example.carlauncher.ui.theme.CarColors
import com.example.carlauncher.ui.launcher.WeatherCalendarWidget

@Composable
fun LauncherScreen(
    isDark: Boolean = true,
    onLaunchSplitScreen: (pkg1: String, pkg2: String) -> Unit = { _, _ -> },
    viewModel: LauncherViewModel = hiltViewModel(),
    dockViewModel: DockViewModel = hiltViewModel()
) {
    val location       by viewModel.vehicleLocation.collectAsStateWithLifecycle()
    val showAppDrawer  by dockViewModel.showAppDrawer.collectAsStateWithLifecycle()
    var debugOverlayVisible by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarColors.Bg)
    ) {
        // ── StatusBar — fixed 44dp ────────────────────────────────────────────
        StatusBar(
            gpsFix = location != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        )

        // ── Main content — fills remaining space ──────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left — nav area ~65% width
                NavAreaWidget(
                    speedKmh = location?.speedKmh ?: 0f,
                    modifier = Modifier
                        .weight(1.85f)
                        .fillMaxHeight()
                )

                // Right — ~35% width: music on top, quick destinations below
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // MusicWidget fills available height (artwork flexes inside);
                    // QuickDest wraps its content — no dead space below the cards
                    MusicWidget(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    SystemControlsWidget(
                        modifier = Modifier.fillMaxWidth()
                    )
                    WeatherCalendarWidget(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

        }

        // ── DockBar — fixed 88dp, padded off the screen edge ─────────────────
        DockBar(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 8.dp),
            onLaunchSplitScreen = onLaunchSplitScreen
        )
    }

    // ── Debug tools — GPS overlay + control panel, DEBUG builds only ──────────
    if (BuildConfig.DEBUG) {
        GpsDebugOverlay(
            location  = location,
            isVisible = debugOverlayVisible,
            onToggle  = { debugOverlayVisible = !debugOverlayVisible }
        )
        DebugPanel(viewModel = viewModel)
    }

    // ── AppDrawer overlay — fullscreen, shown when Menu tile is tapped ────────
    AnimatedVisibility(
        visible = showAppDrawer,
        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 8 },
        exit  = fadeOut(tween(150))
    ) {
        AppDrawer(
            onAppClick = { packageName ->
                dockViewModel.closeAppDrawer()
                dockViewModel.launchApp(packageName)
            },
            onDismiss = { dockViewModel.closeAppDrawer() }
        )
    }

    } // end outer Box
}
