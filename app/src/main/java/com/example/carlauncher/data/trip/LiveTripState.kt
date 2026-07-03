package com.example.carlauncher.data.trip

sealed class LiveTripState {
    data class Idle(val lastTrip: TripEntity? = null) : LiveTripState()
    data class Active(
        val distanceKm: Float,
        val durationSec: Long,
        val avgSpeedKmh: Float,
        val maxSpeedKmh: Float
    ) : LiveTripState()
}
