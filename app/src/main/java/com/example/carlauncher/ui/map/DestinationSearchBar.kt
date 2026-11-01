package com.example.carlauncher.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compact, content-sized "klidový stav" search trigger — matches the reference app's small
 * top-left pill (icon + "Hledat"), not the map's full width. Tapping it opens [SearchOverlay]
 * (owned by [com.example.carlauncher.ui.map.MapWidget], not by this composable, since the
 * overlay needs to cover the whole map panel, not just this button's TopCenter slot).
 */
@Composable
fun DestinationSearchBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome = LocalMapChrome.current
    Row(
        modifier = modifier
            .shadow(chrome.searchShadow, chrome.searchShape, clip = false)
            .clip(chrome.searchShape)
            .background(chrome.searchBg)
            .then(chrome.searchBorder?.let { Modifier.border(1.dp, it, chrome.searchShape) } ?: Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = chrome.searchIconTint,
        )
        Spacer(Modifier.width(10.dp))
        Text(chrome.searchLabel, color = chrome.searchFg, fontSize = 16.sp)
    }
}
