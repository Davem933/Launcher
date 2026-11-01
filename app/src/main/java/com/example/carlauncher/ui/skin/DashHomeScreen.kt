package com.example.carlauncher.ui.skin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import kotlin.math.roundToInt

/** IBM Plex Mono in the design — the platform monospace keeps the instrument-panel feel. */
internal val DashMono = FontFamily.Monospace
private val Hairline = 1.dp
private const val SEGMENTS = 8

/**
 * 2b "Přístrojová deska": sharp panels separated by 1dp hairlines (the page background shows
 * through the gaps), map on the left, a 384dp instrument column on the right. The dock sits on
 * top — DockScaffold puts it there for this style.
 */
@Composable
fun DashHomeScreen(
    onOpenIncident: (() -> Unit)?,
    onMapTouchActiveChanged: (Boolean) -> Unit = {},
    viewModel: LauncherViewModel = hiltViewModel()
) {
    val location   by viewModel.vehicleLocation.collectAsStateWithLifecycle()
    val speedLimit by viewModel.speedLimit.collectAsStateWithLifecycle()
    var debugOverlayVisible by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(DashColors.Line)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Hairline)) {
            CompositionLocalProvider(LocalMapChrome provides DashMapChrome) {
                MapNavPanel(
                    speedKmh = location?.speedKmh ?: 0f,
                    speedLimitKmh = speedLimit,
                    modifier = Modifier.weight(1f).fillMaxHeight().background(DashColors.Panel),
                    onTouchActiveChanged = onMapTouchActiveChanged,
                )
            }

            Column(
                modifier = Modifier.width(384.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(Hairline)
            ) {
                DashMusicPanel(Modifier.fillMaxWidth().weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    horizontalArrangement = Arrangement.spacedBy(Hairline)
                ) {
                    DashLevelPanel(
                        control = rememberVolumeControl(),
                        label = "HLASITOST",
                        icon = Icons.Filled.VolumeUp,
                        color = DashColors.Lime,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    DashLevelPanel(
                        control = rememberBrightnessControl(),
                        label = "JAS",
                        icon = Icons.Filled.WbSunny,
                        color = DashColors.Amber,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        trailing = onOpenIncident?.let { open -> { DashIncidentButton(open) } }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(184.dp),
                    horizontalArrangement = Arrangement.spacedBy(Hairline)
                ) {
                    DashWeatherPanel(Modifier.weight(1f).fillMaxHeight())
                    DashCalendarPanel(Modifier.weight(1f).fillMaxHeight())
                }
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
private fun DashLabel(text: String, color: Color = DashColors.Text2) {
    Text(text, color = color, fontSize = 10.sp, fontFamily = DashMono, letterSpacing = 0.1.em, maxLines = 1)
}

@Composable
private fun DashMusicPanel(modifier: Modifier, viewModel: MediaViewModel = hiltViewModel()) {
    val info by viewModel.trackInfo.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .background(DashColors.Panel)
            .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = DashColors.Lime, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) { DashLabel(info.playerLabel().uppercase()) }
            PlayerMenuButton(tint = DashColors.Text, viewModel = viewModel)
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            AlbumArtOrNote(
                info = info,
                noteTint = DashColors.Dashed,
                noteSize = 34.dp,
                modifier = Modifier
                    .size(120.dp)
                    .border(Hairline, DashColors.Line)
                    .clipToBounds()
                    .stripes()
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    info.titleOrIdle(), color = DashColors.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                    lineHeight = 24.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                val artist = info?.artist.orEmpty()
                if (artist.isNotEmpty()) {
                    Text(artist, color = DashColors.Text2, fontSize = 12.sp, fontFamily = DashMono, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TransportControls(
                    info = info,
                    tint = DashColors.Text,
                    playBg = DashColors.Lime,
                    playFg = DashColors.Panel,
                    viewModel = viewModel,
                    playShape = RectangleShape,
                    playSize = 48.dp,
                )
            }
        }
    }
}

/** The design's diagonal 8px stripes behind the empty album art. */
private fun Modifier.stripes(): Modifier = background(DashColors.Sub).drawBehind {
    val step = 11.dp.toPx()
    val w = 5.5.dp.toPx()
    var x = -size.height
    while (x < size.width) {
        drawLine(DashColors.Stripe, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = w)
        x += step
    }
}

@Composable
private fun DashLevelPanel(
    control: LevelControl,
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier = modifier.background(DashColors.Panel), verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .levelDrag(control)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { DashLabel(label) }
                if (trailing == null) Icon(icon, contentDescription = null, tint = DashColors.Text, modifier = Modifier.size(15.dp))
            }
            Text(
                buildAnnotatedString {
                    append("${control.percent}")
                    withStyle(SpanStyle(fontSize = 13.sp, color = DashColors.Text2)) { append("%") }
                },
                color = DashColors.Text, fontSize = 29.sp, fontWeight = FontWeight.SemiBold
            )
            val lit = (control.level * SEGMENTS).roundToInt()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(SEGMENTS) { i ->
                    Box(Modifier.weight(1f).height(7.dp).background(if (i < lit) color else DashColors.Line))
                }
            }
        }
        if (trailing != null) {
            Box(Modifier.padding(end = 12.dp)) { trailing() }
        }
    }
}

@Composable
private fun DashIncidentButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .border(2.dp, DashColors.Text)
            .background(DashColors.Panel)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(IncidentIcon, contentDescription = "Záznam incidentu", tint = DashColors.Text, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DashWeatherPanel(modifier: Modifier, viewModel: WeatherCalendarViewModel = hiltViewModel()) {
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    Column(
        modifier = modifier.background(DashColors.Panel).padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        DashLabel("POČASÍ")
        Icon(wmoIcon(weather?.code), contentDescription = null, tint = DashColors.Text, modifier = Modifier.size(32.dp))
        Column {
            Text(weather?.let { "${it.tempC}°C" } ?: "–", color = DashColors.Text, fontSize = 38.sp, fontWeight = FontWeight.SemiBold, lineHeight = 40.sp)
            Text(
                weather?.condition.orEmpty(), color = DashColors.Text2, fontSize = 11.sp, fontFamily = DashMono,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun DashCalendarPanel(modifier: Modifier, viewModel: WeatherCalendarViewModel = hiltViewModel()) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    Column(
        modifier = modifier.background(DashColors.Panel).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DashLabel(todayLabel().uppercase(), color = DashColors.Lime)
        if (events.isEmpty()) {
            Text("Žádné události", color = DashColors.Text2, fontSize = 13.sp, fontFamily = DashMono)
        } else {
            events.take(2).forEach { event ->
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(2.dp).fillMaxHeight().background(DashColors.Lime))
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(event.timeLabel, color = DashColors.Text2, fontSize = 11.sp, fontFamily = DashMono)
                        Text(event.title, color = DashColors.Text, fontSize = 14.sp, fontFamily = DashMono, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
