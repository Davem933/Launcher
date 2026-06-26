package com.example.carlauncher.ui.widgets

import android.appwidget.AppWidgetHostView
import android.os.Bundle
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.ui.dock.DockBar
import com.example.carlauncher.ui.dock.DockViewModel
import com.example.carlauncher.ui.theme.CarColors

@Composable
fun WidgetScreen(
    onLaunchSplitScreen: (pkg1: String, pkg2: String) -> Unit = { _, _ -> },
    onAddWidget: () -> Unit = {},
    viewModel: WidgetViewModel = hiltViewModel(),
    dockViewModel: DockViewModel = hiltViewModel()
) {
    val widgetIds by viewModel.widgetIds.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var editMode by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.appWidgetHost.startListening()
                Lifecycle.Event.ON_STOP  -> viewModel.appWidgetHost.stopListening()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarColors.Bg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                // Tap on background cancels edit mode
                .pointerInput(editMode) {
                    if (editMode) detectTapGestures(onTap = { editMode = false })
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WidgetSlot(
                        slotIndex = 0, widgetIds = widgetIds, viewModel = viewModel,
                        editMode = editMode,
                        onAddWidget = onAddWidget,
                        onEnterEditMode = { editMode = true },
                        onExitEditMode = { editMode = false },
                        onRemove = { editMode = false },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                    WidgetSlot(
                        slotIndex = 1, widgetIds = widgetIds, viewModel = viewModel,
                        editMode = editMode,
                        onAddWidget = onAddWidget,
                        onEnterEditMode = { editMode = true },
                        onExitEditMode = { editMode = false },
                        onRemove = { editMode = false },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WidgetSlot(
                        slotIndex = 2, widgetIds = widgetIds, viewModel = viewModel,
                        editMode = editMode,
                        onAddWidget = onAddWidget,
                        onEnterEditMode = { editMode = true },
                        onExitEditMode = { editMode = false },
                        onRemove = { editMode = false },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                    WidgetSlot(
                        slotIndex = 3, widgetIds = widgetIds, viewModel = viewModel,
                        editMode = editMode,
                        onAddWidget = onAddWidget,
                        onEnterEditMode = { editMode = true },
                        onExitEditMode = { editMode = false },
                        onRemove = { editMode = false },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
        }

        DockBar(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 8.dp),
            onLaunchSplitScreen = onLaunchSplitScreen
        )
    }
}

@Composable
private fun WidgetSlot(
    slotIndex: Int,
    widgetIds: List<Int>,
    viewModel: WidgetViewModel,
    editMode: Boolean,
    onAddWidget: () -> Unit,
    onEnterEditMode: () -> Unit,
    onExitEditMode: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val widgetId = widgetIds.getOrNull(slotIndex)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CarColors.Surface)
    ) {
        if (widgetId != null) {
            WidgetCard(
                widgetId = widgetId,
                viewModel = viewModel,
                editMode = editMode,
                onEnterEditMode = onEnterEditMode,
                onExitEditMode = onExitEditMode,
                onRemove = {
                    viewModel.removeWidget(widgetId)
                    onRemove()
                }
            )
        } else {
            EmptySlot(onClick = onAddWidget)
        }
    }
}

@Composable
private fun WidgetCard(
    widgetId: Int,
    viewModel: WidgetViewModel,
    editMode: Boolean,
    onEnterEditMode: () -> Unit,
    onExitEditMode: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var cardSize by remember { mutableStateOf(IntSize.Zero) }

    val hostView = remember(widgetId) {
        val info = viewModel.appWidgetManager.getAppWidgetInfo(widgetId)
        val view = viewModel.appWidgetHost.createView(context, widgetId, info) as AppWidgetHostView
        view.setAppWidget(widgetId, info)
        view.setPadding(0, 0, 0, 0)
        view.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        view
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Widget view — behind the overlay
        AndroidView(
            factory = { hostView },
            update = { view ->
                // Push actual card dimensions as widget options so responsive widgets
                // (e.g. Google Calendar) re-layout for the correct size
                if (cardSize != IntSize.Zero) {
                    val wDp = with(density) { cardSize.width.toDp().value.toInt() }
                    val hDp = with(density) { cardSize.height.toDp().value.toInt() }
                    val opts = Bundle().apply {
                        putInt("appWidgetMinWidth", wDp)
                        putInt("appWidgetMaxWidth", wDp)
                        putInt("appWidgetMinHeight", hDp)
                        putInt("appWidgetMaxHeight", hDp)
                    }
                    viewModel.appWidgetManager.updateAppWidgetOptions(widgetId, opts)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { if (it != IntSize.Zero) cardSize = it }
        )

        // Transparent overlay on top of the native view — intercepts long press.
        // Must be AFTER AndroidView in the Box so it sits above it in z-order.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(editMode) {
                    detectTapGestures(
                        onTap = { /* consume — don't let tap fall through in edit mode */ },
                        onLongPress = { onEnterEditMode() }
                    )
                }
        )

        // Edit mode: dark scrim — tap scrim = exit, tap koš = smazat
        if (editMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x88000000))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onExitEditMode() })
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { onRemove() })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Odebrat widget",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySlot(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0x1AFFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = CarColors.Text2,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = "Přidat widget",
                color = CarColors.Text2,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
