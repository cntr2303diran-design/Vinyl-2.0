package com.vinyl.launcher.ui.room.stage

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * Laci kaset di muka meja kayu (lihat [StageLayout.Frame.drawer]): muka kayu dengan bingkai alur, pegangan kuningan, dan
 * kartu bergambar kaset. Ketuk = tarik/dorong laci; saat ditarik, muka laci maju ke arah penonton dan bagian dalamnya
 * (kain gelap dengan ujung-ujung kaset) terlihat dari atas. Isi laci yang bisa dipilih ada di baki ([com.vinyl.launcher.ui.film.FilmTray]).
 *
 * @param peek warna cangkang kaset yang mengintip dari dalam laci
 * @param tapeOut ada kaset laci yang sedang di deck: lampu kecil di muka laci menyala
 */
@Composable
internal fun StageDrawer(
    f: StageLayout.Frame,
    pal: StagePalette,
    open: Boolean,
    peek: List<Color>,
    tapeOut: Boolean,
    motionAllowed: Boolean,
    enabled: Boolean,
    count: Int,
    onToggle: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val pull = animateFloatAsState(
        if (open) 1f else 0f,
        if (motionAllowed) spring(dampingRatio = 0.72f, stiffness = 380f) else snap(),
        label = "drawerPull",
    )
    val press = animateFloatAsState(if (pressed) 1f else 0f, spring(stiffness = 1_000f), label = "drawerPress")
    val r = f.drawer
    // Ruang gambar: muka laci + bagian dalam di atasnya + bayangan & gerak maju di bawahnya
    val area = remember(f) { SRect(r.left - 6f, r.top - minOf(r.height, 60f) * 0.5f - 6f, r.right + 6f, r.bottom + 18f) }
    val colors = remember(peek) { peek.take(5) }

    Canvas(
        Modifier
            .offset(area.left.dp, area.top.dp)
            .size(area.width.dp, area.height.dp)
            .clearAndSetSemantics { },
    ) {
        drawConsoleDrawer(r, area, pal, pull.value, press.value, colors, tapeOut)
    }
    val hit = f.drawerHit
    Box(
        Modifier
            .offset(hit.left.dp, hit.top.dp)
            .size(hit.width.dp, hit.height.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = if (count > 0) "Laci kaset, $count kaset" else "Laci kaset, kosong"
                stateDescription = if (open) "Terbuka" else "Tertutup"
            }
            .clickable(
                interactionSource = source, indication = null, enabled = enabled,
                onClickLabel = if (open) "Tutup laci" else "Buka laci", role = Role.Button, onClick = onToggle,
            ),
    )
}

private fun lerpC(a: Color, b: Color, t: Float) = lerp(a, b, t)

private fun DrawScope.drawConsoleDrawer(
    r: SRect,
    area: SRect,
    pal: StagePalette,
    pull: Float,
    press: Float,
    peek: List<Color>,
    tapeOut: Boolean,
) {
    val d = density
    val left = (r.left - area.left) * d
    val top = (r.top - area.top) * d
    val w = r.width * d
    val h = r.height * d

    // Ceruk gelap di sekeliling muka laci (laci tidak rata dengan papan di sekitarnya)
    val gap = 2f * d
    drawRect(Color(0xFF050403), Offset(left - gap, top - gap), Size(w + 2 * gap, h + 2 * gap))

    // Muka laci setelah ditarik: turun ke arah penonton, sedikit membesar (perspektif)
    val slide = pull * minOf(h * 0.34f, 20f * d)
    val fw = w * (1f + 0.05f * pull)
    val fh = h * (1f + 0.03f * pull)
    val fl = left - (fw - w) / 2f
    val ft = top + slide + press * 1.2f * d

    // Bagian dalam laci, terlihat dari atas di antara ceruk dan muka laci yang bergeser
    if (slide > 1f * d) {
        val inner = Path().apply {
            moveTo(left + w * 0.02f, top); lineTo(left + w * 0.98f, top); lineTo(fl + fw, ft); lineTo(fl, ft); close()
        }
        drawPath(inner, Brush.verticalGradient(listOf(Color(0xFF0A0607), Color(0xFF241517), Color(0xFF3B2428)), startY = top, endY = ft))
        // Ujung-ujung kaset berdiri di dalamnya
        val depth = ft - top
        if (peek.isNotEmpty() && depth > 6f * d) {
            val slot = fw * 0.78f / peek.size
            val sw = slot * 0.72f
            val x0 = fl + fw * 0.11f
            peek.forEachIndexed { i, c ->
                val x = x0 + i * slot + (slot - sw) / 2f
                val sy = top + depth * 0.22f
                val sh = depth * 0.78f
                drawRoundRect(c, Offset(x, sy), Size(sw, sh), CornerRadius(2f * d))
                drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(x, sy), Size(sw, minOf(2f * d, sh)), CornerRadius(1f * d))
                drawRoundRect(Color.Black.copy(alpha = 0.55f), Offset(x, sy), Size(sw, sh), CornerRadius(2f * d), style = Stroke(width = 0.8f * d))
            }
        }
        // Bayangan papan meja di sisi atas bagian dalam
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent), startY = top, endY = top + depth * 0.5f), Offset(left, top), Size(w, depth * 0.5f))
    }

    // Bayangan muka laci jatuh ke bawah
    if (pull > 0.01f) {
        val sh = 9f * d * pull
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f * pull), Color.Transparent), startY = ft + fh, endY = ft + fh + sh), Offset(fl, ft + fh), Size(fw, sh))
    }

    // Muka kayu: gradasi, serat samar, tepi atas terang dan bawah gelap
    val corner = CornerRadius(2.5f * d)
    val dim = 1f - 0.14f * press
    val c0 = lerpC(pal.woodLight, pal.woodMid, 0.3f)
    val c1 = pal.woodMid
    val c2 = lerpC(pal.woodMid, pal.woodDark, 0.55f)
    drawRoundRect(
        Brush.verticalGradient(listOf(c0.dim(dim), c1.dim(dim), c2.dim(dim)), startY = ft, endY = ft + fh),
        Offset(fl, ft), Size(fw, fh), corner,
    )
    var gy = ft + fh * 0.18f
    var k = 0
    while (gy < ft + fh * 0.92f) {
        drawLine(Color.Black.copy(alpha = if (k % 2 == 0) 0.13f else 0.07f), Offset(fl + 3f * d, gy), Offset(fl + fw - 3f * d, gy + (k % 3 - 1) * 0.8f * d), strokeWidth = 0.9f * d)
        gy += fh * 0.19f
        k++
    }
    drawLine(Color.White.copy(alpha = 0.20f), Offset(fl + 2f * d, ft + 0.7f * d), Offset(fl + fw - 2f * d, ft + 0.7f * d), strokeWidth = 1.3f * d)
    drawLine(Color.Black.copy(alpha = 0.65f), Offset(fl, ft + fh - 0.7f * d), Offset(fl + fw, ft + fh - 0.7f * d), strokeWidth = 1.5f * d)

    // Bingkai alur (panel masuk) di muka laci
    val ins = minOf(fh * 0.16f, 7f * d)
    if (fw > 4 * ins && fh > 4 * ins) {
        val pw = fw - 2 * ins
        val ph = fh - 2 * ins
        drawRoundRect(Color.Black.copy(alpha = 0.42f), Offset(fl + ins, ft + ins), Size(pw, ph), CornerRadius(2f * d), style = Stroke(width = 1.5f * d))
        drawRoundRect(Color.White.copy(alpha = 0.07f), Offset(fl + ins + 1.5f * d, ft + ins + 1.5f * d), Size(pw - 3f * d, ph - 3f * d), CornerRadius(1.5f * d), style = Stroke(width = 1f * d))
    }

    // Pegangan kuningan
    val hw = (fw * 0.26f).coerceIn(40f * d, 130f * d).coerceAtMost(fw * 0.6f)
    val hh = (fh * 0.16f).coerceIn(5f * d, 9f * d)
    val hx = fl + (fw - hw) / 2f
    val hy = ft + (fh - hh) / 2f + (if (fh >= 56f * d) fh * 0.1f else 0f)
    drawRoundRect(Color.Black.copy(alpha = 0.5f), Offset(hx, hy + 2f * d), Size(hw, hh), CornerRadius(hh / 2f))
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFFEBD28A), Color(0xFFB58E3A), Color(0xFF6E5420)), startY = hy, endY = hy + hh),
        Offset(hx, hy), Size(hw, hh), CornerRadius(hh / 2f),
    )
    drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(hx + hh / 2f, hy + 0.8f * d), Size(hw - hh, 1.1f * d), CornerRadius(0.5f * d))

    // Kartu bergambar kaset (penanda isi laci): di atas pegangan pada muka yang cukup tinggi, selain itu di sisi kiri
    val glyphW = (fw * 0.16f).coerceIn(26f * d, 54f * d)
    when {
        fh >= 56f * d -> drawCassetteGlyph(Offset(fl + fw / 2f, ft + fh * 0.30f), glyphW)
        fw >= 200f * d -> drawCassetteGlyph(Offset(fl + fw * 0.17f, ft + fh / 2f), glyphW)
    }

    // Lampu kecil: ada kaset laci yang sedang di deck
    if (tapeOut) {
        val c = Offset(fl + fw - ins - 7f * d, ft + ins + 7f * d)
        val amber = Color(0xFFFFB347)
        drawCircle(Brush.radialGradient(listOf(amber.copy(alpha = 0.55f), Color.Transparent), center = c, radius = 9f * d), 9f * d, c)
        drawCircle(amber, 2.6f * d, c)
    }
}

private fun Color.dim(f: Float) = Color(red * f, green * f, blue * f, alpha)

/** Ikon kaset kecil: bingkai krem, label, dua gulungan. [w] lebar total (px). */
private fun DrawScope.drawCassetteGlyph(center: Offset, w: Float) {
    val h = w * 0.64f
    val tl = Offset(center.x - w / 2f, center.y - h / 2f)
    val cream = Color(0xFFEDE4CF)
    drawRoundRect(Color.Black.copy(alpha = 0.45f), tl + Offset(0f, 1.2f * density), Size(w, h), CornerRadius(h * 0.12f))
    drawRoundRect(Color(0xFF1C1B1A), tl, Size(w, h), CornerRadius(h * 0.12f))
    drawRoundRect(cream, tl + Offset(w * 0.07f, h * 0.10f), Size(w * 0.86f, h * 0.46f), CornerRadius(h * 0.05f))
    val ry = tl.y + h * 0.33f
    for (fx in listOf(0.30f, 0.70f)) {
        drawCircle(Color(0xFF1C1B1A), h * 0.12f, Offset(tl.x + w * fx, ry))
        drawCircle(cream, h * 0.045f, Offset(tl.x + w * fx, ry))
    }
    drawRect(Color(0xFF3A3836), tl + Offset(w * 0.24f, h * 0.72f), Size(w * 0.52f, h * 0.14f))
}
