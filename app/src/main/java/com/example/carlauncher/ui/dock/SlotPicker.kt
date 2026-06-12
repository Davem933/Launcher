package com.example.carlauncher.ui.dock

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import com.example.carlauncher.data.model.DockItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SheetBg   = Color(0xFF14141E)
private val SearchBg  = Color(0xFF1E1E2A)
private val SearchBorder = Color(0xFF2E2E3A)
private val TextColor = Color.White
private val SubText   = Color(0xFF888899)
private val ItemShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotPicker(
    slotIndex: Int,
    onAppSelected: (DockItem) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()

    var query by remember { mutableStateOf("") }
    var allApps by remember { mutableStateOf<List<DockItem>>(emptyList()) }

    // Load installed launcher apps once on open
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

    val filtered = remember(query, allApps) {
        if (query.isEmpty()) allApps
        else allApps.filter {
            it.displayName.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBg
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {

            Text(
                text = "Vyber appku pro slot ${slotIndex + 1}",
                style = TextStyle(fontSize = 16.sp, color = TextColor),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Search field
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                cursorBrush = SolidColor(Color(0xFF22C55E)),
                textStyle = TextStyle(fontSize = 14.sp, color = TextColor),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SearchBg, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        if (query.isEmpty()) {
                            Text("Hledat appku…", style = TextStyle(fontSize = 14.sp, color = SubText))
                        }
                        inner()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SearchBg, RoundedCornerShape(10.dp))
            )

            Spacer(Modifier.height(12.dp))

            if (allApps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Načítám appky…", style = TextStyle(fontSize = 14.sp, color = SubText))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppGridItem(app = app, onClick = { onAppSelected(app) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AppGridItem(app: DockItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(ItemShape)
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
                modifier = Modifier.size(56.dp)
            )
        } else {
            Box(
                modifier = Modifier.size(56.dp)
                    .background(Color(0xFF1E1E2A), RoundedCornerShape(12.dp))
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = app.displayName,
            style = TextStyle(fontSize = 10.sp, color = SubText),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
