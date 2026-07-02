package com.example.carlauncher.debug

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.model.VehicleDisplayLocation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GpsDebugOverlay(
    location: VehicleDisplayLocation?,
    isVisible: Boolean,
    onToggle: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Toggle button — small, top right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(32.dp)
                .background(Color(0x880D0D0F), RoundedCornerShape(8.dp))
                .clickable { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            Text("🛰", fontSize = 16.sp)
        }

        // Overlay panel
        if (isVisible && location != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 8.dp)
                    .background(Color(0xEE0D0D0F), RoundedCornerShape(12.dp))
                    .padding(12.dp)
                    .width(220.dp)
            ) {
                Text(
                    "GPS DEBUG",
                    color = Color(0xFF00C853),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                DebugRow("Lat",       "%.6f".format(location.lat))
                DebugRow("Lng",       "%.6f".format(location.lng))
                DebugRow("Speed",     "%.1f km/h".format(location.speedKmh))
                DebugRow("Bearing",   "%.1f°".format(location.bearingDeg))
                DebugRow("Accuracy",  "%.0fm".format(location.accuracyM))
                DebugRow("Timestamp", SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(location.timestamp)))
            }
        }
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF8A8A9A), fontSize = 11.sp)
        Text(value,  color = Color(0xFFF0F0F5), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}
