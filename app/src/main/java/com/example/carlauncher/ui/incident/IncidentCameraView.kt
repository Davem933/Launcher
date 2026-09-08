package com.example.carlauncher.ui.incident

import androidx.activity.ComponentActivity
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private class IncidentCameraHolder {
    var torndown = false
}

/**
 * Hosts a CameraX [PreviewView] via [AndroidView], following the interop pattern from
 * `ui/map/MapWidget.kt` (view created once in `remember`, bound against the Activity
 * lifecycle rather than `LocalLifecycleOwner`).
 *
 * This composable only enters composition after CAMERA is granted and stays until the user
 * closes the overlay, so the single [DisposableEffect] binds on entry and tears down on
 * close. On every `ON_RESUME` the camera is re-bound so the preview surface reconnects after
 * the launcher was briefly backgrounded (another app taking focus) — otherwise it stays black.
 */
@Composable
fun IncidentCameraView(
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

    DisposableEffect(Unit) {
        viewModel.bindCamera(activity, previewView.surfaceProvider)

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !holder.torndown) {
                viewModel.bindCamera(activity, previewView.surfaceProvider)
            }
        }
        activity.lifecycle.addObserver(observer)

        onDispose {
            activity.lifecycle.removeObserver(observer)
            if (!holder.torndown) {
                holder.torndown = true
                viewModel.unbindCamera()
            }
        }
    }
}
