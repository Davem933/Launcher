package com.example.carlauncher.data.model

data class VehicleDisplayLocation(
    val lat: Double,
    val lng: Double,
    val speedKmh: Float,
    val bearingDeg: Float,
    val accuracyM: Float,
    val timestamp: Long
)
