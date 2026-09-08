package com.example.carlauncher.ui.incident

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.incident.IncidentGpsFix
import com.example.carlauncher.data.incident.PlateBox
import com.example.carlauncher.ui.theme.CarColors

/**
 * Live overlay drawn OVER the camera [androidx.camera.view.PreviewView] as a Compose sibling.
 * It is never handed to any CameraX use case, so the recorded `.mp4` is unaffected.
 */
@Composable
fun IncidentOverlay(
    nowText: String,
    fix: IncidentGpsFix?,
    plateBoxes: List<PlateBox>,
    modifier: Modifier = Modifier,
) {
    val boxColor = CarColors.Go
    val panelBg = Color(0xCC0D0D0F)
    val textColor = Color(0xFFF0F0F5)
    val mutedColor = Color(0xFF8A8A9A)

    Box(modifier = modifier.fillMaxSize()) {

        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 3.dp.toPx()
            plateBoxes.forEach { b ->
                val l = b.left * size.width
                val t = b.top * size.height
                val w = ((b.right - b.left) * size.width).coerceAtLeast(0f)
                val h = ((b.bottom - b.top) * size.height).coerceAtLeast(0f)
                drawRect(
                    color = boxColor,
                    topLeft = Offset(l, t),
                    size = Size(w, h),
                    style = Stroke(width = strokePx),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(panelBg, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = nowText,
                color = textColor,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(2.dp))
            if (fix != null) {
                Text(
                    text = "%.6f, %.6f".format(fix.lat, fix.lon),
                    color = textColor,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "± %.0f m   %.0f km/h".format(fix.accM, fix.speedMps * 3.6f),
                    color = mutedColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            } else {
                Text(
                    text = "GPS: získávám…",
                    color = mutedColor,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
