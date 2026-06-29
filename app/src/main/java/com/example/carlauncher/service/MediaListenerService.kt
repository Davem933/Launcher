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
            "cz.seznam.mapy",
        )
        // Apps whose icons are always the app logo, not a turn arrow
        private val LOGO_ONLY_ICON_PACKAGES = setOf(
            "com.waze",
            "cz.seznam.mapy",
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

        // ── RAW extras dump — helps detect what each nav app actually sends ──────
        Log.d("NavRaw", "=== pkg=${sbn.packageName} ===")
        Log.d("NavRaw", "TITLE   ='$title'")
        Log.d("NavRaw", "TEXT    ='$text'")
        Log.d("NavRaw", "SUBTEXT ='$subText'")
        Log.d("NavRaw", "INFOTEXT='$infoText'")

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

        Log.d("NavListener", "→ street='$street' dist='$distance' eta='$eta' timeLeft='$timeLeft' distLeft='$distLeft'")

        // Skip idle/logo-only notifications (e.g. Waze showing its icon when not navigating).
        // Real navigation has at least a distance or a cancel action.
        val hasNavContent = distance.isNotEmpty() || distLeft.isNotEmpty() || cancelIntent != null
        if (!hasNavContent) {
            Log.d("NavListener", "skip — no nav content (idle notification)")
            return
        }

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

    /**
     * Content-based trip summary parsing — identifies each part by what it looks like,
     * not by position. Google Maps Czech sends: "1 h 2 min · 62 km · Příjezd: 15:05"
     * or just two parts. Order can vary between app versions.
     */
    private fun parseTripSummary(summary: String): Triple<String, String, String> {
        if (summary.isBlank()) return Triple("", "", "")
        val parts = summary.split('·').map { it.trim() }.filter { it.isNotEmpty() }
        var arrival  = ""   // contains ":" → arrival time  e.g. "Příjezd: 15:05"
        var timeLeft = ""   // contains "min" or "h "       e.g. "1 h 2 min"
        var distLeft = ""   // contains "km" or ends in " m" e.g. "62 km"
        for (part in parts) {
            when {
                arrival.isEmpty()  && part.contains(':') -> arrival  = part
                timeLeft.isEmpty() && looksLikeDuration(part) -> timeLeft = part
                distLeft.isEmpty() && looksLikeDistance(part) -> distLeft = part
            }
        }
        // fallback: if only one part and nothing matched, treat as arrival
        if (arrival.isEmpty() && timeLeft.isEmpty() && distLeft.isEmpty() && parts.isNotEmpty())
            arrival = parts[0]
        return Triple(arrival, timeLeft, distLeft)
    }

    /**
     * Google Maps Czech: EXTRA_TITLE = "za 600 m" (with "za " prefix), EXTRA_TEXT = instruction.
     * Strip the "za " prefix before storing — UI adds it back.
     */
    private fun extractStreetAndDistance(title: String, text: String): Pair<String, String> {
        // Normalize non-breaking spaces ( ) before any pattern matching
        val titleT = title.trim().replace(' ', ' ')
        val textT  = text.trim().replace(' ', ' ')
        // "za X m" / "za X km" pattern — Google Maps sends it in title, Waze in text
        val zaPrefix = Regex("^za\\s+", RegexOption.IGNORE_CASE)
        if (zaPrefix.containsMatchIn(titleT)) {
            // Google Maps: title="za 200 m", text="směr Klenovecká"
            return Pair(textT, zaPrefix.replace(titleT, ""))
        }
        if (zaPrefix.containsMatchIn(textT)) {
            // Waze: title="Odbočte vpravo", text="za 200 m"
            return Pair(titleT, zaPrefix.replace(textT, ""))
        }
        return when {
            looksLikeDistance(textT)  -> Pair(titleT, textT)
            looksLikeDistance(titleT) -> Pair(textT, titleT)
            else                      -> Pair(titleT, "")   // neither is a distance
        }
    }

    private fun looksLikeDistance(s: String): Boolean {
        if (s.isEmpty() || !s[0].isDigit()) return false
        // Normalize non-breaking space ( ) used by Google Maps e.g. "200 m"
        val n = s.replace(' ', ' ')
        return n.contains("km", ignoreCase = true)
            || n.contains(" m", ignoreCase = true)
            || n.contains("mi", ignoreCase = true)
            || n.contains("ft", ignoreCase = true)
    }

    private fun looksLikeDuration(s: String): Boolean =
        s.contains("min", ignoreCase = true) ||
        (s.contains(" h ", ignoreCase = true) && !s.contains("km", ignoreCase = true))

    // ── Icon extraction ───────────────────────────────────────────────────────

    private fun extractIcon(notification: Notification, pkg: String): Bitmap? {
        // Some apps (Waze, Mapy.cz) only send their app logo as icon, not a turn arrow.
        // Return null → NavWidget shows the default Navigation arrow instead.
        if (pkg in LOGO_ONLY_ICON_PACKAGES) return null

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
