package com.example.carlauncher.data.navigation

import android.content.Context
import android.util.Log
import com.mapbox.bindgen.Expected
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.trip.session.VoiceInstructionsObserver
import com.mapbox.navigation.ui.base.util.MapboxNavigationConsumer
import com.mapbox.navigation.voice.api.MapboxSpeechApi
import com.mapbox.navigation.voice.api.MapboxVoiceInstructionsPlayer
import com.mapbox.navigation.voice.model.SpeechAnnouncement
import com.mapbox.navigation.voice.model.SpeechError
import com.mapbox.navigation.voice.model.SpeechValue
import java.util.Locale

/**
 * Hlasové pokyny Mapbox navigace, držené MIMO kompozici `MapWidget`u.
 *
 * Proč: `MapWidget` se při přepnutí panelu Mapa → Navigace kompletně disposuje (oprava leaku
 * z Fáze 2), takže cokoliv v jeho `remember`/`DisposableEffect` scope hlas během jízdy utne.
 * Spec §3 přitom výslovně vyžaduje, že aktivní navigace **včetně hlasu** přepnutí panelu
 * přežije. Registruje se proto jednou v [com.example.carlauncher.CarLauncherApp.onCreate] přes
 * [MapboxNavigationApp.registerObserver] — to je přesně SDK mechanismus pro "přežít teardown UI,
 * viset na app-level navigation lifecycle".
 *
 * `onAttached`/`onDetached` jsou párové a mohou proběhnout VÍCEKRÁT za život procesu: SDK volá
 * `onDetached`, jakmile všichni attachnutí `LifecycleOwner`i klesnou pod `STARTED` (tj. i při
 * běžném odejití appky do pozadí), a `onAttached` znovu při návratu. Proto se `MapboxSpeechApi`
 * i `MapboxVoiceInstructionsPlayer` vytvářejí až v `onAttached` a v `onDetached` zahazují —
 * `MapboxVoiceInstructionsPlayer.shutdown()` je nevratný (nastaví interní `isShutDown`, po kterém
 * `play()` už jen loguje a nic nepřehraje, ověřeno ve zdrojáku v3.30.1), takže jedna instance
 * držená v konstruktoru by po prvním přepnutí do pozadí navždy oněměla.
 *
 * Jazyk je locale zařízení, ne natvrdo Locale.US — `MapWidget` posílá route requesty přes
 * `applyLanguageAndVoiceUnitOptions(context)`, takže instrukce chodí v jazyce zařízení a
 * syntéza/TTS to musí respektovat, jinak by je četla anglickou výslovností.
 */
class MapboxVoiceGuidanceObserver(context: Context) : MapboxNavigationObserver {

    private val appContext: Context = context.applicationContext

    private var speechApi: MapboxSpeechApi? = null
    private var voiceInstructionsPlayer: MapboxVoiceInstructionsPlayer? = null

    /** Uvolní stažené mp3, jakmile dohraje. */
    private val playerCallback = MapboxNavigationConsumer<SpeechAnnouncement> { announcement ->
        speechApi?.clean(announcement)
    }

    /**
     * Přehraje syntetizované mp3, nebo — když ho `speechApi` nedokázal vyrobit/stáhnout (bez
     * signálu, chyba serveru) — fallback přes on-device TTS. Kterou větev použít rozhoduje SDK
     * samo, tohle jen oba výsledky posílá do stejného přehrávače.
     */
    private val speechCallback =
        MapboxNavigationConsumer<Expected<SpeechError, SpeechValue>> { expected ->
            voiceInstructionsPlayer?.let { player ->
                expected.fold(
                    { error -> player.play(error.fallback, playerCallback) },
                    { value -> player.play(value.announcement, playerCallback) },
                )
            }
        }

    /**
     * Registrováno bez podmínky na "běží trip session" — SDK `VoiceInstructionsObserver` stejně
     * volá jen během aktivní navigace (hlasové pokyny se odvozují z průběhu po trase), takže při
     * volné jízdě není co spustit bez ohledu na stav registrace.
     */
    private val voiceInstructionsObserver = VoiceInstructionsObserver { voiceInstructions ->
        Log.d(TAG, "voice instruction: ${voiceInstructions.announcement()}")
        speechApi?.generate(voiceInstructions, speechCallback)
    }

    override fun onAttached(mapboxNavigation: MapboxNavigation) {
        val language = Locale.getDefault().language
        speechApi = MapboxSpeechApi(appContext, language)
        voiceInstructionsPlayer = MapboxVoiceInstructionsPlayer(appContext, language)
        mapboxNavigation.registerVoiceInstructionsObserver(voiceInstructionsObserver)
        Log.d(TAG, "onAttached — voice pipeline up (language=$language)")
    }

    override fun onDetached(mapboxNavigation: MapboxNavigation) {
        mapboxNavigation.unregisterVoiceInstructionsObserver(voiceInstructionsObserver)
        speechApi?.cancel()
        speechApi = null
        voiceInstructionsPlayer?.shutdown()
        voiceInstructionsPlayer = null
        Log.d(TAG, "onDetached — voice pipeline released")
    }

    private companion object {
        const val TAG = "MapboxVoice"
    }
}
