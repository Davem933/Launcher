package com.example.carlauncher.ui.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.carlauncher.data.model.WidgetSlot
import com.example.carlauncher.data.widgets.GRID_COLS
import com.example.carlauncher.data.widgets.GRID_ROWS
import com.example.carlauncher.ui.theme.CarColors
import kotlin.math.roundToInt

@Composable
fun WidgetCanvas(
    slots: List<WidgetSlot>,
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager,
    onAddWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onMoveWidget: (appWidgetId: Int, col: Int, row: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var editMode by remember { mutableStateOf(false) }
    var canvasWidthPx by remember { mutableStateOf(0) }
    var canvasHeightPx by remember { mutableStateOf(0) }

    if (slots.isEmpty() && !editMode) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            AddWidgetButton(onClick = onAddWidget)
        }
        return
    }

    Box(
        modifier = modifier
            .onSizeChanged { canvasWidthPx = it.width; canvasHeightPx = it.height }
            .pointerInput(Unit) { detectTapGestures { editMode = false } }
    ) {
        val cellWidthPx = if (canvasWidthPx > 0) canvasWidthPx.toFloat() / GRID_COLS else 1f
        val cellHeightPx = if (canvasHeightPx > 0) canvasHeightPx.toFloat() / GRID_ROWS else 1f

        slots.forEach { slot ->
            key(slot.appWidgetId) {
                WidgetItem(
                    slot = slot,
                    cellWidthPx = cellWidthPx,
                    cellHeightPx = cellHeightPx,
                    editMode = editMode,
                    appWidgetHost = appWidgetHost,
                    appWidgetManager = appWidgetManager,
                    onLongPress = { editMode = true },
                    onRemove = { onRemoveWidget(slot.appWidgetId) },
                    onMove = { col, row -> onMoveWidget(slot.appWidgetId, col, row) }
                )
            }
        }

        if (editMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                AddWidgetButton(onClick = { editMode = false; onAddWidget() })
            }
        }
    }
}

@Composable
private fun WidgetItem(
    slot: WidgetSlot,
    cellWidthPx: Float,
    cellHeightPx: Float,
    editMode: Boolean,
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager,
    onLongPress: () -> Unit,
    onRemove: () -> Unit,
    onMove: (col: Int, row: Int) -> Unit
) {
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val density = LocalDensity.current

    val xPx = (slot.col * cellWidthPx + dragOffsetX).roundToInt()
    val yPx = (slot.row * cellHeightPx + dragOffsetY).roundToInt()
    val widthDp = with(density) { (slot.colSpan * cellWidthPx).toDp() }
    val heightDp = with(density) { (slot.rowSpan * cellHeightPx).toDp() }

    Box(
        modifier = Modifier
            .offset { IntOffset(xPx, yPx) }
            .size(widthDp, heightDp)
            .rotate(if (editMode) 2f else 0f)
            .pointerInput(editMode) {
                if (!editMode) {
                    detectTapGestures(onLongPress = { onLongPress() })
                } else {
                    detectDragGestures(
                        onDragEnd = {
                            val newCol = ((slot.col * cellWidthPx + dragOffsetX) / cellWidthPx)
                                .roundToInt().coerceIn(0, GRID_COLS - slot.colSpan)
                            val newRow = ((slot.row * cellHeightPx + dragOffsetY) / cellHeightPx)
                                .roundToInt().coerceIn(0, GRID_ROWS - slot.rowSpan)
                            onMove(newCol, newRow)
                            dragOffsetX = 0f
                            dragOffsetY = 0f
                        },
                        onDragCancel = { dragOffsetX = 0f; dragOffsetY = 0f },
                        onDrag = { _, amount ->
                            dragOffsetX += amount.x
                            dragOffsetY += amount.y
                        }
                    )
                }
            }
    ) {
        val context = LocalContext.current
        val info = remember(slot.appWidgetId) {
            appWidgetManager.getAppWidgetInfo(slot.appWidgetId)
        }
        AndroidView(
            factory = { ctx ->
                appWidgetHost.createView(ctx, slot.appWidgetId, info) as AppWidgetHostView
            },
            modifier = Modifier.fillMaxSize()
        )

        if (editMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF3B30))
                    .pointerInput(Unit) { detectTapGestures(onTap = { onRemove() }) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Odebrat widget",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun AddWidgetButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(CarColors.Surface)
            .border(1.dp, CarColors.BorderSoft, CircleShape)
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Přidat widget",
            tint = CarColors.Text2,
            modifier = Modifier.size(32.dp)
        )
    }
}
