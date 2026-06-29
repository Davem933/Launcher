package com.example.carlauncher.data.navigation

import android.app.PendingIntent
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Thread-safe singleton nav state. All mutations must arrive on the main thread
 * (NavNotificationListener dispatches via mainHandler.post) so Compose snapshot
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
    }

    fun clearNavigation() {
        maneuverDistance  = ""
        maneuverStreet    = ""
        maneuverIcon      = null
        eta               = ""
        timeRemaining     = ""
        distanceRemaining = ""
        cancelIntent      = null
    }
}
