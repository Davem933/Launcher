package com.example.carlauncher.ui.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.ui.theme.CarColors

private val CardShape  = RoundedCornerShape(20.dp)
private val InnerShape = RoundedCornerShape(14.dp)

@Composable
fun QuickDestWidget(
    modifier: Modifier = Modifier,
    viewModel: QuickDestViewModel = hiltViewModel()
) {
    val homeAddress by viewModel.homeAddress.collectAsStateWithLifecycle()
    val workAddress by viewModel.workAddress.collectAsStateWithLifecycle()

    // null = closed; "home" / "work" = which slot is being edited
    var editTarget by remember { mutableStateOf<String?>(null) }
    var editValue  by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .background(CarColors.Surface, CardShape)
            .border(1.dp, CarColors.BorderSoft, CardShape)
            .padding(14.dp)
    ) {
        Text(
            text = "RYCHLÉ CÍLE",
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CarColors.Text3,
                letterSpacing = 0.08.em
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            QuickDestCard(
                label = "Domů",
                address = homeAddress,
                icon = Icons.Default.Home,
                onClick = { viewModel.navigateTo(homeAddress) },
                onLongPress = { editTarget = "home"; editValue = homeAddress },
                modifier = Modifier.weight(1f)
            )
            QuickDestCard(
                label = "Práce",
                address = workAddress,
                icon = Icons.Default.Work,
                onClick = { viewModel.navigateTo(workAddress) },
                onLongPress = { editTarget = "work"; editValue = workAddress },
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (editTarget != null) {
        AlertDialog(
            onDismissRequest = { editTarget = null },
            containerColor = CarColors.Surface2,
            titleContentColor = CarColors.Text,
            textContentColor = CarColors.Text2,
            title = {
                Text(
                    text = if (editTarget == "home") "Adresa Domů" else "Adresa Práce",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = editValue,
                    onValueChange = { editValue = it },
                    placeholder = {
                        Text(
                            "Např. Václavské náměstí 1, Praha",
                            color = CarColors.Text3
                        )
                    },
                    singleLine = false,
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CarColors.Accent,
                        unfocusedBorderColor = CarColors.Border,
                        focusedTextColor = CarColors.Text,
                        unfocusedTextColor = CarColors.Text,
                        cursorColor = CarColors.Accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editTarget == "home") viewModel.saveHome(editValue)
                        else viewModel.saveWork(editValue)
                        editTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CarColors.Go)
                ) {
                    Text("Uložit", color = Color(0xFF06281B), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) {
                    Text("Zrušit", color = CarColors.Text3)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickDestCard(
    label: String,
    address: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .background(CarColors.Surface2, InnerShape)
            .border(1.dp, CarColors.Border, InnerShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CarColors.Accent,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CarColors.Text)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (address.isBlank()) "Stiskni a drž pro nastavení" else address,
            style = TextStyle(
                fontSize = 12.sp,
                color = if (address.isBlank()) CarColors.Text3.copy(alpha = 0.6f) else CarColors.Text3
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
