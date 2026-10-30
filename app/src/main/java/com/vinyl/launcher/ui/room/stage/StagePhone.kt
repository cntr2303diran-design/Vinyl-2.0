package com.vinyl.launcher.ui.room.stage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.vinyl.launcher.ui.theme.Tokens
import kotlinx.coroutines.launch

/** Letak bagian telepon putar di dalam kotaknya (dp, relatif ke pojok kiri atas kotak); dipakai gambar dan sentuhan. */
internal class PhoneGeometry(val width: Float, val height: Float) {
    val handsetH = height * 0.20f
    val bodyTop = height * 0.17f
    val bodyH = height - bodyTop
    val dialR = minOf(bodyH * 0.45f, width * 0.30f)
    val dialCx = width / 2f
    val dialCy = bodyTop + bodyH * 0.52f
}

/**
 * Telepon putar di meja. Putar cakram: sentuh lubang sebuah angka, seret searah jarum jam sampai penahan jari, lalu lepas;
 * cakram kembali pelan. Angka 1–9 membuka kontak pintasan (diatur di Pengaturan → Benda ruangan), angka 0 membuka aplikasi
 * telepon, ketuk badan telepon juga membuka aplikasi telepon. Tidak ada izin panggilan: yang dibuka hanya aplikasi telepon atau
 * kartu kontak, jadi tidak ada panggilan yang berjalan tanpa tombol hijau dari aplikasi itu. Pembaca layar mendapat aksi
 * khusus untuk tiap angka (seretan tidak bisa dipakai TalkBack).
 */
@Composable
internal fun StagePhone(
    spot: PropSpot,
    look: PropLook,
    enabled: Boolean,
    motionAllowed: Boolean,
    /** Nama kontak pintasan per angka 1–9 (yang sudah diisi). */
    favorites: Map<Int, String>,
    onDial: (Int) -> Unit,
    onOpenPhone: () -> Unit,
) {
    val h = spot.hit
    val b = spot.bounds
    val dx = b.left - h.left
    val dy = b.top - h.top
    val geo = remember(b) { PhoneGeometry(b.width, b.height) }
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .offset(h.left.dp, h.top.dp)
            .size(h.width.dp, h.height.dp)
            .semantics {
                contentDescription = "Telepon putar"
                role = Role.Button
                onClick(label = "Buka aplikasi telepon") { onOpenPhone(); true }
                stateDescription = if (favorites.isEmpty()) "Belum ada pintasan kontak" else "${favorites.size} pintasan kontak"
                customActions = buildList {
                    add(CustomAccessibilityAction("Buka aplikasi telepon") { onOpenPhone(); true })
                    for (digit in 1..9) {
                        val who = favorites[digit]
                        add(CustomAccessibilityAction(if (who != null) "Putar angka $digit, $who" else "Putar angka $digit, belum diisi") { onDial(digit); true })
                    }
                }
            }
            .pointerInput(enabled, geo, dx, dy, motionAllowed) {
                if (!enabled) return@pointerInput
                val cx = (dx + geo.dialCx) * density
                val cy = (dy + geo.dialCy) * density
                val ring = geo.dialR * density
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val dist = (down.position - Offset(cx, cy)).getDistance()
                    val digit = if (dist in ring * 0.45f..ring * 1.02f) RotaryDial.digitAt(RotaryDial.angleOf(cx, cy, down.position.x, down.position.y)) else null
                    if (digit == null) {
                        // Bukan lubang cakram: ketuk badan telepon = buka aplikasi telepon
                        var moved = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                            if (!ch.pressed) {
                                if (!moved) { ch.consume(); onOpenPhone() }
                                break
                            }
                        }
                        return@awaitEachGesture
                    }
                    down.consume()
                    var total = 0f
                    var prev = RotaryDial.angleOf(cx, cy, down.position.x, down.position.y)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        val now = RotaryDial.angleOf(cx, cy, ch.position.x, ch.position.y)
                        total = RotaryDial.advance(total, prev, now, digit)
                        prev = now
                        ch.consume()
                        val shown = total
                        scope.launch { rotation.snapTo(shown) }
                        if (!ch.pressed) break
                    }
                    val done = RotaryDial.completes(total, digit)
                    val back = total
                    scope.launch {
                        if (motionAllowed) rotation.animateTo(0f, tween((back * 3f).toInt().coerceIn(120, 900), easing = LinearEasing)) else rotation.snapTo(0f)
                    }
                    if (done) onDial(digit)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val d = density
            drawPhoneBody(look, geo, Offset(dx * d, dy * d), rotation.value)
        }
    }
}

private fun DrawScope.drawPhoneBody(look: PropLook, g: PhoneGeometry, origin: Offset, rot: Float) {
    val d = density
    val ink = Color(Tokens.Props.INK)
    fun o(x: Float, y: Float) = Offset(origin.x + x * d, origin.y + y * d)
    val bodyCorner = CornerRadius(g.bodyH * 0.22f * d)
    // Badan: bayangan di meja, tepi gelap, bakelit bergradasi
    drawRoundRect(Color.Black.copy(alpha = 0.40f), o(g.width * 0.03f, g.bodyTop + g.bodyH * 0.06f), Size(g.width * d, g.bodyH * d), bodyCorner)
    drawRoundRect(ink, o(0f, g.bodyTop), Size(g.width * d, g.bodyH * d), bodyCorner)
    drawRoundRect(
        Brush.verticalGradient(listOf(look.phoneBody, look.phoneBodyDark), startY = o(0f, g.bodyTop).y, endY = o(0f, g.height).y),
        o(1.2f, g.bodyTop + 1.2f), Size((g.width - 2.4f) * d, (g.bodyH - 2.4f) * d), bodyCorner,
    )
    // Gagang di atas: batang membulat dengan dua ujung (corong dan earpiece)
    val hw = g.width * 0.94f
    val hx = (g.width - hw) / 2f
    drawRoundRect(ink, o(hx, 0f), Size(hw * d, g.handsetH * d), CornerRadius(g.handsetH * 0.5f * d))
    drawRoundRect(
        Brush.verticalGradient(listOf(look.phoneBody, look.phoneBodyDark), startY = o(0f, 0f).y, endY = o(0f, g.handsetH).y),
        o(hx + 1.2f, 1.2f), Size((hw - 2.4f) * d, (g.handsetH - 2.4f) * d), CornerRadius(g.handsetH * 0.45f * d),
    )
    drawRoundRect(Color.White.copy(alpha = 0.16f), o(hx + g.handsetH * 0.5f, 2.2f), Size((hw - g.handsetH) * d, g.handsetH * 0.22f * d), CornerRadius(2f * d))
    // Pelat angka dan cakram
    val c = o(g.dialCx, g.dialCy)
    val r = g.dialR * d
    drawCircle(ink, r * 1.1f, c)
    drawCircle(Color(Tokens.Props.DIAL_PLATE), r * 1.04f, c)
    // Angka di tempat tetap (terlihat lewat lubang cakram)
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true; color = ink.toArgb(); textAlign = android.graphics.Paint.Align.CENTER
        textSize = r * 0.19f; typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    drawIntoCanvas { cv ->
        for (digit in 0..9) {
            val p = polar(c, RotaryDial.holeAngle(digit), r * 0.72f)
            cv.nativeCanvas.drawText(digit.toString(), p.x, p.y + paint.textSize * 0.35f, paint)
        }
    }
    // Cakram jari: cakram perak berlubang (isi-ganjil), diputar sebesar [rot] derajat
    val wheel = Path().apply {
        fillType = PathFillType.EvenOdd
        addOval(androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r))
        for (digit in 0..9) {
            val p = polar(c, RotaryDial.holeAngle(digit), r * 0.72f)
            val hr = r * 0.135f
            addOval(androidx.compose.ui.geometry.Rect(p.x - hr, p.y - hr, p.x + hr, p.y + hr))
        }
    }
    rotate(rot, c) {
        drawPath(wheel, Brush.linearGradient(listOf(Color(Tokens.Props.SILVER), Color(Tokens.Props.SILVER_DARK)), start = Offset(c.x - r, c.y - r), end = Offset(c.x + r, c.y + r)))
        drawPath(wheel, ink.copy(alpha = 0.55f), style = Stroke(width = 0.8f * d))
        drawCircle(look.trim, r * 0.2f, c)
        drawCircle(ink.copy(alpha = 0.5f), r * 0.2f, c, style = Stroke(width = 0.8f * d))
    }
    // Penahan jari: batang perak kecil di tepi cakram
    drawLine(Color(Tokens.Props.SILVER_DARK), polar(c, RotaryDial.STOP_DEG, r * 0.97f), polar(c, RotaryDial.STOP_DEG, r * 1.12f), strokeWidth = 3f * d, cap = androidx.compose.ui.graphics.StrokeCap.Round)
}
