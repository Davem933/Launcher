package com.example.carlauncher.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.data.trip.TripEntity
import com.example.carlauncher.ui.theme.CarColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TripHistoryScreen(
    onBack: () -> Unit,
    viewModel: TripViewModel = hiltViewModel()
) {
    val trips by viewModel.lastTrips.collectAsStateWithLifecycle()
    val dateFmt = SimpleDateFormat("d. M. yyyy HH:mm", Locale("cs"))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarColors.Bg)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět", tint = CarColors.Text)
            }
            Text(
                text = "Historie jízd",
                color = CarColors.Text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
        if (trips.isEmpty()) {
            Text(text = "Žádné jízdy zatím nezaznamenány.", color = CarColors.Text2, fontSize = 14.sp)
        } else {
            LazyColumn {
                items(trips) { trip ->
                    TripHistoryRow(trip = trip, dateFmt = dateFmt)
                    HorizontalDivider(color = CarColors.Surface, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun TripHistoryRow(trip: TripEntity, dateFmt: SimpleDateFormat) {
    val dur = (trip.endTime - trip.startTime) / 1000
    val h = dur / 3600
    val m = (dur % 3600) / 60
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(text = dateFmt.format(Date(trip.startTime)), color = CarColors.Text2, fontSize = 10.sp)
        Text(
            text = "${trip.startAddress.ifEmpty { "?" }} → ${trip.endAddress.ifEmpty { "?" }}",
            color = CarColors.Text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "%.1f km  •  %02d:%02d  •  ø %d km/h  •  max %d km/h".format(
                trip.distanceKm, h, m, trip.avgSpeedKmh.toInt(), trip.maxSpeedKmh.toInt()
            ),
            color = CarColors.Text2,
            fontSize = 11.sp
        )
    }
}
