package com.example.carlauncher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

// Layout je navržený pro Lenovo Tab M10 Plus (~1143×686 dp). Na jiných tabletech se celé UI
// přeškáluje přepsáním LocalDensity, takže vypadá stejně a jen se úměrně zvětší/zmenší.
const val REFERENCE_WIDTH_DP = 1143f
const val REFERENCE_HEIGHT_DP = 686f
const val MIN_SCALE = 0.85f   // pod tím by touch targety (min 48dp) klesly příliš
const val MAX_SCALE = 1.6f    // pojistka proti absurdním displejům
private const val SNAP_TOLERANCE = 0.03f

fun computeScale(widthDp: Float, heightDp: Float): Float {
    val raw = minOf(widthDp / REFERENCE_WIDTH_DP, heightDp / REFERENCE_HEIGHT_DP)
    val clamped = raw.coerceIn(MIN_SCALE, MAX_SCALE)
    // M10 s mírně jinými reálnými dp zůstává na přesně 1.0 (žádná viditelná změna)
    return if (kotlin.math.abs(clamped - 1f) <= SNAP_TOLERANCE) 1f else clamped
}

@Composable
fun DeviceScaleProvider(content: @Composable () -> Unit) {
    val config = LocalConfiguration.current
    val base = LocalDensity.current
    val scale = computeScale(config.screenWidthDp.toFloat(), config.screenHeightDp.toFloat())
    if (scale == 1f) {
        content()
        return
    }
    // fontScale se NEMĚNÍ: sp se počítá jako sp × fontScale × density, takže písmo
    // se přeškáluje samo přes density (násobení fontScale by škálovalo dvakrát).
    CompositionLocalProvider(
        LocalDensity provides Density(base.density * scale, base.fontScale)
    ) {
        content()
    }
}
