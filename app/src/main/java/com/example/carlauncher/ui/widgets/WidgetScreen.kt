package com.example.carlauncher.ui.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.ui.dock.DockBar
import com.example.carlauncher.ui.launcher.StatusBar

@Composable
fun WidgetScreen(
    onLaunchSplitScreen: (pkg1: String, pkg2: String) -> Unit,
    onAddWidget: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WidgetViewModel = hiltViewModel()
) {
    val slots by viewModel.slots.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.appWidgetHost.startListening()
                Lifecycle.Event.ON_STOP  -> viewModel.appWidgetHost.stopListening()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        StatusBar(
            gpsFix = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        )
        WidgetCanvas(
            slots = slots,
            appWidgetHost = viewModel.appWidgetHost,
            appWidgetManager = viewModel.appWidgetManager,
            onAddWidget = onAddWidget,
            onRemoveWidget = viewModel::removeWidget,
            onMoveWidget = viewModel::moveWidget,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        DockBar(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 8.dp),
            onLaunchSplitScreen = onLaunchSplitScreen
        )
    }
}
