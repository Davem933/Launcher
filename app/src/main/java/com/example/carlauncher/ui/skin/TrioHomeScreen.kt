package com.example.carlauncher.ui.skin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.BuildConfig
import com.example.carlauncher.debug.GpsDebugOverlay
import com.example.carlauncher.ui.launcher.LauncherViewModel
import com.example.carlauncher.ui.launcher.MapNavPanel
import com.example.carlauncher.ui.launcher.WeatherCalendarViewModel
import com.example.carlauncher.ui.launcher.wmoIcon
import com.example.carlauncher.ui.map.LocalMapChrome
import com.example.carlauncher.ui.music.MediaViewModel

private val Gap = 12.dp
private val BigShape = RoundedCornerShape(32.dp)
private val TileShape = RoundedCornerShape(26.dp)

/**
 * 2c "Tři sloupce": coral now-playing card + volume/brightness on the left, the map in the
 * middle (incident camera floats next to "Moje poloha"), clock, weather and today's calendar
 * on the right. The centred pill dock below is drawn by DockScaffold/DockBar.
 */
@Composable
fun TrioHomeScreen(
    onOpenIncident: (() -> Unit)?,
    onMapTouchActiveChanged: (Boolean) -> Unit = {},
    viewModel: LauncherViewModel = hiltViewModel()
) {
    val location   by viewModel.vehicleLocation.collectAsStateWithLifecycle()
    val speedLimit by viewModel.speedLimit.collectAsStateWithLifecycle()
    var debugOverlayVisible by remember { mutableStateOf(false) }
    val chrome = remember(onOpenIncident) {
        trioMapChrome(onOpenIncident?.let { open -> { TrioIncidentButton(open) } })
    }

    Box(Modifier.fillMaxSize().background(TrioColors.Bg)) {
        Row(
            modifier = Modifier.fillMaxSize().padding(Gap),
            horizontalArrangement = Arrangement.spacedBy(Gap)
        ) {
            Column(
                modifier = Modifier.width(288.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(Gap)
            ) {
                TrioMusicCard(Modifier.fillMaxWidth().weight(1f))
                Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TrioLevelTile(rememberVolumeControl(), "HLASITOST", Icons.Filled.VolumeUp, TrioColors.Blue, Modifier.weight(1f).fillMaxHeight())
                    TrioLevelTile(rememberBrightnessControl(), "JAS", Icons.Filled.WbSunny, TrioColors.Yellow, Modifier.weight(1f).fillMaxHeight())
                }
            }

            CompositionLocalProvider(LocalMapChrome provides chrome) {
                MapNavPanel(
                    speedKmh = location?.speedKmh ?: 0f,
                    speedLimitKmh = speedLimit,
                    modifier = Modifier.weight(1f).fillMaxHeight().clip(chrome.panelShape),
                    onTouchActiveChanged = onMapTouchActiveChanged,
                )
            }

            Column(
                modifier = Modifier.width(240.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(Gap)
            ) {
                TrioClockCard(Modifier.fillMaxWidth())
                TrioWeatherCard(Modifier.fillMaxWidth())
                TrioCalendarCard(Modifier.fillMaxWidth().weight(1f))
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

@Composable
private fun TrioIncidentButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(52.dp).clip(CircleShape).background(TrioColors.Text).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(IncidentIcon, contentDescription = "Záznam incidentu", tint = TrioColors.Bg, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun TrioMusicCard(modifier: Modifier, viewModel: MediaViewModel = hiltViewModel()) {
    val info by viewModel.trackInfo.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .clip(BigShape)
            .background(TrioColors.Coral)
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = TrioColors.OnCoral, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                info.playerLabel(), color = TrioColors.OnCoral, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            PlayerMenuButton(tint = TrioColors.OnCoral, viewModel = viewModel)
        }
        // Two soft concentric discs around the note (or the album art, cropped round).
        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .size(156.dp)
                .clip(CircleShape)
                .background(TrioColors.OnCoral.copy(alpha = 0.12f))
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
        ) {
            AlbumArtOrNote(
                info = info,
                noteTint = TrioColors.OnCoral,
                noteSize = 36.dp,
                modifier = Modifier
                    .size(if (info?.albumArt != null) 156.dp else 94.dp)
                    .clip(CircleShape)
                    .background(TrioColors.OnCoral.copy(alpha = 0.14f))
            )
        }
        Column(Modifier.padding(end = 12.dp)) {
            Text(
                info.titleOrIdle(), color = TrioColors.OnCoral, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                lineHeight = 30.sp, letterSpacing = (-0.02).em, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            val artist = info?.artist.orEmpty()
            if (artist.isNotEmpty()) {
                Text(artist, color = TrioColors.OnCoral.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        TransportControls(
            info = info,
            tint = TrioColors.OnCoral,
            playBg = TrioColors.OnCoral,
            playFg = TrioColors.Coral,
            viewModel = viewModel,
            modifier = Modifier.padding(end = 12.dp).align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun TrioLevelTile(control: LevelControl, label: String, icon: ImageVector, accent: Color, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(TileShape)
            .background(TrioColors.Card)
            // Vertical swipe, like the tile's column layout suggests.
            .levelDrag(control, vertical = true)
    ) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(control.level)
                .background(accent.copy(alpha = 0.12f))
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = TrioColors.Text2, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.em, maxLines = 1)
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
            Text("${control.percent}%", color = TrioColors.Text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun TrioClockCard(modifier: Modifier, viewModel: WeatherCalendarViewModel = hiltViewModel()) {
    val status = rememberStatus()
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    Column(
        modifier = modifier.clip(BigShape).background(TrioColors.Text).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            status.time, color = TrioColors.Bg, fontSize = 58.sp, fontWeight = FontWeight.ExtraBold,
            lineHeight = 60.sp, letterSpacing = (-0.04).em, maxLines = 1
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.BatteryStd, contentDescription = null, tint = TrioColors.ClockSub, modifier = Modifier.size(14.dp))
                Text("${status.battery} %", color = TrioColors.ClockSub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(wmoIcon(weather?.code), contentDescription = null, tint = TrioColors.ClockSub, modifier = Modifier.size(14.dp))
                Text(weather?.let { " ${it.tempC}°" } ?: "", color = TrioColors.ClockSub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TrioWeatherCard(modifier: Modifier, viewModel: WeatherCalendarViewModel = hiltViewModel()) {
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    Column(
        modifier = modifier.clip(BigShape).background(TrioColors.Card).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(wmoIcon(weather?.code), contentDescription = null, tint = TrioColors.Cloud, modifier = Modifier.size(32.dp))
        Text(weather?.let { "${it.tempC}°C" } ?: "–", color = TrioColors.Text, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 44.sp)
        Text(weather?.condition.orEmpty(), color = TrioColors.Text2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TrioCalendarCard(modifier: Modifier, viewModel: WeatherCalendarViewModel = hiltViewModel()) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    Column(
        modifier = modifier.clip(BigShape).background(TrioColors.Card).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(todayLabel(), color = TrioColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (events.isEmpty()) {
            Text("Žádné události", color = TrioColors.Text2, fontSize = 13.sp)
        } else {
            events.take(3).forEach { event ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(TrioColors.Inner)
                        .padding(horizontal = 13.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(event.timeLabel, color = TrioColors.Blue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(event.title, color = TrioColors.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
