package com.example.carlauncher.service

import android.service.notification.NotificationListenerService
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Required for MediaSessionManager.getActiveSessions().
 * getActiveSessions() demands the ComponentName of a *currently bound* service —
 * not just a permitted one. Track connection state via companion StateFlow so
 * MediaSessionObserver can wait for the real bind event before querying sessions.
 */
class MediaListenerService : NotificationListenerService() {

    companion object {
        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("MediaDebug", "MediaListenerService: CONNECTED")
        _isConnected.value = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d("MediaDebug", "MediaListenerService: DISCONNECTED")
        _isConnected.value = false
    }
}
