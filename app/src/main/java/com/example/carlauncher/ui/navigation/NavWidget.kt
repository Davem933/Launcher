package com.example.carlauncher.ui.navigation

import android.app.PendingIntent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.navigation.NavRepository
import com.example.carlauncher.ui.theme.CarColors

// ── Design tokens ─────────────────────────────────────────────────────────────
private val NavBg       = Color(0xFF1C1B1F)
private val NavGreen    = Color(0xFF34A853)
private val NavRed      = Color(0xCCE53935)   // semi-transparent cancel
private val BarDivider  = Color(0xFF2E2D33)
private val LabelColor  = CarColors.Text3

@Composable
fun NavWidget(modifier: Modifier = Modifier) {
    val isActive          = NavRepository.isActive
    val maneuverDistance  = NavRepository.maneuverDistance
    val maneuverStreet    = NavRepository.maneuverStreet
    val maneuverIcon      = NavRepository.maneuverIcon
    val eta               = NavRepository.eta
    val timeRemaining     = NavRepository.timeRemaining
    val distanceRemaining = NavRepository.distanceRemaining
    val cancelIntent      = NavRepository.cancelIntent

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(NavBg)
            .border(1.dp, CarColors.BorderSoft, RoundedCornerShape(16.dp))
    ) {
        if (!isActive) {
            NavPlaceholder(modifier = Modifier.fillMaxSize())
        } else {
            NavContent(
                maneuverDistance  = maneuverDistance,
                maneuverStreet    = maneuverStreet,
                maneuverIcon      = maneuverIcon,
                eta               = eta,
                timeRemaining     = timeRemaining,
                distanceRemaining = distanceRemaining,
                cancelIntent      = cancelIntent,
                modifier          = Modifier.fillMaxSize(),
            )
        }
    }
}

// ── Placeholder ───────────────────────────────────────────────────────────────

@Composable
private fun NavPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Navigation,
            contentDescription = null,
            tint = CarColors.Text3,
            modifier = Modifier.size(36.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Start navigation in Google Maps or Waze",
            color = CarColors.Text3,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
        )
    }
}

// ── Active navigation content ─────────────────────────────────────────────────

@Composable
private fun NavContent(
    maneuverDistance: String,
    maneuverStreet: String,
    maneuverIcon: Bitmap?,
    eta: String,
    timeRemaining: String,
    distanceRemaining: String,
    cancelIntent: PendingIntent?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // ── Header row: icon/distance + cancel ───────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ManeuverSection(
                icon     = maneuverIcon,
                distance = maneuverDistance,
                street   = maneuverStreet,
                modifier = Modifier.weight(1f),
            )

            if (cancelIntent != null) {
                CancelButton(cancelIntent = cancelIntent)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Trip summary bar ─────────────────────────────────────────────────
        TripSummaryBar(
            eta               = eta,
            timeRemaining     = timeRemaining,
            distanceRemaining = distanceRemaining,
        )
    }
}

// ── Maneuver section ──────────────────────────────────────────────────────────

@Composable
private fun ManeuverSection(
    icon: Bitmap?,
    distance: String,
    street: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Turn arrow icon
            if (icon != null) {
                Image(
                    bitmap = remember(icon) { icon.asImageBitmap() },
                    contentDescription = "Turn direction",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = "Navigation",
                    tint = NavGreen,
                    modifier = Modifier.size(80.dp),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Distance to maneuver — large bold green
                if (distance.isNotEmpty()) {
                    Text(
                        text = distance,
                        color = NavGreen,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                    )
                }

                // Street name — medium white
                if (street.isNotEmpty()) {
                    Text(
                        text = street,
                        color = CarColors.Text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
}

// ── Cancel button ─────────────────────────────────────────────────────────────

@Composable
private fun CancelButton(cancelIntent: PendingIntent) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(NavRed),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = {
                try {
                    cancelIntent.send()
                } catch (_: PendingIntent.CanceledException) {
                    // navigation already stopped on the system side
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Stop navigation",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ── Trip summary bar ──────────────────────────────────────────────────────────

@Composable
private fun TripSummaryBar(
    eta: String,
    timeRemaining: String,
    distanceRemaining: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CarColors.Surface2)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TripSummaryCell(label = "ETA",   value = eta,               emoji = "🕒")
        BarDividerLine()
        TripSummaryCell(label = "Zbývá", value = timeRemaining,     emoji = "⌛")
        BarDividerLine()
        TripSummaryCell(label = "Trasa", value = distanceRemaining, emoji = "🛣️")
    }
}

@Composable
private fun TripSummaryCell(label: String, value: String, emoji: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = emoji,
            fontSize = 14.sp,
        )
        Text(
            text = value.ifEmpty { "–" },
            color = CarColors.Text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = label,
            color = LabelColor,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun BarDividerLine() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(BarDivider)
    )
}
