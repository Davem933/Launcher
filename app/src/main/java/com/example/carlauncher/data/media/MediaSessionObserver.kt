package com.example.carlauncher.data.media

import android.content.ComponentName
import android.content.Context
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

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            Log.d(TAG, "onMetadataChanged: ${metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)}")
            refreshTrackInfo()
        }
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            Log.d(TAG, "onPlaybackStateChanged: state=${state?.state}")
            refreshTrackInfo()
        }
        override fun onSessionDestroyed() {
            Log.d(TAG, "Session destroyed")
            activeController?.unregisterCallback(this)
            activeController = null
            _currentTrack.value = null
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
                    activeController?.unregisterCallback(controllerCallback)
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
        activeController?.unregisterCallback(controllerCallback)
        activeController = null
        _currentTrack.value = null
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
        activeController?.unregisterCallback(controllerCallback)
        activeController = controllers?.firstOrNull()?.also { ctrl ->
            Log.d(TAG, "Binding to: ${ctrl.packageName}")
            ctrl.registerCallback(controllerCallback, mainHandler)
        }
        if (activeController == null) Log.d(TAG, "No active controller to bind")
        refreshTrackInfo()
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
                position  = position
            )
        }
    }

    fun play()         { activeController?.transportControls?.play() }
    fun pause()        { activeController?.transportControls?.pause() }
    fun skipNext()     { activeController?.transportControls?.skipToNext() }
    fun skipPrevious() { activeController?.transportControls?.skipToPrevious() }
}
