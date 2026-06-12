package com.example.carlauncher.ui.launcher

import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.model.DockItem
import com.example.carlauncher.ui.theme.CarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppDrawer(
    onAppClick: (packageName: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var allApps by remember { mutableStateOf<List<DockItem>>(emptyList()) }
    var search  by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        allApps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            pm.queryIntentActivities(intent, 0)
                .sortedBy { it.loadLabel(pm).toString().lowercase() }
                .distinctBy { it.activityInfo.packageName }
                .map { ri: ResolveInfo ->
                    DockItem(
                        packageName = ri.activityInfo.packageName,
                        displayName = ri.loadLabel(pm).toString(),
                        icon        = ri.loadIcon(pm)
                    )
                }
        }
    }

    val filtered = remember(search, allApps) {
        if (search.isBlank()) allApps
        else allApps.filter {
            it.displayName.contains(search, ignoreCase = true) ||
            it.packageName.contains(search, ignoreCase = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CarColors.Bg.copy(alpha = 0.97f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Aplikace",
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = CarColors.Text
                    )
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Zavřít",
                        tint = CarColors.Text2
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Search
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Hledat aplikace…", color = CarColors.Text3) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = CarColors.Text3)
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CarColors.Accent,
                    unfocusedBorderColor = CarColors.Border,
                    focusedTextColor = CarColors.Text,
                    unfocusedTextColor = CarColors.Text,
                    cursorColor = CarColors.Accent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            if (allApps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Načítám aplikace…", style = TextStyle(fontSize = 14.sp, color = CarColors.Text3))
                }
            } else {
                // weight(1f) gives grid a bounded height inside the Column —
                // without it the Column is unbounded and LazyVerticalGrid can't scroll
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        DrawerAppItem(
                            app = app,
                            onClick = { onAppClick(app.packageName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerAppItem(app: DockItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val icon = app.icon
        if (icon != null) {
            val bmp = remember(icon) {
                val size = 96
                val bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bm)
                icon.setBounds(0, 0, size, size)
                icon.draw(canvas)
                bm.asImageBitmap()
            }
            Image(
                bitmap = bmp,
                contentDescription = app.displayName,
                modifier = Modifier.size(52.dp).clip(shape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(CarColors.Surface2, shape)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = app.displayName,
            style = TextStyle(fontSize = 11.sp, color = CarColors.Text2),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
