package com.example.carlauncher.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.data.notes.NoteCategory
import com.example.carlauncher.data.notes.NoteStatus
import com.example.carlauncher.data.notes.VoiceNoteEntity
import com.example.carlauncher.ui.theme.CarColors

private const val LONG_PRESS_MS = 1_500L

@Composable
fun VoiceNotesScreen(
    onBack: () -> Unit,
    initialNoteId: Long? = null,
    viewModel: VoiceNotesViewModel = hiltViewModel(),
) {
    val notes by viewModel.filteredNotes.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<VoiceNoteEntity?>(null) }
    val listState = rememberLazyListState()
    var scrolledToInitial by remember { mutableStateOf(false) }
    val now = remember(notes) { System.currentTimeMillis() }

    BackHandler(onBack = onBack)

    LaunchedEffect(notes, initialNoteId) {
        if (scrolledToInitial || initialNoteId == null) return@LaunchedEffect
        val index = notes.indexOfFirst { it.id == initialNoteId }
        if (index >= 0) {
            listState.scrollToItem(index)
            scrolledToInitial = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarColors.Bg)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět", tint = CarColors.Text)
            }
            Text("Hlasové poznámky", color = CarColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            CategoryChips(selected = filter, onSelect = viewModel::setFilter)
        }
        Spacer(Modifier.height(8.dp))
        if (notes.isEmpty()) {
            Text("Žádné poznámky.", color = CarColors.Text2, fontSize = 14.sp)
        } else {
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(notes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        now = now,
                        onRetry = { viewModel.retry(note.id) },
                        onLongPress = { pendingDelete = note },
                    )
                }
            }
        }
    }

    pendingDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Smazat poznámku?") },
            text = { Text(note.title ?: "Nezpracovaná poznámka") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(note.id); pendingDelete = null }) {
                    Text("Smazat", color = CarColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Zrušit", color = CarColors.Text2) }
            },
            containerColor = CarColors.Surface2,
            titleContentColor = CarColors.Text,
            textContentColor = CarColors.Text2,
        )
    }
}

@Composable
private fun CategoryChips(selected: NoteCategory?, onSelect: (NoteCategory?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(label = "Vše", active = selected == null) { onSelect(null) }
        NoteCategory.entries.forEach { c ->
            Chip(label = c.emoji, active = selected == c) { onSelect(c) }
        }
    }
}

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .width(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) CarColors.Accent else CarColors.Surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = CarColors.Text, fontSize = 15.sp)
    }
}

@Composable
private fun NoteCard(
    note: VoiceNoteEntity,
    now: Long,
    onRetry: () -> Unit,
    onLongPress: () -> Unit,
) {
    val accent = note.category?.color ?: CarColors.Surface3
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CarColors.Surface)
            .pointerInput(note.id) {
                // Stejný vzor jako DockBar: Compose withTimeout hází
                // PointerEventTimeoutCancellationException, ne kotlinx TimeoutCancellationException.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    try {
                        withTimeout(LONG_PRESS_MS) { waitForUpOrCancellation() }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        onLongPress()
                        waitForUpOrCancellation()
                    }
                }
            },
    ) {
        Box(Modifier.width(5.dp).heightIn(min = 72.dp).background(accent))
        Column(Modifier.padding(12.dp).weight(1f)) {
            Text(noteRowTitle(note), color = CarColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            note.text?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = CarColors.Text2, fontSize = 13.sp)
            }
            if (note.status == NoteStatus.FAILED) {
                Spacer(Modifier.height(4.dp))
                Text(note.errorMessage.orEmpty(), color = CarColors.Danger, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = CarColors.Surface3),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Zkusit znovu", color = CarColors.Text, fontSize = 12.sp) }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${formatNoteTime(note.createdAt, now)}  ·  ${formatDuration(note.durationMs)}",
                color = CarColors.Text3,
                fontSize = 11.sp,
            )
        }
    }
}
