package com.vinyl.launcher.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.vinyl.launcher.data.TonearmStyle
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Geometri tonearm. Sudut θ dalam radian: 0 = lengan lurus ke bawah,
 * positif = berayun ke kiri (ke arah tengah piringan).
 */
data class TonearmGeometry(val pivot: Offset, val length: Float, val discCenter: Offset, val discRadius: Float) {

    fun direction(theta: Float) = Offset(-sin(theta), cos(theta))

    fun needle(theta: Float): Offset = pivot + direction(theta) * length

    /** Sudut di mana lengan mengarah tepat ke pusat piringan (batas atas pencarian). */
    private val maxTheta: Float = run {
        val v = discCenter - pivot
        atan2(-v.x, v.y)
    }

    /** Jarak jarum ke pusat piringan untuk sudut θ. */
    fun needleRadius(theta: Float): Float = (needle(theta) - discCenter).getDistance()

    /** Sudut agar jarum berada di radius [r] (pencarian biner, jarak menurun monoton). */
    fun thetaForRadius(r: Float): Float {
        var lo = 0f
        var hi = maxTheta
        repeat(22) {
            val mid = (lo + hi) / 2f
            if (needleRadius(mid) > r) lo = mid else hi = mid
        }
        return (lo + hi) / 2f
    }

    /** Alur terluar → progress 0, alur terdalam → progress 1. */
    fun radiusForProgress(p: Float) = discRadius * (OUTER - (OUTER - INNER) * p.coerceIn(0f, 1f))
    fun progressForRadius(r: Float) = ((OUTER * discRadius - r) / ((OUTER - INNER) * discRadius)).coerceIn(0f, 1f)

    fun thetaForProgress(p: Float) = thetaForRadius(radiusForProgress(p))

    /** Sudut lengan yang menunjuk ke titik sentuh [p]. */
    fun thetaToward(p: Offset): Float {
        val v = p - pivot
        return atan2(-v.x, v.y)
    }

    /** Jarak titik [p] ke batang lengan pada sudut θ (untuk hit-test sentuhan). */
    fun distanceToArm(p: Offset, theta: Float): Float {
        val a = pivot
        val b = needle(theta)
        val ab = b - a
        val t = (((p - a).x * ab.x + (p - a).y * ab.y) / (ab.x * ab.x + ab.y * ab.y)).coerceIn(0f, 1f)
        val proj = a + ab * t
        return hypot(p.x - proj.x, p.y - proj.y)
    }

    companion object {
        const val OUTER = 0.94f
        const val INNER = 0.42f
        const val REST_THETA = -0.07f
    }
}

private data class ArmPalette(
    val chrome: List<Color>,   // gradasi batang (gelap-terang-gelap) untuk efek logam silinder
    val brushed: List<Color>,  // gradasi sapuan untuk dudukan logam "brushed"
    val dark: Color,
    val accent: Color,
)

private fun paletteFor(style: TonearmStyle) = when (style) {
    TonearmStyle.SILVER -> ArmPalette(
        chrome = listOf(Color(0xFF6E7076), Color(0xFFE9EAEE), Color(0xFFFFFFFF), Color(0xFFB9BBC1), Color(0xFF75777D)),
        brushed = listOf(Color(0xFFB7B9BE), Color(0xFFE8E9EC), Color(0xFF9A9CA2), Color(0xFFDADBDF), Color(0xFFA5A7AC), Color(0xFFB7B9BE)),
        dark = Color(0xFF161618), accent = Color(0xFFE6E7EA),
    )
    TonearmStyle.BLACK -> ArmPalette(
        chrome = listOf(Color(0xFF0C0C0E), Color(0xFF3E3E44), Color(0xFF5A5A61), Color(0xFF26262A), Color(0xFF0E0E10)),
        brushed = listOf(Color(0xFF1A1A1D), Color(0xFF34343A), Color(0xFF151517), Color(0xFF2C2C31), Color(0xFF18181B), Color(0xFF1A1A1D)),
        dark = Color(0xFF070708), accent = Color(0xFF8A8A92),
    )
    TonearmStyle.GOLD -> ArmPalette(
        chrome = listOf(Color(0xFF7A5A1E), Color(0xFFE9C978), Color(0xFFFFF0C4), Color(0xFFC9A04A), Color(0xFF7E5E22)),
        brushed = listOf(Color(0xFFC9A253), Color(0xFFEED59A), Color(0xFFAD8738), Color(0xFFE2C47F), Color(0xFFB8913F), Color(0xFFC9A253)),
        dark = Color(0xFF17130C), accent = Color(0xFFF3DC9E),
    )
}

/**
 * Menggambar tonearm ramping: dudukan logam brushed, pemberat bergerigi,
 * batang krom dengan lekuk-S halus, headshell bersudut, dan cartridge.
 * @param lift 0 = jarum di piringan, 1 = terangkat (bayangan lebih jauh & lebih lembut).
 */
fun DrawScope.drawTonearm(geo: TonearmGeometry, theta: Float, lift: Float, style: TonearmStyle) {
    val pal = paletteFor(style)
    val r = geo.discRadius
    val p = geo.pivot
    val len = geo.length
    val deg = Math.toDegrees(theta.toDouble()).toFloat()
    val tubeW = r * 0.026f

    // ---------- Dudukan (tidak berputar) ----------
    drawCircle(Color.Black.copy(alpha = 0.35f), r * 0.175f, p + Offset(r * 0.015f, r * 0.03f))
    drawCircle(Brush.sweepGradient(pal.brushed, center = p), r * 0.17f, p)
    drawCircle(Color.Black.copy(alpha = 0.25f), r * 0.17f, p, style = Stroke(r * 0.006f))
    drawCircle(pal.dark, r * 0.112f, p)
    drawCircle(Brush.sweepGradient(pal.brushed.reversed(), center = p), r * 0.085f, p)
    drawCircle(Color.White.copy(alpha = 0.25f), r * 0.085f, p, style = Stroke(r * 0.004f))

    // Lekuk-S batang (koordinat lokal: lengan mengarah ke +y)
    fun tubePath(o: Offset) = Path().apply {
        moveTo(p.x + o.x, p.y + o.y - len * 0.12f)
        cubicTo(
            p.x + o.x - len * 0.05f, p.y + o.y + len * 0.30f,
            p.x + o.x + len * 0.045f, p.y + o.y + len * 0.58f,
            p.x + o.x, p.y + o.y + len * 0.84f,
        )
    }

    fun headshell(o: Offset, shadow: Boolean) {
        // Headshell sedikit bersudut terhadap batang (offset angle ala tonearm sungguhan)
        val hp = p + o + Offset(0f, len * 0.84f)
        rotate(14f, hp) {
            val hw = r * 0.075f * (1f + 0.05f * lift)
            val hh = len * 0.17f * (1f + 0.05f * lift)
            val path = Path().apply {
                moveTo(hp.x - hw * 0.35f, hp.y)
                lineTo(hp.x + hw * 0.35f, hp.y)
                lineTo(hp.x + hw * 0.5f, hp.y + hh)
                lineTo(hp.x - hw * 0.5f, hp.y + hh)
                close()
            }
            if (shadow) {
                drawPath(path, Color.Black.copy(alpha = 0.32f - 0.12f * lift))
            } else {
                drawPath(path, Brush.horizontalGradient(pal.chrome, startX = hp.x - hw * 0.5f, endX = hp.x + hw * 0.5f))
                // cartridge gelap
                drawRect(pal.dark, Offset(hp.x - hw * 0.36f, hp.y + hh * 0.52f), Size(hw * 0.72f, hh * 0.40f))
                drawRect(pal.accent.copy(alpha = 0.7f), Offset(hp.x - hw * 0.36f, hp.y + hh * 0.52f), Size(hw * 0.72f, hh * 0.04f))
                // pengangkat jari
                drawLine(
                    Brush.horizontalGradient(pal.chrome),
                    Offset(hp.x + hw * 0.42f, hp.y + hh * 0.18f), Offset(hp.x + hw * 1.15f, hp.y + hh * 0.05f),
                    r * 0.011f, StrokeCap.Round,
                )
            }
        }
    }

    val shadowOffset = Offset(r * (0.02f + 0.06f * lift), r * (0.03f + 0.08f * lift))
    // ---------- Bayangan ----------
    rotate(deg, p) {
        drawPath(tubePath(shadowOffset), Color.Black.copy(alpha = 0.30f - 0.10f * lift), style = Stroke(tubeW, cap = StrokeCap.Round))
        headshell(shadowOffset, shadow = true)
    }
    // ---------- Lengan ----------
    rotate(deg, p) {
        // Pemberat bergerigi di belakang pivot
        val cwTop = p.y - len * 0.30f
        val cwH = len * 0.15f
        val cwW = r * 0.12f
        drawRoundRect(
            Brush.horizontalGradient(pal.chrome, startX = p.x - cwW / 2, endX = p.x + cwW / 2),
            topLeft = Offset(p.x - cwW / 2, cwTop), size = Size(cwW, cwH), cornerRadius = CornerRadius(r * 0.018f),
        )
        for (i in 1..4) {
            val y = cwTop + cwH * i / 5f
            drawLine(Color.Black.copy(alpha = 0.22f), Offset(p.x - cwW / 2, y), Offset(p.x + cwW / 2, y), r * 0.003f)
        }
        // Batang krom
        drawPath(
            tubePath(Offset.Zero),
            Brush.horizontalGradient(pal.chrome, startX = p.x - tubeW * 1.6f, endX = p.x + tubeW * 1.6f),
            style = Stroke(tubeW, cap = StrokeCap.Round),
        )
        headshell(Offset.Zero, shadow = false)
    }
    // Tutup bearing di atas lengan
    drawCircle(Brush.radialGradient(pal.chrome.take(3), center = p - Offset(r * 0.015f, r * 0.015f), radius = r * 0.05f), r * 0.04f, p)
}
