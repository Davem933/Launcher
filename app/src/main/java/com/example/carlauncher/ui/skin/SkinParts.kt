package com.example.carlauncher.ui.skin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.carlauncher.data.model.TrackInfo
import com.example.carlauncher.ui.music.MediaViewModel

/** Heading shown above the track: the player's real name, or a neutral label without one. */
internal fun TrackInfo?.playerLabel(): String = this?.appName?.takeIf { it.isNotBlank() } ?: "Hudba"

internal fun TrackInfo?.titleOrIdle(): String = this?.title?.takeIf { it.isNotBlank() } ?: "Žádná hudba"

/** Album art when the session has one, otherwise the design's music-note placeholder. */
@Composable
internal fun AlbumArtOrNote(info: TrackInfo?, noteTint: Color, noteSize: Dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val art = info?.albumArt
        if (art != null) {
            Image(
                bitmap = art.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = noteTint, modifier = Modifier.size(noteSize))
        }
    }
}

/** "•••" in the card header — opens the player app. */
@Composable
internal fun PlayerMenuButton(tint: Color, viewModel: MediaViewModel) {
    IconButton(onClick = { viewModel.launchPlayer() }, modifier = Modifier.size(48.dp)) {
        Icon(Icons.Default.MoreHoriz, contentDescription = "Otevřít přehrávač", tint = tint)
    }
}

/** Previous / play-pause / next. Dimmed while no media session is bound. */
@Composable
internal fun TransportControls(
    info: TrackInfo?,
    tint: Color,
    playBg: Color,
    playFg: Color,
    viewModel: MediaViewModel,
    modifier: Modifier = Modifier,
    playShape: Shape = CircleShape,
    playSize: Dp = 52.dp,
) {
    val playing = info?.isPlaying == true
    Row(
        modifier = modifier.alpha(if (info != null) 1f else 0.45f),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { viewModel.skipPrevious() }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Předchozí", tint = tint, modifier = Modifier.size(28.dp))
        }
        Box(
            modifier = Modifier
                .size(playSize)
                .clip(playShape)
                .background(playBg)
                .clickable { if (playing) viewModel.pause() else viewModel.play() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) "Pozastavit" else "Přehrát",
                tint = playFg,
                modifier = Modifier.size(28.dp)
            )
        }
        IconButton(onClick = { viewModel.skipNext() }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.SkipNext, contentDescription = "Další", tint = tint, modifier = Modifier.size(28.dp))
        }
    }
}
