package com.example.carlauncher.ui.map

import com.mapbox.geojson.Point
import com.mapbox.maps.plugin.locationcomponent.LocationConsumer
import com.mapbox.maps.plugin.locationcomponent.LocationProvider
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Feeds Mapbox's built-in location puck from our own already-processed
 * [com.example.carlauncher.data.model.VehicleDisplayLocation] (Kalman-filtered, route-snapped)
 * instead of Mapbox's default device-GPS-based provider.
 */
class AppLocationProvider : LocationProvider {

    private val consumers = CopyOnWriteArraySet<LocationConsumer>()

    override fun registerLocationConsumer(locationConsumer: LocationConsumer) {
        consumers.add(locationConsumer)
    }

    override fun unRegisterLocationConsumer(locationConsumer: LocationConsumer) {
        consumers.remove(locationConsumer)
    }

    fun push(point: Point, bearingDeg: Double) {
        consumers.forEach {
            it.onLocationUpdated(point)
            it.onBearingUpdated(bearingDeg)
        }
    }
}
