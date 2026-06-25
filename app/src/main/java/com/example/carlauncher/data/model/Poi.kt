package com.example.carlauncher.data.model

data class Poi(
    val id: Long,
    val lat: Double,
    val lng: Double,
    val type: PoiType,
    val name: String?
)

enum class PoiType(val osmAmenity: String, val radiusM: Int) {
    FUEL("fuel", 2000),
    PARKING("parking", 500),
    RESTAURANT("cafe|restaurant", 300),
    HOSPITAL("hospital", 5000)
}
