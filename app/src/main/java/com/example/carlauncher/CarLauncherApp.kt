package com.example.carlauncher

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.carlauncher.data.navigation.MapboxArrivalTeardownObserver
import com.example.carlauncher.data.navigation.MapboxVoiceGuidanceObserver
import com.example.carlauncher.data.notes.VoiceNoteRepository
import com.mapbox.common.MapboxOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CarLauncherApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var voiceNoteRepository: VoiceNoteRepository

    // Výchozí initializer je v manifestu vypnutý — WorkManager se inicializuje na vyžádání
    // s HiltWorkerFactory, aby ProcessNoteWorker dostal NoteProcessor přes Hilt.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

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
        // Ukončení navigace po dojezdu musí přežít teardown MapWidgetu ze stejného důvodu —
        // a navíc Mapbox si dojezd pro danou trasu latchuje a vystřelí ho jen jednou, takže
        // observer registrovaný až po přepnutí zpět na Mapu už nic nedostane. Viz KDoc.
        MapboxNavigationApp.registerObserver(MapboxArrivalTeardownObserver())
        voiceNoteRepository.cleanupOnStartup()
    }
}
