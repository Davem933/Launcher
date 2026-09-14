package com.example.carlauncher.ui.map

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
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.android.gestures.MoveGestureDetector

private class MapState {
    var mapboxMap: MapboxMap? = null
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
    val locationProvider = remember { AppLocationProvider() }
    var styleLoaded by remember { mutableStateOf(false) }
    var isFollowing by remember { mutableStateOf(true) }

    LaunchedEffect(styleLoaded) {
        if (!styleLoaded) return@LaunchedEffect
        val map = mapState.mapboxMap ?: return@LaunchedEffect

        // Race-condition fix: seed the puck immediately if location already available
        val currentLoc = location
        if (currentLoc != null) {
            locationProvider.push(
                Point.fromLngLat(currentLoc.lng, currentLoc.lat),
                currentLoc.bearingDeg.toDouble()
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
                    // Mapbox's own location puck, fed from our Kalman-filtered/route-snapped
                    // location instead of Mapbox's default device-GPS provider — no location
                    // permission needed here since we push updates ourselves.
                    // `this.` is required here: the composable's own `location` (the GPS
                    // fix StateFlow value) would otherwise shadow the MapView.location plugin
                    // extension property of the same name.
                    this.location.setLocationProvider(locationProvider)
                    this.location.updateSettings {
                        enabled = true
                        puckBearing = PuckBearing.COURSE
                        puckBearingEnabled = true
                        locationPuck = createDefault2DPuck(withBearing = true)
                    }
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
                    mapboxMap.loadStyle(TileConfig.MAP_STYLE_URI) {
                        Log.d("MapWidget", "Style loaded OK")
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
        val loc = location ?: return@LaunchedEffect

        val (snapLat, snapLng) = RouteSnapHelper.snapToRoute(
            loc.lat, loc.lng, viewModel.routePolyline.value
        )

        locationProvider.push(Point.fromLngLat(snapLng, snapLat), loc.bearingDeg.toDouble())

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
