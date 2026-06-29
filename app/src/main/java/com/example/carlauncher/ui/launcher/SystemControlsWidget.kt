package com.example.carlauncher.ui.launcher

import android.app.Activity
import android.media.AudioManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.carlauncher.ui.theme.CarColors
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(20.dp)
private val VolumeColor     = Color(0xFF60A5FA)   // modrá
private val BrightnessColor = Color(0xFFFFC107)   // jantarová

@Composable
fun SystemControlsWidget(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(100.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VolumeCard(modifier = Modifier.weight(1f).fillMaxHeight())
        BrightnessCard(modifier = Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun VolumeCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audio   = remember { context.getSystemService(AudioManager::class.java) }
    val maxVol  = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var level   by remember {
        mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol)
    }
    ControlCard(
        icon        = Icons.Filled.VolumeUp,
        label       = "HLASITOST",
        level       = level,
        accentColor = VolumeColor,
        modifier    = modifier,
        onDelta     = { delta ->
            level = (level + delta).coerceIn(0f, 1f)
            audio.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                (level * maxVol).roundToInt().coerceIn(0, maxVol),
                0,
            )
        },
    )
}

@Composable
private fun BrightnessCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val window  = remember { (context as? Activity)?.window }
    var level   by remember {
        mutableFloatStateOf(window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f)
    }
    ControlCard(
        icon        = Icons.Filled.WbSunny,
        label       = "JAS",
        level       = level,
        accentColor = BrightnessColor,
        modifier    = modifier,
        onDelta     = { delta ->
            level = (level + delta).coerceIn(0.01f, 1f)
            window?.let {
                val lp = it.attributes
                lp.screenBrightness = level
                it.attributes = lp
            }
        },
    )
}

@Composable
private fun ControlCard(
    icon: ImageVector,
    label: String,
    level: Float,
    accentColor: Color,
    onDelta: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CardShape)
            .background(CarColors.Surface)
            .border(1.dp, CarColors.BorderSoft, CardShape)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart  = {},
                    onDragEnd    = {},
                    onDragCancel = {},
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        // swipe nahoru = větší level, dolů = menší
                        onDelta(-dragAmount / size.height.toFloat() * 1.5f)
                    },
                )
            },
    ) {
        // Barevná výplň stoupá od spodku podle úrovně
        Canvas(modifier = Modifier.fillMaxSize()) {
            val fillH = size.height * level
            drawRect(
                color    = accentColor.copy(alpha = 0.18f),
                topLeft  = Offset(0f, size.height - fillH),
                size     = Size(size.width, fillH),
            )
        }

        // Label + procenta — nahoře
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text       = label,
                color      = CarColors.Text3,
                fontSize   = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.em,
            )
            Text(
                text       = "${(level * 100).roundToInt()}%",
                color      = CarColors.Text2,
                fontSize   = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        // Ikona uprostřed karty
        Icon(
            imageVector     = icon,
            contentDescription = null,
            tint            = accentColor,
            modifier        = Modifier
                .align(Alignment.Center)
                .size(30.dp),
        )
    }
}
