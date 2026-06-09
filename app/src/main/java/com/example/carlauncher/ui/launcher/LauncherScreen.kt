package com.example.carlauncher.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

@Composable
fun LauncherScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0F)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Car Launcher — ready",
            color = Color(0xFF00C853),
            fontSize = 24.sp
        )
    }
}
