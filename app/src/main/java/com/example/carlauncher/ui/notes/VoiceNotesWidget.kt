package com.example.carlauncher.ui.notes

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.data.notes.RecorderState
import com.example.carlauncher.data.notes.SilenceDetector
import com.example.carlauncher.data.notes.VoiceNoteEntity
import com.example.carlauncher.ui.theme.CarColors

private const val RECENT_NOTES = 3
/** MediaRecorder amplituda, při které je prstenec úplně roztažený. */
private const val FULL_RING_AMPLITUDE = 12_000f
/** Oblouk odpočtu ticha ukázat až po krátké pauze, ne při každém nádechu. */
private const val SILENCE_ARC_VISIBLE_MS = 800L

@Composable
fun VoiceNotesWidget(
    modifier: Modifier = Modifier,
    onOpenNotes: (noteId: Long?) -> Unit,
    viewModel: VoiceNotesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val recorderState by viewModel.recorderState.collectAsStateWithLifecycle()
    val justSaved by viewModel.justSaved.collectAsStateWithLifecycle()
    val now = remember(notes) { System.currentTimeMillis() }

    fun micGranted() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    var micDenied by remember { mutableStateOf(false) }
    var startFailed by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        micDenied = !granted
        if (granted) startFailed = !viewModel.onMicTap()
    }

    val recording = recorderState as? RecorderState.Recording
    val hint = when {
        !viewModel.hasApiKey -> "Chybí Groq API klíč"
        micDenied -> "Povol mikrofon v nastavení aplikace"
        recording != null -> "${formatDuration(recording.elapsedMs)} · zastaví se po 5 s ticha"
        startFailed -> "Mikrofon se nepodařilo spustit"
        justSaved -> "Uloženo ✓"
        else -> "Ťukni a mluv"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(CarColors.Surface)
            .border(1.dp, CarColors.BorderSoft, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "HLASOVÉ POZNÁMKY",
                color = CarColors.Text3,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.em,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onOpenNotes(null) }) {
                Icon(Icons.Default.OpenInFull, contentDescription = "Všechny poznámky", tint = CarColors.Text2)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MicButton(
                recording = recording,
                enabled = viewModel.hasApiKey,
                onClick = {
                    startFailed = false
                    if (recording == null && !micGranted()) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        startFailed = !viewModel.onMicTap()
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            Text(hint, color = CarColors.Text2, fontSize = 13.sp, textAlign = TextAlign.Center)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = CarColors.BorderSoft)

        val recent = widgetNotes(notes, RECENT_NOTES)
        if (recent.isEmpty()) {
            Text("Zatím žádné poznámky", color = CarColors.Text3, fontSize = 11.sp)
        } else {
            recent.forEach { note ->
                RecentNoteRow(note = note, now = now, onClick = { onOpenNotes(note.id) })
            }
        }
    }
}

@Composable
private fun MicButton(recording: RecorderState.Recording?, enabled: Boolean, onClick: () -> Unit) {
    val level by animateFloatAsState(
        targetValue = ((recording?.amplitude ?: 0) / FULL_RING_AMPLITUDE).coerceIn(0f, 1f),
        label = "micLevel",
    )
    val base = when {
        recording != null -> CarColors.Danger
        enabled -> CarColors.Accent
        else -> CarColors.Surface3
    }
    val arcColor = CarColors.Text2

    Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            if (recording == null) return@Canvas
            val inner = 48.dp.toPx()
            val outer = size.minDimension / 2
            drawCircle(base.copy(alpha = 0.25f), radius = inner + (outer - inner) * level)
            if (recording.silenceMs > SILENCE_ARC_VISIBLE_MS) {
                val remaining = 1f - (recording.silenceMs.toFloat() / SilenceDetector.SILENCE_LIMIT_MS)
                    .coerceIn(0f, 1f)
                val inset = 4.dp.toPx()
                drawArc(
                    color = arcColor,
                    startAngle = -90f,
                    sweepAngle = 360f * remaining,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - 2 * inset, size.height - 2 * inset),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(base)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (recording != null) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (recording != null) "Zastavit nahrávání" else "Nahrát poznámku",
                tint = Color.White,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

@Composable
private fun RecentNoteRow(note: VoiceNoteEntity, now: Long, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(note.category?.color ?: CarColors.Surface3),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            noteRowTitle(note),
            color = CarColors.Text,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(formatNoteTime(note.createdAt, now), color = CarColors.Text3, fontSize = 11.sp)
    }
}
