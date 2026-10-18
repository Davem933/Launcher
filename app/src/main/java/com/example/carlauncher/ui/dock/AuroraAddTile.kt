package com.example.carlauncher.ui.dock

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Aurora empty slot: transparent tile with an inner hairline and a grey plus — the design's "+".
 * App slots in Aurora keep the apps' real launcher icons (only reshaped to the squircle), so the
 * driver can tell the apps apart at a glance.
 */
@Composable
internal fun AuroraAddTile(size: Dp, shape: Shape) {
    Box(
        modifier = Modifier.size(size).border(1.5.dp, Color.White.copy(alpha = 0.18f), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Přidat",
            tint = Color(0xFF98989D),
            modifier = Modifier.size(24.dp)
        )
    }
}
