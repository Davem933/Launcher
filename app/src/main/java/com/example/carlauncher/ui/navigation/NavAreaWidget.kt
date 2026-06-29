package com.example.carlauncher.ui.navigation

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.navigation.NavRepository
import com.example.carlauncher.ui.theme.CarColors

private data class NavApp(val pkg: String, val label: String, val color: Color)

private val NAV_APPS = listOf(
    NavApp("com.waze",                    "Waze",        Color(0xFF00BCD4)),
    NavApp("cz.seznam.mapy",              "Mapy.cz",     Color(0xFF4CAF50)),
    NavApp("com.google.android.apps.maps","Google Maps", Color(0xFF5C6BC0)),
)

/**
 * Top-level nav area: shows active turn-by-turn (NavWidget) when navigation
 * is running, otherwise shows the landing screen with app picker.
 */
@Composable
fun NavAreaWidget(
    speedKmh: Float = 0f,
    modifier: Modifier = Modifier,
) {
    if (NavRepository.isActive) {
        NavWidget(speedKmh = speedKmh, modifier = modifier)
    } else {
        NavLanding(modifier = modifier)
    }
}

// ── Landing screen ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavLanding(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(CarColors.Surface)
            .border(1.dp, CarColors.BorderSoft, RoundedCornerShape(24.dp))
    ) {
        // Decorative background arrow — faint, right-aligned
        Icon(
            imageVector = Icons.Default.Navigation,
            contentDescription = null,
            tint = CarColors.Surface3,
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.CenterEnd)
                .offset(x = 48.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // ── Header ───────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = null,
                    tint = CarColors.Accent,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "NAVIGACE",
                    color = CarColors.Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
            }

            // ── Center content ───────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Rounded square icon
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(CarColors.Surface2),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = null,
                            tint = CarColors.Text2,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Kde jedete?",
                        color = CarColors.Text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Spusťte navigaci pro zobrazení trasy",
                        color = CarColors.Text3,
                        fontSize = 12.sp,
                    )
                }
            }

            // ── Bottom buttons ───────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Primary — Navigovat
                Button(
                    onClick = { showPicker = true },
                    modifier = Modifier
                        .weight(1.8f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CarColors.Go),
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        tint = Color(0xFF06281B),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Navigovat",
                        color = Color(0xFF06281B),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }

                // Secondary — Domů
                QuickNavButton(
                    label = "Domů",
                    icon = Icons.Default.Home,
                    modifier = Modifier.weight(1f).height(52.dp),
                    onClick = { launchNavApp(context, "com.google.android.apps.maps") },
                )

                // Secondary — Práce
                QuickNavButton(
                    label = "Práce",
                    icon = Icons.Default.Work,
                    modifier = Modifier.weight(1f).height(52.dp),
                    onClick = { launchNavApp(context, "com.google.android.apps.maps") },
                )
            }
        }
    }

    // ── App picker bottom sheet ───────────────────────────────────────────────
    if (showPicker) {
        ModalBottomSheet(
            onDismissRequest = { showPicker = false },
            sheetState = sheetState,
            containerColor = CarColors.Surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            NavAppPickerContent(
                onAppSelected = { pkg ->
                    showPicker = false
                    launchNavApp(context, pkg)
                },
                onDismiss = { showPicker = false },
            )
        }
    }
}

// ── Quick destination button ──────────────────────────────────────────────────

@Composable
private fun QuickNavButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CarColors.Surface2)
            .border(1.dp, CarColors.BorderSoft, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = CarColors.Text2,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                color = CarColors.Text2,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ── App picker content ────────────────────────────────────────────────────────

@Composable
private fun NavAppPickerContent(
    onAppSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val pm = context.packageManager

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = "Spustit navigaci",
            color = CarColors.Text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Vyberte navigační aplikaci",
            color = CarColors.Text3,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(20.dp))

        NAV_APPS.forEachIndexed { index, app ->
            // Only show if installed
            val installed = pm.getLaunchIntentForPackage(app.pkg) != null
            if (!installed) return@forEachIndexed

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onAppSelected(app.pkg) }
                    .padding(vertical = 14.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // App color badge
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(app.color),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = app.label,
                    color = CarColors.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = CarColors.Text3,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (index < NAV_APPS.lastIndex) {
                HorizontalDivider(color = CarColors.BorderSoft, thickness = 0.5.dp)
            }
        }

        Spacer(Modifier.height(16.dp))

        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Zrušit",
                color = CarColors.Text3,
                fontSize = 15.sp,
            )
        }
    }
}

// ── Helper ────────────────────────────────────────────────────────────────────

private fun launchNavApp(context: android.content.Context, pkg: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ?: return
    context.startActivity(intent)
}
