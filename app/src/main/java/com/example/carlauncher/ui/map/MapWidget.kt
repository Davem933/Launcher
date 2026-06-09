package com.example.carlauncher.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
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

@Composable
fun MapWidget(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val location by viewModel.vehicleLocation.collectAsStateWithLifecycle()

    val mapView = remember {
        MapView(context).also { it.onCreate(null) }
    }
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var vehicleSource by remember { mutableStateOf<GeoJsonSource?>(null) }

    AndroidView(
        factory = {
            mapView.apply {
                getMapAsync { map ->
                    mapLibreMap = map

                    map.uiSettings.isScrollGesturesEnabled = false
                    map.uiSettings.isZoomGesturesEnabled = false
                    map.uiSettings.isRotateGesturesEnabled = false
                    map.uiSettings.isTiltGesturesEnabled = false
                    map.uiSettings.isLogoEnabled = false
                    map.uiSettings.isAttributionEnabled = false

                    map.setStyle(
                        Style.Builder().fromUri(viewModel.mapStyle)
                    ) { style ->
                        style.addImage(MARKER_IMAGE_ID, createVehicleMarkerBitmap())

                        val source = GeoJsonSource(VEHICLE_SOURCE_ID)
                        style.addSource(source)
                        vehicleSource = source

                        style.addLayer(
                            SymbolLayer(VEHICLE_LAYER_ID, VEHICLE_SOURCE_ID)
                                .withProperties(
                                    PropertyFactory.iconImage(MARKER_IMAGE_ID),
                                    PropertyFactory.iconAllowOverlap(true),
                                    PropertyFactory.iconIgnorePlacement(true)
                                )
                        )

                        map.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(50.0755, 14.4378), 15.0)
                        )
                    }
                }
            }
        },
        modifier = modifier
    )

    LaunchedEffect(location) {
        val loc = location ?: return@LaunchedEffect
        val map = mapLibreMap ?: return@LaunchedEffect
        val source = vehicleSource ?: return@LaunchedEffect

        val latLng = LatLng(loc.lat, loc.lng)

        source.setGeoJson(
            Feature.fromGeometry(Point.fromLngLat(loc.lng, loc.lat))
        )

        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(latLng, map.cameraPosition.zoom),
            300
        )
        map.animateCamera(
            CameraUpdateFactory.bearingTo(loc.bearingDeg.toDouble()),
            300
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // Replay missed lifecycle events — Composable may compose when lifecycle
        // is already STARTED or RESUMED, so the observer won't receive past events.
        val state = lifecycleOwner.lifecycle.currentState
        if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

private fun createVehicleMarkerBitmap(): Bitmap {
    val size = 48
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paintOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#40FFFFFF")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(24f, 24f, 22f, paintOuter)

    val paintInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#00C853")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(24f, 24f, 14f, paintInner)

    val paintCenter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(24f, 24f, 5f, paintCenter)

    return bitmap
}
