package com.example.carlauncher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CarColorScheme = darkColorScheme(
    background = Color(0xFF0D0D0F),
    surface = Color(0xFF1A1A1F),
    primary = Color(0xFF22C55E),
    onPrimary = Color(0xFF000000),
    onBackground = Color(0xFFF0F0F5),
    onSurface = Color(0xFFF0F0F5),
)

@Composable
fun CarLauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CarColorScheme,
        content = content
    )
}
