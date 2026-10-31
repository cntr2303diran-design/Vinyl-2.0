package com.vinyl.launcher.ui.room.stage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.vinyl.launcher.ui.theme.Tokens
import kotlinx.coroutines.launch
import com.vinyl.launcher.data.RadioRules

/**
 * Radio kecil di meja. Ketuk = putar stasiun radio internet berikutnya dari daftar pengguna (jarum penala pindah ke
 * letak stasiun itu di skala); tahan = mulai atau batalkan timer tidur (timer tidur yang sama dengan di Pengaturan →
 * Musik). Lampu kecil menyala selama timer tidur berjalan. Untuk pembaca layar kedua aksi tersedia sebagai aksi khusus.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StageRadio(
    spot: PropSpot,
    look: PropLook,
    enabled: Boolean,
    sleepActive: Boolean,
    motionAllowed: Boolean,
    stationCount: Int,
    onPlay: () -> Int,
    onSleep: () -> Unit,
) {
    val needle = remember { Animatable(0.42f) }
    val scope = rememberCoroutineScope()
    fun play() {
        val at = onPlay()
        if (at < 0) return
        val target = RadioRules.needleAt(at, stationCount)
        scope.launch { if (motionAllowed) needle.animateTo(target, spring(dampingRatio = 0.55f, stiffness = 120f)) else needle.snapTo(target) }
    }
    val b = spot.bounds
    Canvas(
        Modifier
            .offset(b.left.dp, b.top.dp)
            .size(b.width.dp, b.height.dp)
            .clearAndSetSemantics { },
    ) {
        drawRadio(look, needle.value, sleepActive)
        if (look.aged) drawWear(seed = 11)
    }
    val h = spot.hit
    Box(
        Modifier
            .offset(h.left.dp, h.top.dp)
            .size(h.width.dp, h.height.dp)
            .semantics {
                contentDescription = "Radio pos"
                stateDescription = if (sleepActive) "Timer tidur menyala" else "Timer tidur mati"
                customActions = listOf(
                    CustomAccessibilityAction("Putar stasiun berikutnya") { play(); true },
                    CustomAccessibilityAction(if (sleepActive) "Batalkan timer tidur" else "Mulai timer tidur") { onSleep(); true },
                )
            }
            .combinedClickable(
                enabled = enabled, onClickLabel = "Putar stasiun berikutnya", role = Role.Button, onClick = { play() },
                onLongClickLabel = if (sleepActive) "Batalkan timer tidur" else "Mulai timer tidur", onLongClick = onSleep,
            ),
    )
}

private fun DrawScope.drawRadio(look: PropLook, needle: Float, sleepActive: Boolean) {
    val d = density
    val w = size.width
    val h = size.height
    val ink = Color(Tokens.Props.INK)
    val silver = Color(Tokens.Props.SILVER)
    val silverDark = Color(Tokens.Props.SILVER_DARK)
    val corner = CornerRadius(h * 0.12f)
    // Badan kayu: bayangan di meja, tepi gelap, isi bergradasi
    drawRoundRect(Color.Black.copy(alpha = 0.40f), Offset(h * 0.04f, h * 0.06f), Size(w, h), corner)
    drawRoundRect(ink, Offset.Zero, Size(w, h), corner)
    drawRoundRect(Brush.verticalGradient(listOf(look.wood, look.woodDark)), Offset(1.2f * d, 1.2f * d), Size(w - 2.4f * d, h - 2.4f * d), corner)
    drawRoundRect(Color.White.copy(alpha = 0.10f), Offset(1.2f * d, 1.2f * d), Size(w - 2.4f * d, h * 0.16f), corner)
    // Kisi speaker kain di kiri
    val gx = w * 0.07f; val gy = h * 0.16f; val gw = w * 0.42f; val gh = h * 0.68f
    drawRoundRect(Color.Black.copy(alpha = 0.55f), Offset(gx, gy), Size(gw, gh), CornerRadius(h * 0.06f))
    var x = gx + gw * 0.07f
    while (x < gx + gw - gw * 0.05f) {
        drawLine(look.trim.copy(alpha = 0.55f), Offset(x, gy + gh * 0.08f), Offset(x, gy + gh * 0.92f), strokeWidth = 1.1f * d)
        x += gw * 0.085f
    }
    // Jendela penala di kanan atas: muka krem, garis skala, jarum merah
    val dx = w * 0.56f; val dy = h * 0.16f; val dw = w * 0.37f; val dh = h * 0.27f
    drawRoundRect(ink, Offset(dx - 1.2f * d, dy - 1.2f * d), Size(dw + 2.4f * d, dh + 2.4f * d), CornerRadius(h * 0.04f))
    drawRoundRect(Color(Tokens.Props.CLOCK_FACE), Offset(dx, dy), Size(dw, dh), CornerRadius(h * 0.03f))
    for (i in 0..10) {
        val tx = dx + dw * (0.06f + 0.88f * i / 10f)
        drawLine(ink.copy(alpha = 0.7f), Offset(tx, dy + dh * (if (i % 5 == 0) 0.5f else 0.62f)), Offset(tx, dy + dh * 0.86f), strokeWidth = 1f * d)
    }
    val nx = dx + dw * (0.06f + 0.88f * needle.coerceIn(0f, 1f))
    drawLine(Color(Tokens.Props.NEEDLE), Offset(nx, dy + dh * 0.1f), Offset(nx, dy + dh * 0.92f), strokeWidth = 1.8f * d, cap = StrokeCap.Round)
    // Dua knob perak dengan penanda
    for ((i, kx) in listOf(0.66f, 0.84f).withIndex()) {
        val c = Offset(w * kx, h * 0.70f)
        val r = h * 0.105f
        drawCircle(ink, r + 1.2f * d, c)
        drawCircle(Brush.linearGradient(listOf(silver, silverDark), start = Offset(c.x - r, c.y - r), end = Offset(c.x + r, c.y + r)), r, c)
        drawLine(ink, c, polar(c, if (i == 0) 35f else -50f, r * 0.85f), strokeWidth = 1.6f * d, cap = StrokeCap.Round)
    }
    // Lampu timer tidur
    val led = Offset(w * 0.93f, h * 0.075f)
    if (sleepActive) drawCircle(Color(Tokens.Props.LED_ON).copy(alpha = 0.35f), 5.5f * d, led)
    drawCircle(ink, 2.9f * d, led)
    drawCircle(Color(if (sleepActive) Tokens.Props.LED_ON else Tokens.Props.LED_OFF), 2.1f * d, led)
    drawRoundRect(ink.copy(alpha = 0.6f), Offset(1.2f * d, 1.2f * d), Size(w - 2.4f * d, h - 2.4f * d), corner, style = Stroke(width = 0.8f * d))
}
