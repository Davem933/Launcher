package com.example.carlauncher

import android.app.Application
import com.mapbox.common.MapboxOptions
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CarLauncherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapboxOptions.accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN
    }
}
