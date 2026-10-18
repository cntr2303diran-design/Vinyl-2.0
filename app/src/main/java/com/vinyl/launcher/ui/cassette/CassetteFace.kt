package com.vinyl.launcher.ui.cassette

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.vinyl.launcher.data.CassetteShell
import com.vinyl.launcher.ui.theme.MarkerFamily
import com.vinyl.launcher.ui.theme.MonoFamily
import com.vinyl.launcher.ui.theme.Tokens
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Muka depan kaset audio digambar dalam satuan tetap 1000 × 635 (10 × kaset asli 100 × 63,5 mm). Pemanggil memasang
 * transformasi kanvas: skala biasa (rak perpustakaan) atau perspektif (kaset 3D di beranda). Teks label diukur sekali
 * dalam satuan yang sama, jadi tetap tajam di ukuran apa pun.
 */
internal object CassetteFaceUnits {
    const val W = 1000f
    const val H = 635f
    /** Pusat hub kiri/kanan (jarak asli 42,6 mm) dan jari-jari pita penuh. */
    val HUB_L = Offset(287f, 262f)
    val HUB_R = Offset(713f, 262f)
    const val TAPE_FULL_R = 232f
    val WINDOW = Rect(212f, 186f, 788f, 336f)
    val LABEL = Rect(66f, 34f, 934f, 414f)
    const val TITLE_MAX_W = 700
    const val ARTIST_MAX_W = 560
}

/** Rasio lebar : tinggi muka kaset. */
const val CASSETTE_ASPECT = CassetteFaceUnits.W / CassetteFaceUnits.H

@Immutable
internal data class ShellLook(
    val body: Color,
    val bodyLight: Color,
    val bodyDark: Color,
    /** Plastik bening berasap: gulungan pita penuh terlihat menembus cangkang. */
    val translucent: Boolean,
    val screw: Color,
)

internal fun shellLook(shell: CassetteShell, accent: Color): ShellLook = when (shell) {
    CassetteShell.BLACK -> ShellLook(Color(0xFF1C1C1D), Color(0xFF3A3A3C), Color(0xFF0B0B0C), translucent = false, screw = Color(0xFF9A9A9A))
    CassetteShell.TRANSPARENT -> ShellLook(Color(0x8C2F2A26), Color(0x9A5A524A), Color(0xB0141210), translucent = true, screw = Color(0xFFB8B4AC))
    CassetteShell.COLORED -> ShellLook(
        accent, lerpColor(accent, Color.White, 0.25f), lerpColor(accent, Color.Black, 0.45f), translucent = false, screw = Color(0xFFD6D3CC),
    )
    CassetteShell.PICTURE -> ShellLook(Color(0xFF262628), Color(0xFF444447), Color(0xFF111112), translucent = false, screw = Color(0xFF9A9A9A))
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, a.alpha,
)

/** Teks label yang sudah diukur dalam satuan muka kaset. */
@Immutable
internal class LabelText(
    val title: TextLayoutResult?,
    val artist: TextLayoutResult?,
    val side: TextLayoutResult,
    val time: TextLayoutResult?,
    val type: TextLayoutResult,
)

/** Ukur teks label (tulisan spidol) sekali per lagu. */
@Composable
internal fun rememberLabelText(title: String, artist: String, timeText: String): LabelText {
    val measurer = rememberTextMeasurer(cacheSize = 8)
    return remember(title, artist, timeText) {
        val unit = Density(1f, 1f)
        fun marker(text: String, size: Float, maxW: Int) = measurer.measure(
            text, TextStyle(fontFamily = MarkerFamily, fontSize = size.sp, color = Color(Tokens.Vhs.LABEL_INK)),
            overflow = TextOverflow.Ellipsis, softWrap = false, maxLines = 1,
            constraints = Constraints(maxWidth = maxW), density = unit,
        )
        fun mono(text: String, size: Float) = measurer.measure(
            text, TextStyle(fontFamily = MonoFamily, fontSize = size.sp, color = Color(Tokens.Vhs.LABEL_INK)),
            softWrap = false, maxLines = 1, density = unit,
        )
        LabelText(
            title = title.takeIf { it.isNotBlank() }?.let { marker(it, 68f, CassetteFaceUnits.TITLE_MAX_W) },
            artist = artist.takeIf { it.isNotBlank() }?.let { marker(it, 50f, CassetteFaceUnits.ARTIST_MAX_W) },
            side = marker("A", 52f, 120),
            time = timeText.takeIf { it.isNotBlank() }?.let { mono(it, 34f) },
            type = mono("C90", 30f),
        )
    }
}

/**
 * Gambar muka depan kaset di kotak 0..1000 × 0..635 kanvas saat ini.
 *
 * @param rl jari-jari pita gulungan kiri/kanan (pecahan pita penuh, lihat [Reels.radii])
 * @param angleL sudut putar hub (derajat)
 * @param light 0..1 kekuatan cahaya layar TV dari kiri; [glow] warnanya
 */
internal fun DrawScope.drawCassetteFace(
    look: ShellLook,
    text: LabelText,
    accent: Color,
    rl: Float,
    rr: Float,
    angleL: Float,
    angleR: Float,
    art: ImageBitmap?,
    picture: Boolean,
    light: Float,
    glow: Color,
) {
    val u = CassetteFaceUnits
    val shellShape = RoundRect(0f, 0f, u.W, u.H, CornerRadius(34f))
    val shellPath = Path().apply { addRoundRect(shellShape) }

    // ---------- Cangkang
    if (look.translucent) {
        // Bagian dalam terlihat: dasar gelap samar, pita penuh, rol pemandu
        drawPath(shellPath, Color(0x55120F0D))
        clipPath(shellPath) {
            drawTapePack(u.HUB_L, rl * u.TAPE_FULL_R, translucent = true)
            drawTapePack(u.HUB_R, rr * u.TAPE_FULL_R, translucent = true)
            for (x in listOf(132f, 868f)) drawCircle(Color(0xFFD9D4C8).copy(alpha = 0.55f), 22f, Offset(x, 548f))
            drawLine(Color(0xFF3A2416).copy(alpha = 0.8f), Offset(132f, 570f), Offset(868f, 570f), strokeWidth = 10f)
        }
        drawPath(shellPath, Brush.verticalGradient(listOf(look.bodyLight, look.body, look.bodyDark)))
    } else {
        drawPath(shellPath, Brush.verticalGradient(0f to look.bodyLight, 0.25f to look.body, 1f to look.bodyDark))
    }
    // Garis cetakan halus di bawah label & tepi bevel
    for (i in 0 until 5) {
        val y = 430f + i * 10f
        drawLine(Color.Black.copy(alpha = 0.20f), Offset(40f, y), Offset(u.W - 40f, y), strokeWidth = 2f)
    }
    drawPath(shellPath, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent, Color.Black.copy(alpha = 0.45f))), style = Stroke(width = 8f))

    // ---------- Area kepala pita (trapesium bawah) dengan lubang
    val trap = Path().apply {
        moveTo(214f, 478f); lineTo(786f, 478f); lineTo(842f, u.H); lineTo(158f, u.H); close()
    }
    drawPath(trap, Brush.verticalGradient(listOf(look.bodyLight.copy(alpha = 1f), look.bodyDark.copy(alpha = 1f)), startY = 478f, endY = u.H))
    drawPath(trap, Color.Black.copy(alpha = 0.35f), style = Stroke(width = 5f))
    for ((x, wHole) in listOf(268f to 54f, 380f to 46f, 476f to 48f, 574f to 46f, 678f to 54f)) {
        drawRoundRect(Color(0xFF0A0908), topLeft = Offset(x, 560f), size = Size(wHole, 52f), cornerRadius = CornerRadius(8f))
    }
    if (look.translucent) {
        // Pita terlihat melintas di area kepala
        drawLine(Color(0xFF3A2416), Offset(210f, 596f), Offset(790f, 596f), strokeWidth = 9f)
    }

    // ---------- Sekrup
    for (p in listOf(Offset(52f, 52f), Offset(u.W - 52f, 52f), Offset(52f, u.H - 52f), Offset(u.W - 52f, u.H - 52f), Offset(u.W / 2f, 452f))) {
        drawCircle(Color.Black.copy(alpha = 0.45f), 21f, p + Offset(2f, 3f))
        drawCircle(Brush.radialGradient(listOf(look.screw, look.screw.copy(alpha = 1f).darken(0.45f)), center = p - Offset(5f, 5f), radius = 26f), 18f, p)
        drawLine(Color.Black.copy(alpha = 0.6f), p - Offset(11f, 4f), p + Offset(11f, 4f), strokeWidth = 4f)
    }

    // ---------- Label kertas (berlubang di jendela)
    val label = u.LABEL
    val win = u.WINDOW
    val labelPath = Path().apply {
        fillType = PathFillType.EvenOdd
        addRoundRect(RoundRect(label, CornerRadius(18f)))
        addRoundRect(RoundRect(win.inflateBy(10f), CornerRadius(52f)))
    }
    if (picture && art != null) {
        clipPath(labelPath) {
            // Potong cover ke rasio label (tengah), tidak direntangkan
            val want = label.width / label.height
            val have = art.width.toFloat() / art.height.coerceAtLeast(1)
            val srcW = if (have > want) (art.height * want).roundToInt() else art.width
            val srcH = if (have > want) art.height else (art.width / want).roundToInt()
            drawImage(
                art,
                srcOffset = IntOffset((art.width - srcW) / 2, (art.height - srcH) / 2), srcSize = IntSize(srcW.coerceAtLeast(1), srcH.coerceAtLeast(1)),
                dstOffset = IntOffset(label.left.roundToInt(), label.top.roundToInt()),
                dstSize = IntSize(label.width.roundToInt(), label.height.roundToInt()),
            )
            drawRect(Color.Black.copy(alpha = 0.12f), topLeft = label.topLeft, size = label.size)
        }
    } else {
        drawPath(labelPath, Brush.verticalGradient(listOf(Color(Tokens.Vhs.LABEL_PAPER), Color(0xFFD9CFB8)), startY = label.top, endY = label.bottom))
        clipPath(labelPath) {
            // Pita warna di atas & garis tulis
            drawRect(accent.copy(alpha = 0.92f), topLeft = label.topLeft, size = Size(label.width, 26f))
            drawRect(Color.Black.copy(alpha = 0.10f), topLeft = Offset(label.left, label.top + 26f), size = Size(label.width, 4f))
            drawLine(Color(0xFF8B8478).copy(alpha = 0.55f), Offset(190f, 172f), Offset(900f, 172f), strokeWidth = 3f)
            drawLine(Color(0xFF8B8478).copy(alpha = 0.55f), Offset(100f, 398f), Offset(900f, 398f), strokeWidth = 3f)
        }
        // Tulisan spidol: judul di atas jendela, artis di bawahnya, durasi di kanan
        text.title?.let { drawText(it, topLeft = Offset(196f, 172f - it.size.height + 14f)) }
        text.artist?.let { drawText(it, topLeft = Offset(110f, 398f - it.size.height + 10f)) }
        text.time?.let { drawText(it, topLeft = Offset(900f - it.size.width, 398f - it.size.height - 2f)) }
    }
    // Sisi "A" dalam lingkaran dan jenis pita
    drawCircle(Color(Tokens.Vhs.LABEL_PAPER), 44f, Offset(140f, 262f))
    drawCircle(Color(Tokens.Vhs.LABEL_INK).copy(alpha = 0.85f), 44f, Offset(140f, 262f), style = Stroke(width = 5f))
    drawText(text.side, topLeft = Offset(140f - text.side.size.width / 2f, 262f - text.side.size.height / 2f))
    drawText(text.type, topLeft = Offset(810f, 262f - text.type.size.height / 2f))

    // ---------- Jendela pita
    val winShape = RoundRect(win, CornerRadius(44f))
    val winPath = Path().apply { addRoundRect(winShape) }
    clipPath(winPath) {
        drawRect(Color(0xFF15120F), topLeft = win.topLeft, size = win.size)
        drawTapePack(u.HUB_L, rl * u.TAPE_FULL_R, translucent = false)
        drawTapePack(u.HUB_R, rr * u.TAPE_FULL_R, translucent = false)
        drawHub(u.HUB_L, angleL)
        drawHub(u.HUB_R, angleR)
        // Kaca berasap + pantulan
        drawRect(Color(0xFF2B241E).copy(alpha = 0.28f), topLeft = win.topLeft, size = win.size)
        drawRect(
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), start = win.topLeft, end = Offset(win.left + 260f, win.bottom)),
            topLeft = win.topLeft, size = win.size,
        )
    }
    drawPath(winPath, Color.Black.copy(alpha = 0.55f), style = Stroke(width = 7f))

    // ---------- Cahaya layar TV dari kiri: sisi kiri lebih terang & bernuansa warna layar, kanan lebih gelap
    val l = light.coerceIn(0f, 1f)
    if (l > 0f) {
        drawPath(
            shellPath,
            Brush.horizontalGradient(listOf(glow.copy(alpha = 0.22f * l), Color.Transparent, Color.Black.copy(alpha = 0.30f * l)), startX = 0f, endX = u.W),
        )
    }
}

/** Gulungan pita cokelat dengan alur halus. */
private fun DrawScope.drawTapePack(c: Offset, r: Float, translucent: Boolean) {
    val hub = Reels.HUB * CassetteFaceUnits.TAPE_FULL_R
    if (r <= hub + 1f) return
    drawCircle(Brush.radialGradient(listOf(Color(0xFF5A3A24), Color(0xFF3A2416), Color(0xFF26170D)), center = c, radius = r), r, c)
    drawCircle(Color.Black.copy(alpha = 0.20f), r * 0.78f, c, style = Stroke(width = 2f))
    drawCircle(Color.Black.copy(alpha = 0.15f), r * 0.6f, c, style = Stroke(width = 2f))
    drawCircle(Color(0xFFB08560).copy(alpha = if (translucent) 0.35f else 0.25f), r, c, style = Stroke(width = 3f))
}

/** Hub putih bergigi enam yang berputar [deg] derajat. */
private fun DrawScope.drawHub(c: Offset, deg: Float) {
    val r = Reels.HUB * CassetteFaceUnits.TAPE_FULL_R * 0.86f
    drawCircle(Color(0xFFEDE8DD), r, c)
    drawCircle(Color.Black.copy(alpha = 0.25f), r, c, style = Stroke(width = 3f))
    drawCircle(Color(0xFF16130F), r * 0.58f, c)
    rotate(deg, c) {
        for (i in 0 until 6) {
            val a = i / 6f * 2 * PI
            val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
            drawLine(Color(0xFFEDE8DD), c + dir * (r * 0.58f), c + dir * (r * 0.34f), strokeWidth = r * 0.16f)
        }
    }
}

private fun Rect.inflateBy(d: Float) = Rect(left - d, top - d, right + d, bottom + d)

private fun Color.darken(k: Float) = Color(red * (1f - k), green * (1f - k), blue * (1f - k), alpha)
