package com.example.carlauncher.ui.aurora

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.example.carlauncher.ui.music.MediaViewModel
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ColumnWidth = 300.dp
private val Edge = 16.dp
private val GlassShape = RoundedCornerShape(24.dp)
private val GlassBg = Color(0xE01C1C1E)              // Card at ~88% over the map
private val GlassOutline = Color.White.copy(alpha = 0.10f)

/** Height of the floating dock (DockScaffold) the map overlays have to clear. */
val AuroraFloatingDockInset = 100.dp

/**
 * "Aurora – mapa přes celou plochu" home page (design 2b): the map is the full-screen background,
 * with a glass column floating top-right — time + weather, now playing, horizontal volume and
 * brightness sliders. The dock floats over the map too (see DockScaffold), so the map's own
 * overlays are inset by [AuroraFloatingDockInset] when [dockFloating].
 */
@Composable
fun AuroraMapScreen(
    dockFloating: Boolean,
    onMapTouchActiveChanged: (Boolean) -> Unit = {},
    viewModel: LauncherViewModel = hiltViewModel()
) {
    val location   by viewModel.vehicleLocation.collectAsStateWithLifecycle()
    val speedLimit by viewModel.speedLimit.collectAsStateWithLifecycle()
    var debugOverlayVisible by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(AuroraColors.Bg)) {
        MapNavPanel(
            speedKmh = location?.speedKmh ?: 0f,
            speedLimitKmh = speedLimit,
            modifier = Modifier.fillMaxSize(),
            onTouchActiveChanged = onMapTouchActiveChanged,
            fullBleed = true,
            overlayInsets = PaddingValues(
                end = ColumnWidth + Edge,
                bottom = if (dockFloating) AuroraFloatingDockInset else 0.dp,
            ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Edge)
                .width(ColumnWidth),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ClockWeatherCard(Modifier.fillMaxWidth())
            CompactMusicCard(Modifier.fillMaxWidth())
            AuroraVolumeSlider(
                modifier = Modifier.fillMaxWidth().height(52.dp).glassOutline(),
                horizontal = true
            )
            AuroraBrightnessSlider(
                modifier = Modifier.fillMaxWidth().height(52.dp).glassOutline(),
                horizontal = true
            )
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

private fun Modifier.glass(): Modifier = this
    .clip(GlassShape)
    .background(GlassBg)
    .border(0.5.dp, GlassOutline, GlassShape)

private fun Modifier.glassOutline(): Modifier = border(0.5.dp, GlassOutline, AuroraCardShape)

// ── Time + weather ───────────────────────────────────────────────────────────

private val timeFmt = DateTimeFormatter.ofPattern("H:mm")
private val dateFmt = DateTimeFormatter.ofPattern("EEEE d. MMMM", Locale("cs", "CZ"))

@Composable
private fun ClockWeatherCard(
    modifier: Modifier = Modifier,
    weatherViewModel: WeatherCalendarViewModel = hiltViewModel(),
) {
    val weather by weatherViewModel.weather.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            // Wake right after the next minute boundary so the clock never lags.
            delay((60 - now.second) * 1000L + 50L)
        }
    }

    Row(
        modifier = modifier.glass().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = now.format(timeFmt),
                color = AuroraColors.Text,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.02).em,
                lineHeight = 46.sp
            )
            Text(
                text = now.format(dateFmt).replaceFirstChar { it.uppercase() },
                color = AuroraColors.Text2,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                wmoIcon(weather?.code), contentDescription = weather?.condition,
                tint = AuroraColors.Text, modifier = Modifier.size(22.dp)
            )
            Text(
                text = weather?.let { "${it.tempC}°" } ?: "–",
                color = AuroraColors.Text,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ── Now playing (compact) ────────────────────────────────────────────────────

@Composable
private fun CompactMusicCard(
    modifier: Modifier = Modifier,
    viewModel: MediaViewModel = hiltViewModel()
) {
    val trackInfo by viewModel.trackInfo.collectAsStateWithLifecycle()
    val position  by viewModel.position.collectAsStateWithLifecycle()
    val info = trackInfo
    val playing = info?.isPlaying == true

    Column(
        modifier = modifier.glass().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF6E4A86), Color(0xFF2C2236)))),
                contentAlignment = Alignment.Center
            ) {
                val art = info?.albumArt
                if (art != null) {
                    Image(
                        bitmap = art.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote, contentDescription = null,
                        tint = AuroraColors.Text.copy(alpha = 0.7f), modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = info?.title ?: "Nic nehraje",
                    color = AuroraColors.Text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val artist = info?.artist.orEmpty()
                if (artist.isNotEmpty()) {
                    Text(
                        text = artist,
                        color = AuroraColors.Text2,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        val duration = info?.duration ?: 0L
        val fraction = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.16f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(Color.White.copy(alpha = 0.85f))
            )
        }

        // Always shown; dimmed while no session is bound (same as the other music widgets).
        Row(
            modifier = Modifier.fillMaxWidth().alpha(if (info != null) 1f else 0.45f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransportButton(Icons.Default.FastRewind, "Předchozí") { viewModel.skipPrevious() }
            TransportButton(
                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (playing) "Pozastavit" else "Přehrát"
            ) { if (playing) viewModel.pause() else viewModel.play() }
            TransportButton(Icons.Default.FastForward, "Další") { viewModel.skipNext() }
        }
    }
}

@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = AuroraColors.Text, modifier = Modifier.size(30.dp))
    }
}
