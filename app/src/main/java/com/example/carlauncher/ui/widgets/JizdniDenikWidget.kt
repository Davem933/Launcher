package com.example.carlauncher.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    val weekKm    = weekTrips.sumOf { it.distanceKm.toDouble() }.toFloat()
    val weekCount = weekTrips.size
    val recentTwo = lastTrips.take(2)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CarColors.Surface)
            .border(1.dp, Color(0xFF2A2C35), RoundedCornerShape(16.dp))
            .clickable { onOpenHistory() }
            .padding(12.dp)
    ) {
        Text(text = "Tento týden", color = CarColors.Text2, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = "%.0f km  ·  %d jízd".format(weekKm, weekCount),
            color = CarColors.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFF2A2C35))

        if (recentTwo.isEmpty()) {
            Text(text = "Zatím žádné jízdy", color = CarColors.Text2, fontSize = 11.sp)
        } else {
            recentTwo.forEach { trip -> RecentTripRow(trip) }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { viewModel.exportCsv(context) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2C35)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(text = "Exportovat CSV", color = CarColors.Text, fontSize = 11.sp)
        }
    }
}

@Composable
private fun RecentTripRow(trip: TripEntity) {
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dateLabel = when {
        isToday(trip.startTime)     -> "Dnes"
        isYesterday(trip.startTime) -> "Včera"
        else -> SimpleDateFormat("d. M.", Locale("cs")).format(Date(trip.startTime))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "$dateLabel ${timeFmt.format(Date(trip.startTime))}",
                color = CarColors.Text2,
                fontSize = 10.sp
            )
            Text(
                text = "%.1f km".format(trip.distanceKm),
                color = CarColors.Text,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        val dest = trip.endAddress.ifEmpty { trip.startAddress.ifEmpty { "?" } }
        Text(text = dest.take(40), color = CarColors.Text2, fontSize = 10.sp)
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
