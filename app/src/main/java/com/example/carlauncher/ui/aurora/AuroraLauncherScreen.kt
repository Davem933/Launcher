package com.example.carlauncher.ui.aurora

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.BuildConfig
import com.example.carlauncher.debug.GpsDebugOverlay
import com.example.carlauncher.ui.launcher.LauncherViewModel
import com.example.carlauncher.ui.launcher.MapNavPanel

/** Apple-inspired palette from the "Car Launcher Apple v2" Claude Design file. */
internal object AuroraColors {
    val Bg       = Color(0xFF000000)
    val Card     = Color(0xFF1C1C1E)
    val Fill     = Color(0xFF2C2C2E)
    val Text     = Color(0xFFF5F5F7)
    val Text2    = Color(0x99EBEBF5)   // rgba(235,235,245,.6)
    val Text3    = Color(0x80EBEBF5)   // rgba(235,235,245,.5)
    val Gray     = Color(0xFF98989D)
    val Glow     = Color(0xFFBF5AF2)
    val Red      = Color(0xFFFF453A)
    val SkyTop   = Color(0xFF3D7FD9)
    val SkyBot   = Color(0xFF2A5EB0)
}

internal val AuroraCardShape = RoundedCornerShape(28.dp)
private val Gap = 14.dp

/**
 * "Aurora" home page (design 2a Dashboard): map on the left, a 340dp column on the right with
 * now-playing, vertical volume/brightness sliders + weather, and today's calendar.
 * Same data sources as the classic [com.example.carlauncher.ui.launcher.LauncherScreen].
 */
@Composable
fun AuroraLauncherScreen(
    onMapTouchActiveChanged: (Boolean) -> Unit = {},
    viewModel: LauncherViewModel = hiltViewModel()
) {
    val location   by viewModel.vehicleLocation.collectAsStateWithLifecycle()
    val speedLimit by viewModel.speedLimit.collectAsStateWithLifecycle()
    var debugOverlayVisible by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(AuroraColors.Bg)) {
        Row(
            modifier = Modifier.fillMaxSize().padding(Gap),
            horizontalArrangement = Arrangement.spacedBy(Gap)
        ) {
            MapNavPanel(
                speedKmh = location?.speedKmh ?: 0f,
                speedLimitKmh = speedLimit,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(AuroraCardShape),
                onTouchActiveChanged = onMapTouchActiveChanged,
            )

            Column(
                modifier = Modifier.width(340.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(Gap)
            ) {
                AuroraMusicCard(modifier = Modifier.fillMaxWidth().weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    horizontalArrangement = Arrangement.spacedBy(Gap)
                ) {
                    AuroraVolumeSlider(modifier = Modifier.width(80.dp).fillMaxHeight())
                    AuroraBrightnessSlider(modifier = Modifier.width(80.dp).fillMaxHeight())
                    AuroraWeatherCard(modifier = Modifier.weight(1f).fillMaxHeight())
                }
                AuroraCalendarCard(modifier = Modifier.fillMaxWidth())
            }
        }

        if (BuildConfig.DEBUG) {
            GpsDebugOverlay(
                location  = location,
                isVisible = debugOverlayVisible,
                onToggle  = { debugOverlayVisible = !debugOverlayVisible }
            )
        }
    }
}
