package com.example.carlauncher.ui.navigation

import android.app.PendingIntent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.navigation.NavRepository
import com.example.carlauncher.ui.theme.CarColors

private val NavGreen    = Color(0xFF4ADE80)
private val BottomBg    = Color(0xFF111318)
private val SpeedLimitRed = Color(0xFFE53935)
private val CancelBg    = Color(0xFF252830)

@Composable
fun NavWidget(
    speedKmh: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val street            = NavRepository.maneuverStreet
    val distance          = NavRepository.maneuverDistance
    val icon              = NavRepository.maneuverIcon
    val eta               = NavRepository.eta
    val distanceRemaining = NavRepository.distanceRemaining
    val cancelIntent      = NavRepository.cancelIntent
    val progressFraction  = NavRepository.progressFraction

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(CarColors.Bg)
            .border(1.dp, CarColors.BorderSoft, RoundedCornerShape(24.dp)),
    ) {
        // ── Header — maneuver instruction ─────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Navigation,
                contentDescription = null,
                tint = CarColors.Accent,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = street.uppercase(),
                color = CarColors.Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // ── Center — icon + distance + street ─────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Maneuver arrow icon
            if (icon != null) {
                Image(
                    bitmap = remember(icon) { icon.asImageBitmap() },
                    contentDescription = "Maneuver",
                    modifier = Modifier.size(140.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = "Maneuver",
                    tint = Color.White,
                    modifier = Modifier.size(120.dp),
                )
            }

            Spacer(Modifier.height(20.dp))

            // "za 600 m" — "za" smaller, distance larger
            if (distance.isNotEmpty()) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontSize = 28.sp, fontWeight = FontWeight.Medium, color = NavGreen)) {
                            append("za ")
                        }
                        withStyle(SpanStyle(fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, color = NavGreen)) {
                            append(distance.trim())
                        }
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }

        // ── Bottom bar ────────────────────────────────────────────────────────
        NavBottomBar(
            speedKmh        = speedKmh,
            distRemaining   = distanceRemaining,
            eta             = eta,
            cancelIntent    = cancelIntent,
            progressFraction = progressFraction,
        )
    }
}

// ── Bottom status bar ─────────────────────────────────────────────────────────

@Composable
private fun NavBottomBar(
    speedKmh: Float,
    distRemaining: String,
    eta: String,
    cancelIntent: PendingIntent?,
    progressFraction: Float,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BottomBg)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── Speed + limit badge ───────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.wrapContentWidth(),
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = CarColors.Text)) {
                        append(speedKmh.toInt().toString())
                    }
                    withStyle(SpanStyle(fontSize = 11.sp, color = CarColors.Text3)) {
                        append(" km/h")
                    }
                }
            )
            Spacer(Modifier.width(10.dp))
            SpeedLimitBadge(limit = "50")
        }

        Spacer(Modifier.width(16.dp))

        // ── Distance remaining + progress bar (fills available space) ─────────
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = if (distRemaining.isNotEmpty()) "Zbývá $distRemaining" else "",
                color = CarColors.Text2,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(5.dp))
            // Full-width progress bar — green portion = driven fraction of route
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(CarColors.Surface3),
            ) {
                Box(
                    modifier = Modifier
                        .width(maxWidth * progressFraction)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(NavGreen)
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        // ── ETA ──────────────────────────────────────────────────────────────
        if (eta.isNotEmpty()) {
            Text(
                text = eta,
                color = CarColors.Text2,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.wrapContentWidth(),
            )
            Spacer(Modifier.width(12.dp))
        }

        // ── Ukončit ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(CancelBg)
                .then(
                    if (cancelIntent != null) Modifier.clickable {
                        try { cancelIntent.send() }
                        catch (_: PendingIntent.CanceledException) {}
                    } else Modifier
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Ukončit",
                tint = CarColors.Text2,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Ukončit",
                color = CarColors.Text2,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ── Speed limit roundel ───────────────────────────────────────────────────────

@Composable
private fun SpeedLimitBadge(limit: String) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(3.dp, SpeedLimitRed, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = limit,
            color = Color.Black,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}
