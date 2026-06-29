package com.example.carlauncher.service

import android.app.Notification
import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.carlauncher.data.navigation.NavRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single NotificationListenerService that handles two roles:
 * 1. Media session tracking — isConnected StateFlow for MediaSessionObserver
 * 2. Navigation notification parsing — updates NavRepository for NavWidget
 */
class MediaListenerService : NotificationListenerService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val NAV_PACKAGES = setOf(
            "com.google.android.apps.maps",
            "com.waze",
        )
        private val CANCEL_KEYWORDS = setOf(
            "stop", "exit", "cancel", "end", "dismiss", "quit", "close",
            "ukončit", "zastavit", "zrušit",
        )
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

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

    // ── Notification callbacks ────────────────────────────────────────────────

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName !in NAV_PACKAGES) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title    = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()     ?: ""
        val text     = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()      ?: ""
        val subText  = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()  ?: ""
        val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString() ?: ""

        val (street, distance) = extractStreetAndDistance(title, text)
        val (eta, timeLeft, distLeft) = parseTripSummary(
            when {
                subText.contains('·')  -> subText
                infoText.contains('·') -> infoText
                text.contains('·')     -> text
                else                   -> ""
            }
        )

        val icon         = extractIcon(notification, sbn.packageName)
        val cancelIntent = findCancelIntent(notification)

        Log.d("NavListener", "pkg=${sbn.packageName} street=$street dist=$distance eta=$eta")

        mainHandler.post {
            NavRepository.updateNavigation(
                maneuverStreet    = street,
                maneuverDistance  = distance,
                maneuverIcon      = icon,
                eta               = eta,
                timeRemaining     = timeLeft,
                distanceRemaining = distLeft,
                cancelIntent      = cancelIntent,
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName !in NAV_PACKAGES) return
        Log.d("NavListener", "nav cleared: ${sbn.packageName}")
        mainHandler.post { NavRepository.clearNavigation() }
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    private fun parseTripSummary(summary: String): Triple<String, String, String> {
        if (summary.isBlank()) return Triple("", "", "")
        val parts = summary.split('·').map { it.trim() }
        return Triple(
            parts.getOrElse(0) { "" },
            parts.getOrElse(1) { "" },
            parts.getOrElse(2) { "" },
        )
    }

    private fun extractStreetAndDistance(title: String, text: String): Pair<String, String> = when {
        looksLikeDistance(text)  -> Pair(title, text)
        looksLikeDistance(title) -> Pair(text, title)
        else                     -> Pair(title, text)
    }

    private fun looksLikeDistance(s: String): Boolean {
        val t = s.trim()
        if (t.isEmpty() || !t[0].isDigit()) return false
        return t.contains("km", ignoreCase = true)
            || t.contains(" m", ignoreCase = true)
            || t.contains("mi", ignoreCase = true)
            || t.contains("ft", ignoreCase = true)
    }

    // ── Icon extraction ───────────────────────────────────────────────────────

    private fun extractIcon(notification: Notification, pkg: String): Bitmap? {
        try {
            val d = notification.getLargeIcon()?.loadDrawable(applicationContext)
            if (d != null) return drawableToBitmap(d)
        } catch (e: Exception) {
            Log.w("NavListener", "getLargeIcon failed: ${e.message}")
        }
        try {
            val resId  = notification.smallIcon?.resId ?: return null
            val pkgCtx = applicationContext.createPackageContext(pkg, 0)
            val d      = pkgCtx.getDrawable(resId) ?: return null
            return drawableToBitmap(d)
        } catch (e: Exception) {
            Log.w("NavListener", "smallIcon fallback failed: ${e.message}")
        }
        return null
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) drawable.bitmap?.let { return it }
        val w = drawable.intrinsicWidth.takeIf  { it > 0 } ?: 96
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bitmap
    }

    // ── Cancel intent ─────────────────────────────────────────────────────────

    private fun findCancelIntent(notification: Notification): PendingIntent? =
        notification.actions?.firstOrNull { action ->
            val label = action.title?.toString()?.lowercase() ?: return@firstOrNull false
            CANCEL_KEYWORDS.any { label.contains(it) }
        }?.actionIntent
}
