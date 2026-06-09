package com.example.carlauncher.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LauncherScreen(viewModel: LauncherViewModel = hiltViewModel()) {
    val location by viewModel.vehicleLocation.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0F)),
        contentAlignment = Alignment.Center
    ) {
        if (location != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("GPS OK", color = Color(0xFF00C853), fontSize = 24.sp)
                Text("${location!!.lat}, ${location!!.lng}", color = Color(0xFFFFFFFF), fontSize = 14.sp)
                Text("${location!!.speedKmh} km/h", color = Color(0xFFFFFFFF), fontSize = 18.sp)
            }
        } else {
            Text("Waiting for GPS...", color = Color(0xFF8A8A9A), fontSize = 20.sp)
        }
    }
}
