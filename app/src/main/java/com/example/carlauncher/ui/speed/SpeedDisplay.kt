package com.example.carlauncher.ui.speed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BackgroundColor = Color(0xDB161620)   // oklch(0.165 0.008 255 / 0.86)
private val BorderColor     = Color(0xFF252530)
private val TextSecondary   = Color(0xFF888899)
private val ColorNormal     = Color.White
private val ColorWarning     = Color(0xFFFF9500)
private val ColorOverspeed  = Color(0xFFFF3B30)
private val Shape = RoundedCornerShape(18.dp)

@Composable
fun SpeedDisplay(
    speedKmh: Float,
    modifier: Modifier = Modifier
) {
    // remember — skip re-calculation on recompose when speed hasn't changed
    val display = remember(speedKmh) { if (speedKmh < 3f) 0 else speedKmh.toInt() }
    val numberColor = remember(speedKmh) {
        when {
            speedKmh > 120f -> ColorOverspeed
            speedKmh >= 90f -> ColorWarning
            else            -> ColorNormal
        }
    }

    Row(
        modifier = modifier
            .background(color = BackgroundColor, shape = Shape)
            .border(width = 1.dp, color = BorderColor, shape = Shape)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = display.toString(),
            style = TextStyle(
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = numberColor,
                fontFeatureSettings = "tnum",
                lineHeight = 56.sp
            )
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "km/h",
            style = TextStyle(
                fontSize = 14.sp,
                color = TextSecondary
            ),
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Spacer(Modifier.width(14.dp))

        // Speed limit roundel — hardcoded 50, dynamic value (OSM maxspeed) comes in v0.3
        Box(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(4.dp, Color(0xFFE05C3A), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "50",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111111),
                    fontFamily = FontFamily.SansSerif
                )
            )
        }
    }
}
