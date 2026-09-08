package com.example.carlauncher.ui.incident

import androidx.activity.ComponentActivity
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

private class IncidentCameraHolder {
    var torndown = false
}

/**
 * Hosts a CameraX [PreviewView] via [AndroidView], following the interop pattern from
 * `ui/map/MapWidget.kt` (view created once in `remember`, bound against the Activity
 * lifecycle rather than `LocalLifecycleOwner`).
 *
 * Unlike `MapWidget` this is a full-screen overlay, not a pager page, so the disposal
 * teardown is unconditional — it fires only when the user closes the screen or the Activity
 * is destroyed.
 */
@Composable
fun IncidentCameraView(
    hasPermissions: Boolean,
    viewModel: IncidentViewModel,
    modifier: Modifier = Modifier,
) {
    val activity = LocalContext.current as ComponentActivity
    val previewView = remember {
        PreviewView(activity).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val holder = remember { IncidentCameraHolder() }

    AndroidView(factory = { previewView }, modifier = modifier)

    LaunchedEffect(hasPermissions) {
        if (hasPermissions) {
            viewModel.bindCamera(activity, previewView.surfaceProvider)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!holder.torndown) {
                holder.torndown = true
                viewModel.unbindCamera()
            }
        }
    }
}
