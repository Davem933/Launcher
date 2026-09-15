package com.example.carlauncher.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import com.example.carlauncher.data.model.Parking
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.mapbox.bindgen.Value
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
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
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.LineLayer
import com.mapbox.maps.extension.style.layers.generated.SymbolLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.VectorSource
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions

private const val TRAFFIC_SOURCE_ID = "traffic-source"
private const val TRAFFIC_LAYER_ID = "traffic-congestion"

private const val PARKING_IMAGE_ID = "parking-icon"
private const val PARKING_SOURCE_ID = "parking-source"
private const val PARKING_LAYER_ID = "parking-layer"

private class MapState {
    var mapboxMap: MapboxMap? = null
    var parkingSource: GeoJsonSource? = null
    var destroyed = false
}

// Tracks the in-flight requestRoutes() call so a newer destination selection can cancel a
// still-pending older one via MapboxNavigation.cancelRouteRequest — otherwise a slow route
// response for an earlier pick could land after a newer one and overwrite it on the map.
// Not Compose State: it's only read/written inside the onDestinationSelected callback, never
// during composition, so a plain remembered var (same pattern as MapState above) is enough.
private class RouteRequestState {
    var activeRequestId: Long? = null
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

    // MapboxNavigation instance for Task 3's route request/render (and Task 4's trip session).
    // `requireMapboxNavigation()` is a `LifecycleOwner` extension (not an Activity-only API) —
    // every SDK example uses it as `private val x by requireMapboxNavigation()` on a *class*
    // (Activity/Service/Session), where `this` is a valid non-null receiver for the delegate's
    // `getValue(thisRef: Any, ...)`. MapWidget is a bare @Composable function with no such
    // receiver, and the Kotlin compiler confirms local delegated properties require a
    // `getValue(Nothing?, KProperty0<*>)` overload (nullable thisRef) — this delegate's
    // non-null `Any` signature doesn't qualify, so `by` cannot be used here directly
    // (verified via ./gradlew compileDebugKotlin, not just inferred).
    // Constructing the delegate still performs its real job as a side effect — attaching
    // `lifecycleOwner` to MapboxNavigationApp (already set up in CarLauncherApp.onCreate())
    // and registering a lifecycle observer — so it's created exactly once via `remember`, and
    // the instance is then read the same way the delegate's own getValue() does internally.
    remember { lifecycleOwner.requireMapboxNavigation() }
    val mapboxNavigation = checkNotNull(MapboxNavigationApp.current()) {
        "MapboxNavigation cannot be null. Ensure that MapboxNavigationApp is setup and an" +
            " attached lifecycle is at least CREATED."
    }
    val routeLineApi = remember { MapboxRouteLineApi(MapboxRouteLineApiOptions.Builder().build()) }
    val routeLineView = remember {
        // No .routeLineBelowLayerId(...) — Style.STANDARD auto-detects and places the route
        // line in the MIDDLE slot on its own (Mapbox route-line UI component docs).
        MapboxRouteLineView(MapboxRouteLineViewOptions.Builder(context).build())
    }
    var routeRequestError by remember { mutableStateOf<String?>(null) }
    val routeRequestState = remember { RouteRequestState() }

    LaunchedEffect(routeRequestError) {
        if (routeRequestError == null) return@LaunchedEffect
        delay(4000)
        routeRequestError = null
    }

    // Mirrors the reference app's routesObserver: draw the route line when routes are set,
    // clear it when they're reset. Registered for this composable's lifetime rather than via
    // requireMapboxNavigation()'s onResumedObserver param, since MapWidget isn't a class that
    // can host that observer as a member the way the reference Activity does.
    DisposableEffect(mapboxNavigation, routeLineApi, routeLineView) {
        val routesObserver = RoutesObserver { routeUpdateResult ->
            val style = mapState.mapboxMap?.style
            if (routeUpdateResult.navigationRoutes.isEmpty()) {
                routeLineApi.clearRouteLine { value ->
                    style?.let { routeLineView.renderClearRouteLineValue(it, value) }
                }
            } else {
                routeLineApi.setNavigationRoutes(routeUpdateResult.navigationRoutes) { value ->
                    style?.let { routeLineView.renderRouteDrawData(it, value) }
                }
            }
        }
        mapboxNavigation.registerRoutesObserver(routesObserver)
        onDispose {
            mapboxNavigation.unregisterRoutesObserver(routesObserver)
        }
    }

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
                    .pitch(45.0)
                    .build()
            )
        } else {
            map.setCamera(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(14.4378, 50.0755))
                    .zoom(17.5)
                    .pitch(45.0)
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
                        rotateEnabled = true
                        pitchEnabled = true
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
                        // Standard style config — dark theme always, so pin the night light
                        // preset rather than following real-world time of day, and make sure
                        // 3D buildings/landmarks render (the point of switching to Standard).
                        style.setStyleImportConfigProperty(
                            TileConfig.STANDARD_IMPORT_ID, "lightPreset", Value.valueOf("night")
                        )
                        style.setStyleImportConfigProperty(
                            TileConfig.STANDARD_IMPORT_ID, "show3dObjects", Value.valueOf(true)
                        )

                        // Live traffic congestion coloring — Standard has no built-in traffic,
                        // unlike the classic TRAFFIC_NIGHT style, so it's layered on manually
                        // from Mapbox's traffic tileset (same source the classic style uses).
                        style.addSource(
                            VectorSource.Builder(TRAFFIC_SOURCE_ID)
                                .url("mapbox://mapbox.mapbox-traffic-v1")
                                .build()
                        )
                        style.addLayer(
                            LineLayer(TRAFFIC_LAYER_ID, TRAFFIC_SOURCE_ID)
                                .sourceLayer("traffic")
                                .lineCap(LineCap.ROUND)
                                .lineJoin(LineJoin.ROUND)
                                .lineColor(
                                    Expression.match(
                                        input = Expression.get("congestion"),
                                        stops = arrayOf(
                                            Expression.literal("low") to Expression.rgb(57.0, 198.0, 109.0),
                                            Expression.literal("moderate") to Expression.rgb(255.0, 140.0, 26.0),
                                            Expression.literal("heavy") to Expression.rgb(255.0, 0.0, 21.0),
                                            Expression.literal("severe") to Expression.rgb(152.0, 27.0, 37.0),
                                        ),
                                        fallback = Expression.rgba(0.0, 0.0, 0.0, 0.0)
                                    )
                                )
                                .lineWidth(
                                    Expression.exponentialInterpolator(
                                        1.5,
                                        Expression.zoom(),
                                        Expression.literal(10.0) to Expression.literal(1.0),
                                        Expression.literal(15.0) to Expression.literal(4.0),
                                        Expression.literal(20.0) to Expression.literal(14.0)
                                    )
                                )
                                .slot("middle")
                        )

                        // Parking icons — Mapbox's own Standard POI data is sparse for parking
                        // in this area, so this is sourced from Overpass/OSM like before instead
                        // of relying on the style's built-in POIs (see ParkingRepository).
                        style.addImage(PARKING_IMAGE_ID, createParkingIcon())
                        val parkingSource = GeoJsonSource.Builder(PARKING_SOURCE_ID).build()
                        style.addSource(parkingSource)
                        mapState.parkingSource = parkingSource
                        style.addLayer(
                            SymbolLayer(PARKING_LAYER_ID, PARKING_SOURCE_ID)
                                .iconImage(PARKING_IMAGE_ID)
                                .iconAllowOverlap(true)
                                .iconIgnorePlacement(true)
                                .iconSize(0.7)
                        )
                        val pendingParking = viewModel.nearbyParking.value
                        if (pendingParking.isNotEmpty()) {
                            parkingSource.featureCollection(parkingToFeatureCollection(pendingParking))
                        }

                        // Route line layers must exist before any route is drawn on top.
                        routeLineView.initializeLayers(style)

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

        // Always-visible destination search — matches the reference app's
        // search-bar-over-map convention. onDestinationSelected requests a route from the
        // current GPS fix to the chosen point; the routesObserver above draws it once ready.
        // Starting guidance (startTripSession()) is out of scope here — that's Task 4.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            DestinationSearchBar(
                currentLocation = location,
                onDestinationSelected = { point, _ ->
                    val currentLoc = location
                    if (currentLoc == null) {
                        routeRequestError = "Poloha není dostupná"
                        return@DestinationSearchBar
                    }
                    routeRequestError = null
                    // Cancel any still-pending request from a previous destination pick so its
                    // callback can't land after (and overwrite) this newer one's route.
                    routeRequestState.activeRequestId?.let { mapboxNavigation.cancelRouteRequest(it) }
                    routeRequestState.activeRequestId = mapboxNavigation.requestRoutes(
                        RouteOptions.builder()
                            .applyDefaultNavigationOptions()
                            .applyLanguageAndVoiceUnitOptions(context)
                            .coordinatesList(
                                listOf(
                                    Point.fromLngLat(currentLoc.lng, currentLoc.lat),
                                    point
                                )
                            )
                            .build(),
                        object : NavigationRouterCallback {
                            override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {
                                routeRequestState.activeRequestId = null
                            }
                            override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                                routeRequestState.activeRequestId = null
                                Log.e("MapWidget", "Route request failed: $reasons")
                                routeRequestError = "Trasu se nepodařilo najít"
                            }
                            override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
                                routeRequestState.activeRequestId = null
                                mapboxNavigation.setNavigationRoutes(routes)
                            }
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (routeRequestError != null) {
                Text(
                    text = routeRequestError.orEmpty(),
                    color = CarColors.Danger,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
            }
        }

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

    LaunchedEffect("parking") {
        viewModel.nearbyParking.collect { parking ->
            mapState.parkingSource?.featureCollection(parkingToFeatureCollection(parking))
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

private fun parkingToFeatureCollection(parking: List<Parking>): FeatureCollection {
    val features = parking.map { p ->
        Feature.fromGeometry(Point.fromLngLat(p.lng, p.lat))
    }
    return FeatureCollection.fromFeatures(features)
}

// Purple circle with a white "P" — matches the convention used by other nav apps on this device.
private fun createParkingIcon(): Bitmap {
    val size = 56
    val c = size / 2f
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#8B5CF6")
        style = Paint.Style.FILL
    }.also { canvas.drawCircle(c, c, c - 2f, it) }

    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }.also { canvas.drawText("P", c, c - (it.descent() + it.ascent()) / 2f, it) }

    return bitmap
}
