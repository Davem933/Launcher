package com.example.carlauncher.ui.launcher

import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.ui.theme.CarColors
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormatter = DateTimeFormatter.ofPattern("EEEE d. MMMM", Locale("cs", "CZ"))

@Composable
fun StatusBar(
    gpsFix: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var batteryPct by remember { mutableIntStateOf(readBatteryPercent(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            batteryPct = readBatteryPercent(context)
            delay(30_000L)
        }
    }

    Row(
        modifier = modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Time + date ───────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = now.format(timeFormatter),
                style = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CarColors.Text,
                    fontFeatureSettings = "tnum"
                )
            )
            Text(
                text = now.format(dateFormatter),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CarColors.Text3
                ),
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // ── System indicators ─────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    imageVector = Icons.Default.BatteryFull,
                    contentDescription = null,
                    tint = CarColors.Text2,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "$batteryPct %",
                    style = TextStyle(fontSize = 14.sp, color = CarColors.Text2)
                )
            }

            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                tint = CarColors.Text2,
                modifier = Modifier.size(18.dp)
            )

            // GPS status dot — green = fix, red = no signal
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (gpsFix) CarColors.Go else CarColors.Danger, CircleShape)
                )
                Text(
                    text = "GPS",
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CarColors.Text3)
                )
            }
        }
    }
}

private fun readBatteryPercent(context: Context): Int {
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
}
