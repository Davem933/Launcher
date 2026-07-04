package com.example.carlauncher.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(CarColors.Surface)
            .border(1.dp, CarColors.BorderSoft, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        when (val state = liveTrip) {
            is LiveTripState.Active -> ActiveTrip(state)
            is LiveTripState.Idle   -> IdleTrip(state.lastTrip)
        }
    }
}

@Composable
private fun ColumnScope.ActiveTrip(state: LiveTripState.Active) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(8.dp)) { drawCircle(color = CarColors.Go) }
        Text(
            text = "  JÍZDA PROBÍHÁ",
            color = CarColors.Go,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatDuration(state.durationSec),
            color = CarColors.Go,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
    Spacer(Modifier.height(12.dp))
    StatGrid(
        distanceKm  = state.distanceKm,
        avgSpeedKmh = state.avgSpeedKmh,
        maxSpeedKmh = state.maxSpeedKmh,
        durationSec = state.durationSec,
        modifier    = Modifier.weight(1f)
    )
}

@Composable
private fun ColumnScope.IdleTrip(lastTrip: TripEntity?) {
    if (lastTrip == null) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Žádná jízda", color = CarColors.Text2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(text = "Start detekován automaticky", color = CarColors.Text3, fontSize = 11.sp)
            }
        }
        return
    }
    Text(
        text = "Poslední jízda",
        color = CarColors.Text2,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
    )
    Text(text = relativeDate(lastTrip.startTime), color = CarColors.Text2, fontSize = 11.sp)
    Spacer(Modifier.height(12.dp))
    StatGrid(
        distanceKm  = lastTrip.distanceKm,
        avgSpeedKmh = lastTrip.avgSpeedKmh,
        maxSpeedKmh = lastTrip.maxSpeedKmh,
        durationSec = (lastTrip.endTime - lastTrip.startTime) / 1000,
        modifier    = Modifier.weight(1f)
    )
}

@Composable
private fun StatGrid(
    distanceKm: Float,
    avgSpeedKmh: Float,
    maxSpeedKmh: Float,
    durationSec: Long,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Icons.Filled.Route, "%.1f km".format(distanceKm), "vzdálenost", CarColors.Accent, Modifier.weight(1f).fillMaxHeight())
            StatCard(Icons.Filled.Speed, "${avgSpeedKmh.toInt()} km/h", "průměr", CarColors.Go, Modifier.weight(1f).fillMaxHeight())
        }
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Icons.AutoMirrored.Filled.TrendingUp, "${maxSpeedKmh.toInt()} km/h", "maximum", CarColors.Warn, Modifier.weight(1f).fillMaxHeight())
            StatCard(Icons.Filled.Timer, formatDuration(durationSec), "trvání", CarColors.Text2, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CarColors.Surface2)
            .padding(12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(text = value, color = CarColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = CarColors.Text2, fontSize = 11.sp)
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
