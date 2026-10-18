package com.example.carlauncher.ui.dock

import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.ui.launcher.WeatherCalendarViewModel
import com.example.carlauncher.ui.launcher.wmoIcon
import com.example.carlauncher.ui.launcher.wmoIconColor
import com.example.carlauncher.ui.theme.CarColors
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// hiltViewModel() without a key resolves to the same Activity-scoped instance
// WeatherCalendarWidget already created — reusing it here avoids a second
// WeatherRepository polling loop for the same data.
@Composable
fun DockStatusCluster(
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    weatherViewModel: WeatherCalendarViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val weather by weatherViewModel.weather.collectAsStateWithLifecycle()

    var now by remember { mutableStateOf(LocalTime.now()) }
    var batteryPct by remember { mutableIntStateOf(readBatteryPercent(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            batteryPct = readBatteryPercent(context)
            delay(30_000L)
        }
    }

    val batteryItem: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = batteryIcon(batteryPct),
                contentDescription = null,
                tint = CarColors.Text2,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "$batteryPct %",
                color = CarColors.Text2,
                fontSize = 13.sp,
            )
        }
    }

    val weatherItem: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = wmoIcon(weather?.code),
                contentDescription = null,
                tint = wmoIconColor(weather?.code),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = weather?.let { "${it.tempC}°" } ?: "...",
                color = CarColors.Text2,
                fontSize = 13.sp,
            )
        }
    }

    Column(
        modifier = modifier,
        // Horizontal dock hugs the right edge; the narrow vertical dock centers it.
        horizontalAlignment = if (vertical) Alignment.CenterHorizontally else Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = now.format(timeFormatter),
            color = CarColors.Text,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )

        if (vertical) {
            batteryItem()
            weatherItem()
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                batteryItem()
                weatherItem()
            }
        }
    }
}

private fun batteryIcon(pct: Int): ImageVector = when {
    pct >= 95 -> Icons.Default.BatteryFull
    pct >= 80 -> Icons.Default.Battery6Bar
    pct >= 60 -> Icons.Default.Battery5Bar
    pct >= 45 -> Icons.Default.Battery4Bar
    pct >= 30 -> Icons.Default.Battery3Bar
    pct >= 15 -> Icons.Default.Battery2Bar
    pct >= 5  -> Icons.Default.Battery1Bar
    else      -> Icons.Default.BatteryAlert
}

private fun readBatteryPercent(context: Context): Int {
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
}

// ── Aurora ────────────────────────────────────────────────────────────────────

private val AuroraStatusText = Color(0xFFF5F5F7)

/**
 * Aurora-style status: clock over iOS-like signal + battery glyphs. The signal bars are
 * decorative (the design's status-bar look, no telephony/network permission involved);
 * the battery glyph's fill tracks the real charge level.
 */
@Composable
fun AuroraDockStatus(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalTime.now()) }
    var batteryPct by remember { mutableIntStateOf(readBatteryPercent(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            batteryPct = readBatteryPercent(context)
            delay(30_000L)
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = now.format(timeFormatter),
            color = AuroraStatusText,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            SignalBarsGlyph(Modifier.size(width = 15.dp, height = 10.dp))
            BatteryGlyph(batteryPct, Modifier.size(width = 22.dp, height = 10.dp))
        }
    }
}

@Composable
private fun SignalBarsGlyph(modifier: Modifier) {
    Canvas(modifier) {
        val bars = 4
        val gap = size.width * 0.12f
        val barW = (size.width - gap * (bars - 1)) / bars
        repeat(bars) { i ->
            val h = size.height * (0.4f + 0.2f * i)
            drawRoundRect(
                color = AuroraStatusText,
                topLeft = Offset(i * (barW + gap), size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 3f),
            )
        }
    }
}

@Composable
private fun BatteryGlyph(pct: Int, modifier: Modifier) {
    Canvas(modifier) {
        val capW = size.width * 0.08f
        val bodyW = size.width - capW - 1.dp.toPx()
        val stroke = 1.dp.toPx()
        val radius = CornerRadius(size.height * 0.28f)
        drawRoundRect(
            color = AuroraStatusText.copy(alpha = 0.4f),
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(bodyW - stroke, size.height - stroke),
            cornerRadius = radius,
            style = Stroke(stroke),
        )
        drawRoundRect(
            color = AuroraStatusText.copy(alpha = 0.4f),
            topLeft = Offset(size.width - capW, size.height * 0.33f),
            size = Size(capW, size.height * 0.34f),
            cornerRadius = CornerRadius(capW / 2),
        )
        val inset = stroke * 2
        val fillMax = bodyW - inset * 2
        drawRoundRect(
            color = if (pct < 20) Color(0xFFFF453A) else AuroraStatusText,
            topLeft = Offset(inset, inset),
            size = Size(fillMax * pct.coerceIn(0, 100) / 100f, size.height - inset * 2),
            cornerRadius = CornerRadius(size.height * 0.18f),
        )
    }
}
