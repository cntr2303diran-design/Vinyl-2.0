package com.vinyl.launcher.ui.room.stage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.vinyl.launcher.data.RoomTone
import com.vinyl.launcher.ui.theme.Tokens

/**
 * Lampu dinding di kiri atas dinding (lihat [StageLayout.Frame.lamp]): lengan kuningan, kap krem, dan bohlam. Ketuk =
 * ruang terang/gelap (pilihan "Ruang" di Pengaturan → Dinding ruang yang sama). Menyala (ruang terang) ditandai bohlam
 * terang, kap bercahaya, dan kerucut cahaya di bawah kap; mati hanya kap dan bohlam yang redup. Untuk pembaca layar
 * lampu adalah sakelar "Lampu ruangan" berstatus Menyala/Mati.
 *
 * Gambar memakai tepi ganda (isi terang + garis tepi gelap, [Tokens.Lamp]) agar terbaca di atas dinding gelap maupun terang.
 */
@Composable
internal fun StageLamp(r: SRect, on: Boolean, motionAllowed: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val glow = animateFloatAsState(if (on) 1f else 0f, if (motionAllowed) tween(260) else snap(), label = "lampGlow")
    val press = animateFloatAsState(if (pressed) 1f else 0f, spring(stiffness = 1_000f), label = "lampPress")
    // Ruang gambar: area sentuh + ruang untuk cahaya di sekelilingnya (kerucut jatuh ke bawah)
    val area = remember(r) { SRect(r.left - 40f, r.top - 24f, r.right + 40f, r.bottom + 40f) }

    Canvas(
        Modifier
            .offset(area.left.dp, area.top.dp)
            .size(area.width.dp, area.height.dp)
            .clearAndSetSemantics { },
    ) {
        drawWallLamp(Offset((r.left - area.left) * density, (r.top - area.top) * density), glow.value, press.value)
    }
    Box(
        Modifier
            .offset(r.left.dp, r.top.dp)
            .size(r.width.dp, r.height.dp)
            .semantics {
                contentDescription = "Lampu ruangan"
                stateDescription = if (on) "Menyala" else "Mati"
            }
            .toggleable(
                value = on, interactionSource = source, indication = null, enabled = enabled,
                role = Role.Switch, onValueChange = { onToggle() },
            ),
    )
}

/**
 * Ruang berganti terang/gelap: kilatan singkat yang memudar menutupi pergantian palet yang seketika (terang = krem hangat,
 * gelap = hitam). Hanya saat nada benar-benar berubah sesudah tampil pertama, dan tidak ada bila gerak dimatikan.
 */
@Composable
internal fun ToneFlash(tone: RoomTone, motionAllowed: Boolean) {
    var shown by remember { mutableStateOf(tone) }
    val flash = remember { Animatable(0f) }
    LaunchedEffect(tone, motionAllowed) {
        if (tone != shown) {
            shown = tone
            if (motionAllowed) {
                flash.snapTo(1f)
                flash.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
            }
        }
    }
    val color = if (tone == RoomTone.LIGHT) Color(Tokens.Lamp.SHADE_ON) else Color.Black
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        val a = flash.value
        if (a > 0.001f) drawRect(color.copy(alpha = 0.5f * a))
    }
}

/**
 * Menggambar lampu pada kotak [LAMP_W] × [LAMP_H] dp yang pojok kirinya di [origin] (px). [glow] 0..1 = tingkat nyala
 * (cahaya, kap, bohlam); [press] 0..1 menurunkan lampu sedikit saat ditekan.
 */
private fun DrawScope.drawWallLamp(origin: Offset, glow: Float, press: Float) {
    val d = density
    val dy = press * 1.2f
    fun p(x: Float, y: Float) = Offset(origin.x + x * d, origin.y + (y + dy) * d)
    val ink = Color(Tokens.Lamp.OUTLINE)
    val brassHi = Color(Tokens.Lamp.BRASS_LIGHT)
    val brassLo = Color(Tokens.Lamp.BRASS_DARK)
    val warm = Color(Tokens.Lamp.GLOW)

    // Cahaya: lingkaran lembut di sekitar bohlam dan kerucut yang jatuh ke dinding di bawah kap (bentuk, bukan hanya warna)
    if (glow > 0.01f) {
        val bulb = p(34f, 39f)
        drawCircle(Brush.radialGradient(listOf(warm.copy(alpha = 0.42f * glow), Color.Transparent), center = bulb, radius = 46f * d), 46f * d, bulb)
        val cone = Path().apply {
            moveTo(p(14f, 38f).x, p(14f, 38f).y); lineTo(p(54f, 38f).x, p(54f, 38f).y)
            lineTo(p(80f, 94f).x, p(80f, 94f).y); lineTo(p(-12f, 94f).x, p(-12f, 94f).y); close()
        }
        drawPath(cone, Brush.verticalGradient(listOf(warm.copy(alpha = 0.30f * glow), Color.Transparent), startY = p(0f, 38f).y, endY = p(0f, 94f).y))
    }

    // Lengan kuningan dari pelat dinding ke kap
    val plate = p(9f, 31f)
    val knee = p(30f, 19f)
    drawLine(ink, plate, knee, strokeWidth = 5.6f * d, cap = StrokeCap.Round)
    drawLine(Brush.linearGradient(listOf(brassHi, brassLo), start = plate, end = knee), plate, knee, strokeWidth = 3.4f * d, cap = StrokeCap.Round)
    // Pelat dinding
    drawCircle(ink, 8.8f * d, plate)
    drawCircle(Brush.verticalGradient(listOf(brassHi, brassLo), startY = plate.y - 7.4f * d, endY = plate.y + 7.4f * d), 7.4f * d, plate)
    drawCircle(ink.copy(alpha = 0.55f), 1.6f * d, plate)

    // Tutup kuningan di atas kap
    val capTop = p(30f, 8f)
    drawRoundRect(ink, capTop - Offset(1.2f * d, 1.2f * d), Size(10.4f * d, 7.6f * d), CornerRadius(2f * d))
    drawRoundRect(Brush.verticalGradient(listOf(brassHi, brassLo), startY = capTop.y, endY = capTop.y + 5.2f * d), capTop, Size(8f * d, 5.2f * d), CornerRadius(1.4f * d))

    // Kap: krem yang makin bercahaya saat menyala, tepi samping lebih gelap (silinder)
    val shadeBase = lerp(Color(Tokens.Lamp.SHADE_OFF), Color(Tokens.Lamp.SHADE_ON), glow)
    val shadeSide = lerp(shadeBase, Color.Black, 0.22f)
    val shade = Path().apply {
        moveTo(p(24f, 12f).x, p(24f, 12f).y); lineTo(p(44f, 12f).x, p(44f, 12f).y)
        lineTo(p(54f, 38f).x, p(54f, 38f).y); lineTo(p(14f, 38f).x, p(14f, 38f).y); close()
    }
    drawPath(shade, Brush.horizontalGradient(0f to shadeSide, 0.5f to shadeBase, 1f to shadeSide, startX = p(14f, 0f).x, endX = p(54f, 0f).x))
    // Lipatan kain
    for ((tx, bx) in listOf(28f to 22f, 34f to 34f, 40f to 46f)) {
        drawLine(ink.copy(alpha = 0.14f), p(tx, 13f), p(bx, 37f), strokeWidth = 1f * d)
    }
    drawPath(shade, ink, style = Stroke(width = 1.6f * d, join = StrokeJoin.Round))
    // Mulut kap dilihat dari bawah: gelap saat mati, hangat saat menyala
    drawOval(lerp(ink.copy(alpha = 0.72f), warm.copy(alpha = 0.9f), glow), p(14f, 35f), Size(40f * d, 6.4f * d))
    // Bohlam
    val bulb = p(34f, 39f)
    drawCircle(ink, 5.9f * d, bulb)
    drawCircle(lerp(Color(Tokens.Lamp.BULB_OFF), Color(Tokens.Lamp.BULB_ON), glow), 4.8f * d, bulb)
}
