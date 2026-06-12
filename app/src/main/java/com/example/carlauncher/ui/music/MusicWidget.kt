package com.example.carlauncher.ui.music

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.ui.theme.CarColors

private val CardShape = RoundedCornerShape(24.dp)
private val ArtShape  = RoundedCornerShape(14.dp)

@Composable
fun MusicWidget(
    modifier: Modifier = Modifier,
    viewModel: MediaViewModel = hiltViewModel()
) {
    val trackInfo by viewModel.trackInfo.collectAsStateWithLifecycle()
    val position  by viewModel.position.collectAsStateWithLifecycle()
    // Local snapshot — delegated property blocks smart cast
    val info = trackInfo

    Column(
        modifier = modifier
            .background(CarColors.Surface, CardShape)
            .border(1.dp, CarColors.BorderSoft, CardShape)
            .padding(18.dp)
    ) {
        // ── 1. Header — Bluetooth + app name + equalizer ──────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = CarColors.Accent,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "YouTube Music",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = CarColors.Text2,
                        letterSpacing = 0.01.em
                    )
                )
            }
            EqualizerAnimation(isPlaying = info?.isPlaying == true)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── 2. Artwork — flexes to fill the card's available height ───────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(ArtShape)
                .background(CarColors.Surface2),
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
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = CarColors.Text2.copy(alpha = 0.85f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── 3. Title + artist ─────────────────────────────────────────────────
        Text(
            text = info?.title ?: "Žádná hudba",
            style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = CarColors.Text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = info?.artist ?: "",
            style = TextStyle(fontSize = 14.sp, color = CarColors.Text3),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // ── 4. Progress + times ───────────────────────────────────────────────
        val duration = info?.duration ?: 0L
        if (duration > 0L) {
            val fraction = (position.toFloat() / duration).coerceIn(0f, 1f)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    formatMs(position),
                    style = TextStyle(fontSize = 12.sp, color = CarColors.Text3, fontFeatureSettings = "tnum")
                )
                ProgressWithThumb(
                    fraction = fraction,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatMs(duration),
                    style = TextStyle(fontSize = 12.sp, color = CarColors.Text3, fontFeatureSettings = "tnum")
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // ── 5. Controls — centered, compact ───────────────────────────────────
        if (info != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.skipPrevious() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.SkipPrevious, contentDescription = "Předchozí",
                        tint = CarColors.Text, modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(CarColors.Go, CarColors.GoStrong)))
                        .clickable {
                            if (info.isPlaying) viewModel.pause() else viewModel.play()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (info.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (info.isPlaying) "Pozastavit" else "Přehrát",
                        tint = Color(0xFF06281B),
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                IconButton(
                    onClick = { viewModel.skipNext() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.SkipNext, contentDescription = "Další",
                        tint = CarColors.Text, modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

// Slider-style progress: 4dp track + filled part + 12dp round white thumb (per mockup)
@Composable
private fun ProgressWithThumb(fraction: Float, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier.height(14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val thumbSize = 12.dp
        val trackWidth = maxWidth - thumbSize
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(CarColors.Surface3)
        )
        Box(
            modifier = Modifier
                .width(thumbSize / 2 + trackWidth * fraction)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(CarColors.Accent)
        )
        Box(
            modifier = Modifier
                .padding(start = trackWidth * fraction)
                .size(thumbSize)
                .background(Color.White, CircleShape)
        )
    }
}

@Composable
private fun EqualizerAnimation(isPlaying: Boolean) {
    val bars = 4
    // Single shared transition for all bars — 4 separate instances waste CPU
    val infiniteTransition = rememberInfiniteTransition(label = "eq")
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(16.dp)
    ) {
        repeat(bars) { i ->
            val barHeight by infiniteTransition.animateFloat(
                initialValue = 4f,
                targetValue = if (isPlaying) 16f else 4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 400,
                        delayMillis = i * 80,
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "eqBar$i"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF8BA4F0))
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
