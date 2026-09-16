package com.example.carlauncher

import android.app.Application
import com.example.carlauncher.data.navigation.MapboxVoiceGuidanceObserver
import com.mapbox.common.MapboxOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CarLauncherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapboxOptions.accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN
        MapboxNavigationApp.setup(
            NavigationOptions.Builder(this).build()
        )
        // Hlas musí přežít teardown MapWidgetu při přepnutí panelu Mapa → Navigace (spec §3),
        // takže visí na app-level navigation lifecycle, ne na kompozici — viz KDoc observeru.
        // Registrace před prvním attach() je v pořádku: onAttached se zavolá, až se nějaký
        // LifecycleOwner (MainActivity přes requireMapboxNavigation) dostane na STARTED.
        MapboxNavigationApp.registerObserver(MapboxVoiceGuidanceObserver(this))
    }
}
