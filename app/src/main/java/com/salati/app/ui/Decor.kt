package com.salati.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.salati.app.domain.Prayer
import com.salati.app.domain.PrayerStatus
import com.salati.app.ui.theme.StatusColors
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

private val SunColor = Color(0xFFF5A83B)
private val SunDeep = Color(0xFFEE7B30)
private val MoonColor = Color(0xFF9B7BE0)
private val CloudColor = Color(0xFF8CC8F2)

fun statusAccent(s: PrayerStatus): Color = when (s) {
    PrayerStatus.NOT_RECORDED -> StatusColors.unrecorded
    PrayerStatus.ON_TIME -> StatusColors.onTime
    PrayerStatus.LATE -> StatusColors.late
    PrayerStatus.MISSED -> StatusColors.missed
}

/** Hijri date (Umm al-Qura calendar from java.time); may differ by a day from local sighting. */
@Composable
fun hijriDateText(date: LocalDate): String? {
    val locale = currentLocale()
    return remember(date, locale) {
        runCatching { HijrahDate.from(date).format(DateTimeFormatter.ofPattern("d MMMM y", locale)) }.getOrNull()
    }
}

/** Mosque skyline drawn with simple shapes (central dome, side domes, four minarets). */
@Composable
fun MosqueSilhouette(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        // main hall
        drawRect(color, Offset(cx - w * 0.26f, h * 0.62f), Size(w * 0.52f, h * 0.38f))
        dome(cx, h * 0.62f, w * 0.15f, color)
        drawCircle(color, w * 0.012f, Offset(cx, h * 0.62f - w * 0.15f * 1.9f - w * 0.02f))
        for (s in listOf(-1f, 1f)) {
            val sx = cx + s * w * 0.19f
            drawRect(color, Offset(sx - w * 0.07f, h * 0.74f), Size(w * 0.14f, h * 0.26f))
            dome(sx, h * 0.74f, w * 0.06f, color)
            for ((k, top) in listOf(0.31f to 0.12f, 0.41f to 0.34f)) {
                val mx = cx + s * w * k
                val mw = w * 0.014f
                val ty = h * top
                drawRect(color, Offset(mx - mw, ty), Size(mw * 2, h - ty))
                drawRect(color, Offset(mx - mw * 1.8f, ty + (h - ty) * 0.22f), Size(mw * 3.6f, h * 0.025f))
                val cap = Path().apply {
                    moveTo(mx - mw * 1.5f, ty)
                    lineTo(mx, ty - h * 0.1f)
                    lineTo(mx + mw * 1.5f, ty)
                    close()
                }
                drawPath(cap, color)
            }
        }
        drawRect(color, Offset(0f, h * 0.96f), Size(w, h * 0.04f))
    }
}

private fun DrawScope.dome(cx: Float, base: Float, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(cx - r, base)
        cubicTo(cx - r * 1.05f, base - r * 1.25f, cx - r * 0.25f, base - r * 1.45f, cx, base - r * 1.9f)
        cubicTo(cx + r * 0.25f, base - r * 1.45f, cx + r * 1.05f, base - r * 1.25f, cx + r, base)
        close()
    }
    drawPath(p, color)
}

private fun DrawScope.crescent(center: Offset, r: Float, color: Color) {
    val outer = Path().apply { addOval(Rect(center, r)) }
    val inner = Path().apply { addOval(Rect(Offset(center.x + r * 0.45f, center.y - r * 0.3f), r * 0.85f)) }
    val c = Path()
    c.op(outer, inner, PathOperation.Difference)
    drawPath(c, color)
}

@Composable
fun CrescentGlyph(modifier: Modifier = Modifier, color: Color = SunColor) {
    Canvas(modifier) { crescent(Offset(size.width / 2f, size.height / 2f), size.minDimension * 0.42f, color) }
}

/** Small illustrated icon for each prayer (sunrise, sun, sun+cloud, sunset, moon). */
@Composable
fun PrayerGlyph(prayer: Prayer, modifier: Modifier = Modifier.size(28.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.075f
        fun rays(center: Offset, r: Float, color: Color, angles: List<Int>) {
            for (deg in angles) {
                val a = Math.toRadians(deg.toDouble())
                val c = cos(a).toFloat()
                val s = sin(a).toFloat()
                drawLine(
                    color,
                    Offset(center.x + c * r * 1.4f, center.y + s * r * 1.4f),
                    Offset(center.x + c * r * 1.8f, center.y + s * r * 1.8f),
                    strokeWidth = stroke, cap = StrokeCap.Round,
                )
            }
        }
        when (prayer) {
            Prayer.FAJR -> {
                val c = Offset(w / 2f, h * 0.7f)
                val r = w * 0.22f
                clipRect(bottom = h * 0.7f) { drawCircle(SunColor, r, c) }
                rays(c, r, SunColor, listOf(180, 225, 270, 315, 360))
                drawLine(SunDeep, Offset(w * 0.08f, h * 0.7f), Offset(w * 0.92f, h * 0.7f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            Prayer.DHUHR -> {
                val c = Offset(w / 2f, h / 2f)
                drawCircle(SunColor, w * 0.2f, c)
                rays(c, w * 0.2f, SunColor, (0 until 360 step 45).toList())
            }
            Prayer.ASR -> {
                val c = Offset(w * 0.62f, h * 0.36f)
                drawCircle(SunColor, w * 0.18f, c)
                rays(c, w * 0.18f, SunColor, listOf(270, 315, 0))
                drawCircle(CloudColor, w * 0.13f, Offset(w * 0.36f, h * 0.62f))
                drawCircle(CloudColor, w * 0.17f, Offset(w * 0.55f, h * 0.57f))
                drawRoundRect(CloudColor, Offset(w * 0.2f, h * 0.6f), Size(w * 0.6f, h * 0.17f), CornerRadius(h * 0.085f))
            }
            Prayer.MAGHRIB -> {
                val c = Offset(w / 2f, h * 0.64f)
                clipRect(bottom = h * 0.64f) { drawCircle(SunDeep, w * 0.26f, c) }
                drawLine(SunDeep, Offset(w * 0.06f, h * 0.64f), Offset(w * 0.94f, h * 0.64f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(SunColor, Offset(w * 0.22f, h * 0.8f), Offset(w * 0.78f, h * 0.8f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            Prayer.ISHA -> {
                crescent(Offset(w * 0.45f, h * 0.52f), w * 0.3f, MoonColor)
                drawCircle(MoonColor, w * 0.05f, Offset(w * 0.8f, h * 0.22f))
            }
        }
    }
}

/** Rounded selectable pill used for prayer status choices. */
@Composable
fun StatusPill(text: String, selected: Boolean, accent: Color, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (selected) accent.copy(alpha = 0.16f) else cs.surfaceContainerHigh,
        border = if (selected) BorderStroke(1.5.dp, accent) else BorderStroke(1.dp, cs.outlineVariant),
        modifier = modifier.heightIn(min = 48.dp).alpha(if (enabled) 1f else 0.45f),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) cs.onSurface else cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
