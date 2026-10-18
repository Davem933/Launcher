package com.example.carlauncher.ui.aurora

import android.app.Activity
import android.media.AudioManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.data.calendar.CalendarEvent
import com.example.carlauncher.ui.launcher.WeatherCalendarViewModel
import com.example.carlauncher.ui.launcher.wmoIcon
import com.example.carlauncher.ui.music.MediaViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// ── Now playing ──────────────────────────────────────────────────────────────

@Composable
fun AuroraMusicCard(
    modifier: Modifier = Modifier,
    viewModel: MediaViewModel = hiltViewModel()
) {
    val trackInfo by viewModel.trackInfo.collectAsStateWithLifecycle()
    val position  by viewModel.position.collectAsStateWithLifecycle()
    val info = trackInfo
    val playing = info?.isPlaying == true

    Box(
        modifier = modifier
            .clip(AuroraCardShape)
            .background(AuroraColors.Card)
            // Purple "aurora" glow from the top-left corner
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(AuroraColors.Glow.copy(alpha = 0.32f), Color.Transparent),
                        center = Offset(size.width * 0.2f, 0f),
                        radius = size.width * 0.9f
                    )
                )
            }
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
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
                            tint = AuroraColors.Text.copy(alpha = 0.7f), modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = info?.title ?: "Nic nehraje",
                        color = AuroraColors.Text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.015).em,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val artist = info?.artist.orEmpty()
                    if (artist.isNotEmpty()) {
                        Text(
                            text = artist,
                            color = AuroraColors.Text2,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            val duration = info?.duration ?: 0L
            val fraction = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .background(Color.White.copy(alpha = 0.85f))
                    )
                }
                if (duration > 0L) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TimeLabel(formatMs(position))
                        TimeLabel("−" + formatMs((duration - position).coerceAtLeast(0L)))
                    }
                }
            }

            // Always shown; dimmed while no session is bound (same as the classic widget)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .alpha(if (info != null) 1f else 0.45f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.skipPrevious() }, modifier = Modifier.size(56.dp)) {
                    Icon(
                        Icons.Default.FastRewind, contentDescription = "Předchozí",
                        tint = AuroraColors.Text, modifier = Modifier.size(36.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(AuroraColors.Text)
                        .clickable { if (playing) viewModel.pause() else viewModel.play() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "Pozastavit" else "Přehrát",
                        tint = AuroraColors.Card,
                        modifier = Modifier.size(30.dp)
                    )
                }
                IconButton(onClick = { viewModel.skipNext() }, modifier = Modifier.size(56.dp)) {
                    Icon(
                        Icons.Default.FastForward, contentDescription = "Další",
                        tint = AuroraColors.Text, modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeLabel(text: String) {
    Text(
        text = text,
        color = AuroraColors.Text3,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
    )
}

private fun formatMs(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

// ── Volume / brightness — vertical sliders, absolute touch position sets the level ───────────

@Composable
fun AuroraVolumeSlider(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audio   = remember { context.getSystemService(AudioManager::class.java) }
    val maxVol  = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var level   by remember {
        mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol)
    }
    AuroraSlider(
        level = level,
        icon = Icons.Filled.VolumeUp,
        contentDescription = "Hlasitost",
        modifier = modifier,
        onLevelChange = { new ->
            level = new
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, (new * maxVol).roundToInt().coerceIn(0, maxVol), 0)
        }
    )
}

@Composable
fun AuroraBrightnessSlider(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val window  = remember { (context as? Activity)?.window }
    var level   by remember {
        mutableFloatStateOf(window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f)
    }
    AuroraSlider(
        level = level,
        icon = Icons.Filled.WbSunny,
        contentDescription = "Jas",
        modifier = modifier,
        onLevelChange = { new ->
            level = new.coerceAtLeast(0.01f)
            window?.let {
                val lp = it.attributes
                lp.screenBrightness = level
                it.attributes = lp
            }
        }
    )
}

@Composable
private fun AuroraSlider(
    level: Float,
    icon: ImageVector,
    contentDescription: String,
    onLevelChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onChange by rememberUpdatedState(onLevelChange)
    Box(
        modifier = modifier
            .clip(AuroraCardShape)
            .background(AuroraColors.Fill)
            // Consumes every move so a sideways wobble can't turn into a pager swipe.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    onChange((1f - down.position.y / size.height).coerceIn(0f, 1f))
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        onChange((1f - change.position.y / size.height).coerceIn(0f, 1f))
                    }
                }
            }
    ) {
        // Light icon on the empty track…
        SliderIcon(icon, contentDescription, AuroraColors.Text)
        // …and a dark copy inside the white fill, clipped to it — mimics the design's
        // mix-blend-mode:difference so the icon inverts exactly where the fill covers it.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(level)
                .clipToBounds()
                .background(AuroraColors.Text)
        ) {
            SliderIcon(icon, null, AuroraColors.Card)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.SliderIcon(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color
) {
    Icon(
        icon, contentDescription = contentDescription, tint = tint,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 18.dp)
            .size(26.dp)
    )
}

// ── Weather ──────────────────────────────────────────────────────────────────

@Composable
fun AuroraWeatherCard(
    modifier: Modifier = Modifier,
    viewModel: WeatherCalendarViewModel = hiltViewModel(),
) {
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .clip(AuroraCardShape)
            .background(Brush.verticalGradient(listOf(AuroraColors.SkyTop, AuroraColors.SkyBot)))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("Počasí", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = weather?.let { "${it.tempC}°" } ?: "–",
                color = Color.White,
                fontSize = 46.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-0.03).em,
                lineHeight = 48.sp
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(wmoIcon(weather?.code), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(
                text = weather?.condition.orEmpty(),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── Calendar ─────────────────────────────────────────────────────────────────

private val dayFmt = DateTimeFormatter.ofPattern("EEEE d.", Locale("cs", "CZ"))

@Composable
fun AuroraCalendarCard(
    modifier: Modifier = Modifier,
    viewModel: WeatherCalendarViewModel = hiltViewModel(),
) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .clip(AuroraCardShape)
            .background(AuroraColors.Card)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = LocalDate.now().format(dayFmt).uppercase(Locale("cs", "CZ")),
                color = AuroraColors.Red,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.02.em
            )
            Text("Kalendář", color = AuroraColors.Gray, fontSize = 13.sp)
        }
        if (events.isEmpty()) {
            Text("Žádné události", color = AuroraColors.Gray, fontSize = 15.sp)
        } else {
            // Two rows keep the column inside the M10's height with the dock at the bottom.
            events.take(2).forEach { AuroraEventRow(it) }
        }
    }
}

@Composable
private fun AuroraEventRow(event: CalendarEvent) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(3.5.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(Color(event.color))
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = event.title,
                color = AuroraColors.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(event.timeLabel, color = AuroraColors.Gray, fontSize = 13.sp)
        }
    }
}
