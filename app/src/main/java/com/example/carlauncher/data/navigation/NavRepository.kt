package com.example.carlauncher.data.navigation

import android.app.PendingIntent
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Thread-safe singleton nav state. All mutations must arrive on the main thread
 * (MediaListenerService dispatches via mainHandler.post) so Compose snapshot
 * reads are always consistent.
 */
object NavRepository {

    var maneuverDistance by mutableStateOf("")
        private set

    var maneuverStreet by mutableStateOf("")
        private set

    var maneuverIcon by mutableStateOf<Bitmap?>(null)
        private set

    var eta by mutableStateOf("")
        private set

    var timeRemaining by mutableStateOf("")
        private set

    var distanceRemaining by mutableStateOf("")
        private set

    var cancelIntent by mutableStateOf<PendingIntent?>(null)
        private set

    // Tracks the largest distance seen since nav started — used as total route length.
    private var totalDistanceMeters: Float = 0f

    /** 0f–1f: fraction of route already driven. 0 until we have a reference total. */
    var progressFraction by mutableStateOf(0f)
        private set

    val isActive: Boolean
        get() = maneuverStreet.isNotEmpty() || maneuverDistance.isNotEmpty()

    fun updateNavigation(
        maneuverDistance: String      = this.maneuverDistance,
        maneuverStreet: String        = this.maneuverStreet,
        maneuverIcon: Bitmap?         = this.maneuverIcon,
        eta: String                   = this.eta,
        timeRemaining: String         = this.timeRemaining,
        distanceRemaining: String     = this.distanceRemaining,
        cancelIntent: PendingIntent?  = this.cancelIntent,
    ) {
        this.maneuverDistance  = maneuverDistance
        this.maneuverStreet    = maneuverStreet
        this.maneuverIcon      = maneuverIcon
        this.eta               = eta
        this.timeRemaining     = timeRemaining
        this.distanceRemaining = distanceRemaining
        this.cancelIntent      = cancelIntent

        val remainingM = parseDistanceToMeters(distanceRemaining)
        if (remainingM > totalDistanceMeters) totalDistanceMeters = remainingM
        progressFraction = if (totalDistanceMeters > 0f)
            ((totalDistanceMeters - remainingM) / totalDistanceMeters).coerceIn(0f, 1f)
        else 0f
    }

    fun clearNavigation() {
        maneuverDistance    = ""
        maneuverStreet      = ""
        maneuverIcon        = null
        eta                 = ""
        timeRemaining       = ""
        distanceRemaining   = ""
        cancelIntent        = null
        totalDistanceMeters = 0f
        progressFraction    = 0f
    }

    /** Parses "8.4 km", "800 m", "1,2 km" etc. → metres. Returns 0 on failure. */
    private fun parseDistanceToMeters(s: String): Float {
        val clean = s.trim().replace(',', '.')
        return when {
            clean.contains("km", ignoreCase = true) ->
                clean.replace(Regex("[^0-9.]"), "").toFloatOrNull()?.times(1000f) ?: 0f
            clean.contains("mi", ignoreCase = true) ->
                clean.replace(Regex("[^0-9.]"), "").toFloatOrNull()?.times(1609f) ?: 0f
            clean.contains("ft", ignoreCase = true) ->
                clean.replace(Regex("[^0-9.]"), "").toFloatOrNull()?.times(0.305f) ?: 0f
            clean.any { it.isDigit() } ->
                clean.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: 0f
            else -> 0f
        }
    }
}
