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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.withTimeout
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.data.model.DockPosition
import com.example.carlauncher.data.model.DockSlot
import com.example.carlauncher.data.model.LauncherStyle
import com.example.carlauncher.ui.theme.CarColors

private val DockBorderTop = Color(0xFF252530)
private val SlotBg        = Color(0xFF1A1A28)
private val SlotBorder    = Color(0xFF2A2A38)
private val SlotShape     = CircleShape
private val MenuSize      = 48.dp
private val SlotSize      = 60.dp
private val SlotSizeCompact = 52.dp   // vertical dock — 56dp touch target incl. padding

// Aurora (Apple v2, screen 2a): squircle-ish tiles with a hairline inner outline.
private val AuroraTileSize    = 58.dp
private val AuroraTileShape   = RoundedCornerShape(13.dp)
private val AuroraTileOutline = Color.White.copy(alpha = 0.20f)
private val AuroraMenuBg      = Color.White.copy(alpha = 0.10f)
private val AuroraGlyph       = Color(0xFFF5F5F7)
private val AuroraPillBg      = Color(0x991E1E20)   // rgba(30,30,32,.6)
private val AuroraPillOutline = Color.White.copy(alpha = 0.14f)
private val AuroraPillShape   = RoundedCornerShape(30.dp)
private val AuroraDockWidth   = 76.dp

@Composable
fun DockBar(
    modifier: Modifier = Modifier,
    onLaunchSplitScreen: (pkg1: String, pkg2: String) -> Unit = { _, _ -> },
    viewModel: DockViewModel = hiltViewModel()
) {
    val slots by viewModel.slots.collectAsStateWithLifecycle()
    val position by viewModel.position.collectAsStateWithLifecycle()
    val style    by viewModel.style.collectAsStateWithLifecycle()
    var editMode   by remember { mutableStateOf(false) }
    var pickerSlot by remember { mutableIntStateOf(-1) }

    val vertical = position == DockPosition.LEFT
    val aurora = style == LauncherStyle.AURORA
    // 8 slots + menu + status have to fit the ~686dp screen height in the vertical dock.
    val slotSize = when {
        aurora   -> AuroraTileSize
        vertical -> SlotSizeCompact
        else     -> SlotSize
    }

    val slotTiles: @Composable () -> Unit = {
        slots.forEachIndexed { index, slot ->
            // The 2 split-screen slots (navigation + YouTube Music) are permanently
            // fixed — no long-press edit, no picker, not affected by another slot's
            // edit mode. Every other slot stays freely user-configurable as before.
            val locked = slot is DockSlot.SplitScreen
            DockSlotTile(
                slot        = slot,
                slotSize    = slotSize,
                aurora      = aurora,
                editMode   = editMode && !locked,
                onClick     = {
                    when {
                        slot is DockSlot.SplitScreen -> onLaunchSplitScreen(
                            slot.packageName1, slot.packageName2)
                        editMode                     -> pickerSlot = index
                        slot is DockSlot.App         -> viewModel.launchApp(slot.packageName)
                        slot == DockSlot.Empty       -> { pickerSlot = index; editMode = true }
                        slot == DockSlot.Navigate    -> viewModel.launchNavigation()
                    }
                },
                onLongClick = {
                    if (!locked) { editMode = true; pickerSlot = index }
                }
            )
        }
    }

    // No shelf/card background — tiles float directly over whatever is behind the dock,
    // each tile carries its own shadow for legibility instead.
    if (aurora && vertical) {
        Column(
            modifier = modifier
                .fillMaxHeight()
                .width(AuroraDockWidth)
                .background(Color.Black)
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AuroraDockStatus()

            // Tiles already carry a 4dp touch gutter, so 2dp spacing reads as ~6dp and
            // keeps 8 slots + menu + status inside the ~686dp screen height.
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) { slotTiles() }

            AuroraMenuTile(onClick = { viewModel.openAppDrawer() })
        }
    } else if (aurora) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .size(88.dp)
        ) {
            // Glass pill: app tiles, a hairline divider, then the all-apps tile.
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(AuroraPillShape)
                    .background(AuroraPillBg)
                    .border(0.5.dp, AuroraPillOutline, AuroraPillShape)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                slotTiles()
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .width(1.dp)
                        .height(40.dp)
                        .background(AuroraTileOutline)
                )
                AuroraMenuTile(onClick = { viewModel.openAppDrawer() })
            }

            AuroraDockStatus(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 20.dp)
            )
        }
    } else if (vertical) {
        Column(
            modifier = modifier
                .fillMaxHeight()
                .width(88.dp)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DockStatusCluster(vertical = true)

            // Slots centered in the space between the status cluster (top) and menu (bottom).
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) { slotTiles() }

            MenuTile(onClick = { viewModel.openAppDrawer() })
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .size(88.dp)
        ) {
            // Menu sits pinned near the left edge, independent of the slot cluster below.
            MenuTile(
                onClick = { viewModel.openAppDrawer() },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 20.dp)
            )

            // Battery / time / weather — pinned near the right edge, mirroring the Menu
            // tile (Android Auto style status cluster).
            DockStatusCluster(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 20.dp)
            )

            // Configurable app slots — their own compact group, centered in the full dock width.
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) { slotTiles() }
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
private fun MenuTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(MenuSize)
            .shadow(6.dp, SlotShape, clip = false)
            .clip(SlotShape)
            .background(CarColors.Surface2)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Apps,
            contentDescription = "Všechny aplikace",
            tint = CarColors.Text,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun AuroraMenuTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(AuroraTileSize)
            .clip(AuroraTileShape)
            .background(AuroraMenuBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // 2×2 rounded squares — the design's all-apps glyph.
        Canvas(Modifier.size(22.dp)) {
            val gap = size.width * 0.16f
            val cell = (size.width - gap) / 2f
            for (row in 0..1) for (col in 0..1) {
                drawRoundRect(
                    color = AuroraGlyph,
                    topLeft = Offset(col * (cell + gap), row * (cell + gap)),
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(cell * 0.28f),
                )
            }
        }
    }
}

/** Aurora tiles get a 0.5dp white hairline on the inside of their shape. */
private fun Modifier.auroraOutline(aurora: Boolean, shape: Shape): Modifier =
    if (aurora) border(0.5.dp, AuroraTileOutline, shape) else this

// ── Configurable slot tile ────────────────────────────────────────────────────

@Composable
private fun DockSlotTile(
    slot: DockSlot,
    slotSize: Dp,
    aurora: Boolean,
    editMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val shape = if (aurora) AuroraTileShape else SlotShape
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
            .size(slotSize + 4.dp)
            .graphicsLayer { rotationZ = wiggle.value }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    try {
                        withTimeout(1500L) {
                            val up = waitForUpOrCancellation()
                            if (up != null) onClick()   // prst zvednut před timeoutem → tap
                            // up == null → gesto zrušeno jiným handlerem → nic
                        }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        // Prst stále dole po 1.5s → skutečný long press.
                        // waitForUpOrCancellation() throws Compose's own
                        // PointerEventTimeoutCancellationException on timeout, NOT
                        // kotlinx.coroutines.TimeoutCancellationException — catching the
                        // wrong type here silently swallowed every long-press forever.
                        onLongClick()
                        waitForUpOrCancellation()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            // Aurora draws the design's gradient + white-glyph tiles, not the apps' own icons.
            aurora && slot is DockSlot.App -> AuroraAppTile(
                slot.packageName, slotSize, shape,
                Modifier.shadow(6.dp, shape, clip = false).auroraOutline(true, shape)
            )
            aurora && slot is DockSlot.SplitScreen -> AuroraSplitTile(
                slot.packageName1, slot.packageName2, slotSize, shape,
                Modifier.shadow(6.dp, shape, clip = false).auroraOutline(true, shape)
            )
            aurora && slot == DockSlot.Empty -> AuroraAddTile(slotSize, shape)
            slot is DockSlot.App         -> AppTile(slot.packageName, slotSize, shape, aurora)
            slot is DockSlot.SplitScreen -> SplitTile(slot.packageName1, slot.packageName2, slotSize, shape, aurora)
            slot == DockSlot.Empty       -> EmptyTile(slotSize, shape)
            else                         -> { /* Navigovat is in MapWidget overlay, not in dock */ }
        }
    }
}

// ── Tile variants ─────────────────────────────────────────────────────────────

@Composable
private fun AppTile(packageName: String, size: Dp, shape: Shape, aurora: Boolean) {
    val icon = rememberAppIcon(packageName)
    AppIcon(
        drawable = icon,
        modifier = Modifier
            .size(size)
            .shadow(6.dp, shape, clip = false)
            .clip(shape)
            .auroraOutline(aurora, shape)
    )
}

@Composable
private fun SplitTile(pkg1: String, pkg2: String, size: Dp, shape: Shape, aurora: Boolean) {
    val icon1 = rememberAppIcon(pkg1)
    val icon2 = rememberAppIcon(pkg2)

    Box(
        modifier = Modifier
            .size(size)
            .shadow(6.dp, shape, clip = false)
            .clip(shape)
            .background(SlotBg)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            AppIcon(icon1, Modifier.weight(1f).fillMaxHeight())
            Box(Modifier.width(1.dp).fillMaxHeight().background(DockBorderTop))
            AppIcon(icon2, Modifier.weight(1f).fillMaxHeight())
        }
        // Split badge — bottom-right, round to match the circular tile
        Box(
            modifier = Modifier
                .size(18.dp)
                .align(Alignment.BottomEnd)
                .background(Color(0xCC101018), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.CallSplit,
                contentDescription = null,
                tint = Color(0xFF888899),
                modifier = Modifier.size(11.dp)
            )
        }
        // Drawn last so the hairline sits over both icon halves.
        Box(Modifier.fillMaxSize().auroraOutline(aurora, shape))
    }
}

@Composable
private fun EmptyTile(size: Dp, shape: Shape) {
    Box(
        modifier = Modifier
            .size(size)
            .background(SlotBg.copy(alpha = 0.6f), shape)
            .border(1.dp, SlotBorder, shape),
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
