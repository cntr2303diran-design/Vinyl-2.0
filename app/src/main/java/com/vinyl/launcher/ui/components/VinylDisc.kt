package com.vinyl.launcher.ui.components

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.content.res.ResourcesCompat
import com.vinyl.launcher.R
import com.vinyl.launcher.data.VinylStyle
import com.vinyl.launcher.ui.theme.darken
import com.vinyl.launcher.ui.theme.lighten
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Isi label tengah vinyl. */
sealed interface VinylLabel {
    /** Label warna dengan judul & artis melingkar (home screen). */
    data class Text(val title: String, val subtitle: String) : VinylLabel
    /** Label bergambar cover album (layar kunci). */
    data class Art(val art: ImageBitmap?) : VinylLabel
}

/** Typeface Inter untuk teks yang digambar di Canvas native (label & tepi vinyl). */
fun interTypeface(context: Context): Typeface =
    runCatching { ResourcesCompat.getFont(context, R.font.inter) }.getOrNull() ?: Typeface.SANS_SERIF

/**
 * Piringan vinyl yang digambar sepenuhnya dengan Canvas agar tajam di semua ukuran.
 *
 * Lapisan (dari bawah):
 * 1. badan piringan (hitam / berwarna / transparan / picture disc)
 * 2. alur — dirender SEKALI ke bitmap (drawWithCache) lalu dipakai ulang tiap frame → 60fps ringan
 * 3. kilap cahaya anisotropik yang diam (sumber cahaya tetap) sehingga putaran terlihat realistis
 * 4. label + teks melingkar yang ikut berputar
 */
@Composable
fun VinylDisc(
    modifier: Modifier = Modifier,
    rotation: Float,
    style: VinylStyle,
    labelColor: Color,
    discTint: Color,
    label: VinylLabel,
    art: ImageBitmap? = null,
    rimText: String? = null,
    rimTextColor: Color = Color.White,
) {
    val context = LocalContext.current
    val localDensity = LocalDensity.current
    val localLayoutDirection = LocalLayoutDirection.current
    val typeface = remember { interTypeface(context) }
    val titlePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            fontVariationSettings = "'wght' 650, 'opsz' 14"
            letterSpacing = 0.12f
        }
    }
    val subPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            fontVariationSettings = "'wght' 450, 'opsz' 14"
            letterSpacing = 0.06f
        }
    }
    val rimPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            fontVariationSettings = "'wght' 300, 'opsz' 32"
            letterSpacing = 0.32f
        }
    }

    Box(modifier) {
        // ---- Lapisan A: badan piringan (+ gambar picture disc yang berputar) ----
        Canvas(Modifier.matchParentSize()) {
            val r = min(size.width, size.height) / 2f
            val base = when (style) {
                VinylStyle.CLASSIC -> Color(0xFF0B0B0D)
                VinylStyle.COLORED -> discTint.darken(0.2f)
                VinylStyle.TRANSPARENT -> discTint.lighten(0.25f).copy(alpha = 0.5f)
                VinylStyle.PICTURE_DISC -> Color(0xFF151517)
            }
            drawCircle(base, r, center)
            if (style == VinylStyle.PICTURE_DISC && art != null) {
                rotate(rotation, center) { drawImageInCircle(art, center, r * 0.99f) }
                drawCircle(Color.Black.copy(alpha = 0.22f), r, center)
            }
        }

        // ---- Lapisan B: alur + kilap. Dirender SEKALI per ukuran/gaya (tidak ikut berputar) ----
        Box(
            Modifier.matchParentSize().drawWithCache {
                val r = min(size.width, size.height) / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                val grooves = ImageBitmap(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1))
                CanvasDrawScope().draw(localDensity, localLayoutDirection, androidx.compose.ui.graphics.Canvas(grooves), size) {
                    drawGrooves(c, r, style)
                }
                val sheenAlpha = if (style == VinylStyle.TRANSPARENT) 0.10f else 0.17f
                val sheen = Brush.sweepGradient(
                    0.00f to Color.Transparent,
                    0.07f to Color.White.copy(alpha = sheenAlpha),
                    0.10f to Color.White.copy(alpha = sheenAlpha * 0.4f),
                    0.16f to Color.Transparent,
                    0.50f to Color.Transparent,
                    0.57f to Color.White.copy(alpha = sheenAlpha * 0.85f),
                    0.60f to Color.White.copy(alpha = sheenAlpha * 0.3f),
                    0.66f to Color.Transparent,
                    1.00f to Color.Transparent,
                    center = c,
                )
                val rimLight = Brush.radialGradient(
                    0.90f to Color.Transparent, 0.985f to Color.White.copy(alpha = 0.10f), 1f to Color.Transparent,
                    center = c, radius = r,
                )
                onDrawBehind {
                    drawImage(grooves)
                    drawCircle(sheen, r * 0.985f, c)
                    drawCircle(rimLight, r, c)
                }
            }
        )

        // ---- Lapisan C: label, as, dan teks tepi (berputar) ----
        Canvas(Modifier.matchParentSize()) {
            val r = min(size.width, size.height) / 2f
            val c = center
            val labelR = r * 0.335f
            rotate(rotation, c) {
                when (label) {
                    is VinylLabel.Art -> {
                        if (label.art != null) drawImageInCircle(label.art, c, labelR) else drawCircle(labelColor, labelR, c)
                        drawCircle(Color.Black.copy(alpha = 0.25f), labelR, c, style = Stroke(r * 0.006f))
                    }
                    is VinylLabel.Text -> {
                        if (style != VinylStyle.PICTURE_DISC || art == null) {
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(labelColor.lighten(0.10f), labelColor, labelColor.darken(0.18f)),
                                    center = c - Offset(labelR * 0.25f, labelR * 0.25f), radius = labelR * 1.4f,
                                ),
                                labelR, c,
                            )
                        }
                        val ink = if (labelColor.luminanceApprox() > 0.55f) Color(0xFF141414) else Color(0xFFFAF7F2)
                        drawCircle(ink.copy(alpha = 0.22f), labelR * 0.93f, c, style = Stroke(r * 0.0035f))
                        drawCircle(ink.copy(alpha = 0.14f), labelR * 0.30f, c, style = Stroke(r * 0.0035f))
                        drawCircularText(label.title.uppercase(), c, labelR * 0.74f, labelR * 0.135f, ink, titlePaint, startDeg = 200f)
                        drawCircularText(label.subtitle, c, labelR * 0.54f, labelR * 0.105f, ink.copy(alpha = 0.72f), subPaint, startDeg = 120f)
                    }
                }
            }
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFF2F2F4), Color(0xFF8D8E93)), center = c - Offset(r * 0.008f, r * 0.008f), radius = r * 0.03f),
                r * 0.024f, c,
            )
            drawCircle(Color.Black.copy(alpha = 0.45f), r * 0.024f, c, style = Stroke(r * 0.003f))
            if (!rimText.isNullOrBlank()) {
                drawCircularText(rimText, c, r * 0.885f, r * 0.075f, rimTextColor, rimPaint, startDeg = -90f, centered = true)
            }
        }
    }
}

private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** Alur dengan tekstur acak deterministik + beberapa jeda antar-lagu. */
private fun DrawScope.drawGrooves(c: Offset, r: Float, style: VinylStyle) {
    val inner = r * 0.375f
    val outer = r * 0.97f
    val count = 140
    val step = (outer - inner) / count
    val baseAlpha = when (style) {
        VinylStyle.CLASSIC -> 0.045f
        VinylStyle.TRANSPARENT -> 0.07f
        else -> 0.06f
    }
    val gaps = floatArrayOf(0.50f, 0.62f, 0.74f, 0.87f)
    for (i in 0 until count) {
        val rr = inner + i * step
        val frac = rr / r
        val isGap = gaps.any { kotlin.math.abs(it - frac) < step / r * 1.2f }
        if (isGap) {
            drawCircle(Color.Black.copy(alpha = 0.45f), rr, c, style = Stroke(step * 1.6f))
            drawCircle(Color.White.copy(alpha = baseAlpha * 1.4f), rr + step * 0.9f, c, style = Stroke(0.6f))
            continue
        }
        // variasi kecerahan pseudo-acak agar terasa seperti alur sungguhan
        val n = (sin(i * 12.9898f) * 43758.547f).let { it - kotlin.math.floor(it) }
        drawCircle(Color.White.copy(alpha = baseAlpha * (0.5f + n)), rr, c, style = Stroke(0.7f))
    }
    // Area mulus: lead-in (tepi) & run-out (dekat label)
    drawCircle(Color.Black.copy(alpha = 0.35f), inner - r * 0.012f, c, style = Stroke(r * 0.022f))
    drawCircle(Color.White.copy(alpha = 0.035f), outer + r * 0.012f, c, style = Stroke(r * 0.02f))
}

/** Gambar bitmap yang dipotong lingkaran, mengisi (center-crop) radius [radius]. */
fun DrawScope.drawImageInCircle(img: ImageBitmap, c: Offset, radius: Float) {
    val path = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, radius)) }
    clipPath(path) {
        val side = min(img.width, img.height)
        drawImage(
            image = img,
            srcOffset = IntOffset((img.width - side) / 2, (img.height - side) / 2),
            srcSize = IntSize(side, side),
            dstOffset = IntOffset((c.x - radius).roundToInt(), (c.y - radius).roundToInt()),
            dstSize = IntSize((radius * 2).roundToInt(), (radius * 2).roundToInt()),
        )
    }
}

/**
 * Menulis teks mengikuti lingkaran berjari-jari [radius].
 * @param startDeg sudut awal (0° = arah jam 3, searah jarum jam).
 * @param centered jika true, teks dipusatkan di sekitar [startDeg].
 */
fun DrawScope.drawCircularText(
    text: String,
    c: Offset,
    radius: Float,
    textSize: Float,
    color: Color,
    paint: Paint,
    startDeg: Float = 180f,
    centered: Boolean = false,
) {
    if (text.isBlank() || radius <= 0f) return
    paint.textSize = textSize
    paint.color = color.toArgb()
    val circumference = (2 * Math.PI * radius).toFloat()
    var t = text
    val maxLen = circumference * 0.9f
    while (paint.measureText(t) > maxLen && t.length > 2) t = t.dropLast(2) + "…"
    val textLen = paint.measureText(t)
    val startAngle = if (centered) startDeg - (textLen / circumference) * 180f else startDeg
    val path = android.graphics.Path().apply {
        addArc(c.x - radius, c.y - radius, c.x + radius, c.y + radius, startAngle, 359.9f)
    }
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawTextOnPath(t, path, 0f, textSize * 0.35f, paint)
    }
}

@Suppress("unused")
internal val PreviewDiscSize = Size(400f, 400f)
