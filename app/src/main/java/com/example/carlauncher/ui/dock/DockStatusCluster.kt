package com.example.carlauncher.ui.dock

import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
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

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = batteryIcon(batteryPct),
                contentDescription = null,
                tint = CarColors.Text2,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "$batteryPct %",
                color = CarColors.Text2,
                fontSize = 14.sp,
            )
        }

        Text(
            text = now.format(timeFormatter),
            color = CarColors.Text,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = wmoIcon(weather?.code),
                contentDescription = null,
                tint = wmoIconColor(weather?.code),
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = weather?.let { "${it.tempC}°" } ?: "...",
                color = CarColors.Text2,
                fontSize = 14.sp,
            )
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
