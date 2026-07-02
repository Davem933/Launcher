package com.example.carlauncher.debug

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.BuildConfig
import com.example.carlauncher.ui.launcher.LauncherViewModel
import java.io.File

@Composable
fun DebugPanel(
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier
) {
    if (!BuildConfig.DEBUG) return

    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var pendingMultiplier by remember { mutableFloatStateOf(1f) }

    val gpxPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val gpxFile = File(context.filesDir, "replay.gpx")
            context.contentResolver.openInputStream(uri)?.use { input ->
                gpxFile.outputStream().use { output -> input.copyTo(output) }
            }
            viewModel.startGpxReplay(gpxFile, pendingMultiplier)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (expanded) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 56.dp, end = 8.dp)
                    .background(Color(0xEE0D0D0F), RoundedCornerShape(12.dp))
                    .padding(12.dp)
                    .width(180.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("DEBUG", color = Color(0xFF00C853), fontSize = 11.sp, fontWeight = FontWeight.Bold)

                val isLogging by viewModel.isGpsLogging.collectAsStateWithLifecycle()
                DebugButton(
                    label = if (isLogging) "⏹ Stop CSV log" else "⏺ Start CSV log",
                    color = if (isLogging) Color(0xFFFF4444) else Color(0xFF00C853)
                ) { viewModel.toggleGpsLogging() }

                val isReplaying by viewModel.isGpxReplaying.collectAsStateWithLifecycle()
                if (isReplaying) {
                    DebugButton("⏹ Stop replay", Color(0xFFFF4444)) { viewModel.stopGpxReplay() }
                } else {
                    DebugButton("▶ GPX Replay 1×", Color(0xFF8A8AFF)) {
                        pendingMultiplier = 1f
                        gpxPicker.launch(arrayOf("*/*"))
                    }
                    DebugButton("▶ GPX Replay 3×", Color(0xFF8A8AFF)) {
                        pendingMultiplier = 3f
                        gpxPicker.launch(arrayOf("*/*"))
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .size(40.dp)
                .background(Color(0xCC1A1A1F), RoundedCornerShape(10.dp))
                .clickable { expanded = !expanded },
            contentAlignment = Alignment.Center
        ) {
            Text("🔧", fontSize = 18.sp)
        }
    }
}

@Composable
private fun DebugButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(label, color = color, fontSize = 12.sp)
    }
}
