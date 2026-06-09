package com.example.carlauncher.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.ui.map.MapWidget

@Composable
fun LauncherScreen(viewModel: LauncherViewModel = hiltViewModel()) {
    val location by viewModel.vehicleLocation.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0F))
    ) {
        MapWidget(modifier = Modifier.fillMaxSize())

        location?.let {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Text(
                    "GPS: ${"%.5f".format(it.lat)}, ${"%.5f".format(it.lng)}",
                    color = Color(0xFF00C853),
                    fontSize = 12.sp
                )
                Text(
                    "Speed: ${"%.1f".format(it.speedKmh)} km/h",
                    color = Color(0xFFFFFFFF),
                    fontSize = 12.sp
                )
                Text(
                    "Bearing: ${"%.0f".format(it.bearingDeg)}°",
                    color = Color(0xFFFFFFFF),
                    fontSize = 12.sp
                )
                Text(
                    "Accuracy: ${"%.0f".format(it.accuracyM)}m",
                    color = Color(0xFF8A8A9A),
                    fontSize = 12.sp
                )
            }
        }
    }
}
