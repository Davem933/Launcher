package com.example.carlauncher.ui.map

import kotlin.math.*

/**
 * Snaps a raw GPS coordinate to the nearest point on a route polyline when
 * the driver is within SNAP_THRESHOLD_M metres. Returns the raw coordinate
 * unchanged when the polyline is empty or the driver is off-route.
 */
object RouteSnapHelper {

    private const val SNAP_THRESHOLD_M = 50.0

    fun snapToRoute(
        lat: Double,
        lng: Double,
        polyline: List<Pair<Double, Double>>
    ): Pair<Double, Double> {
        if (polyline.size < 2) return lat to lng

        var bestDist = Double.MAX_VALUE
        var bestPoint = lat to lng

        for (i in 0 until polyline.size - 1) {
            val (pLat, pLng) = nearestPointOnSegment(
                lat, lng,
                polyline[i].first, polyline[i].second,
                polyline[i + 1].first, polyline[i + 1].second
            )
            val d = haversineM(lat, lng, pLat, pLng)
            if (d < bestDist) {
                bestDist = d
                bestPoint = pLat to pLng
            }
        }

        return if (bestDist <= SNAP_THRESHOLD_M) bestPoint else lat to lng
    }

    // Projects point P onto segment AB using a cos(lat) correction so that
    // longitude degrees are scaled to the same metric length as latitude degrees.
    private fun nearestPointOnSegment(
        pLat: Double, pLng: Double,
        aLat: Double, aLng: Double,
        bLat: Double, bLng: Double
    ): Pair<Double, Double> {
        val cosLat = cos(Math.toRadians((aLat + bLat) / 2.0))
        val ax = (bLng - aLng) * cosLat
        val ay = bLat - aLat
        val lenSq = ax * ax + ay * ay
        if (lenSq == 0.0) return aLat to aLng
        val px = (pLng - aLng) * cosLat
        val py = pLat - aLat
        val t = ((px * ax) + (py * ay)) / lenSq
        val tc = t.coerceIn(0.0, 1.0)
        return (aLat + tc * ay) to (aLng + tc * ax / cosLat)
    }

    private fun haversineM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return r * 2.0 * asin(sqrt(a))
    }
}
