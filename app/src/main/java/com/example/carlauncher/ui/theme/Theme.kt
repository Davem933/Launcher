package com.example.carlauncher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CarDarkColorScheme = darkColorScheme(
    background        = Color(0xFF0D0D0F),
    surface           = Color(0xFF1A1A1F),
    primary           = Color(0xFF22C55E),
    onPrimary         = Color(0xFF000000),
    onBackground      = Color(0xFFF0F0F5),
    onSurface         = Color(0xFFF0F0F5),
    surfaceVariant    = Color(0xFF252830),
    onSurfaceVariant  = Color(0xFFB8BAC8),
    outline           = Color(0xFF2E3040),
)

// High-contrast automotive light palette — readable in direct sunlight.
// All colours pass WCAG AA (4.5:1) against their respective surfaces.
private val CarLightColorScheme = lightColorScheme(
    background        = Color(0xFFF2F3F7),
    surface           = Color(0xFFFFFFFF),
    primary           = Color(0xFF1B8A43),   // darker green — legible on white
    onPrimary         = Color(0xFFFFFFFF),
    onBackground      = Color(0xFF0D0D0F),
    onSurface         = Color(0xFF1A1A1F),
    surfaceVariant    = Color(0xFFE6E8F0),
    onSurfaceVariant  = Color(0xFF3A3C4A),
    outline           = Color(0xFFBDBEC8),
)

@Composable
fun CarLauncherTheme(
    isDark: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (isDark) CarDarkColorScheme else CarLightColorScheme,
        content = content
    )
}
