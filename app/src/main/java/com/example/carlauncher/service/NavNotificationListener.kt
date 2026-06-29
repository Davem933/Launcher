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

class NavNotificationListener : NotificationListenerService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    // ── NotificationListenerService callbacks ─────────────────────────────────

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName !in NAV_PACKAGES) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title    = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()    ?: ""
        val text     = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()     ?: ""
        val subText  = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString() ?: ""

        val (street, distance) = extractStreetAndDistance(title, text, sbn.packageName)
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

        Log.d(TAG, "nav update pkg=${sbn.packageName} street=$street dist=$distance eta=$eta")

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
        Log.d(TAG, "nav cleared pkg=${sbn.packageName}")
        mainHandler.post { NavRepository.clearNavigation() }
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    /**
     * Splits "18:40 · 22 min · 18 km" (or any "·"-delimited summary) into
     * (eta, timeRemaining, distanceRemaining). Returns empty strings on missing parts.
     */
    private fun parseTripSummary(summary: String): Triple<String, String, String> {
        if (summary.isBlank()) return Triple("", "", "")
        val parts = summary.split('·').map { it.trim() }
        return Triple(
            parts.getOrElse(0) { "" },
            parts.getOrElse(1) { "" },
            parts.getOrElse(2) { "" },
        )
    }

    /**
     * Heuristic: whichever of title/text looks like a distance value is the
     * distance field; the other is the street. Falls back to title=street, text=distance.
     */
    private fun extractStreetAndDistance(
        title: String,
        text: String,
        pkg: String,
    ): Pair<String, String> = when {
        looksLikeDistance(text)  -> Pair(title, text)
        looksLikeDistance(title) -> Pair(text, title)
        else                     -> Pair(title, text)
    }

    /** Returns true if the string starts with a digit and contains a distance unit. */
    private fun looksLikeDistance(s: String): Boolean {
        val t = s.trim()
        if (t.isEmpty() || !t[0].isDigit()) return false
        return t.contains("km", ignoreCase = true)
            || t.contains(" m", ignoreCase = true)
            || t.contains("mi", ignoreCase = true)
            || t.contains("ft", ignoreCase = true)
            || t.contains("yd", ignoreCase = true)
    }

    // ── Icon extraction ───────────────────────────────────────────────────────

    private fun extractIcon(notification: Notification, pkg: String): Bitmap? {
        // 1st attempt: large icon carries the turn-arrow bitmap in most nav apps
        try {
            val drawable = notification.getLargeIcon()?.loadDrawable(applicationContext)
            if (drawable != null) return drawableToBitmap(drawable)
        } catch (e: Exception) {
            Log.w(TAG, "getLargeIcon failed: ${e.message}")
        }

        // 2nd attempt: Waze encodes the turn icon in the small icon resource
        try {
            val resId = notification.smallIcon?.resId ?: return null
            val pkgCtx = applicationContext.createPackageContext(pkg, 0)
            val drawable = pkgCtx.getDrawable(resId) ?: return null
            return drawableToBitmap(drawable)
        } catch (e: Exception) {
            Log.w(TAG, "smallIcon fallback failed: ${e.message}")
        }

        return null
    }

    /**
     * Converts any Drawable to a Bitmap.
     * BitmapDrawable: returns the backing bitmap directly (no copy).
     * Everything else: renders onto a new ARGB_8888 canvas.
     */
    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            drawable.bitmap?.let { return it }
        }
        val w = drawable.intrinsicWidth.takeIf  { it > 0 } ?: 96
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bitmap
    }

    // ── Cancel action ─────────────────────────────────────────────────────────

    /**
     * Scans notification actions for a "stop/exit/cancel" label and returns
     * its PendingIntent so the user can terminate navigation from the widget.
     */
    private fun findCancelIntent(notification: Notification): PendingIntent? {
        val actions = notification.actions ?: return null
        return actions.firstOrNull { action ->
            val label = action.title?.toString()?.lowercase() ?: return@firstOrNull false
            CANCEL_KEYWORDS.any { label.contains(it) }
        }?.actionIntent
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "NavListener"

        val NAV_PACKAGES = setOf(
            "com.google.android.apps.maps",
            "com.waze",
        )

        private val CANCEL_KEYWORDS = setOf(
            "stop", "exit", "cancel", "end", "dismiss", "quit", "close",
        )
    }
}
