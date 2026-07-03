package com.example.carlauncher.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.data.trip.LiveTripState
import com.example.carlauncher.data.trip.TripEntity
import com.example.carlauncher.ui.theme.CarColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun TripComputerWidget(
    modifier: Modifier = Modifier,
    viewModel: TripViewModel = hiltViewModel()
) {
    val liveTrip by viewModel.liveTrip.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CarColors.Surface)
            .border(1.dp, Color(0xFF2A2C35), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        when (val state = liveTrip) {
            is LiveTripState.Active -> ActiveTrip(state)
            is LiveTripState.Idle   -> IdleTrip(state.lastTrip)
        }
    }
}

@Composable
private fun ActiveTrip(state: LiveTripState.Active) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(8.dp)) { drawCircle(color = CarColors.Go) }
        Text(
            text = "  JÍZDA PROBÍHÁ",
            color = CarColors.Go,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatDuration(state.durationSec),
            color = CarColors.Go,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StatItem(value = "%.1f km".format(state.distanceKm), label = "vzdálenost")
        StatItem(value = "${state.avgSpeedKmh.toInt()} km/h", label = "průměr")
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        StatItem(value = "${state.maxSpeedKmh.toInt()} km/h", label = "maximum")
    }
}

@Composable
private fun IdleTrip(lastTrip: TripEntity?) {
    Text(
        text = if (lastTrip != null) "Poslední jízda" else "Žádná jízda",
        color = CarColors.Text2,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold
    )
    if (lastTrip == null) {
        Spacer(Modifier.height(8.dp))
        Text(text = "Start detekován automaticky", color = CarColors.Text2, fontSize = 10.sp)
        return
    }
    Text(text = relativeDate(lastTrip.startTime), color = CarColors.Text2, fontSize = 10.sp)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StatItem(value = "%.1f km".format(lastTrip.distanceKm), label = "vzdálenost")
        StatItem(value = "${lastTrip.avgSpeedKmh.toInt()} km/h", label = "průměr")
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StatItem(value = formatDuration((lastTrip.endTime - lastTrip.startTime) / 1000), label = "trvání")
        StatItem(value = "${lastTrip.maxSpeedKmh.toInt()} km/h", label = "maximum")
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = CarColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = CarColors.Text2, fontSize = 9.sp)
    }
}

private fun formatDuration(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

private fun relativeDate(epochMs: Long): String {
    val diffMs = System.currentTimeMillis() - epochMs
    return when {
        diffMs < TimeUnit.HOURS.toMillis(1) -> "před méně než hodinou"
        diffMs < TimeUnit.DAYS.toMillis(1)  -> "dnes"
        diffMs < TimeUnit.DAYS.toMillis(2)  -> "včera"
        else -> SimpleDateFormat("d. M. yyyy", Locale("cs")).format(Date(epochMs))
    }
}
