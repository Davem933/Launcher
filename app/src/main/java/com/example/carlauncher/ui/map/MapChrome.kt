package com.example.carlauncher.ui.map

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.carlauncher.ui.theme.CarColors

/**
 * Look of the map panel's own chrome (panel corners, "Hledat" button, "Moje poloha" button,
 * Mapbox light preset), so a launcher style can restyle [MapWidget] without threading
 * parameters through [com.example.carlauncher.ui.launcher.MapNavPanel]. Defaults = Klasický.
 */
data class MapChrome(
    val panelShape: Shape = RoundedCornerShape(24.dp),
    /** Hairline around the rounded panel; `null` = none. */
    val panelBorder: Color? = Color(0xFF2A2C35),
    val searchBg: Color = CarColors.Surface,
    val searchFg: Color = CarColors.Text3,
    val searchIconTint: Color = CarColors.Text3,
    val searchBorder: Color? = CarColors.BorderSoft,
    val searchShape: Shape = RoundedCornerShape(28.dp),
    val searchLabel: String = "Hledat",
    val searchFillWidth: Boolean = false,
    val searchShadow: Dp = 0.dp,
    val locateActiveBg: Color = CarColors.Accent,
    val locateActiveFg: Color = CarColors.Text,
    val locateIdleBg: Color = CarColors.Surface2,
    val locateIdleFg: Color = CarColors.Text,
    val locateShape: Shape = CircleShape,
    /** `null` = Material3 IconButton's own 48dp. */
    val locateSize: Dp? = null,
    /** Gap between the button and the panel's bottom-right corner (0 = flush, design 2b). */
    val locatePadding: Dp = 16.dp,
    /** Mapbox Standard `lightPreset`: "night", "dusk", "dawn" or "day". */
    val lightPreset: String = "night",
    /** Mapbox Standard `theme`: "default", "faded" or "monochrome". */
    val mapTheme: String = "default",
    /** Extra round button drawn left of "Moje poloha" in free drive (design 2c: incident camera). */
    val extraAction: (@Composable () -> Unit)? = null,
)

val LocalMapChrome = staticCompositionLocalOf { MapChrome() }
