package com.example.carlauncher.ui.launcher

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.BuildConfig
import com.example.carlauncher.ui.dock.DockBar
import com.example.carlauncher.ui.dock.DockViewModel
import com.example.carlauncher.ui.map.MapWidget
import com.example.carlauncher.ui.music.MusicWidget
import com.example.carlauncher.ui.theme.CarColors

@Composable
fun LauncherScreen(
    onLaunchSplitScreen: (pkg1: String, pkg2: String) -> Unit = { _, _ -> },
    viewModel: LauncherViewModel = hiltViewModel(),
    dockViewModel: DockViewModel = hiltViewModel()
) {
    val location       by viewModel.vehicleLocation.collectAsStateWithLifecycle()
    val showAppDrawer  by dockViewModel.showAppDrawer.collectAsStateWithLifecycle()

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
                // Left — map ~65% width (SpeedDisplay + Navigovat overlays inside)
                MapWidget(
                    modifier = Modifier
                        .weight(1.85f)
                        .fillMaxHeight(),
                    onNavigate = { dockViewModel.launchNavigation() }
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
                    QuickDestWidget(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // GPS debug overlay (top-left over the map) — debug builds only
            if (BuildConfig.DEBUG) location?.let {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(24.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        "GPS: ${"%.5f".format(it.lat)}, ${"%.5f".format(it.lng)}",
                        color = CarColors.Go,
                        fontSize = 12.sp
                    )
                    Text(
                        "Speed: ${"%.1f".format(it.speedKmh)} km/h",
                        color = CarColors.Text,
                        fontSize = 12.sp
                    )
                    Text(
                        "Bearing: ${"%.0f".format(it.bearingDeg)}°",
                        color = CarColors.Text,
                        fontSize = 12.sp
                    )
                    Text(
                        "Accuracy: ${"%.0f".format(it.accuracyM)}m",
                        color = CarColors.Text3,
                        fontSize = 12.sp
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

    // ── AppDrawer overlay — fullscreen, shown when Menu tile is tapped ────────
    if (showAppDrawer) {
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
