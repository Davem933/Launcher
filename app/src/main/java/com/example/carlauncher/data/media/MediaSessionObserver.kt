package com.example.carlauncher.data.media

import android.content.ComponentName
import android.content.Context
import android.view.KeyEvent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log
import com.example.carlauncher.data.model.TrackInfo
import com.example.carlauncher.service.MediaListenerService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MediaDebug"

// Navigation apps that expose a media session but are not music players
private val NAV_PACKAGES = setOf(
    "com.google.android.apps.maps",
    "cz.seznam.mapy",
    "com.waze",
    "com.sygic.aura",
    "com.here.app.maps"
)

@Singleton
class MediaSessionObserver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listenerComponent = ComponentName(context, MediaListenerService::class.java)

    private val _currentTrack = MutableStateFlow<TrackInfo?>(null)
    val currentTrack: StateFlow<TrackInfo?> = _currentTrack.asStateFlow()

    private var activeController: MediaController? = null
    private var started = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var connectionJob: Job? = null

    val currentPositionMs: Long
        get() {
            val state = activeController?.playbackState ?: return 0L
            if (state.state != PlaybackState.STATE_PLAYING) return state.position
            val elapsed = android.os.SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            return (state.position + elapsed * state.playbackSpeed).toLong()
                .coerceAtMost(_currentTrack.value?.duration ?: Long.MAX_VALUE)
        }

    private val sessionChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            Log.d(TAG, "OnActiveSessionsChanged: ${controllers?.size ?: 0} sessions")
            controllers?.forEach { Log.d(TAG, "  session pkg: ${it.packageName}") }
            bindToFirst(controllers)
        }

    // Every non-navigation session gets the callback, so a session that starts playing
    // can take over from a paused one.
    private var observed: List<MediaController> = emptyList()
    private var lastPackage: String? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            Log.d(TAG, "onMetadataChanged: ${metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)}")
            selectActive()
        }
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            Log.d(TAG, "onPlaybackStateChanged: state=${state?.state}")
            selectActive()
        }
        override fun onSessionDestroyed() {
            Log.d(TAG, "Session destroyed")
            selectActive()
        }
    }

    fun start() {
        if (started) return
        started = true
        Log.d(TAG, "start() — requesting rebind, waiting for service connect")

        // Prompt system to bind the service (no-op if already bound)
        NotificationListenerService.requestRebind(listenerComponent)

        // Wait for the NotificationListenerService to actually be bound before
        // calling getActiveSessions() — Android 13+ requires it to be actively connected
        connectionJob = scope.launch {
            MediaListenerService.isConnected.collect { connected ->
                Log.d(TAG, "Service connected=$connected")
                if (connected) {
                    querySessions()
                } else {
                    try {
                        sessionManager.removeOnActiveSessionsChangedListener(sessionChangedListener)
                    } catch (_: Exception) {}
                    unregisterObserved()
                    activeController = null
                    _currentTrack.value = null
                }
            }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        connectionJob?.cancel()
        connectionJob = null
        try { sessionManager.removeOnActiveSessionsChangedListener(sessionChangedListener) }
        catch (_: Exception) {}
        unregisterObserved()
        activeController = null
        _currentTrack.value = null
    }

    private fun unregisterObserved() {
        observed.forEach { it.unregisterCallback(controllerCallback) }
        observed = emptyList()
    }

    private fun querySessions() {
        // Remove stale listener first (idempotent)
        try { sessionManager.removeOnActiveSessionsChangedListener(sessionChangedListener) }
        catch (_: Exception) {}

        try {
            val sessions = sessionManager.getActiveSessions(listenerComponent)
            Log.d(TAG, "querySessions(): ${sessions.size} active session(s)")
            sessions.forEachIndexed { i, ctrl ->
                Log.d(TAG, "  [$i] pkg=${ctrl.packageName} state=${ctrl.playbackState?.state}")
            }
            bindToFirst(sessions)
            sessionManager.addOnActiveSessionsChangedListener(
                sessionChangedListener, listenerComponent, mainHandler
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException — notification access may not be granted: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "querySessions() failed: ${e.message}", e)
        }
    }

    private fun bindToFirst(controllers: List<MediaController>?) {
        unregisterObserved()
        observed = controllers.orEmpty().filter { it.packageName !in NAV_PACKAGES }
        observed.forEach { it.registerCallback(controllerCallback, mainHandler) }
        selectActive()
    }

    // Prefer a playing session, then the one we already follow, then the first in the list.
    private fun selectActive() {
        val chosen = observed.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: observed.firstOrNull { it.packageName == activeController?.packageName }
            ?: observed.firstOrNull()
        if (chosen?.packageName != activeController?.packageName) {
            Log.d(TAG, "Binding to: ${chosen?.packageName}")
        }
        activeController = chosen
        chosen?.packageName?.let { lastPackage = it }
        refreshTrackInfo()
    }

    private fun appLabel(pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { "" }

    /** Opens the player that is (or was last) active; false if none is known/launchable. */
    fun launchPlayer(): Boolean {
        val pkg = activeController?.packageName ?: lastPackage ?: return false
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { context.startActivity(intent); true } catch (_: Exception) { false }
    }

    private fun refreshTrackInfo() {
        val ctrl = activeController
        if (ctrl == null) { _currentTrack.value = null; return }
        val meta = ctrl.metadata
        if (meta == null) {
            Log.d(TAG, "refreshTrackInfo: metadata is null for ${ctrl.packageName}")
            _currentTrack.value = null
            return
        }
        val state   = ctrl.playbackState
        val title   = meta.getString(MediaMetadata.METADATA_KEY_TITLE)
        Log.d(TAG, "refreshTrackInfo: title=$title isPlaying=${state?.state == PlaybackState.STATE_PLAYING}")

        if (title.isNullOrEmpty()) { _currentTrack.value = null; return }

        // Snapshot lightweight fields on Main thread before switching dispatcher
        val artist    = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        val duration  = meta.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 } ?: 0L
        val position  = state?.position ?: 0L

        // getBitmap() can decode a JPEG/PNG — move off Main thread to avoid jank
        scope.launch {
            val art = withContext(Dispatchers.Default) {
                try {
                    meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
                        ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                } catch (e: Exception) {
                    Log.w(TAG, "Artwork decode failed: ${e.message}")
                    null
                }
            }
            _currentTrack.value = TrackInfo(
                title     = title,
                artist    = artist,
                albumArt  = art,
                isPlaying = isPlaying,
                duration  = duration,
                position  = position,
                packageName = ctrl.packageName,
                appName   = appLabel(ctrl.packageName)
            )
        }
    }

    // With no bound session the widget still shows its controls — route the press through
    // the system media-button path so it reaches whichever player was last active.
    fun play()         { activeController?.transportControls?.play()           ?: sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY) }
    fun pause()        { activeController?.transportControls?.pause()          ?: sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE) }
    fun skipNext()     { activeController?.transportControls?.skipToNext()     ?: sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }
    fun skipPrevious() { activeController?.transportControls?.skipToPrevious() ?: sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }

    private fun sendMediaKey(keyCode: Int) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
}
