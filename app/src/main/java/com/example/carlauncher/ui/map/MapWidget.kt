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
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

private const val MARKER_IMAGE_ID = "vehicle-marker"
private const val VEHICLE_SOURCE_ID = "vehicle-source"
private const val VEHICLE_LAYER_ID = "vehicle-layer"

private const val POI_SOURCE_ID = "poi-source"
private const val POI_LAYER_ID = "poi-layer"

private class MapState {
    var map: MapLibreMap? = null
    var source: GeoJsonSource? = null
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
    var isFollowing by remember { mutableStateOf(true) }

    // Load Mapy.cz raster style as soon as MapLibre is ready
    LaunchedEffect(mapAsyncReady) {
        if (!mapAsyncReady) return@LaunchedEffect
        val map = mapState.map ?: return@LaunchedEffect

        val styleBuilder = when (viewModel.tileSource) {
            TileSource.MAPYCZ  -> Style.Builder().fromJson(buildMapyczStyleJson(TileConfig.MAPYCZ_BASIC))
            TileSource.DEMO    -> Style.Builder().fromUri(TileConfig.DEMO)
            // TODO: Přepnout na PMTILES po ověření POI vrstev na zařízení.
            TileSource.PMTILES -> Style.Builder().fromJson(buildMapyczStyleJson(TileConfig.MAPYCZ_BASIC))
        }
        Log.d("MapWidget", "Loading style: ${viewModel.tileSource}")
        map.setStyle(styleBuilder) { style ->
            Log.d("MapWidget", "Style loaded OK, layers=${style.layers.size}")

            // POI layer — below vehicle marker
            PoiType.entries.forEach { type ->
                style.addImage("poi-${type.name.lowercase()}", createPoiIcon(type))
            }
            val poiSource = GeoJsonSource(POI_SOURCE_ID)
            style.addSource(poiSource)
            mapState.poiSource = poiSource
            style.addLayer(
                SymbolLayer(POI_LAYER_ID, POI_SOURCE_ID).withProperties(
                    PropertyFactory.iconImage(Expression.get("icon")),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconSize(0.8f)
                )
            )
            val pendingPois = viewModel.nearbyPois.value
            if (pendingPois.isNotEmpty()) poiSource.setGeoJson(poisToGeoJson(pendingPois))

            // Vehicle marker layer — on top
            style.addImage(MARKER_IMAGE_ID, createVehicleMarkerBitmap())

            val source = GeoJsonSource(VEHICLE_SOURCE_ID)
            style.addSource(source)
            mapState.source = source

            // icon-rotate reads "bearing" property from each GeoJSON feature —
            // rotation updates without touching the layer style (no style re-evaluation)
            style.addLayer(
                SymbolLayer(VEHICLE_LAYER_ID, VEHICLE_SOURCE_ID)
                    .withProperties(
                        PropertyFactory.iconImage(MARKER_IMAGE_ID),
                        PropertyFactory.iconSize(0.5f),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                        PropertyFactory.iconRotationAlignment("map"),
                        PropertyFactory.iconRotate(Expression.get("bearing"))
                    )
            )

            // Race-condition fix: seed source immediately if location already available
            val currentLoc = location
            if (currentLoc != null) {
                source.setGeoJson(featureWithBearing(currentLoc.lat, currentLoc.lng, currentLoc.bearingDeg))
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(currentLoc.lat, currentLoc.lng), 17.5))
            } else {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(50.0755, 14.4378), 17.5))
            }
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
                        map.uiSettings.isScrollGesturesEnabled = true
                        map.uiSettings.isZoomGesturesEnabled = true
                        map.uiSettings.isRotateGesturesEnabled = false
                        map.uiSettings.isTiltGesturesEnabled = false
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isAttributionEnabled = false
                        // reason == 1 → REASON_GESTURE (user touch)
                        map.addOnCameraMoveStartedListener { reason ->
                            if (reason == 1) isFollowing = false
                        }
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
        val loc    = location ?: return@LaunchedEffect
        val source = mapState.source ?: return@LaunchedEffect

        source.setGeoJson(featureWithBearing(loc.lat, loc.lng, loc.bearingDeg))

        if (isFollowing) {
            mapState.map?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(loc.lat, loc.lng)), 500)
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
            mapState.poiSource?.setGeoJson(poisToGeoJson(pois))
        }
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
            // Only destroy MapView when the Activity is actually finishing, not on pager swipe.
            // HorizontalPager removes composables from composition when swiped away — calling
            // onDestroy() here would kill the MapView permanently; it cannot be restarted.
            if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
                if (!mapState.destroyed) { mapState.destroyed = true; mapView.onDestroy() }
            }
        }
    }
}

private fun poisToGeoJson(pois: List<Poi>): String {
    val features = pois.joinToString(",") { poi ->
        val name = (poi.name ?: "").replace("\\", "\\\\").replace("\"", "\\\"")
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[${poi.lng},${poi.lat}]},"properties":{"icon":"poi-${poi.type.name.lowercase()}","name":"$name"}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
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
