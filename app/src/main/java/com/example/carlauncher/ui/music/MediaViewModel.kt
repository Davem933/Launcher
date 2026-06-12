package com.example.carlauncher.ui.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.carlauncher.data.media.MediaSessionObserver
import com.example.carlauncher.data.model.TrackInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MediaViewModel @Inject constructor(
    private val mediaObserver: MediaSessionObserver
) : ViewModel() {

    val trackInfo: StateFlow<TrackInfo?> = mediaObserver.currentTrack

    // Polls real-time position every second for progress bar
    val position: StateFlow<Long> = flow {
        while (true) {
            emit(mediaObserver.currentPositionMs)
            delay(1_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    init { mediaObserver.start() }

    override fun onCleared() {
        super.onCleared()
        mediaObserver.stop()
    }

    fun play()         = mediaObserver.play()
    fun pause()        = mediaObserver.pause()
    fun skipNext()     = mediaObserver.skipNext()
    fun skipPrevious() = mediaObserver.skipPrevious()
}
