package com.example.carlauncher.ui.dock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Aurora dock tiles (Claude Design "Car Launcher Apple v2", screen 2a): a vertical gradient
 * squircle with a white glyph, instead of the app's own launcher icon. Known app kinds get the
 * design's exact gradient + glyph; any other app gets a gradient picked from the same palette
 * and its themed-icon (monochrome) layer, or its initial when it has none.
 */

private data class AuroraGlyph(val top: Color, val bottom: Color, val icon: ImageVector)

private fun glyph(viewport: Float = 24f, build: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = viewport, viewportHeight = viewport)
        .apply(build).build()

private fun ImageVector.Builder.fillPath(d: String) =
    addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.White))

private fun ImageVector.Builder.strokePath(d: String, width: Float = 2f) =
    addPath(
        pathData = PathParser().parsePathString(d).toNodes(),
        fill = null,
        stroke = SolidColor(Color.White),
        strokeLineWidth = width,
        strokeLineJoin = StrokeJoin.Round
    )

// Path data copied 1:1 from the design's inline SVGs (24×24 viewBox).
private val NavGlyph = glyph { fillPath("M12 2.5 L19.5 20 L12 16.3 L4.5 20 Z") }
private val MusicGlyph = glyph {
    strokePath("M9 18V6l11-2v12")
    fillPath("M3.9 18a2.6 2.6 0 1 0 5.2 0a2.6 2.6 0 1 0 -5.2 0Z")
    fillPath("M14.9 16a2.6 2.6 0 1 0 5.2 0a2.6 2.6 0 1 0 -5.2 0Z")
}
private val PhoneGlyph = glyph {
    fillPath("M6.6 3.5c.6 0 1.1.4 1.3 1l1 3c.2.5 0 1.1-.4 1.4l-1.4 1.1a11 11 0 0 0 5.9 5.9l1.1-1.4c.3-.4.9-.6 1.4-.4l3 1c.6.2 1 .7 1 1.3V19c0 .8-.7 1.5-1.5 1.5C10.6 20.5 3.5 13.4 3.5 5c0-.8.7-1.5 1.5-1.5Z")
}
private val MessageGlyph = glyph {
    fillPath("M12 4c4.7 0 8.5 3.1 8.5 7s-3.8 7-8.5 7c-.9 0-1.8-.1-2.6-.3L5 19.5l1.2-3.4C4.6 14.8 3.5 13 3.5 11c0-3.9 3.8-7 8.5-7Z")
}
private val SpeakerGlyph = glyph {
    strokePath("M7 3h10a3 3 0 0 1 3 3v12a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3Z")
    fillPath("M8.8 14a3.2 3.2 0 1 0 6.4 0a3.2 3.2 0 1 0 -6.4 0Z")
    fillPath("M10.7 7a1.3 1.3 0 1 0 2.6 0a1.3 1.3 0 1 0 -2.6 0Z")
}
private val PodcastGlyph = glyph {
    fillPath("M12 3a7 7 0 0 1 4 12.7v-2.4a5 5 0 1 0-8 0v2.4A7 7 0 0 1 12 3Zm0 5a2.5 2.5 0 1 1 0 5 2.5 2.5 0 0 1 0-5Zm-1.5 6.5h3l-.6 6.5h-1.8Z")
}
private val CalendarGlyph = glyph {
    fillPath("M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Zm0 5v9h14v-9Zm2-8h2v4H7Zm8 0h2v4h-2Z")
}
private val BookGlyph = glyph {
    fillPath("M4 5c3 0 6 .8 8 2.3C14 5.8 17 5 20 5v13c-3 0-6 .8-8 2.3C10 18.8 7 18 4 18Z")
}
private val GearGlyph = glyph {
    fillPath("M12 8a4 4 0 1 1 0 8 4 4 0 0 1 0-8Zm-1-6h2l.5 3 2 .8 2.5-1.8 1.4 1.4-1.8 2.5.8 2 3 .5v2l-3 .5-.8 2 1.8 2.5-1.4 1.4-2.5-1.8-2 .8-.5 3h-2l-.5-3-2-.8-2.5 1.8-1.4-1.4 1.8-2.5-.8-2-3-.5v-2l3-.5.8-2L4.6 6l1.4-1.4L8.5 6.4l2-.8Z")
}

private val Blue   = Color(0xFF5AC8FA) to Color(0xFF0A7AFF)
private val Red    = Color(0xFFFF5E57) to Color(0xFFE5262E)
private val Green  = Color(0xFF5EF17A) to Color(0xFF28C840)
private val Orange = Color(0xFFFF9F45) to Color(0xFFFF7A00)
private val Purple = Color(0xFFD56EFC) to Color(0xFF8E3AE0)
private val Gray   = Color(0xFFA1A1A6) to Color(0xFF636366)

/** Gradients used for apps the design has no glyph for — same palette, picked by package. */
private val FallbackGradients = listOf(Blue, Red, Green, Orange, Purple, Gray)

private fun g(colors: Pair<Color, Color>, icon: ImageVector) = AuroraGlyph(colors.first, colors.second, icon)

/** Matches by package-name keywords, so e.g. every navigation app gets the blue arrow. */
private fun designGlyphFor(pkg: String): AuroraGlyph? {
    val p = pkg.lowercase()
    fun has(vararg k: String) = k.any { p.contains(it) }
    return when {
        has("maps", "mapy", "waze", "tomtom", "navigation", "here.app", "sygic", "osmand") -> g(Blue, NavGlyph)
        has("youtube.music", "spotify", "deezer", "tidal", "music", "soundcloud") -> g(Red, MusicGlyph)
        has("podcast", "pocketcasts") -> g(Purple, PodcastGlyph)
        has("audible", "audiobook", "audioknih") -> g(Orange, BookGlyph)
        has("dialer", "contacts", ".phone", "incallui") -> g(Green, PhoneGlyph)
        has("messaging", "mms", "whatsapp", "telegram", "signal", "messenger", "sms") -> g(Green, MessageGlyph)
        has("calendar") -> g(Red, CalendarGlyph)
        p == "com.android.settings" -> g(Gray, GearGlyph)
        has("radio", "speaker", "bluetooth") -> g(Orange, SpeakerGlyph)
        else -> null
    }
}

private sealed interface FallbackArt {
    data class Mono(val bitmap: ImageBitmap) : FallbackArt
    data class Initial(val letter: String) : FallbackArt
}

private fun fallbackArt(context: Context, pkg: String): FallbackArt {
    val pm = context.packageManager
    val icon = runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && icon is AdaptiveIconDrawable) {
        icon.monochrome?.let { mono ->
            val size = 144
            val bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            mono.setBounds(0, 0, size, size)
            mono.draw(android.graphics.Canvas(bm))
            return FallbackArt.Mono(bm.asImageBitmap())
        }
    }
    val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
        .getOrDefault(pkg.substringAfterLast('.'))
    return FallbackArt.Initial(label.firstOrNull()?.uppercase() ?: "?")
}

/** Full-size Aurora tile for one app. */
@Composable
internal fun AuroraAppTile(packageName: String, size: Dp, shape: Shape, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(shape)) {
        AuroraAppFace(packageName, glyphSize = size * 0.48f, modifier = Modifier.fillMaxSize())
    }
}

/** Split-screen slot: the two apps' faces side by side in one tile (nav left, music right). */
@Composable
internal fun AuroraSplitTile(pkg1: String, pkg2: String, size: Dp, shape: Shape, modifier: Modifier = Modifier) {
    Row(modifier.size(size).clip(shape)) {
        AuroraAppFace(pkg1, glyphSize = size * 0.34f, modifier = Modifier.weight(1f).fillMaxHeight())
        Box(Modifier.width(1.dp).fillMaxHeight().background(Color.Black.copy(alpha = 0.25f)))
        AuroraAppFace(pkg2, glyphSize = size * 0.34f, modifier = Modifier.weight(1f).fillMaxHeight())
    }
}

/** Empty slot: transparent tile with an inner hairline and a grey plus — the design's "+". */
@Composable
internal fun AuroraAddTile(size: Dp, shape: Shape) {
    Box(
        modifier = Modifier.size(size).border(1.5.dp, Color.White.copy(alpha = 0.18f), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = remember { glyph { strokePath("M12 5v14M5 12h14", width = 2.2f) } },
            contentDescription = "Přidat",
            tint = Color(0xFF98989D),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun AuroraAppFace(packageName: String, glyphSize: Dp, modifier: Modifier) {
    val context = LocalContext.current
    val design = remember(packageName) { designGlyphFor(packageName) }
    val top: Color
    val bottom: Color
    if (design != null) {
        top = design.top; bottom = design.bottom
    } else {
        val (t, b) = FallbackGradients[Math.floorMod(packageName.hashCode(), FallbackGradients.size)]
        top = t; bottom = b
    }
    Box(
        modifier = modifier.background(Brush.verticalGradient(listOf(top, bottom))),
        contentAlignment = Alignment.Center
    ) {
        if (design != null) {
            Icon(design.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(glyphSize))
        } else {
            when (val art = remember(packageName) { fallbackArt(context, packageName) }) {
                // The monochrome layer is an adaptive-icon layer: the glyph sits in its centre
                // ~45%, so draw it larger than the glyph box to match the design's glyph size.
                is FallbackArt.Mono -> Image(
                    bitmap = art.bitmap,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(glyphSize * 2.1f)
                )
                is FallbackArt.Initial -> Text(
                    art.letter,
                    color = Color.White,
                    fontSize = (glyphSize.value * 0.85f).sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 1.dp)
                )
            }
        }
    }
}
