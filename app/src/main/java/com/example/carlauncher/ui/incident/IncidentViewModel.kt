package com.example.carlauncher.ui.incident

import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.carlauncher.data.incident.IncidentGpsFix
import com.example.carlauncher.data.incident.IncidentLocationSource
import com.example.carlauncher.data.incident.IncidentRecorder
import com.example.carlauncher.data.incident.IncidentUiState
import com.example.carlauncher.data.incident.PlateBox
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IncidentViewModel @Inject constructor(
    private val recorder: IncidentRecorder,
    private val locationSource: IncidentLocationSource,
) : ViewModel() {

    /** Recorder state — drives the toggle button, elapsed time, filename, errors. */
    val uiState: StateFlow<IncidentUiState> = recorder.state

    /** Live GPS fix for the overlay (independent of recording state). */
    val liveFix: StateFlow<IncidentGpsFix?> = locationSource.fix

    /** Latest plate boxes for the overlay. */
    val plateBoxes: StateFlow<List<PlateBox>> = recorder.plateBoxes

    val isRecording: Boolean
        get() = recorder.isRecording

    /** Called once CAMERA + location permissions are granted. Starts the GPS stream. */
    fun onPermissionsGranted() {
        locationSource.start()
    }

    fun bindCamera(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        viewModelScope.launch { recorder.bind(owner, surfaceProvider) }
    }

    fun unbindCamera() {
        recorder.unbind()
    }

    fun toggle() {
        if (recorder.isRecording) recorder.stop() else recorder.start()
    }

    /** Stop recording without tearing the camera down (used when closing the screen). */
    fun stopRecording() {
        recorder.stop()
    }

    /** Called when the recorder screen leaves composition — release the GPS stream. */
    fun onScreenClosed() {
        locationSource.stop()
    }

    override fun onCleared() {
        super.onCleared()
        if (recorder.isRecording) recorder.stop()
        locationSource.stop()
    }
}
