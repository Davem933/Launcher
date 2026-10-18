package com.example.carlauncher.ui.dock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.data.model.DockPosition

/**
 * Lays out a pager page around the dock according to the user's [DockPosition]:
 * content above a horizontal dock (BOTTOM) or to the right of a vertical one (LEFT).
 * [content] always receives the remaining space as a plain `fillMaxSize()` box.
 */
@Composable
fun DockScaffold(
    modifier: Modifier = Modifier,
    onLaunchSplitScreen: (pkg1: String, pkg2: String) -> Unit = { _, _ -> },
    viewModel: DockViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    val position by viewModel.position.collectAsStateWithLifecycle()

    when (position) {
        DockPosition.BOTTOM -> Column(modifier = modifier) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) { content() }
            DockBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp),
                onLaunchSplitScreen = onLaunchSplitScreen
            )
        }

        DockPosition.LEFT -> Row(modifier = modifier) {
            DockBar(
                modifier = Modifier.padding(start = 12.dp),
                onLaunchSplitScreen = onLaunchSplitScreen
            )
            Box(modifier = Modifier.fillMaxHeight().weight(1f)) { content() }
        }
    }
}
