package com.example.carlauncher.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.navigation.NavRepository
import com.example.carlauncher.ui.map.MapWidget
import com.example.carlauncher.ui.navigation.NavAreaWidget
import com.example.carlauncher.ui.theme.CarColors

/** Which content the left panel shows. MAP is the default when no navigation is active. */
enum class PanelView { MAP, NAV }

/**
 * Active navigation always wins (safety) — otherwise fall back to the user's
 * manual choice for this session, defaulting to MAP if they haven't picked one.
 */
fun resolveEffectiveView(isNavActive: Boolean, manualView: PanelView?): PanelView =
    if (isNavActive) PanelView.NAV else manualView ?: PanelView.MAP

private const val LONG_PRESS_TIMEOUT_MS = 1500L
private val LONG_PRESS_SLOP = 12.dp

/**
 * Fires [onLongPress] after a 1500ms hold with <12dp movement. Never consumes
 * the down event, so taps/drags (map pan-zoom, buttons underneath) still work.
 */
private fun Modifier.detectPanelLongPress(onLongPress: () -> Unit): Modifier =
    this.pointerInput(Unit) {
        val slopPx = LONG_PRESS_SLOP.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val downPosition = down.position
            val longPressFired = try {
                withTimeout(LONG_PRESS_TIMEOUT_MS) {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.size > 1) return@withTimeout
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: return@withTimeout
                        if (!change.pressed) return@withTimeout
                        if ((change.position - downPosition).getDistance() > slopPx) {
                            return@withTimeout
                        }
                    }
                }
                false
            } catch (timeout: PointerEventTimeoutCancellationException) {
                true
            }
            if (longPressFired) onLongPress()
        }
    }

@Composable
private fun PanelChoiceCard(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) CarColors.Surface2 else CarColors.Surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) CarColors.Accent else CarColors.BorderSoft,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) CarColors.Accent else CarColors.Text2,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = CarColors.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PanelPickerSheet(
    currentView: PanelView,
    onSelect: (PanelView) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CarColors.Surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "ZOBRAZENÍ PANELU",
                color = CarColors.Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PanelChoiceCard(
                    icon = Icons.Default.Map,
                    label = "Mapa",
                    selected = currentView == PanelView.MAP,
                    onClick = { onSelect(PanelView.MAP) },
                    modifier = Modifier.weight(1f),
                )
                PanelChoiceCard(
                    icon = Icons.Default.Navigation,
                    label = "Navigace",
                    selected = currentView == PanelView.NAV,
                    onClick = { onSelect(PanelView.NAV) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Left-panel switcher: shows [MapWidget] by default, or [NavAreaWidget] when
 * navigation is active or manually selected. Long-press opens a picker sheet.
 */
@Composable
fun MapNavPanel(
    speedKmh: Float = 0f,
    speedLimitKmh: Int = 50,
    modifier: Modifier = Modifier,
) {
    var manualView by remember { mutableStateOf<PanelView?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    val effectiveView = resolveEffectiveView(NavRepository.isActive, manualView)

    Box(modifier = modifier.detectPanelLongPress(onLongPress = { showPicker = true })) {
        when (effectiveView) {
            PanelView.MAP -> MapWidget(
                modifier = Modifier.fillMaxSize(),
                onNavigate = { manualView = PanelView.NAV },
            )
            PanelView.NAV -> NavAreaWidget(
                speedKmh = speedKmh,
                speedLimitKmh = speedLimitKmh,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (showPicker) {
        PanelPickerSheet(
            currentView = effectiveView,
            onSelect = { chosen ->
                manualView = chosen
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}
