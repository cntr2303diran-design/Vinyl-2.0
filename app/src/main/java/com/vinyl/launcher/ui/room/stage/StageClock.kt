package com.vinyl.launcher.ui.room.stage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.vinyl.launcher.ui.theme.Tokens
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * Jam dinding analog di dinding kanan atas (kembaran lampu di kiri atas), sesuai waktu perangkat; bingkainya mengikuti era.
 * Ketuk = buka daftar alarm di aplikasi jam sistem, tahan = buka timer. Jarum diperbarui tiap pergantian menit (tidak ada
 * jarum detik, jadi tidak ada gambar ulang tiap detik). [fixed] mengunci waktu (tangkapan layar).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StageClock(
    spot: PropSpot,
    look: PropLook,
    enabled: Boolean,
    fixed: LocalTime?,
    onOpenAlarms: () -> Unit,
    onOpenTimers: () -> Unit,
) {
    val time by produceState(initialValue = fixed ?: LocalTime.now(), fixed) {
        if (fixed != null) { value = fixed; return@produceState }
        while (true) {
            delay(ClockMath.msToNextMinute(System.currentTimeMillis()))
            value = LocalTime.now()
        }
    }
    val b = spot.bounds
    Canvas(
        Modifier
            .offset(b.left.dp, b.top.dp)
            .size(b.width.dp, b.height.dp)
            .clearAndSetSemantics { },
    ) {
        drawWallClock(look, time.hour, time.minute)
        if (look.aged) drawWear(seed = 3, round = true)
    }
    val h = spot.hit
    Box(
        Modifier
            .offset(h.left.dp, h.top.dp)
            .size(h.width.dp, h.height.dp)
            .semantics {
                contentDescription = "Jam dinding"
                stateDescription = ClockMath.spoken(time.hour, time.minute)
                customActions = listOf(CustomAccessibilityAction("Buka timer") { onOpenTimers(); true })
            }
            .combinedClickable(
                enabled = enabled, onClickLabel = "Buka alarm", role = Role.Button, onClick = onOpenAlarms,
                onLongClickLabel = "Buka timer", onLongClick = onOpenTimers,
            ),
    )
}

private fun DrawScope.drawWallClock(look: PropLook, hour: Int, minute: Int) {
    val d = density
    val c = center
    val r = size.minDimension / 2f
    val ink = Color(Tokens.Props.INK)
    // Bayangan lembut di dinding, tepi gelap, bingkai berkilau, lalu muka krem
    drawCircle(Color.Black.copy(alpha = 0.28f), r, c + Offset(0f, 1.5f * d))
    drawCircle(ink, r)
    drawCircle(Brush.linearGradient(listOf(look.rim, look.rimDark), start = Offset(c.x - r, c.y - r), end = Offset(c.x + r, c.y + r)), r - 1.2f * d, c)
    drawCircle(Color(Tokens.Props.CLOCK_FACE), r * 0.80f, c)
    if (look.aged) {
        // Muka menguning dari tepi, dan retak rambut di kaca
        drawCircle(
            Brush.radialGradient(listOf(Color.Transparent, Color(0xFF6B4F2A).copy(alpha = 0.34f)), center = c, radius = r * 0.80f),
            r * 0.80f, c,
        )
        drawLine(Color.Black.copy(alpha = 0.35f), c + Offset(r * 0.22f, -r * 0.74f), c + Offset(r * 0.04f, -r * 0.18f), strokeWidth = 0.8f * d)
        drawLine(Color.Black.copy(alpha = 0.35f), c + Offset(r * 0.04f, -r * 0.18f), c + Offset(r * 0.30f, r * 0.10f), strokeWidth = 0.8f * d)
    }
    drawCircle(ink.copy(alpha = 0.35f), r * 0.80f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.8f * d))
    // Penanda jam: tebal di 12, 3, 6, 9
    for (i in 0 until 12) {
        val major = i % 3 == 0
        drawLine(
            ink, polar(c, i * 30f, r * (if (major) 0.56f else 0.65f)), polar(c, i * 30f, r * 0.74f),
            strokeWidth = (if (major) 2.4f else 1.3f) * d, cap = StrokeCap.Round,
        )
    }
    // Jarum jam dan menit, lalu pin tengah
    drawLine(ink, c, polar(c, ClockMath.hourAngle(hour, minute), r * 0.40f), strokeWidth = 3.4f * d, cap = StrokeCap.Round)
    drawLine(ink, c, polar(c, ClockMath.minuteAngle(minute), r * 0.62f), strokeWidth = 2.2f * d, cap = StrokeCap.Round)
    drawCircle(look.rimDark, 2.6f * d, c)
    drawCircle(ink, 1.1f * d, c)
}
