package com.example.carlauncher.ui.incident

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.data.incident.IncidentUiState
import com.example.carlauncher.ui.theme.CarColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen Incident Recorder overlay, opened from the floating button in `MainActivity`.
 * Single toggle button starts / stops recording; closing the overlay stops any active
 * recording first so CameraX finalizes the `.mp4` and the sidecar `.json` is written.
 */
@Composable
fun IncidentRecorderScreen(
    onClose: () -> Unit,
    viewModel: IncidentViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val fix by viewModel.liveFix.collectAsStateWithLifecycle()
    val plateBoxes by viewModel.plateBoxes.collectAsStateWithLifecycle()

    fun granted(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    var hasCamera by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { res ->
        hasCamera = res[Manifest.permission.CAMERA] == true || granted(Manifest.permission.CAMERA)
        // Location denial is tolerated — recording still works, GPS section stays empty.
        if (hasCamera) viewModel.onPermissionsGranted()
    }

    LaunchedEffect(Unit) {
        if (hasCamera) viewModel.onPermissionsGranted()
    }

    var nowText by remember { mutableStateOf(clockText()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowText = clockText()
            delay(1_000L)
        }
    }

    fun closeScreen() {
        if (viewModel.isRecording) viewModel.stopRecording()
        onClose()
    }

    BackHandler(onBack = ::closeScreen)

    DisposableEffect(Unit) {
        onDispose { viewModel.onScreenClosed() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CarColors.Bg),
    ) {
        if (hasCamera) {
            IncidentCameraView(
                hasPermissions = hasCamera,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
            )
            IncidentOverlay(
                nowText = nowText,
                fix = fix,
                plateBoxes = plateBoxes,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PermissionCard(
                onGrant = {
                    permLauncher.launch(
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        ),
                    )
                },
                onOpenSettings = { context.openAppSettings() },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        IconButton(
            onClick = ::closeScreen,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        ) {
            Icon(Icons.Default.Close, contentDescription = "Zavřít", tint = CarColors.Text)
        }

        if (hasCamera) {
            RecordControls(
                uiState = uiState,
                onToggle = { viewModel.toggle() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp),
            )
        }
    }
}

@Composable
private fun RecordControls(
    uiState: IncidentUiState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recording = uiState is IncidentUiState.Recording

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (uiState as? IncidentUiState.Recording)?.let { r ->
            Text(
                text = "${formatElapsed(r.elapsedMs)}   ${r.fileName}",
                color = CarColors.Text,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        (uiState as? IncidentUiState.Error)?.let { e ->
            Text(text = e.message, color = CarColors.Danger, fontSize = 13.sp)
        }
        Button(
            onClick = onToggle,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (recording) CarColors.Danger else CarColors.Go,
            ),
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValues(horizontal = 34.dp, vertical = 16.dp),
        ) {
            Icon(
                imageVector = if (recording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                contentDescription = null,
                tint = Color(0xFF06281B),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (recording) "ZASTAVIT" else "NAHRÁVAT",
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF06281B),
            )
        }
    }
}

@Composable
private fun PermissionCard(
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(CarColors.Surface, RoundedCornerShape(16.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Incident Recorder potřebuje přístup ke kameře a poloze.",
            color = CarColors.Text,
            fontSize = 14.sp,
        )
        Button(
            onClick = onGrant,
            colors = ButtonDefaults.buttonColors(containerColor = CarColors.Accent),
        ) {
            Text("Povolit", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = onOpenSettings,
            colors = ButtonDefaults.buttonColors(containerColor = CarColors.Surface2),
        ) {
            Text("Otevřít nastavení aplikace", color = CarColors.Text)
        }
    }
}

private fun clockText(): String =
    SimpleDateFormat("HH:mm:ss", Locale("cs")).format(Date())

private fun formatElapsed(ms: Long): String {
    val totalSec = ms / 1000
    return "%02d:%02d".format(totalSec / 60, totalSec % 60)
}

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:$packageName"),
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    startActivity(intent)
}
