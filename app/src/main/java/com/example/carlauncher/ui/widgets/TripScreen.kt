package com.example.carlauncher.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.carlauncher.ui.dock.DockBar
import com.example.carlauncher.ui.notes.VoiceNotesScreen
import com.example.carlauncher.ui.notes.VoiceNotesWidget
import com.example.carlauncher.ui.theme.CarColors

@Composable
fun TripScreen(
    onLaunchSplitScreen: (pkg1: String, pkg2: String) -> Unit = { _, _ -> }
) {
    var showHistory by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var notesInitialId by remember { mutableStateOf<Long?>(null) }

    if (showHistory) {
        TripHistoryScreen(onBack = { showHistory = false })
        return
    }
    if (showNotes) {
        VoiceNotesScreen(onBack = { showNotes = false }, initialNoteId = notesInitialId)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarColors.Bg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TripComputerWidget(modifier = Modifier.weight(1f))
            JizdniDenikWidget(
                modifier = Modifier.weight(1f),
                onOpenHistory = { showHistory = true }
            )
            VoiceNotesWidget(
                modifier = Modifier.weight(1f),
                onOpenNotes = { id ->
                    notesInitialId = id
                    showNotes = true
                },
            )
        }

        DockBar(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 8.dp),
            onLaunchSplitScreen = onLaunchSplitScreen
        )
    }
}
