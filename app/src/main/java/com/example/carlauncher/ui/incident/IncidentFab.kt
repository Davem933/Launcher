package com.example.carlauncher.ui.incident

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.ui.theme.CarColors
import kotlin.math.roundToInt

private val FAB_SIZE = 64.dp
private val EDGE_MARGIN = 16.dp

/**
 * Draggable floating entry point for the Incident Recorder. The user drags it anywhere on
 * screen; the position is clamped to the viewport and persisted via [IncidentButtonViewModel].
 * A tap (no drag) opens the recorder.
 */
@Composable
fun IncidentFab(
    onOpen: () -> Unit,
    vm: IncidentButtonViewModel = hiltViewModel(),
) {
    val saved by vm.pos.collectAsStateWithLifecycle()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxWpx = constraints.maxWidth.toFloat()
        val maxHpx = constraints.maxHeight.toFloat()
        val sizePx = with(density) { FAB_SIZE.toPx() }
        val marginPx = with(density) { EDGE_MARGIN.toPx() }
        val maxX = (maxWpx - sizePx).coerceAtLeast(0f)
        val maxY = (maxHpx - sizePx).coerceAtLeast(0f)

        // Non-null once the user drags this session; otherwise the rendered position
        // follows the persisted value (or the default placement).
        var dragOffset by remember { mutableStateOf<Offset?>(null) }

        val o: Offset = dragOffset ?: run {
            val s = saved
            if (s != null) {
                Offset(
                    with(density) { s.first.dp.toPx() }.coerceIn(0f, maxX),
                    with(density) { s.second.dp.toPx() }.coerceIn(0f, maxY),
                )
            } else {
                Offset(maxX - marginPx.coerceAtMost(maxX), maxY / 2f)
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(o.x.roundToInt(), o.y.roundToInt()) }
                .size(FAB_SIZE)
                .shadow(14.dp, CircleShape)
                .clip(CircleShape)
                .background(CarColors.Surface)
                .border(3.dp, Color.White, CircleShape)
                .pointerInput(maxX, maxY) {
                    detectDragGestures(
                        onDragStart = { dragOffset = o },
                        onDragEnd = {
                            dragOffset?.let { d ->
                                vm.save(d.x.toDp().value, d.y.toDp().value)
                            }
                        },
                    ) { change, drag ->
                        change.consume()
                        val cur = dragOffset ?: o
                        dragOffset = Offset(
                            (cur.x + drag.x).coerceIn(0f, maxX),
                            (cur.y + drag.y).coerceIn(0f, maxY),
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onOpen() })
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = "Incident Recorder",
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
