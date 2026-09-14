package com.example.carlauncher.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.example.carlauncher.data.model.Poi
import com.example.carlauncher.data.model.PoiType
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
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.maps.extension.style.expressions.dsl.generated.get
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.SymbolLayer
import com.mapbox.maps.extension.style.layers.properties.generated.IconRotationAlignment
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource

private const val MARKER_IMAGE_ID = "vehicle-marker"
private const val VEHICLE_SOURCE_ID = "vehicle-source"
private const val VEHICLE_LAYER_ID = "vehicle-layer"

private const val POI_SOURCE_ID = "poi-source"
private const val POI_LAYER_ID = "poi-layer"

private class MapState {
    var mapboxMap: MapboxMap? = null
    var vehicleSource: GeoJsonSource? = null
    var poiSource: GeoJsonSource? = null
    var destroyed = false
}

@Composable
fun MapWidget(
    modifier: Modifier = Modifier,
    onNavigate: () -> Unit = {},
    viewModel: MapViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    // Use the Activity lifecycle directly — HorizontalPager gives each page its own
    // LocalLifecycleOwner that may not advance to RESUMED while the page is offscreen,
    // which would leave MapView stuck and rendering a black surface.
    val lifecycleOwner = context as ComponentActivity
    val location   by viewModel.vehicleLocation.collectAsStateWithLifecycle()
    val speedLimit by viewModel.speedLimit.collectAsStateWithLifecycle()

    val mapView = remember {
        // textureView: render via TextureView so Compose clip() can round the corners
        // (default SurfaceView is composited separately and ignores clipping)
        MapView(context, MapInitOptions(context = context, textureView = true))
    }
    val mapState = remember { MapState() }
    var styleLoaded by remember { mutableStateOf(false) }
    var isFollowing by remember { mutableStateOf(true) }

    LaunchedEffect(styleLoaded) {
        if (!styleLoaded) return@LaunchedEffect
        val map = mapState.mapboxMap ?: return@LaunchedEffect

        // Race-condition fix: seed source immediately if location already available
        val currentLoc = location
        if (currentLoc != null) {
            mapState.vehicleSource?.feature(
                featureWithBearing(currentLoc.lat, currentLoc.lng, currentLoc.bearingDeg)
            )
            map.setCamera(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(currentLoc.lng, currentLoc.lat))
                    .zoom(17.5)
                    .build()
            )
        } else {
            map.setCamera(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(14.4378, 50.0755))
                    .zoom(17.5)
                    .build()
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
                    // Map-level setup — must not live inside the style-load callback below,
                    // since that callback only fires on a successful (online) style load. Without
                    // a signal, the style never loads, so gestures/camera-follow must already be
                    // configured before that point, not gated behind it.
                    mapState.mapboxMap = mapboxMap
                    gestures.updateSettings {
                        scrollEnabled = true
                        pinchToZoomEnabled = true
                        rotateEnabled = false
                        pitchEnabled = false
                    }
                    // Detect user touch to pause auto-follow — mirrors the old
                    // "reason == REASON_GESTURE" check from the previous map engine's camera listener.
                    gestures.addOnMoveListener(object : OnMoveListener {
                        override fun onMoveBegin(detector: MoveGestureDetector) {
                            isFollowing = false
                        }
                        override fun onMove(detector: MoveGestureDetector): Boolean = false
                        override fun onMoveEnd(detector: MoveGestureDetector) {}
                    })
                    // Style fetch is online-only (no offline fallback in this phase) — log failures
                    // so a no-signal black map is diagnosable instead of silently inert.
                    mapboxMap.subscribeMapLoadingError { error ->
                        Log.e("MapWidget", "Style load failed: $error")
                    }

                    // loadStyle() is the current (v11) API — the older loadStyleUri() overloads
                    // are deprecated in favor of this unified loader.
                    mapboxMap.loadStyle(TileConfig.MAP_STYLE_URI) { style ->
                        Log.d("MapWidget", "Style loaded OK")

                        // POI layer — below vehicle marker
                        PoiType.entries.forEach { type ->
                            style.addImage("poi-${type.name.lowercase()}", createPoiIcon(type))
                        }
                        val poiSource = GeoJsonSource.Builder(POI_SOURCE_ID).build()
                        style.addSource(poiSource)
                        mapState.poiSource = poiSource
                        style.addLayer(
                            SymbolLayer(POI_LAYER_ID, POI_SOURCE_ID)
                                .iconImage(get("icon"))
                                .iconAllowOverlap(true)
                                .iconIgnorePlacement(true)
                                .iconSize(0.8)
                        )
                        val pendingPois = viewModel.nearbyPois.value
                        if (pendingPois.isNotEmpty()) {
                            poiSource.featureCollection(poisToFeatureCollection(pendingPois))
                        }

                        // Vehicle marker layer — on top
                        style.addImage(MARKER_IMAGE_ID, createVehicleMarkerBitmap())
                        val vehicleSource = GeoJsonSource.Builder(VEHICLE_SOURCE_ID).build()
                        style.addSource(vehicleSource)
                        mapState.vehicleSource = vehicleSource

                        // icon-rotate reads "bearing" property from each GeoJSON feature —
                        // rotation updates without touching the layer style (no style re-evaluation)
                        style.addLayer(
                            SymbolLayer(VEHICLE_LAYER_ID, VEHICLE_SOURCE_ID)
                                .iconImage(MARKER_IMAGE_ID)
                                .iconSize(0.5)
                                .iconAllowOverlap(true)
                                .iconIgnorePlacement(true)
                                .iconRotationAlignment(IconRotationAlignment.MAP)
                                .iconRotate(get("bearing"))
                        )

                        styleLoaded = true
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        SpeedDisplay(
            speedKmh = location?.speedKmh ?: 0f,
            speedLimitKmh = speedLimit,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 18.dp, bottom = 18.dp)
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
        val loc    = location ?: return@LaunchedEffect
        val source = mapState.vehicleSource ?: return@LaunchedEffect

        val (snapLat, snapLng) = RouteSnapHelper.snapToRoute(
            loc.lat, loc.lng, viewModel.routePolyline.value
        )

        source.feature(featureWithBearing(snapLat, snapLng, loc.bearingDeg))

        if (isFollowing) {
            mapState.mapboxMap?.easeTo(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(snapLng, snapLat))
                    .build(),
                MapAnimationOptions.mapAnimationOptions { duration(500) }
            )
        }
    }

    // Resume following 10s after user last touched the map
    LaunchedEffect(isFollowing) {
        if (!isFollowing) {
            delay(10_000)
            isFollowing = true
        }
    }

    LaunchedEffect("poi") {
        viewModel.nearbyPois.collect { pois ->
            mapState.poiSource?.featureCollection(poisToFeatureCollection(pois))
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                // MapView v11.30.1 still exposes onResume() (unlike onPause(), which this
                // SDK version doesn't have) — call it for parity with the previous map engine.
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        val state = lifecycleOwner.lifecycle.currentState
        if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // The MapView instance lives in `remember` inside this composable's own scope, so
            // once MapWidget leaves composition (e.g. MapNavPanel switching to NAV) this exact
            // instance is gone for good regardless of what we do here — a fresh MapView is
            // created via `remember` if/when MapWidget re-enters composition. Always destroy it
            // to release its resources; the `mapState.destroyed` guard prevents a double-destroy.
            if (!mapState.destroyed) { mapState.destroyed = true; mapView.onDestroy() }
        }
    }
}

private fun poisToFeatureCollection(pois: List<Poi>): FeatureCollection {
    val features = pois.map { poi ->
        Feature.fromGeometry(Point.fromLngLat(poi.lng, poi.lat)).also {
            it.addStringProperty("icon", "poi-${poi.type.name.lowercase()}")
            it.addStringProperty("name", poi.name ?: "")
        }
    }
    return FeatureCollection.fromFeatures(features)
}

private fun createPoiIcon(type: PoiType): Bitmap {
    val size = 64
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val c = size / 2f
    val r = c - 4f
    val bgColor = when (type) {
        PoiType.FUEL       -> android.graphics.Color.parseColor("#22C55E")
        PoiType.PARKING    -> android.graphics.Color.parseColor("#3B82F6")
        PoiType.RESTAURANT -> android.graphics.Color.parseColor("#F97316")
        PoiType.HOSPITAL   -> android.graphics.Color.parseColor("#EF4444")
    }
    val label = when (type) {
        PoiType.FUEL       -> "⛽"
        PoiType.PARKING    -> "P"
        PoiType.RESTAURANT -> "☕"
        PoiType.HOSPITAL   -> "+"
    }
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor; style = Paint.Style.FILL }
    if (type == PoiType.PARKING) canvas.drawRoundRect(RectF(4f, 4f, size - 4f, size - 4f), 10f, 10f, bgPaint)
    else canvas.drawCircle(c, c, r, bgPaint)
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = if (type == PoiType.PARKING || type == PoiType.HOSPITAL) 30f else 22f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    canvas.drawText(label, c, c - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
    return bitmap
}

// Encode bearing as GeoJSON feature property so icon-rotate is data-driven.
// Updating only the source data never triggers a style re-evaluation → no flicker.
private fun featureWithBearing(lat: Double, lng: Double, bearing: Float): Feature =
    Feature.fromGeometry(Point.fromLngLat(lng, lat)).also {
        it.addNumberProperty("bearing", bearing)
    }

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
