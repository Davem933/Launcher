package com.example.carlauncher.ui.widgets

import android.appwidget.AppWidgetHostView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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

@Composable
fun WidgetScreen(
    onLaunchSplitScreen: (pkg1: String, pkg2: String) -> Unit = { _, _ -> },
    onAddWidget: () -> Unit = {},
    viewModel: WidgetViewModel = hiltViewModel(),
    dockViewModel: DockViewModel = hiltViewModel()
) {
    val widgetIds by viewModel.widgetIds.collectAsStateWithLifecycle()
    val widgetHeights by viewModel.widgetHeights.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Synchronize AppWidgetHost listening with lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START  -> viewModel.appWidgetHost.startListening()
                Lifecycle.Event.ON_STOP   -> viewModel.appWidgetHost.stopListening()
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
        // Content area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (widgetIds.isEmpty()) {
                // Empty state — add button centered
                AddWidgetButton(
                    onClick = onAddWidget,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(widgetIds) { widgetId ->
                        val savedHeight = widgetHeights[widgetId]
                        WidgetCard(
                            widgetId = widgetId,
                            viewModel = viewModel,
                            savedHeightDp = savedHeight,
                            onRemove = { viewModel.removeWidget(widgetId) },
                            onHeightChanged = { viewModel.setWidgetHeight(widgetId, it) }
                        )
                    }
                    item {
                        // Add more button at the bottom of the list
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AddWidgetButton(onClick = onAddWidget)
                        }
                    }
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
private fun WidgetCard(
    widgetId: Int,
    viewModel: WidgetViewModel,
    savedHeightDp: Int?,
    onRemove: () -> Unit,
    onHeightChanged: (Int) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val hostView = remember(widgetId) {
        val info = viewModel.appWidgetManager.getAppWidgetInfo(widgetId)
        val view = viewModel.appWidgetHost.createView(context, widgetId, info) as AppWidgetHostView
        view.setAppWidget(widgetId, info)
        view
    }

    val defaultHeight = remember(widgetId) {
        val info = viewModel.appWidgetManager.getAppWidgetInfo(widgetId)
        (info?.minHeight ?: 200).coerceIn(120, 320)
    }

    var heightDp by remember(widgetId) {
        mutableFloatStateOf((savedHeightDp ?: defaultHeight).toFloat())
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(CarColors.Surface)
        ) {
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

        // Drag handle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                .background(CarColors.Surface.copy(alpha = 0.6f))
                .pointerInput(widgetId) {
                    detectVerticalDragGestures(
                        onDragEnd = { onHeightChanged(heightDp.toInt()) }
                    ) { _, dragAmount ->
                        val deltaDp = with(density) { dragAmount.toDp().value }
                        heightDp = (heightDp + deltaDp).coerceIn(80f, 480f)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x66FFFFFF))
            )
        }
    }
}

@Composable
private fun AddWidgetButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CarColors.Surface)
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(CarColors.Surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = CarColors.Text2,
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            text = "Přidat widget",
            color = CarColors.Text2,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
