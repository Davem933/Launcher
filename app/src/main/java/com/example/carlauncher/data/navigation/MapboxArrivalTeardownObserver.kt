package com.example.carlauncher.data.navigation

import android.util.Log
import com.mapbox.navigation.base.trip.model.RouteLegProgress
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.arrival.ArrivalObserver
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver

/**
 * Ukončení trip session po dojezdu do cíle, držené MIMO kompozici `MapWidget`u — ze stejného
 * důvodu jako [MapboxVoiceGuidanceObserver], se kterým sdílí i mechanismus registrace
 * (`MapboxNavigationApp.registerObserver` v [com.example.carlauncher.CarLauncherApp.onCreate]).
 *
 * Proč ne v `MapWidget`u: `MapWidget` se při přepnutí panelu Mapa → Navigace celý disposuje
 * (oprava leaku z Fáze 2), takže `ArrivalObserver` registrovaný v jeho `DisposableEffect`u
 * po dobu zobrazeného panelu Navigace **vůbec neexistuje**. A Mapboxí `ArrivalProgressObserver`
 * si dojezd latchuje (`routeArrived = routeProgress.navigationRoute`, ověřeno ve zdrojáku
 * v3.30.1) — pro danou trasu tedy `onFinalDestinationArrival` vystřelí jen JEDNOU a už nikdy,
 * ani směrem k observerům registrovaným později. Dojezd, který padne do okna s panelem
 * Navigace, by se tak ztratil natrvalo a foreground služba + GPS + hlas by běžely donekonečna.
 *
 * Dělá přesně to, co tlačítko Ukončit v `MapWidget`u (`stopTripSession()` +
 * `setNavigationRoutes(emptyList())`) — a nic víc. UI stav se z toho odvodí sám:
 * `MapWidget` sleduje `TripSessionStateObserver`em skutečný stav session, takže po zastavení
 * (ať už odsud, nebo tlačítkem) přepne zpět na volnou jízdu bez druhé, konkurenční cesty.
 *
 * `onWaypointArrival` / `onNextRouteLegStart` jsou schválně prázdné: mezicíl nesmí ukončit
 * trasu (dnešní route requesty vozí jen origin + cíl, ale to se může změnit) a
 * `navigateNextRouteLeg()` appka nevolá vůbec.
 *
 * Limit (stejný jako u hlasu): `onDetached` přijde, jakmile všichni attachnutí `LifecycleOwner`i
 * klesnou pod `STARTED`, tj. i při odchodu appky do pozadí — dojezd ve chvíli, kdy je launcher
 * kompletně na pozadí, se pořád neodchytí. Na trvale zapnutém launcheru je to okrajový stav.
 */
class MapboxArrivalTeardownObserver : MapboxNavigationObserver {

    /**
     * `ArrivalObserver` callbacky nedostávají `MapboxNavigation` jako parametr, takže si
     * instanci držíme z `onAttached` a v `onDetached` ji zase pouštíme — nikdy tedy nevoláme
     * na už odpojenou instanci.
     */
    private var navigation: MapboxNavigation? = null

    private val arrivalObserver = object : ArrivalObserver {
        override fun onWaypointArrival(routeProgress: RouteProgress) = Unit

        override fun onNextRouteLegStart(routeLegProgress: RouteLegProgress) = Unit

        override fun onFinalDestinationArrival(routeProgress: RouteProgress) {
            val mapboxNavigation = navigation ?: return
            Log.d(TAG, "final destination arrival — ending trip session")
            mapboxNavigation.stopTripSession()
            mapboxNavigation.setNavigationRoutes(emptyList())
        }
    }

    override fun onAttached(mapboxNavigation: MapboxNavigation) {
        navigation = mapboxNavigation
        mapboxNavigation.registerArrivalObserver(arrivalObserver)
        Log.d(TAG, "onAttached — arrival teardown armed")
    }

    override fun onDetached(mapboxNavigation: MapboxNavigation) {
        mapboxNavigation.unregisterArrivalObserver(arrivalObserver)
        navigation = null
        Log.d(TAG, "onDetached — arrival teardown released")
    }

    private companion object {
        const val TAG = "MapboxArrival"
    }
}
