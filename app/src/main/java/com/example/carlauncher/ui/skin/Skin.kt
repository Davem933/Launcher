package com.example.carlauncher.ui.skin

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.carlauncher.data.model.LauncherStyle
import com.example.carlauncher.ui.map.MapChrome
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/*
 * The three directions from the Claude Design file "Car Launcher" (turn 2): 2a Světlý,
 * 2b Přístrojová deska, 2c Tři sloupce. The design canvas is 1400×876 px; the M10 is
 * 1143×686 dp, so sizes below are the design's px × ~0.8.
 */

internal object LightColors {
    val Bg      = Color(0xFFECE8E1)
    val Card    = Color(0xFFFBFAF7)
    val Inset   = Color(0xFFECE8E1)
    val Text    = Color(0xFF1D1B18)
    val Text2   = Color(0xFF6B665D)
    val Muted   = Color(0xFFB5AEA2)
    val Plus    = Color(0xFF9A9388)
    val Red     = Color(0xFFE5533D)
    val VolBg   = Color(0xFFD9E4F7)
    val VolFg   = Color(0xFF2F5CB8)
    val BriBg   = Color(0xFFF6E3B4)
    val BriFg   = Color(0xFFA86F00)
    val Cloud   = Color(0xFF9AA3B3)
}

internal object DashColors {
    val Line    = Color(0xFF2A2C27)   // 1dp gaps between panels
    val Panel   = Color(0xFF0E0F0C)
    val Sub     = Color(0xFF141510)
    val Stripe  = Color(0xFF171913)
    val Text    = Color(0xFFE8EADF)
    val Text2   = Color(0xFF8D9183)
    val Lime    = Color(0xFFC6FF3D)
    val Amber   = Color(0xFFFFB547)
    val Dashed  = Color(0xFF4A4D43)
    val Plus    = Color(0xFF6F7366)
    val Outline = Color(0xFF3A3D34)
}

internal object TrioColors {
    val Bg       = Color(0xFF111426)
    val Card     = Color(0xFF232849)
    val Inner    = Color(0xFF2E3460)
    val Dock     = Color(0xFF1A1E38)
    val Text     = Color(0xFFF3F0FF)
    val Text2    = Color(0xFFA7AEDB)
    val Coral    = Color(0xFFFF7A59)
    val OnCoral  = Color(0xFF1A0F0B)
    val Blue     = Color(0xFF9DB4FF)
    val Yellow   = Color(0xFFFFD36B)
    val Cloud    = Color(0xFFC9CFEE)
    val ClockSub = Color(0xFF5A5F86)
    val Plus     = Color(0xFF6A71A3)
}

/** Page background behind the pager + dock for [style]; `null` = not a skin style. */
fun skinBackground(style: LauncherStyle): Color? = when (style) {
    LauncherStyle.LIGHT -> LightColors.Bg
    LauncherStyle.DASH  -> DashColors.Line
    LauncherStyle.TRIO  -> TrioColors.Bg
    else -> null
}

/** Pager dot colours (active, inactive) — dark dots on the light skin. */
fun skinPageDots(style: LauncherStyle): Pair<Color, Color> = when (style) {
    LauncherStyle.LIGHT -> LightColors.Text to LightColors.Text.copy(alpha = 0.25f)
    LauncherStyle.DASH  -> DashColors.Lime to Color.White.copy(alpha = 0.35f)
    else -> Color.White to Color.White.copy(alpha = 0.35f)
}

internal val LightMapChrome = MapChrome(
    panelShape = RoundedCornerShape(26.dp),
    panelBorder = null,
    searchBg = LightColors.Card,
    searchFg = LightColors.Text2,
    searchIconTint = LightColors.Text2,
    searchBorder = null,
    searchShadow = 6.dp,
    locateActiveBg = LightColors.Red,
    locateActiveFg = Color.White,
    locateIdleBg = LightColors.Card,
    locateIdleFg = LightColors.Red,
    locateSize = 52.dp,
    lightPreset = "day",
    mapTheme = "faded",
)

internal val DashMapChrome = MapChrome(
    panelShape = RectangleShape,
    panelBorder = null,
    searchBg = DashColors.Panel.copy(alpha = 0.92f),
    searchFg = DashColors.Text2,
    searchIconTint = DashColors.Lime,
    searchBorder = DashColors.Outline,
    searchShape = RectangleShape,
    searchLabel = "Hledat_",
    locateActiveBg = DashColors.Lime,
    locateActiveFg = DashColors.Panel,
    locateIdleBg = DashColors.Panel,
    locateIdleFg = DashColors.Lime,
    locateShape = RectangleShape,
    locateSize = 58.dp,
    locatePadding = 0.dp,
    mapTheme = "monochrome",
)

internal fun trioMapChrome(extraAction: (@Composable () -> Unit)?) = MapChrome(
    panelShape = RoundedCornerShape(32.dp),
    panelBorder = null,
    searchBg = TrioColors.Bg.copy(alpha = 0.85f),
    searchFg = TrioColors.Text2,
    searchIconTint = TrioColors.Text2,
    searchBorder = null,
    searchShape = CircleShape,
    searchFillWidth = true,
    locateActiveBg = TrioColors.Blue,
    locateActiveFg = TrioColors.Bg,
    locateIdleBg = TrioColors.Card,
    locateIdleFg = TrioColors.Blue,
    locateSize = 52.dp,
    extraAction = extraAction,
)

internal val IncidentIcon = Icons.Filled.Videocam
internal val LocateIcon = Icons.Filled.MyLocation

// ── Shared state: clock, battery, date ───────────────────────────────────────

private val clockFmt = DateTimeFormatter.ofPattern("HH:mm")
private val czech = Locale("cs", "CZ")
private val dayFmt = DateTimeFormatter.ofPattern("EEEE d. MMMM", czech)

internal class StatusState(time: String, battery: Int) {
    var time by mutableStateOf(time)
    var battery by mutableIntStateOf(battery)
}

/** Clock + battery, refreshed every 15 s (the minute flips at most 15 s late). */
@Composable
internal fun rememberStatus(): StatusState {
    val context = LocalContext.current
    val state = remember { StatusState(LocalTime.now().format(clockFmt), readBattery(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            state.time = LocalTime.now().format(clockFmt)
            state.battery = readBattery(context)
            delay(15_000L)
        }
    }
    return state
}

private fun readBattery(context: Context): Int =
    (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

/** "Úterý 13. října" — the design's capitalised Czech day + date. */
internal fun todayLabel(): String =
    LocalDate.now().format(dayFmt).replaceFirstChar { it.titlecase(czech) }

// ── Shared controls: volume, brightness ──────────────────────────────────────

internal class LevelControl(initial: Float, private val apply: (Float) -> Float) {
    var level by mutableFloatStateOf(initial)
        private set
    fun set(value: Float) { level = apply(value.coerceIn(0f, 1f)) }
    val percent: Int get() = (level * 100).roundToInt()
}

@Composable
internal fun rememberVolumeControl(): LevelControl {
    val context = LocalContext.current
    return remember {
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        LevelControl(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max) { v ->
            val steps = (v * max).roundToInt().coerceIn(0, max)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, steps, 0)
            steps.toFloat() / max
        }
    }
}

@Composable
internal fun rememberBrightnessControl(): LevelControl {
    val context = LocalContext.current
    return remember {
        val window = (context as? Activity)?.window
        LevelControl(window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f) { v ->
            val level = v.coerceAtLeast(0.01f)
            window?.let { w -> w.attributes = w.attributes.also { it.screenBrightness = level } }
            level
        }
    }
}

/**
 * Drag anywhere on the tile to change the level: horizontal drags move it relative to the
 * tile width (tap alone does nothing). Every move is consumed, so a drag on the tile can't
 * turn into a pager swipe.
 */
internal fun Modifier.levelDrag(control: LevelControl, vertical: Boolean = false): Modifier =
    pointerInput(control) {
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            var last = down.position
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                change.consume()
                val delta = if (vertical) (last.y - change.position.y) / size.height
                            else (change.position.x - last.x) / size.width
                last = change.position
                control.set(control.level + delta * 1.5f)
            }
        }
    }

// ── Glyphs ───────────────────────────────────────────────────────────────────

/** The design's all-apps glyph: a 3×3 grid of dots (round) or squares (2b). */
@Composable
internal fun DotGridGlyph(color: Color, round: Boolean, size: Dp = 26.dp) {
    Canvas(Modifier.size(size)) {
        val dot = this.size.width / 4.2f
        val gap = (this.size.width - dot * 3) / 2f
        for (r in 0..2) for (c in 0..2) {
            drawRoundRect(
                color = color,
                topLeft = Offset(c * (dot + gap), r * (dot + gap)),
                size = Size(dot, dot),
                cornerRadius = CornerRadius(if (round) dot / 2 else 0f),
            )
        }
    }
}
