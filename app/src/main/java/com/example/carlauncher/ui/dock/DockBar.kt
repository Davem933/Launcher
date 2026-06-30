package com.example.carlauncher.ui.dock

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.withTimeoutOrNull
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.data.model.DockSlot
import com.example.carlauncher.ui.theme.CarColors

private val DockBorderTop = Color(0xFF252530)
private val SlotBg        = Color(0xFF1A1A28)
private val SlotBorder    = Color(0xFF2A2A38)
private val SlotShape     = RoundedCornerShape(14.dp)

@Composable
fun DockBar(
    modifier: Modifier = Modifier,
    onLaunchSplitScreen: (pkg1: String, pkg2: String) -> Unit = { _, _ -> },
    viewModel: DockViewModel = hiltViewModel()
) {
    val slots by viewModel.slots.collectAsStateWithLifecycle()
    var editMode   by remember { mutableStateOf(false) }
    var pickerSlot by remember { mutableIntStateOf(-1) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .size(88.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(CarColors.Surface)
            .border(width = 1.dp, color = CarColors.BorderSoft, shape = RoundedCornerShape(24.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Slot 0 — fixed Menu icon, not configurable
            MenuTile(onClick = { viewModel.openAppDrawer() })

            // Slots 1–6 — configurable app slots
            slots.forEachIndexed { index, slot ->
                DockSlotTile(
                    slot        = slot,
                    editMode    = editMode,
                    onClick     = {
                        if (editMode) {
                            pickerSlot = index
                        } else {
                            when (slot) {
                                is DockSlot.App         -> viewModel.launchApp(slot.packageName)
                                is DockSlot.SplitScreen -> onLaunchSplitScreen(
                                    slot.packageName1, slot.packageName2)
                                DockSlot.Empty          -> { pickerSlot = index; editMode = true }
                                DockSlot.Navigate       -> viewModel.launchNavigation()
                            }
                        }
                    },
                    onLongClick = { editMode = true; pickerSlot = index }
                )
            }
        }
    }

    if (editMode && pickerSlot == -1) {
        LaunchedEffect(Unit) { editMode = false }
    }

    if (pickerSlot >= 0) {
        SlotPicker(
            slotIndex     = pickerSlot,
            onAppSelected = { item ->
                viewModel.updateSlot(pickerSlot, DockSlot.App(item.packageName))
                pickerSlot = -1; editMode = false
            },
            onDismiss = { pickerSlot = -1; editMode = false }
        )
    }
}

// ── Fixed slot tiles ──────────────────────────────────────────────────────────

@Composable
private fun MenuTile(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(SlotShape)
            .background(CarColors.Surface2)
            .border(1.dp, CarColors.Border, SlotShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Apps,
            contentDescription = "Všechny aplikace",
            tint = CarColors.Text,
            modifier = Modifier.size(26.dp)
        )
    }
}

// ── Configurable slot tile ────────────────────────────────────────────────────

@Composable
private fun DockSlotTile(
    slot: DockSlot,
    editMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val wiggle = remember { Animatable(0f) }
    LaunchedEffect(editMode) {
        if (editMode) {
            while (true) {
                wiggle.animateTo( 3f, tween(80))
                wiggle.animateTo(-3f, tween(160))
                wiggle.animateTo( 0f, tween(80))
            }
        } else wiggle.snapTo(0f)
    }

    Box(
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer { rotationZ = wiggle.value }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val up = withTimeoutOrNull(1500L) { waitForUpOrCancellation() }
                    if (up == null) {
                        // Prst stále dole po 1.5s → long press
                        onLongClick()
                        waitForUpOrCancellation()
                    } else {
                        // Krátký tap
                        onClick()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when (slot) {
            is DockSlot.App         -> AppTile(slot.packageName)
            is DockSlot.SplitScreen -> SplitTile(slot.packageName1, slot.packageName2)
            DockSlot.Empty          -> EmptyTile()
            DockSlot.Navigate       -> { /* Navigovat is in MapWidget overlay, not in dock */ }
        }
    }
}

// ── Tile variants ─────────────────────────────────────────────────────────────

@Composable
private fun AppTile(packageName: String) {
    val icon = rememberAppIcon(packageName)
    AppIcon(drawable = icon, modifier = Modifier.size(52.dp).clip(SlotShape))
}

@Composable
private fun SplitTile(pkg1: String, pkg2: String) {
    val icon1 = rememberAppIcon(pkg1)
    val icon2 = rememberAppIcon(pkg2)

    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(SlotShape)
            .background(SlotBg)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            AppIcon(icon1, Modifier.weight(1f).fillMaxHeight())
            Box(Modifier.width(1.dp).fillMaxHeight().background(DockBorderTop))
            AppIcon(icon2, Modifier.weight(1f).fillMaxHeight())
        }
        // Split badge — bottom-right corner
        Box(
            modifier = Modifier
                .size(16.dp)
                .align(Alignment.BottomEnd)
                .background(Color(0xCC101018), RoundedCornerShape(topStart = 4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.CallSplit,
                contentDescription = null,
                tint = Color(0xFF888899),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun EmptyTile() {
    Box(
        modifier = Modifier
            .size(52.dp)
            .background(SlotBg, SlotShape)
            .border(1.dp, SlotBorder, SlotShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Add, contentDescription = "Přidat", tint = Color(0xFF444455),
             modifier = Modifier.size(24.dp))
    }
}

// ── Icon helpers ──────────────────────────────────────────────────────────────

@Composable
internal fun rememberAppIcon(packageName: String): Drawable? {
    val context = LocalContext.current
    return remember(packageName) {
        try { context.packageManager.getApplicationIcon(packageName) }
        catch (_: Exception) { null }
    }
}

@Composable
internal fun AppIcon(drawable: Drawable?, modifier: Modifier) {
    if (drawable != null) {
        val bmp = remember(drawable) {
            val size = 96
            val bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bm)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bm.asImageBitmap()
        }
        Image(bitmap = bmp, contentDescription = null, modifier = modifier)
    } else {
        Box(modifier = modifier.background(SlotBg))
    }
}

// Navigate button moved to MapWidget (bottom-right overlay) per final mockup
