package com.example.carlauncher.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.data.trip.TripEntity
import com.example.carlauncher.ui.theme.CarColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun JizdniDenikWidget(
    modifier: Modifier = Modifier,
    onOpenHistory: () -> Unit = {},
    viewModel: TripViewModel = hiltViewModel()
) {
    val context   = LocalContext.current
    val lastTrips by viewModel.lastTrips.collectAsStateWithLifecycle()
    val weekTrips by viewModel.tripsThisWeek.collectAsStateWithLifecycle()

    val weekKm      = weekTrips.sumOf { it.distanceKm.toDouble() }.toFloat()
    val weekCount   = weekTrips.size
    val recentTrips = lastTrips.take(4)
    val distances   = remember(weekTrips) { weeklyDistances(weekTrips) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(CarColors.Surface)
            .border(1.dp, CarColors.BorderSoft, RoundedCornerShape(16.dp))
            .clickable { onOpenHistory() }
            .padding(14.dp)
    ) {
        Text(text = "Tento týden", color = CarColors.Text2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = "%.0f km  ·  %d jízd".format(weekKm, weekCount),
            color = CarColors.Text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(10.dp))
        WeekBarChart(distances = distances, modifier = Modifier.weight(1f))

        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = CarColors.BorderSoft)

        Column(modifier = Modifier.weight(1f)) {
            if (recentTrips.isEmpty()) {
                Text(text = "Zatím žádné jízdy", color = CarColors.Text2, fontSize = 11.sp)
            } else {
                recentTrips.forEach { trip -> RecentTripRow(trip) }
            }
        }

        Button(
            onClick = { viewModel.exportCsv(context) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CarColors.Surface3),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(text = "Exportovat CSV", color = CarColors.Text, fontSize = 12.sp)
        }
    }
}

@Composable
private fun WeekBarChart(distances: FloatArray, modifier: Modifier = Modifier) {
    val maxDist  = remember(distances) { distances.maxOrNull()?.takeIf { it > 0f } ?: 1f }
    val todayIdx = remember { todayMondayIndex() }
    val dayLabels = listOf("Po", "Út", "St", "Čt", "Pá", "So", "Ne")
    val accent  = CarColors.Accent
    val neutral = CarColors.Surface3

    Column(modifier = modifier) {
        Text(
            text = "KM PO DNECH",
            color = CarColors.Text3,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.08.em,
        )
        Spacer(Modifier.height(8.dp))
        Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val barCount  = distances.size
            val gap       = 6.dp.toPx()
            val barWidth  = (size.width - gap * (barCount - 1)) / barCount
            distances.forEachIndexed { i, dist ->
                val fraction   = (dist / maxDist).coerceIn(0f, 1f)
                val barHeight  = (size.height * fraction).coerceAtLeast(3.dp.toPx())
                val x          = i * (barWidth + gap)
                drawRoundRect(
                    color      = if (i == todayIdx) accent else neutral,
                    topLeft    = Offset(x, size.height - barHeight),
                    size       = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            dayLabels.forEachIndexed { i, label ->
                Text(
                    text = label,
                    color = if (i == todayIdx) CarColors.Accent else CarColors.Text3,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Index 0=Po..6=Ne, summed distanceKm per weekday for the given trips. */
private fun weeklyDistances(trips: List<TripEntity>): FloatArray {
    val result = FloatArray(7)
    trips.forEach { trip ->
        val cal = Calendar.getInstance().apply { timeInMillis = trip.startTime }
        val dow = cal.get(Calendar.DAY_OF_WEEK) // Sunday=1..Saturday=7
        val idx = (dow + 5) % 7                 // remap to Monday=0..Sunday=6
        result[idx] += trip.distanceKm
    }
    return result
}

private fun todayMondayIndex(): Int {
    val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    return (dow + 5) % 7
}

@Composable
private fun RecentTripRow(trip: TripEntity) {
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dateLabel = when {
        isToday(trip.startTime)     -> "Dnes"
        isYesterday(trip.startTime) -> "Včera"
        else -> SimpleDateFormat("d. M.", Locale("cs")).format(Date(trip.startTime))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CarColors.Surface3),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = CarColors.Text2,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$dateLabel ${timeFmt.format(Date(trip.startTime))}",
                color = CarColors.Text2,
                fontSize = 10.sp
            )
            val dest = trip.endAddress.ifEmpty { trip.startAddress.ifEmpty { "?" } }
            Text(text = dest.take(30), color = CarColors.Text, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        Text(
            text = "%.1f km".format(trip.distanceKm),
            color = CarColors.Text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun isToday(epochMs: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = epochMs }
    val c2 = Calendar.getInstance()
    return c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR) &&
           c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
}

private fun isYesterday(epochMs: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = epochMs }
    val c2 = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    return c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR) &&
           c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
}
