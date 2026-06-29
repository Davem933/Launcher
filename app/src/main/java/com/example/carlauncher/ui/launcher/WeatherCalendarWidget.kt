package com.example.carlauncher.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.data.calendar.CalendarEvent
import com.example.carlauncher.data.weather.WeatherData
import com.example.carlauncher.ui.theme.CarColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WeatherCalendarWidget(
    modifier: Modifier = Modifier,
    viewModel: WeatherCalendarViewModel = hiltViewModel(),
) {
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val events  by viewModel.events.collectAsStateWithLifecycle()

    SideEffect { Log.d("WeatherCalUI", "render: weather=$weather events=${events.size}") }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CarColors.Surface)
            .border(1.dp, CarColors.BorderSoft, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeatherPanel(weather = weather, modifier = Modifier.weight(1f))

        VerticalDivider(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 14.dp),
            thickness = 1.dp,
            color = CarColors.BorderSoft,
        )

        CalendarPanel(events = events, modifier = Modifier.weight(1.6f))
    }
}

// ── Weather left panel ────────────────────────────────────────────────────────

@Composable
private fun WeatherPanel(weather: WeatherData?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = wmoIcon(weather?.code),
            contentDescription = null,
            tint = wmoIconColor(weather?.code),
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (weather != null) "${weather.tempC}°C" else "...",
            color = CarColors.Text,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 30.sp,
        )
        Text(
            text = weather?.condition ?: "",
            color = CarColors.Text3,
            fontSize = 12.sp,
        )
    }
}

// ── Calendar right panel ──────────────────────────────────────────────────────

private val dateFmt = DateTimeFormatter.ofPattern("EEEE d. MMMM", Locale("cs", "CZ"))

@Composable
private fun CalendarPanel(events: List<CalendarEvent>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = LocalDate.now().format(dateFmt)
                .replaceFirstChar { it.uppercase() },
            color = CarColors.Text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (events.isEmpty()) {
            Text(
                text = "Žádné události",
                color = CarColors.Text3,
                fontSize = 12.sp,
            )
        } else {
            events.forEach { event -> EventRow(event = event) }
        }
    }
}

@Composable
private fun EventRow(event: CalendarEvent) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(event.color)),
        )
        Text(
            text = event.timeLabel,
            color = CarColors.Text3,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(44.dp),
        )
        Text(
            text = event.title,
            color = CarColors.Text2,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── WMO helpers ───────────────────────────────────────────────────────────────

private fun wmoIcon(code: Int?): ImageVector = when (code) {
    null         -> WbSunny
    0            -> Icons.Default.WbSunny
    1, 2, 3      -> Icons.Default.WbCloudy
    45, 48       -> Icons.Default.Cloud
    in 51..67    -> Icons.Default.Grain
    in 71..77    -> Icons.Default.AcUnit
    in 80..82    -> Icons.Default.Umbrella
    85, 86       -> Icons.Default.AcUnit
    in 95..99    -> Icons.Default.Thunderstorm
    else         -> Icons.Default.WbCloudy
}

private fun wmoIconColor(code: Int?): Color = when (code) {
    null         -> Color(0xFFFACC15)
    0            -> Color(0xFFFACC15)  // žlutá — slunce
    1, 2, 3      -> Color(0xFF94A3B8)  // šedo-modrá — polojasno
    45, 48       -> Color(0xFF78808C)  // tmavá šedá — mlha
    in 51..67    -> Color(0xFF60A5FA)  // světle modrá — déšť
    in 71..77    -> Color(0xFFBAE6FD)  // bledě modrá — sníh
    in 80..82    -> Color(0xFF60A5FA)  // světle modrá — přeháňky
    85, 86       -> Color(0xFFBAE6FD)  // bledě modrá — sněhové přeháňky
    in 95..99    -> Color(0xFFA78BFA)  // fialová — bouřka
    else         -> Color(0xFF94A3B8)
}

private val WbSunny = Icons.Default.WbSunny
