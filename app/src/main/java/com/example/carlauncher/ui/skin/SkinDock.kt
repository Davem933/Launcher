package com.example.carlauncher.ui.skin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.data.model.DockSlot
import com.example.carlauncher.data.model.LauncherStyle
import com.example.carlauncher.ui.dock.AppIcon
import com.example.carlauncher.ui.dock.rememberAppIcon
import com.example.carlauncher.ui.launcher.WeatherCalendarViewModel

/** Visual size of one app slot in the skin's dock. */
fun skinSlotSize(style: LauncherStyle): Dp = when (style) {
    LauncherStyle.DASH -> 48.dp
    else               -> 52.dp
}

/**
 * The dock bar of a skin style around [slotTiles] (the slot tiles with their tap / long-press /
 * edit-mode handling, owned by DockBar). Horizontal: 2a pill with status, 2b top bar with a
 * framed slot group and status, 2c centred pill. Vertical (dock on the left): a plain column
 * in the skin's colours.
 */
@Composable
fun SkinDockBar(
    style: LauncherStyle,
    vertical: Boolean,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    slotTiles: @Composable () -> Unit,
) {
    if (vertical) {
        Column(
            modifier = modifier
                .fillMaxHeight()
                .width(84.dp)
                .background(if (style == LauncherStyle.LIGHT) LightColors.Card else if (style == LauncherStyle.DASH) DashColors.Panel else TrioColors.Dock)
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (style != LauncherStyle.TRIO) SkinClock(style, fontSize = 20)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) { slotTiles() }
            SkinMenuTile(style, onMenu)
        }
        return
    }

    when (style) {
        LauncherStyle.LIGHT -> Row(
            modifier = modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(RoundedCornerShape(38.dp))
                .background(LightColors.Card)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkinMenuTile(style, onMenu)
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) { slotTiles() }
            SkinStatus(style, Modifier.padding(end = 10.dp))
        }

        LauncherStyle.DASH -> Row(
            modifier = modifier
                .fillMaxWidth()
                .height(84.dp)
                .background(DashColors.Panel)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkinMenuTile(style, onMenu)
            Row(
                modifier = Modifier
                    .height(62.dp)
                    .background(DashColors.Sub)
                    .border(1.dp, DashColors.Line)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) { slotTiles() }
            Box(Modifier.weight(1f))
            SkinStatus(style, Modifier.padding(end = 8.dp))
        }

        else -> Row(
            modifier = modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(CircleShape)
                .background(TrioColors.Dock)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkinMenuTile(style, onMenu)
            slotTiles()
        }
    }
}

@Composable
private fun SkinMenuTile(style: LauncherStyle, onClick: () -> Unit) {
    val (bg, fg, shape) = when (style) {
        LauncherStyle.LIGHT -> Triple(LightColors.Text, Color.White, CircleShape)
        LauncherStyle.DASH  -> Triple(DashColors.Lime, DashColors.Panel, RectangleShape)
        else                -> Triple(TrioColors.Coral, TrioColors.OnCoral, CircleShape)
    }
    Box(
        modifier = Modifier
            .size(if (style == LauncherStyle.DASH) 58.dp else 54.dp)
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        DotGridGlyph(color = fg, round = style != LauncherStyle.DASH, size = 22.dp)
    }
}

@Composable
private fun SkinClock(style: LauncherStyle, fontSize: Int) {
    val status = rememberStatus()
    Text(
        status.time,
        color = if (style == LauncherStyle.LIGHT) LightColors.Text else DashColors.Text,
        fontSize = fontSize.sp,
        fontWeight = if (style == LauncherStyle.LIGHT) FontWeight.Bold else FontWeight.SemiBold,
        fontFamily = if (style == LauncherStyle.DASH) DashMono else null,
    )
}

/** Battery + temperature + big clock at the dock's right end (2a stacked, 2b inline). */
@Composable
private fun SkinStatus(
    style: LauncherStyle,
    modifier: Modifier = Modifier,
    weatherViewModel: WeatherCalendarViewModel = hiltViewModel(),
) {
    val status = rememberStatus()
    val weather by weatherViewModel.weather.collectAsStateWithLifecycle()
    val battery = "${status.battery} %"
    val temp = weather?.let { "${it.tempC}°" } ?: "–"
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (style == LauncherStyle.DASH) {
            Text("BAT $battery", color = DashColors.Text2, fontSize = 11.sp, fontFamily = DashMono)
            Text("☁ $temp", color = DashColors.Text2, fontSize = 11.sp, fontFamily = DashMono)
        } else {
            Column(horizontalAlignment = Alignment.End) {
                Text(battery, color = LightColors.Text2, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Text(temp, color = LightColors.Text2, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
        SkinClock(style, fontSize = 27)
    }
}

// ── Slot tiles ───────────────────────────────────────────────────────────────

/** One dock slot drawn in the skin's look; the caller handles gestures and edit-mode wiggle. */
@Composable
fun SkinSlotContent(style: LauncherStyle, slot: DockSlot, size: Dp) {
    when (slot) {
        is DockSlot.App -> AppIcon(
            drawable = rememberAppIcon(slot.packageName),
            modifier = Modifier
                .size(size)
                .then(if (style == LauncherStyle.LIGHT) Modifier.shadow(5.dp, CircleShape, clip = false) else Modifier)
                .clip(CircleShape)
        )
        is DockSlot.SplitScreen -> SkinSplitTile(style, slot, size)
        DockSlot.Empty -> SkinEmptyTile(style, size)
        else -> Unit
    }
}

@Composable
private fun SkinSplitTile(style: LauncherStyle, slot: DockSlot.SplitScreen, size: Dp) {
    val (bg, shape) = when (style) {
        LauncherStyle.LIGHT -> LightColors.Text to CircleShape as Shape
        LauncherStyle.DASH  -> DashColors.Panel to RectangleShape
        else                -> TrioColors.Card to CircleShape as Shape
    }
    Row(
        modifier = Modifier
            .width(size * 1.5f)
            .height(size)
            .clip(shape)
            .background(bg)
            .then(if (style == LauncherStyle.DASH) Modifier.border(1.dp, DashColors.Line) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconSize = size * 0.6f
        AppIcon(rememberAppIcon(slot.packageName1), Modifier.size(iconSize).clip(CircleShape))
        AppIcon(rememberAppIcon(slot.packageName2), Modifier.size(iconSize).clip(CircleShape))
    }
}

@Composable
private fun SkinEmptyTile(style: LauncherStyle, size: Dp) {
    val modifier = when (style) {
        LauncherStyle.LIGHT -> Modifier.size(size - 4.dp).clip(CircleShape).background(LightColors.Inset)
        LauncherStyle.DASH  -> Modifier.size(size).drawBehind {
            drawRect(
                color = DashColors.Dashed,
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            )
        }
        else -> Modifier.size(size).clip(CircleShape).background(TrioColors.Card)
    }
    val tint = when (style) {
        LauncherStyle.LIGHT -> LightColors.Plus
        LauncherStyle.DASH  -> DashColors.Plus
        else                -> TrioColors.Plus
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Add, contentDescription = "Přidat", tint = tint, modifier = Modifier.size(20.dp))
    }
}
