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
private val CardShape = RoundedCornerShape(26.dp)
private val TileShape = RoundedCornerShape(22.dp)

/**
 * 2a "Světlý režim": a 350dp widget column on the left (now playing, volume / brightness /
 * incident camera, weather + today's calendar), the map on the right. The pill dock below
 * is drawn by DockScaffold/DockBar.
 */
@Composable
fun LightHomeScreen(
    onOpenIncident: (() -> Unit)?,
    onMapTouchActiveChanged: (Boolean) -> Unit = {},
    viewModel: LauncherViewModel = hiltViewModel()
) {
    val location   by viewModel.vehicleLocation.collectAsStateWithLifecycle()
    val speedLimit by viewModel.speedLimit.collectAsStateWithLifecycle()
    var debugOverlayVisible by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(LightColors.Bg)) {
        Row(
            modifier = Modifier.fillMaxSize().padding(Gap),
            horizontalArrangement = Arrangement.spacedBy(Gap)
        ) {
            Column(
                modifier = Modifier.width(350.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(Gap)
            ) {
                LightMusicCard(Modifier.fillMaxWidth().weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth().height(88.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LightLevelTile(
                        control = rememberVolumeControl(),
                        label = "HLASITOST",
                        icon = Icons.Filled.VolumeUp,
                        bg = LightColors.VolBg,
                        fg = LightColors.VolFg,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    LightLevelTile(
                        control = rememberBrightnessControl(),
                        label = "JAS",
                        icon = Icons.Filled.WbSunny,
                        bg = LightColors.BriBg,
                        fg = LightColors.BriFg,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    if (onOpenIncident != null) {
                        Box(
                            modifier = Modifier
                                .width(70.dp)
                                .fillMaxHeight()
                                .clip(TileShape)
                                .background(LightColors.Text)
                                .clickable(onClick = onOpenIncident),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(IncidentIcon, contentDescription = "Záznam incidentu", tint = LightColors.Card, modifier = Modifier.size(26.dp))
                        }
                    }
                }
                LightWeatherCalendarCard(Modifier.fillMaxWidth().height(176.dp))
            }

            CompositionLocalProvider(LocalMapChrome provides LightMapChrome) {
                MapNavPanel(
                    speedKmh = location?.speedKmh ?: 0f,
                    speedLimitKmh = speedLimit,
                    modifier = Modifier.weight(1f).fillMaxHeight().clip(LightMapChrome.panelShape),
                    onTouchActiveChanged = onMapTouchActiveChanged,
                )
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
private fun LightMusicCard(modifier: Modifier, viewModel: MediaViewModel = hiltViewModel()) {
    val info by viewModel.trackInfo.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .clip(CardShape)
            .background(LightColors.Card)
            .padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = LightColors.Red, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                info.playerLabel(), color = LightColors.Text2, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            PlayerMenuButton(tint = LightColors.Text2, viewModel = viewModel)
        }
        AlbumArtOrNote(
            info = info,
            noteTint = LightColors.Muted,
            noteSize = 44.dp,
            modifier = Modifier
                .padding(end = 10.dp)
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(LightColors.Inset)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    info.titleOrIdle(), color = LightColors.Text, fontSize = 23.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.01).em, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                val artist = info?.artist.orEmpty()
                if (artist.isNotEmpty()) {
                    Text(artist, color = LightColors.Text2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            TransportControls(
                info = info,
                tint = LightColors.Text,
                playBg = LightColors.Text,
                playFg = LightColors.Card,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun LightLevelTile(
    control: LevelControl,
    label: String,
    icon: ImageVector,
    bg: Color,
    fg: Color,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .clip(TileShape)
            .background(bg)
            .levelDrag(control)
    ) {
        // Level shown as a slightly deeper wash from the left — swipe sideways to change it.
        Box(Modifier.fillMaxHeight().fillMaxWidth(control.level).background(fg.copy(alpha = 0.10f)))
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(19.dp))
            }
            Column(Modifier.padding(start = 10.dp)) {
                Text(label, color = fg, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em, maxLines = 1)
                Text("${control.percent}%", color = LightColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LightWeatherCalendarCard(modifier: Modifier, viewModel: WeatherCalendarViewModel = hiltViewModel()) {
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val events  by viewModel.events.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .clip(CardShape)
            .background(LightColors.Card)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(wmoIcon(weather?.code), contentDescription = null, tint = LightColors.Cloud, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
            Text(weather?.let { "${it.tempC}°C" } ?: "–", color = LightColors.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Text(
                weather?.condition.orEmpty(), color = LightColors.Text2, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            Text(todayLabel(), color = LightColors.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        if (events.isEmpty()) {
            LightEventRow(time = "", title = "Žádné události dnes", dot = LightColors.Muted)
        } else {
            events.take(2).forEach { LightEventRow(it.timeLabel, it.title, Color(it.color)) }
        }
    }
}

@Composable
private fun LightEventRow(time: String, title: String, dot: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(LightColors.Inset)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(11.dp))
        if (time.isNotEmpty()) {
            Text(time, color = LightColors.Text2, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(44.dp))
        }
        Text(title, color = LightColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
