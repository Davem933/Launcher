package com.example.carlauncher.ui.widgets

import android.os.Bundle
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.carlauncher.ui.theme.CarColors

@Composable
fun WidgetScreen(
    onLaunchSplitScreen: (pkg1: String, pkg2: String) -> Unit = { _, _ -> },
    onAddWidget: (slotIndex: Int) -> Unit = {},
    viewModel: WidgetViewModel = hiltViewModel()
) {
    val stacks   by viewModel.stacks.collectAsStateWithLifecycle()
    val template by viewModel.template.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // -1 = no edit mode active; 0-N = slot index in edit mode
    var editSlot by remember { mutableIntStateOf(-1) }
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
                    .pointerInput(Unit) { detectTapGestures(onTap = { showTemplatePicker = true }) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.GridView, null, tint = CarColors.Text2, modifier = Modifier.size(16.dp))
                    Text(template.labelCs(), color = CarColors.Text2, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // ── Grid area ─────────────────────────────────────────────────────────
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            val gap = 8.dp
            val rowHeight = (maxHeight - gap) / 2

            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                when (template) {
                    WidgetLayoutTemplate.GRID_2X2 -> {
                        Row(
                            modifier = Modifier.height(rowHeight).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gap)
                        ) {
                            SlotCard(
                                stack = stacks.getOrElse(0) { WidgetStack() },
                                slotIndex = 0,
                                viewModel = viewModel,
                                editMode = editSlot == 0,
                                onAddWidget = { onAddWidget(0) },
                                onEnterEditMode = { editSlot = 0 },
                                onExitEditMode = { editSlot = -1 },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            SlotCard(
                                stack = stacks.getOrElse(1) { WidgetStack() },
                                slotIndex = 1,
                                viewModel = viewModel,
                                editMode = editSlot == 1,
                                onAddWidget = { onAddWidget(1) },
                                onEnterEditMode = { editSlot = 1 },
                                onExitEditMode = { editSlot = -1 },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                        Row(
                            modifier = Modifier.height(rowHeight).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gap)
                        ) {
                            SlotCard(
                                stack = stacks.getOrElse(2) { WidgetStack() },
                                slotIndex = 2,
                                viewModel = viewModel,
                                editMode = editSlot == 2,
                                onAddWidget = { onAddWidget(2) },
                                onEnterEditMode = { editSlot = 2 },
                                onExitEditMode = { editSlot = -1 },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            SlotCard(
                                stack = stacks.getOrElse(3) { WidgetStack() },
                                slotIndex = 3,
                                viewModel = viewModel,
                                editMode = editSlot == 3,
                                onAddWidget = { onAddWidget(3) },
                                onEnterEditMode = { editSlot = 3 },
                                onExitEditMode = { editSlot = -1 },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }

                    WidgetLayoutTemplate.WIDE_TOP_TWO_BOTTOM -> {
                        SlotCard(
                            stack = stacks.getOrElse(0) { WidgetStack() },
                            slotIndex = 0,
                            viewModel = viewModel,
                            editMode = editSlot == 0,
                            onAddWidget = { onAddWidget(0) },
                            onEnterEditMode = { editSlot = 0 },
                            onExitEditMode = { editSlot = -1 },
                            modifier = Modifier.height(rowHeight).fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.height(rowHeight).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gap)
                        ) {
                            SlotCard(
                                stack = stacks.getOrElse(1) { WidgetStack() },
                                slotIndex = 1,
                                viewModel = viewModel,
                                editMode = editSlot == 1,
                                onAddWidget = { onAddWidget(1) },
                                onEnterEditMode = { editSlot = 1 },
                                onExitEditMode = { editSlot = -1 },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            SlotCard(
                                stack = stacks.getOrElse(2) { WidgetStack() },
                                slotIndex = 2,
                                viewModel = viewModel,
                                editMode = editSlot == 2,
                                onAddWidget = { onAddWidget(2) },
                                onEnterEditMode = { editSlot = 2 },
                                onExitEditMode = { editSlot = -1 },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }

                    WidgetLayoutTemplate.TWO_WIDE_ROWS -> {
                        SlotCard(
                            stack = stacks.getOrElse(0) { WidgetStack() },
                            slotIndex = 0,
                            viewModel = viewModel,
                            editMode = editSlot == 0,
                            onAddWidget = { onAddWidget(0) },
                            onEnterEditMode = { editSlot = 0 },
                            onExitEditMode = { editSlot = -1 },
                            modifier = Modifier.height(rowHeight).fillMaxWidth()
                        )
                        SlotCard(
                            stack = stacks.getOrElse(1) { WidgetStack() },
                            slotIndex = 1,
                            viewModel = viewModel,
                            editMode = editSlot == 1,
                            onAddWidget = { onAddWidget(1) },
                            onEnterEditMode = { editSlot = 1 },
                            onExitEditMode = { editSlot = -1 },
                            modifier = Modifier.height(rowHeight).fillMaxWidth()
                        )
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

    if (showTemplatePicker) {
        TemplatePickerDialog(
            current = template,
            onSelect = { tmpl ->
                viewModel.setTemplate(tmpl)
                showTemplatePicker = false
                editSlot = -1
            },
            onDismiss = { showTemplatePicker = false }
        )
    }
}

// ── Slot card ─────────────────────────────────────────────────────────────────

@Composable
private fun SlotCard(
    stack: WidgetStack,
    slotIndex: Int,
    viewModel: WidgetViewModel,
    editMode: Boolean,
    onAddWidget: () -> Unit,
    onEnterEditMode: () -> Unit,
    onExitEditMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CarColors.Surface)
    ) {
        if (stack.widgetIds.isEmpty()) {
            EmptySlot(onClick = onAddWidget)
        } else {
            StackContent(
                stack = stack,
                slotIndex = slotIndex,
                viewModel = viewModel,
                editMode = editMode,
                onAddWidget = onAddWidget,
                onEnterEditMode = onEnterEditMode,
                onExitEditMode = onExitEditMode
            )
        }
    }
}

// ── Stack content (VerticalPager + indicator + edit overlay) ──────────────────

@Composable
private fun StackContent(
    stack: WidgetStack,
    slotIndex: Int,
    viewModel: WidgetViewModel,
    editMode: Boolean,
    onAddWidget: () -> Unit,
    onEnterEditMode: () -> Unit,
    onExitEditMode: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { stack.widgetIds.size })

    Box(modifier = Modifier.fillMaxSize()) {
        // ── VerticalPager ─────────────────────────────────────────────────────
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !editMode,
            beyondViewportPageCount = 1  // preload adjacent page for smooth swipe
        ) { page ->
            val widgetId = stack.widgetIds[page]
            WidgetPage(
                widgetId = widgetId,
                viewModel = viewModel,
                onLongPress = onEnterEditMode
            )
        }

        // ── Stack indicator ───────────────────────────────────────────────────
        if (stack.widgetIds.size > 1) {
            val currentPage = pagerState.currentPage
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 7.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                repeat(stack.widgetIds.size) { i ->
                    val active = i == currentPage
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(if (active) 18.dp else 10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (active) Color.White else Color.White.copy(alpha = 0.28f)
                            )
                    )
                }
            }
        }

        // ── Edit mode overlay ─────────────────────────────────────────────────
        if (editMode) {
            val currentWidgetId = stack.widgetIds.getOrNull(pagerState.currentPage)

            // Scrim — visual only
            Box(modifier = Modifier.fillMaxSize().background(Color(0x88000000)))

            // Tap scrim to exit edit mode
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onTap = { onExitEditMode() }) }
            )

            // Action buttons row (centered)
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Delete current widget from stack
                if (currentWidgetId != null) {
                    ActionButton(
                        icon = Icons.Default.Delete,
                        color = Color(0xFFEF4444),
                        contentDescription = "Odebrat widget",
                        onClick = {
                            viewModel.removeWidgetFromStack(slotIndex, currentWidgetId)
                            onExitEditMode()
                        }
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                }

                // Add another widget to this slot's stack
                ActionButton(
                    icon = Icons.Default.Add,
                    color = CarColors.Go,
                    contentDescription = "Přidat widget do sady",
                    onClick = {
                        onExitEditMode()
                        onAddWidget()
                    }
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(color)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val up = waitForUpOrCancellation()
                    if (up != null) { up.consume(); onClick() }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(34.dp))
    }
}

// ── Single widget page inside the VerticalPager ───────────────────────────────

@Composable
private fun WidgetPage(
    widgetId: Int,
    viewModel: WidgetViewModel,
    onLongPress: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var cardSize by remember { mutableStateOf(IntSize.Zero) }

    val hostView = remember(widgetId) {
        val info = viewModel.appWidgetManager.getAppWidgetInfo(widgetId)
        val view = viewModel.appWidgetHost.createView(context, widgetId, info) as LongPressWidgetHostView
        view.setAppWidget(widgetId, info)
        view.setPadding(0, 0, 0, 0)
        view.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        view
    }
    hostView.onLongPress = onLongPress

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
}

// ── Empty slot ────────────────────────────────────────────────────────────────

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
                Icon(Icons.Default.Add, null, tint = CarColors.Text2, modifier = Modifier.size(28.dp))
            }
            Text("Přidat widget", color = CarColors.Text2, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
        Surface(shape = RoundedCornerShape(24.dp), color = CarColors.Surface, tonalElevation = 0.dp) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Rozvržení widgetů",
                    color = CarColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                WidgetLayoutTemplate.entries.forEach { tmpl ->
                    val selected = tmpl == current
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) CarColors.Accent.copy(alpha = 0.18f) else Color(0x0DFFFFFF))
                            .pointerInput(tmpl) { detectTapGestures(onTap = { onSelect(tmpl) }) }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    tmpl.labelCs(),
                                    color = if (selected) CarColors.Accent else CarColors.Text,
                                    fontSize = 14.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                                Text("${tmpl.slotCount} sloty", color = CarColors.Text2, fontSize = 11.sp)
                            }
                            if (selected) {
                                Box(
                                    Modifier.size(8.dp).clip(CircleShape).background(CarColors.Accent)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
