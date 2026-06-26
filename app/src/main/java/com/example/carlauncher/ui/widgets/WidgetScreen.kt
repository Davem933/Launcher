package com.example.carlauncher.ui.widgets

import android.appwidget.AppWidgetHostView
import android.os.Bundle
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
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
    val template  by viewModel.template.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var editMode by remember { mutableStateOf(false) }
    var showTemplatePicker by remember { mutableStateOf(false) }

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
        // ── Template picker button ────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(CarColors.Surface)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { showTemplatePicker = true })
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = null,
                        tint = CarColors.Text2,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = template.labelCs(),
                        color = CarColors.Text2,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // ── Widget grid ───────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(editMode) {
                    if (editMode) detectTapGestures(onTap = { editMode = false })
                }
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    count = template.slotCount,
                    span = { index -> GridItemSpan(template.spans.getOrElse(index) { 1 }) }
                ) { index ->
                    val widgetId = widgetIds.getOrNull(index)
                    SlotCard(
                        widgetId = widgetId,
                        viewModel = viewModel,
                        editMode = editMode,
                        onAddWidget = onAddWidget,
                        onEnterEditMode = { editMode = true },
                        onExitEditMode = { editMode = false },
                        onRemove = {
                            if (widgetId != null) viewModel.removeWidget(widgetId)
                            editMode = false
                        }
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

    if (showTemplatePicker) {
        TemplatePickerDialog(
            current = template,
            onSelect = { tmpl ->
                viewModel.setTemplate(tmpl)
                showTemplatePicker = false
                editMode = false
            },
            onDismiss = { showTemplatePicker = false }
        )
    }
}

// ── Slot card — filled or empty ───────────────────────────────────────────────

@Composable
private fun SlotCard(
    widgetId: Int?,
    viewModel: WidgetViewModel,
    editMode: Boolean,
    onAddWidget: () -> Unit,
    onEnterEditMode: () -> Unit,
    onExitEditMode: () -> Unit,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Let the grid row height be driven by content; use a reasonable min
            .then(Modifier) // height managed by grid row
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
                onRemove = onRemove
            )
        } else {
            EmptySlot(onClick = onAddWidget)
        }
    }
}

// ── Filled widget card ────────────────────────────────────────────────────────

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
        AndroidView(
            factory = { hostView },
            update = {
                if (cardSize != IntSize.Zero) {
                    val wDp = with(density) { cardSize.width.toDp().value.toInt() }
                    val hDp = with(density) { cardSize.height.toDp().value.toInt() }
                    viewModel.appWidgetManager.updateAppWidgetOptions(widgetId, Bundle().apply {
                        putInt("appWidgetMinWidth", wDp)
                        putInt("appWidgetMaxWidth", wDp)
                        putInt("appWidgetMinHeight", hDp)
                        putInt("appWidgetMaxHeight", hDp)
                    })
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { if (it != IntSize.Zero) cardSize = it }
        )

        // Transparent overlay — captures long press above native view
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(editMode) {
                    detectTapGestures(
                        onTap = {},
                        onLongPress = { onEnterEditMode() }
                    )
                }
        )

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
                        .pointerInput(Unit) { detectTapGestures(onTap = { onRemove() }) },
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

// ── Empty slot ────────────────────────────────────────────────────────────────

@Composable
private fun EmptySlot(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp)
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

// ── Template picker dialog ────────────────────────────────────────────────────

@Composable
private fun TemplatePickerDialog(
    current: WidgetLayoutTemplate,
    onSelect: (WidgetLayoutTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CarColors.Surface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Rozvržení widgetů",
                    color = CarColors.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                WidgetLayoutTemplate.entries.forEach { tmpl ->
                    val selected = tmpl == current
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) CarColors.Accent.copy(alpha = 0.18f)
                                else Color(0x0DFFFFFF)
                            )
                            .pointerInput(tmpl) {
                                detectTapGestures(onTap = { onSelect(tmpl) })
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = tmpl.labelCs(),
                                    color = if (selected) CarColors.Accent else CarColors.Text,
                                    fontSize = 14.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                                Text(
                                    text = "${tmpl.slotCount} sloty",
                                    color = CarColors.Text2,
                                    fontSize = 11.sp
                                )
                            }
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(CarColors.Accent)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
