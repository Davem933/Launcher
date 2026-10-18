package com.example.carlauncher.ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.carlauncher.BuildConfig
import com.example.carlauncher.data.model.DockPosition
import com.example.carlauncher.data.model.LauncherStyle
import com.example.carlauncher.ui.dock.DockViewModel
import com.example.carlauncher.ui.theme.CarColors

@Composable
fun LauncherSettingsScreen(
    onDismiss: () -> Unit,
    dockViewModel: DockViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var permissions by remember { mutableStateOf(permissionItems(context)) }
    val dockPosition by dockViewModel.position.collectAsStateWithLifecycle()
    var showDockPicker by remember { mutableStateOf(false) }
    val launcherStyle by dockViewModel.style.collectAsStateWithLifecycle()
    var showStylePicker by remember { mutableStateOf(false) }

    // Re-check on return from system settings. Activity lifecycle, not LocalLifecycleOwner —
    // the latter is per-page from HorizontalPager and need not reach RESUMED.
    val activity = context as ComponentActivity
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissions = permissionItems(context)
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    BackHandler(onBack = onDismiss)

    if (showDockPicker) {
        ChoiceDialog(
            title = "Poloha doku",
            options = DockPosition.entries,
            current = dockPosition,
            label = { it.label() },
            icon = { it.icon() },
            onSelect = { dockViewModel.setPosition(it); showDockPicker = false },
            onDismiss = { showDockPicker = false }
        )
    }
    if (showStylePicker) {
        ChoiceDialog(
            title = "Vzhled",
            options = LauncherStyle.entries,
            current = launcherStyle,
            label = { it.label() },
            icon = { it.icon() },
            onSelect = { dockViewModel.setStyle(it); showStylePicker = false },
            onDismiss = { showStylePicker = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CarColors.Bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Nastavení",
                    style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = CarColors.Text)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Zavřít", tint = CarColors.Text2)
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { SectionHeader("Vzhled") }
                item {
                    ValueRow(
                        icon = Icons.Default.Palette,
                        title = "Vzhled",
                        value = launcherStyle.label(),
                        onClick = { showStylePicker = true }
                    )
                }
                item {
                    ValueRow(
                        icon = Icons.Default.ViewAgenda,
                        title = "Dock",
                        value = dockPosition.label(),
                        onClick = { showDockPicker = true }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
                item { SectionHeader("Oprávnění") }
                items(permissions, key = { it.id }) { item ->
                    PermissionRow(
                        item = item,
                        onClick = { openSystemSettings(context, item.settingsIntent) }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
                item { SectionHeader("O aplikaci") }
                item { InfoRow("Aplikace", "CarLauncher") }
                item { InfoRow("Verze", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") }
                item { InfoRow("Build", BuildConfig.BUILD_TYPE) }
            }
        }
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    current: T,
    label: (T) -> String,
    icon: (T) -> ImageVector,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = CarColors.Surface) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    title,
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = CarColors.Text),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                options.forEach { option ->
                    val selected = option == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) CarColors.Accent.copy(alpha = 0.18f) else CarColors.Surface2)
                            .clickable { onSelect(option) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            icon(option), contentDescription = null,
                            tint = if (selected) CarColors.Accent else CarColors.Text2,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            label(option),
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) CarColors.Accent else CarColors.Text
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Icon(
                                Icons.Default.CheckCircle, contentDescription = "Vybráno",
                                tint = CarColors.Accent, modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ValueRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(CarColors.Surface, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = CarColors.Text2, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            title,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = CarColors.Text),
            modifier = Modifier.weight(1f)
        )
        Text(value, style = TextStyle(fontSize = 15.sp, color = CarColors.Text2))
    }
}

private fun DockPosition.label(): String = when (this) {
    DockPosition.BOTTOM -> "Dole (vodorovně)"
    DockPosition.LEFT   -> "Vlevo (svisle)"
}

private fun DockPosition.icon(): ImageVector = when (this) {
    DockPosition.BOTTOM -> Icons.Default.ViewAgenda
    DockPosition.LEFT   -> Icons.Default.ViewSidebar
}

private fun LauncherStyle.label(): String = when (this) {
    LauncherStyle.CLASSIC -> "Klasický"
    LauncherStyle.AURORA  -> "Aurora"
}

private fun LauncherStyle.icon(): ImageVector = when (this) {
    LauncherStyle.CLASSIC -> Icons.Default.Dashboard
    LauncherStyle.AURORA  -> Icons.Default.AutoAwesome
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CarColors.Text3),
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun PermissionRow(item: PermissionItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(CarColors.Surface, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(item.id.icon(), contentDescription = null, tint = CarColors.Text2, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = CarColors.Text))
            Text(item.description, style = TextStyle(fontSize = 13.sp, color = CarColors.Text3))
        }
        Spacer(Modifier.width(16.dp))
        Icon(
            imageVector = if (item.granted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = if (item.granted) "Povoleno" else "Chybí",
            tint = if (item.granted) CarColors.Go else CarColors.Warn,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(CarColors.Surface, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = TextStyle(fontSize = 15.sp, color = CarColors.Text2))
        Text(value, style = TextStyle(fontSize = 15.sp, color = CarColors.Text))
    }
}

private fun PermissionId.icon(): ImageVector = when (this) {
    PermissionId.NOTIFICATIONS -> Icons.Default.Notifications
    PermissionId.LOCATION      -> Icons.Default.LocationOn
    PermissionId.CALENDAR      -> Icons.Default.CalendarMonth
    PermissionId.CAMERA        -> Icons.Default.PhotoCamera
    PermissionId.HOME_LAUNCHER -> Icons.Default.Home
}
