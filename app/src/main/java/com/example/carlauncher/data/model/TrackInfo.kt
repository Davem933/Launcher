package com.example.carlauncher.data.model

import android.graphics.Bitmap

data class TrackInfo(
    val title: String,
    val artist: String,
    val albumArt: Bitmap?,
    val isPlaying: Boolean,
    val duration: Long,   // ms, 0 if unavailable
    val position: Long    // ms at last state update
)
