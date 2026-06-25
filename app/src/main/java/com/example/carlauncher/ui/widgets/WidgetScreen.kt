package com.example.carlauncher.ui.widgets

import android.appwidget.AppWidgetHostView
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

private const val MAX_SLOTS = 4

@Composable
fun WidgetScreen(
    onLaunchSplitScreen: (pkg1: String, pkg2: String) -> Unit = { _, _ -> },
    onAddWidget: () -> Unit = {},
    viewModel: WidgetViewModel = hiltViewModel(),
    dockViewModel: DockViewModel = hiltViewModel()
) {
    val widgetIds by viewModel.widgetIds.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

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
        // 2×2 grid — always shows exactly 4 slots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Left column: slots 0 and 1
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WidgetSlot(
                    slotIndex = 0,
                    widgetIds = widgetIds,
                    viewModel = viewModel,
                    onAddWidget = onAddWidget,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                WidgetSlot(
                    slotIndex = 1,
                    widgetIds = widgetIds,
                    viewModel = viewModel,
                    onAddWidget = onAddWidget,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            // Right column: slots 2 and 3
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WidgetSlot(
                    slotIndex = 2,
                    widgetIds = widgetIds,
                    viewModel = viewModel,
                    onAddWidget = onAddWidget,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                WidgetSlot(
                    slotIndex = 3,
                    widgetIds = widgetIds,
                    viewModel = viewModel,
                    onAddWidget = onAddWidget,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
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
    onAddWidget: () -> Unit,
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
                onRemove = { viewModel.removeWidget(widgetId) }
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
    onRemove: () -> Unit
) {
    val context = LocalContext.current

    val hostView = remember(widgetId) {
        val info = viewModel.appWidgetManager.getAppWidgetInfo(widgetId)
        val view = viewModel.appWidgetHost.createView(context, widgetId, info) as AppWidgetHostView
        view.setAppWidget(widgetId, info)
        view
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { hostView },
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(28.dp)
                .background(Color(0x99000000), CircleShape)
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
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0x1AFFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = CarColors.Text2,
                    modifier = Modifier.size(24.dp)
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
