package com.example.carlauncher.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import com.example.carlauncher.R
import com.example.carlauncher.data.model.Parking
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.mapbox.bindgen.ExpectedFactory
import com.mapbox.bindgen.Value
import com.mapbox.common.location.Location
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
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
import com.mapbox.navigation.base.TimeFormat
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.trip.model.RouteLegProgress
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.arrival.ArrivalObserver
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.trip.session.TripSessionState
import com.mapbox.navigation.tripdata.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.tripdata.maneuver.model.Maneuver
import com.mapbox.navigation.tripdata.progress.api.MapboxTripProgressApi
import com.mapbox.navigation.tripdata.progress.model.DistanceRemainingFormatter
import com.mapbox.navigation.tripdata.progress.model.EstimatedTimeOfArrivalFormatter
import com.mapbox.navigation.tripdata.progress.model.PercentDistanceTraveledFormatter
import com.mapbox.navigation.tripdata.progress.model.TimeRemainingFormatter
import com.mapbox.navigation.tripdata.progress.model.TripProgressUpdateFormatter
import com.mapbox.navigation.tripdata.progress.model.TripProgressUpdateValue
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverPrimaryOptions
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverSecondaryOptions
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverSubOptions
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverViewOptions
import com.mapbox.navigation.ui.components.maneuver.view.MapboxManeuverView
import com.mapbox.navigation.ui.components.tripprogress.view.MapboxTripProgressView
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.lifecycle.NavigationBasicGesturesHandler
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
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
    // Task 4: Navigation SDK's own camera system, created once at map-creation time (see the
    // mapView.apply{} block) and read from LaunchedEffect(isNavigating)/observers below. Plain
    // remembered fields, not Compose State — same rationale as mapboxMap/parkingSource above.
    var navigationCamera: NavigationCamera? = null
    var viewportDataSource: MapboxNavigationViewportDataSource? = null
}

// Tracks the in-flight requestRoutes() call so a newer destination selection can cancel a
// still-pending older one via MapboxNavigation.cancelRouteRequest — otherwise a slow route
// response for an earlier pick could land after a newer one and overwrite it on the map.
// Not Compose State: it's only read/written inside the onDestinationSelected callback, never
// during composition, so a plain remembered var (same pattern as MapState above) is enough.
private class RouteRequestState {
    var activeRequestId: Long? = null
}

// Lets a NavigationRouterCallback close over "the request id this specific callback instance
// is for" even though requestRoutes() only returns that id after the call (and thus after the
// callback object already exists) — filled in immediately once known, read only from callbacks
// that fire later on the main thread.
private class LongHolder {
    var value: Long? = null
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

    // Task 4: whether active turn-by-turn guidance is running. Deliberately NOT defaulted to
    // false — MapboxNavigation's trip session lives outside this composable's own lifetime
    // (it's tied to the Activity via MapboxNavigationApp/requireMapboxNavigation, see the
    // long comment above), so switching MapNavPanel away from Map and back tears down and
    // recreates this whole composable while guidance keeps running underneath. Deriving the
    // initial value from mapboxNavigation.getTripSessionState() (instead of blindly starting
    // in free-drive) is what makes the free-drive UI correctly NOT reappear over a still-active
    // trip session. Verified against the SDK source (MapboxNavigation.kt, navigationcore
    // 3.30.1 pinned in this project): `fun getTripSessionState(): TripSessionState =
    // tripSession.getState()`, STARTED while a session (foreground service + location updates)
    // is active, STOPPED otherwise — exactly the signal needed here.
    var isNavigating by remember {
        mutableStateOf(mapboxNavigation.getTripSessionState() == TripSessionState.STARTED)
    }
    // Repopulated by routeProgressObserver below. Left null (not emptyList()/an empty progress
    // object) until the first RouteProgress arrives after (re)registering, so the maneuver
    // banner/trip progress views simply don't render (see the `?.let` usage further down)
    // instead of flashing empty content — including right after a panel-switch-back while
    // isNavigating restores to true from the check above.
    var currentManeuvers by remember { mutableStateOf<List<Maneuver>?>(null) }
    var currentTripProgress by remember { mutableStateOf<TripProgressUpdateValue?>(null) }

    // Mapbox's own LocationProvider implementation, fed exclusively by locationObserver below
    // (route-matched/enhanced locations from the Navigation SDK's own trip session) — swapped
    // in as the map's active puck source only while isNavigating, see LaunchedEffect(isNavigating).
    val navigationLocationProvider = remember { NavigationLocationProvider() }
    val distanceFormatterOptions = remember { DistanceFormatterOptions.Builder(context).build() }
    val maneuverApi = remember {
        MapboxManeuverApi(MapboxDistanceFormatter(distanceFormatterOptions))
    }
    val tripProgressApi = remember {
        MapboxTripProgressApi(
            TripProgressUpdateFormatter.Builder(context)
                .distanceRemainingFormatter(DistanceRemainingFormatter(distanceFormatterOptions))
                .timeRemainingFormatter(TimeRemainingFormatter(context))
                .percentRouteTraveledFormatter(PercentDistanceTraveledFormatter())
                .estimatedTimeOfArrivalFormatter(
                    EstimatedTimeOfArrivalFormatter(context, TimeFormat.NONE_SPECIFIED)
                )
                .build()
        )
    }

    // Task 5 / final review I-2: voice guidance is NOT wired here. MapWidget is torn down on
    // every MapNavPanel switch away from Map (Fáze 2 leak fix), so anything scoped to this
    // composition would silence a driver mid-route the moment they look at the Navigace panel —
    // while spec §3 requires active navigation *including voice* to survive that switch. The
    // whole voice pipeline therefore lives in MapboxVoiceGuidanceObserver, registered once on
    // MapboxNavigationApp in CarLauncherApp.onCreate().

    // Shared teardown for "guidance is over": the Ukončit button and the arrival observer below
    // must do exactly the same thing. Closes only over `mapboxNavigation` and the state delegates
    // declared above, all of which are stable for this composition, so it's safe for the
    // DisposableEffect to capture it without being keyed on it.
    val endGuidance: () -> Unit = {
        mapboxNavigation.stopTripSession()
        mapboxNavigation.setNavigationRoutes(emptyList())
        currentManeuvers = null
        currentTripProgress = null
        isNavigating = false
    }

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
                // Task 4: drop the route from camera-frame evaluation too — mirrors the
                // reference app's routesObserver (JetpackComposeActivity.kt).
                mapState.viewportDataSource?.clearRouteData()
                mapState.viewportDataSource?.evaluate()
            } else {
                routeLineApi.setNavigationRoutes(routeUpdateResult.navigationRoutes) { value ->
                    style?.let { routeLineView.renderRouteDrawData(it, value) }
                }
                mapState.viewportDataSource?.onRouteChanged(routeUpdateResult.navigationRoutes.first())
                mapState.viewportDataSource?.evaluate()
            }
        }
        mapboxNavigation.registerRoutesObserver(routesObserver)
        onDispose {
            mapboxNavigation.unregisterRoutesObserver(routesObserver)
        }
    }

    // Task 4: feeds the Navigation SDK's own puck (navigationLocationProvider) and camera
    // (viewportDataSource) while a trip session is running, and updates the maneuver banner /
    // trip progress overlays. Registered for this composable's lifetime, same rationale as the
    // routesObserver DisposableEffect above (MapWidget isn't a class, so it can't host these as
    // requireMapboxNavigation()'s onResumedObserver members the way the reference Activity does).
    DisposableEffect(
        mapboxNavigation,
        maneuverApi,
        tripProgressApi,
        navigationLocationProvider,
    ) {
        val locationObserver = object : LocationObserver {
            override fun onNewRawLocation(rawLocation: Location) {
                // Not used — the enhanced/map-matched location below is what drives the puck
                // and camera, same as the reference app.
            }
            override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
                val enhancedLocation = locationMatcherResult.enhancedLocation
                navigationLocationProvider.changePosition(
                    location = enhancedLocation,
                    keyPoints = locationMatcherResult.keyPoints,
                )
                mapState.viewportDataSource?.onLocationChanged(enhancedLocation)
                mapState.viewportDataSource?.evaluate()
            }
        }
        val routeProgressObserver = RouteProgressObserver { routeProgress ->
            mapState.viewportDataSource?.onRouteProgressChanged(routeProgress)
            mapState.viewportDataSource?.evaluate()
            currentManeuvers = maneuverApi.getManeuvers(routeProgress).getValueOrElse { emptyList() }
            currentTripProgress = tripProgressApi.getTripProgress(routeProgress)
        }
        // Final review I-4: without this, the trip session (foreground service + continuous GPS
        // + voice) would only ever end by the driver tapping Ukončit — on an always-on car
        // launcher it would otherwise keep running indefinitely after arrival. Only
        // onFinalDestinationArrival tears things down; onWaypointArrival fires per intermediate
        // leg (this app's route requests only ever carry origin + destination today, but a
        // via-point must not end the trip if that ever changes), and onNextRouteLegStart is
        // driven by navigateNextRouteLeg(), which this app never calls.
        val arrivalObserver = object : ArrivalObserver {
            override fun onWaypointArrival(routeProgress: RouteProgress) {}
            override fun onNextRouteLegStart(routeLegProgress: RouteLegProgress) {}
            override fun onFinalDestinationArrival(routeProgress: RouteProgress) {
                Log.d("MapWidget", "Final destination reached — ending trip session")
                endGuidance()
            }
        }
        mapboxNavigation.registerLocationObserver(locationObserver)
        mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
        mapboxNavigation.registerArrivalObserver(arrivalObserver)
        onDispose {
            mapboxNavigation.unregisterLocationObserver(locationObserver)
            mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
            mapboxNavigation.unregisterArrivalObserver(arrivalObserver)
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

                    // Task 4: Navigation SDK's own camera system — set up alongside the puck
                    // above (map-creation time, not inside the loadStyle callback below, for the
                    // same reason documented on this block: loadStyle's callback only fires on a
                    // successful online load). Stays inert (IDLE) until isNavigating flips true
                    // (see LaunchedEffect(isNavigating) further down), so it never fights the
                    // existing easeTo/isFollowing free-drive logic while not navigating.
                    val viewportDataSource = MapboxNavigationViewportDataSource(mapboxMap)
                    mapState.viewportDataSource = viewportDataSource
                    val navigationCamera = NavigationCamera(mapboxMap, camera, viewportDataSource)
                    mapState.navigationCamera = navigationCamera
                    // Stops NavigationCamera's own following/overview state automatically when
                    // the user manually pans/zooms/rotates the map, same as the reference app.
                    camera.addCameraAnimationsLifecycleListener(
                        NavigationBasicGesturesHandler(navigationCamera)
                    )
                    val density = context.resources.displayMetrics.density
                    viewportDataSource.followingPadding = EdgeInsets(
                        140.0 * density,
                        24.0 * density,
                        110.0 * density,
                        24.0 * density,
                    )
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

                        // Redraw an already-active route after a MapNavPanel switch back to Mapa.
                        // MapWidget — and with it MapView, routeLineApi and routeLineView — is
                        // recreated on every switch, while the trip session keeps running
                        // underneath. The RoutesObserver above *does* fire immediately on
                        // re-registration with the still-active routes, but that happens before
                        // this style finishes loading (measured on device: setNavigationRoutes at
                        // T+0.465s vs. style loaded at T+0.926s), so `mapboxMap.style` was still
                        // null there and the draw was dropped. The route set never changes again
                        // afterwards, so without this the route line stays missing from the map
                        // for the rest of the drive while guidance carries on regardless.
                        // getRouteDrawData renders whatever routeLineApi already holds, so it is
                        // correct in both orderings (and a harmless no-op when there's no route).
                        routeLineApi.getRouteDrawData { value ->
                            routeLineView.renderRouteDrawData(style, value)
                        }

                        styleLoaded = true
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Final review I-1: hidden while navigating. MapboxTripProgressView below is a full-width
        // opaque bar in the same bottom band and would otherwise sit straight over the speed
        // readout. Unchanged outside active guidance — free drive keeps today's exact layout.
        if (!isNavigating) {
            SpeedDisplay(
                speedKmh = location?.speedKmh ?: 0f,
                speedLimitKmh = speedLimit,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 18.dp, bottom = 18.dp)
            )
        }

        // Destination search — matches the reference app's search-bar-over-map convention.
        // Hidden while isNavigating: the maneuver banner below occupies the same TopCenter
        // overlay slot, and showing both stacked at once would look broken. It's the entry
        // point for free-drive only; once guidance starts, the maneuver banner is the
        // equivalent top overlay (design spec's free-drive vs. active-navigation split).
        if (!isNavigating) {
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
                        // The callback needs to know which request it belongs to so it can tell
                        // whether it's still the authoritative (most recent) one by the time it
                        // fires — requestRoutes() only returns that id after being called, so the
                        // callback captures this mutable holder and it's filled in right after.
                        // A chain of 3+ rapid selections can otherwise let a stale callback (e.g.
                        // request B's onCanceled, fired as a side effect of cancelling B for C) wipe
                        // out activeRequestId while it actually holds a newer request's id (C's),
                        // untracking it — or worse, let a stale onRoutesReady call
                        // setNavigationRoutes() with an outdated route after a newer one already
                        // rendered. Comparing against activeRequestId before acting closes both.
                        val ownRequestId = LongHolder()
                        val callback = object : NavigationRouterCallback {
                            override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {
                                if (routeRequestState.activeRequestId == ownRequestId.value) {
                                    routeRequestState.activeRequestId = null
                                }
                            }
                            override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                                if (routeRequestState.activeRequestId == ownRequestId.value) {
                                    routeRequestState.activeRequestId = null
                                    Log.e("MapWidget", "Route request failed: $reasons")
                                    routeRequestError = "Trasu se nepodařilo najít"
                                }
                            }
                            override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
                                // Only apply this result — and only clear the tracked id — if no
                                // newer request has since taken ownership of activeRequestId. A
                                // superseded (stale) result must never overwrite a newer route.
                                if (routeRequestState.activeRequestId == ownRequestId.value) {
                                    routeRequestState.activeRequestId = null
                                    mapboxNavigation.setNavigationRoutes(routes)
                                    // Task 4: this plan has no separate route-preview/"Start" step —
                                    // guidance begins immediately once a route is ready (spec §3 flow).
                                    mapboxNavigation.startTripSession()
                                    isNavigating = true
                                }
                            }
                        }
                        val requestId = mapboxNavigation.requestRoutes(
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
                            callback
                        )
                        ownRequestId.value = requestId
                        routeRequestState.activeRequestId = requestId
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
        }

        // Active turn-by-turn overlays — maneuver banner (TopCenter, replaces the search bar
        // above) and trip progress (BottomCenter), fed by routeProgressObserver above. Each is
        // gated on its own `?.let` rather than isNavigating alone, so it simply doesn't render
        // until the first RouteProgress arrives (including right after a panel-switch-back —
        // see the isNavigating remember{} comment for why that can start true immediately).
        if (isNavigating) {
            currentManeuvers?.let { maneuvers ->
                AndroidView(
                    // Final review I-1: extra end padding keeps the banner's right-hand content
                    // (step distance / lane guidance) clear of the Ukončit IconButton below,
                    // which sits at TopEnd with 18dp padding around a 48dp touch target.
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 16.dp, end = 72.dp, bottom = 16.dp),
                    factory = { ctx ->
                        // Final review I-3: SDK defaults are light (blue-grey #37516F banner);
                        // re-tint to CarColors so the banner isn't a glare source at night.
                        MapboxManeuverView(ctx).apply {
                            updateManeuverViewOptions(darkManeuverViewOptions())
                        }
                    },
                    update = { view -> view.renderManeuvers(ExpectedFactory.createValue(maneuvers)) }
                )
            }
            currentTripProgress?.let { progress ->
                AndroidView(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(64.dp),
                    factory = { ctx ->
                        // Final review I-3: the SDK default background is @color/colorSurface =
                        // pure white (in Mapbox's values-night too), i.e. a full-width glowing
                        // bar across the bottom of the map at night.
                        MapboxTripProgressView(ctx).apply {
                            updateStyle(R.style.CarTripProgressView)
                        }
                    },
                    update = { view -> view.render(progress) }
                )
            }
            // Ukončit — ends active Mapbox guidance and restores Fáze 2's free-drive default
            // (this is a completely separate control from NavWidget's own "Ukončit", which
            // ends the unrelated notification-based navigation panel).
            IconButton(
                onClick = endGuidance,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(18.dp)
                    .clip(CircleShape)
                    .background(CarColors.Surface2)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Ukončit navigaci",
                    tint = CarColors.Text
                )
            }
        }

        // Navigovat — primary CTA, bottom-right corner of the map.
        // Final review I-1: hidden while navigating, same bottom band as the full-width
        // MapboxTripProgressView above. It only switches to the (unrelated, notification-based)
        // Navigace panel, so there's nothing lost by taking it out of the active-guidance UI —
        // and the Ukončit button is the correct control in that state. Unchanged in free drive.
        if (!isNavigating) {
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
    }

    LaunchedEffect(location) {
        val loc = location ?: return@LaunchedEffect

        val (snapLat, snapLng) = RouteSnapHelper.snapToRoute(
            loc.lat, loc.lng, viewModel.routePolyline.value
        )

        locationProvider.push(Point.fromLngLat(snapLng, snapLat), loc.bearingDeg.toDouble())

        // Task 4: gated on !isNavigating — while active guidance is running, the Navigation
        // SDK's own NavigationCamera (fed by viewportDataSource in the locationObserver/
        // routeProgressObserver above) drives the camera instead, so this free-drive follow
        // logic must stand down rather than fight it for control every location update.
        if (isFollowing && !isNavigating) {
            mapState.mapboxMap?.easeTo(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(snapLng, snapLat))
                    .build(),
                MapAnimationOptions.mapAnimationOptions { duration(500) }
            )
        }
    }

    // Task 4: swap the map's active puck source and camera-follow ownership when guidance
    // starts/ends. `mapView.location` here is unambiguous (unlike inside the mapView.apply{}
    // block above) since there's no implicit `this` receiver in this scope to be shadowed by
    // the composable's own `location` (GPS fix) val — `mapView.location` always resolves to
    // the MapView.location plugin extension property regardless.
    LaunchedEffect(isNavigating) {
        if (isNavigating) {
            mapView.location.setLocationProvider(navigationLocationProvider)
            mapState.navigationCamera?.requestNavigationCameraToFollowing()
        } else {
            mapView.location.setLocationProvider(locationProvider)
            mapState.navigationCamera?.requestNavigationCameraToIdle()
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
            if (!mapState.destroyed) {
                mapState.destroyed = true
                // Final review M-1: cancel every MapWidget-scoped API that holds background work,
                // matching the reference app's onDestroy(). The voice pipeline is deliberately
                // absent — it is no longer scoped here (see I-2 / MapboxVoiceGuidanceObserver),
                // and shutting it down on a panel switch is exactly the bug that fix removes.
                routeLineApi.cancel()
                routeLineView.cancel()
                maneuverApi.cancel()
                mapView.onDestroy()
            }
        }
    }
}

/**
 * Tmavý styl pro MapboxManeuverView. Na rozdíl od MapboxTripProgressView tohle View nemá
 * `updateStyle(@StyleRes Int)` — jediné veřejné API pro runtime přebarvení je
 * `updateManeuverViewOptions(ManeuverViewOptions)` (ověřeno ve zdrojáku ui-components v3.30.1),
 * kde barvy jdou jako @ColorRes a textové vzhledy jako @StyleRes. Odsud res/values/nav_styles.xml.
 */
private fun darkManeuverViewOptions(): ManeuverViewOptions =
    ManeuverViewOptions.Builder()
        .maneuverBackgroundColor(R.color.car_nav_surface)
        .subManeuverBackgroundColor(R.color.car_nav_surface_2)
        .upcomingManeuverBackgroundColor(R.color.car_nav_bg_2)
        .turnIconManeuver(R.style.CarManeuverTurnIcon)
        .laneGuidanceTurnIconManeuver(R.style.CarManeuverTurnIcon)
        .stepDistanceTextAppearance(R.style.CarManeuverStepDistance)
        .primaryManeuverOptions(
            ManeuverPrimaryOptions.Builder()
                .textAppearance(R.style.CarManeuverPrimaryText)
                .build()
        )
        .secondaryManeuverOptions(
            ManeuverSecondaryOptions.Builder()
                .textAppearance(R.style.CarManeuverSecondaryText)
                .build()
        )
        .subManeuverOptions(
            ManeuverSubOptions.Builder()
                .textAppearance(R.style.CarManeuverSubText)
                .build()
        )
        .build()

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
