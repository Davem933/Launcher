package com.example.carlauncher.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.ui.speed.SpeedDisplay
import com.example.carlauncher.ui.theme.CarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

private const val MARKER_IMAGE_ID = "vehicle-marker"
private const val VEHICLE_SOURCE_ID = "vehicle-source"
private const val VEHICLE_LAYER_ID = "vehicle-layer"

private class MapState {
    var map: MapLibreMap? = null
    var source: GeoJsonSource? = null
    var layer: SymbolLayer? = null
    var destroyed = false
    // Threshold tracking — skip marker/camera update when position hasn't changed enough
    var lastLat: Double? = null
    var lastLng: Double? = null
    var lastBearing: Float = 0f
}

@Composable
fun MapWidget(
    modifier: Modifier = Modifier,
    onNavigate: () -> Unit = {},
    viewModel: MapViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val location by viewModel.vehicleLocation.collectAsStateWithLifecycle()

    val mapView = remember {
        // textureMode: render via TextureView so Compose clip() can round the corners
        // (default SurfaceView is composited separately and ignores clipping)
        val options = MapLibreMapOptions.createFromAttributes(context).textureMode(true)
        MapView(context, options).also { it.onCreate(null) }
    }
    val mapState = remember { MapState() }
    var mapAsyncReady by remember { mutableStateOf(false) }

    // Load Mapy.cz raster style as soon as MapLibre is ready
    LaunchedEffect(mapAsyncReady) {
        if (!mapAsyncReady) return@LaunchedEffect
        val map = mapState.map ?: return@LaunchedEffect

        val styleJson = buildMapyczStyleJson(TileConfig.MAPYCZ_BASIC)
        Log.d("MapWidget", "Loading Mapy.cz style")
        map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
            Log.d("MapWidget", "Style loaded OK, layers=${style.layers.size}")
            style.addImage(MARKER_IMAGE_ID, createVehicleMarkerBitmap())

            val source = GeoJsonSource(VEHICLE_SOURCE_ID)
            style.addSource(source)
            mapState.source = source

            val layer = SymbolLayer(VEHICLE_LAYER_ID, VEHICLE_SOURCE_ID)
                .withProperties(
                    PropertyFactory.iconImage(MARKER_IMAGE_ID),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    // Rotate with the map so the wedge tracks vehicle bearing
                    PropertyFactory.iconRotationAlignment("map")
                )
            style.addLayer(layer)
            mapState.layer = layer

            map.moveCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(50.0755, 14.4378), 15.0)
            )
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .border(width = 1.dp, color = Color(0xFF2A2C35), shape = RoundedCornerShape(24.dp))
    ) {
        AndroidView(
            factory = {
                mapView.apply {
                    addOnDidFailLoadingMapListener { errorMessage ->
                        Log.e("MapWidget", "Map load error: $errorMessage")
                    }
                    getMapAsync { map ->
                        mapState.map = map
                        map.uiSettings.isScrollGesturesEnabled = false
                        map.uiSettings.isZoomGesturesEnabled = false
                        map.uiSettings.isRotateGesturesEnabled = false
                        map.uiSettings.isTiltGesturesEnabled = false
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isAttributionEnabled = false
                        mapAsyncReady = true
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        SpeedDisplay(
            speedKmh = location?.speedKmh ?: 0f,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 18.dp, bottom = 18.dp)  // dock no longer overlays the map
        )

        // Navigovat — primary CTA, bottom-right corner of the map
        Button(
            onClick = onNavigate,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CarColors.Go),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 15.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Navigation,
                contentDescription = null,
                tint = Color(0xFF06281B),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Navigovat",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF06281B)
            )
        }
    }

    LaunchedEffect(location) {
        val loc = location ?: return@LaunchedEffect
        val map = mapState.map ?: return@LaunchedEffect
        val source = mapState.source ?: return@LaunchedEffect

        // Skip update if position hasn't moved enough — avoids flooding GPU with animateCamera
        val prevLat = mapState.lastLat
        val shouldUpdate = prevLat == null ||
            haversineMeters(prevLat, mapState.lastLng!!, loc.lat, loc.lng) > 5.0 ||
            angleDiff(mapState.lastBearing, loc.bearingDeg) > 5.0

        if (!shouldUpdate) return@LaunchedEffect

        source.setGeoJson(Feature.fromGeometry(Point.fromLngLat(loc.lng, loc.lat)))
        mapState.layer?.setProperties(PropertyFactory.iconRotate(loc.bearingDeg))

        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(loc.lat, loc.lng))
                    .zoom(map.cameraPosition.zoom)
                    .bearing(loc.bearingDeg.toDouble())
                    .build()
            ),
            300
        )

        mapState.lastLat = loc.lat
        mapState.lastLng = loc.lng
        mapState.lastBearing = loc.bearingDeg
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> {
                    if (!mapState.destroyed) { mapState.destroyed = true; mapView.onDestroy() }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        val state = lifecycleOwner.lifecycle.currentState
        if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Composable may leave composition without a lifecycle ON_DESTROY
            if (!mapState.destroyed) { mapState.destroyed = true; mapView.onDestroy() }
        }
    }
}

private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLng / 2) * Math.sin(dLng / 2)
    return 6_371_000.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a))
}

private fun angleDiff(a: Float, b: Float): Float =
    kotlin.math.abs(((b - a + 180f) % 360f) - 180f)

private fun createVehicleMarkerBitmap(): Bitmap {
    // 128px ≈ 46dp at ~2.75x density — big enough to spot at a glance while driving
    val size = 128
    val c = size / 2f
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Translucent halo
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#40FFFFFF")
        style = Paint.Style.FILL
    }.also { canvas.drawCircle(c, c, 60f, it) }

    // Green disc
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#22C55E")
        style = Paint.Style.FILL
    }.also { canvas.drawCircle(c, c, 40f, it) }

    // White direction wedge pointing north (iconRotate aligns it to bearing)
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }.also {
        val path = android.graphics.Path().apply {
            moveTo(c, c - 28f)        // tip
            lineTo(c - 16f, c + 14f)  // bottom left
            lineTo(c, c + 4f)         // notch
            lineTo(c + 16f, c + 14f)  // bottom right
            close()
        }
        canvas.drawPath(path, it)
    }

    return bitmap
}
