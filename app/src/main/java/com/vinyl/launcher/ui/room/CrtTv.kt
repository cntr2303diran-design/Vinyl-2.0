package com.vinyl.launcher.ui.room

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinyl.launcher.data.AlbumMotion
import com.vinyl.launcher.data.KnobStyle
import com.vinyl.launcher.data.TvBody
import com.vinyl.launcher.data.VfdColor
import com.vinyl.launcher.ui.theme.MonoFamily
import com.vinyl.launcher.ui.theme.RetroColors
import com.vinyl.launcher.ui.theme.Tokens
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Warna-warna satu varian bodi TV. */
private data class BodyPalette(val top: Color, val bottom: Color, val panel: Color, val trim: Color, val wood: Boolean)

private fun bodyPalette(body: TvBody): BodyPalette = when (body) {
    TvBody.CREAM -> BodyPalette(Color(0xFFD6C29F), Color(0xFFB9A07A), RetroColors.Walnut, Color(0xFF8E7656), wood = true)
    TvBody.WALNUT -> BodyPalette(Color(0xFF7A563D), Color(0xFF5A3D2A), Color(0xFFCDB894), Color(0xFF3E2A1D), wood = false)
    TvBody.CHARCOAL -> BodyPalette(Color(0xFF454743), Color(0xFF2C2E2B), RetroColors.CharcoalRaised, Color(0xFF1D1E1C), wood = false)
    TvBody.SPACE_AGE -> BodyPalette(Color(0xFFDB8A49), Color(0xFFB9672E), Color(0xFFE9DCC4), Color(0xFF8A4B20), wood = false)
}

private data class KnobPalette(val face: Color, val rim: Color, val mark: Color)

private fun knobPalette(style: KnobStyle): KnobPalette = when (style) {
    KnobStyle.BAKELITE -> KnobPalette(Color(0xFF2B2623), Color(0xFF4A423C), Color(0xFFE8DCC6))
    KnobStyle.CHROME -> KnobPalette(Color(0xFFC9CCCD), Color(0xFF8D9192), Color(0xFF2B2B2B))
    KnobStyle.BRASS -> KnobPalette(Color(0xFFC9A55A), Color(0xFF8F6E2F), Color(0xFF3A2A10))
}

/**
 * TV tabung orisinal (bukan tiruan produk tertentu): bodi tebal membulat, layar cembung di kiri, panel kontrol di kanan
 * berisi knob, VFD, kisi speaker, dan lampu daya. Isi layar mengikuti [mode].
 *
 * Semua gambar memakai [TvGeometry] sehingga area sentuh knob (dipasang pemanggil) sejajar dengan gambarnya.
 *
 * @param powerKey berubah saat layar perlu "menyala ulang" (mis. dari standby ke cover)
 * @param knobTurns putaran knob (1 = satu putaran penuh) untuk jarum penanda
 * @param knobHighlight knob sedang disentuh
 * @param motionTime 0..1 berulang untuk Mode C (diberikan pemanggil agar satu sumber waktu)
 */
@Composable
fun CrtTv(
    mode: TvScreenMode,
    art: ImageBitmap?,
    title: String,
    accent: Color,
    body: TvBody,
    knob: KnobStyle,
    vfdColor: VfdColor,
    vfdText: String,
    crtStrength: Float,
    motion: AlbumMotion,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    knobTurns: Float = 0f,
    knobHighlight: Boolean = false,
) {
    val palette = bodyPalette(body)
    val knobColors = knobPalette(knob)
    val crt = crtStrength.coerceIn(0f, 1f)

    // Animasi "power on": garis horizontal mengembang menjadi gambar saat keluar dari standby
    val power = remember { Animatable(if (mode == TvScreenMode.STANDBY) 0f else 1f) }
    LaunchedEffect(mode == TvScreenMode.STANDBY) {
        if (mode == TvScreenMode.STANDBY) power.snapTo(0f)
        else if (reduceMotion) power.snapTo(1f)
        else power.animateTo(1f, tween(Tokens.Motion.CRT_POWER_ON_MS, easing = LinearEasing))
    }

    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        fun fx(f: Float): Dp = w * f
        fun fy(f: Float): Dp = h * f

        // Bodi, panel, knob, kisi, lampu
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            drawTvBody(palette, knobColors, knobTurns, knobHighlight, mode != TvScreenMode.STANDBY)
        }

        // Layar
        val screen = TvGeometry.SCREEN
        val shape = RoundedCornerShape(fx(screen.width) * TvGeometry.SCREEN_CORNER)
        Box(
            Modifier
                .offset(fx(screen.left), fy(screen.top))
                .size(fx(screen.width), fy(screen.height))
                .clip(shape)
                .background(RetroColors.CrtGlass)
                .clearAndSetSemantics { },
        ) {
            if (mode != TvScreenMode.STANDBY) {
                val p = power.value
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // 0 → garis tipis terang, 1 → gambar penuh
                            scaleY = (0.015f + 0.985f * ((p - 0.25f) / 0.75f).coerceIn(0f, 1f)).coerceAtLeast(0.015f)
                            scaleX = 0.82f + 0.18f * (p / 0.4f).coerceIn(0f, 1f)
                        },
                ) {
                    ScreenPicture(art, title, accent, motion, mode == TvScreenMode.MOTION)
                }
                // Kilat putih saat menyala
                if (p < 1f) {
                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = (1f - p) * 0.35f)))
                }
            }
            // Lapisan kaca CRT: scanline, vignette, kilau
            Canvas(Modifier.fillMaxSize()) { drawCrtGlass(crt, standby = mode == TvScreenMode.STANDBY) }
        }

        // VFD kecil di panel
        val vfd = TvGeometry.VFD
        Box(
            Modifier
                .offset(fx(vfd.left), fy(vfd.top))
                .size(fx(vfd.width), fy(vfd.height))
                .clip(RoundedCornerShape(3.dp))
                .background(RetroColors.VfdGlass)
                .padding(horizontal = 3.dp)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            val color = if (vfdColor == VfdColor.GREEN) RetroColors.VfdGreen else RetroColors.VfdAmber
            Text(
                vfdText,
                color = color,
                fontFamily = MonoFamily,
                fontWeight = FontWeight.Medium,
                fontSize = (w.value * 0.024f).coerceIn(7f, 18f).sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Isi layar saat ada lagu: cover (Mode B) atau cover yang bergerak (Mode C). */
@Composable
private fun ScreenPicture(art: ImageBitmap?, title: String, accent: Color, motion: AlbumMotion, moving: Boolean) {
    val t = if (moving) {
        val tr = rememberInfiniteTransition(label = "albumMotion")
        val v by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Restart), label = "m")
        v
    } else 0f
    val cycle = (sin(t * 2 * PI)).toFloat()
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                if (moving) when (motion) {
                    AlbumMotion.ROTATE -> { rotationZ = t * 360f; scaleX = 1.42f; scaleY = 1.42f }
                    AlbumMotion.ZOOM -> { val s = 1.05f + 0.10f * (0.5f + 0.5f * cycle); scaleX = s; scaleY = s }
                    AlbumMotion.PERSPECTIVE -> { rotationY = 9f * cycle; rotationX = 4f * cos(t * 2 * PI).toFloat(); cameraDistance = 10f * density; scaleX = 1.08f; scaleY = 1.08f }
                    AlbumMotion.ANALOG -> { translationX = size.width * 0.006f * sin(t * 2 * PI * 23).toFloat(); scaleX = 1.03f; scaleY = 1.03f }
                    AlbumMotion.OFF -> Unit
                }
            },
    ) {
        if (art != null) {
            Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            // Lagu tanpa cover: warna aksen dan judulnya saja, bukan gambar tiruan
            Box(
                Modifier.fillMaxSize().background(Brush.linearGradient(listOf(accent.copy(alpha = 0.9f), Color(0xFF151515)))),
                contentAlignment = Alignment.Center,
            ) {
                Text(title, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

/** Kaca CRT di atas gambar. [k] kekuatan efek 0..1. */
private fun DrawScope.drawCrtGlass(k: Float, standby: Boolean) {
    val w = size.width
    val h = size.height
    if (standby) {
        // Kaca gelap dengan pantulan ruangan yang samar
        drawRect(Brush.radialGradient(listOf(Color(0xFF2A2E2C), Color(0xFF0C0D0C)), center = Offset(w * 0.4f, h * 0.35f), radius = maxOf(w, h)))
    }
    if (k > 0f && !standby) {
        // Scanline: garis gelap tipis tiap ~3 px
        val step = 3f
        var y = 0f
        val lineColor = Color.Black.copy(alpha = 0.16f * k)
        while (y < h) {
            drawLine(lineColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            y += step
        }
    }
    // Vignette: tepi layar cembung lebih gelap
    drawRect(
        Brush.radialGradient(
            0f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.25f + 0.35f * k),
            center = Offset(w / 2f, h / 2f), radius = maxOf(w, h) * 0.78f,
        ),
    )
    // Kilau kaca di kiri atas
    drawOval(
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), start = Offset(0f, 0f), end = Offset(w * 0.5f, h * 0.5f)),
        topLeft = Offset(-w * 0.15f, -h * 0.25f), size = Size(w * 0.85f, h * 0.7f),
    )
}

/** Bodi TV, panel kontrol, knob, kisi speaker, dan lampu daya. */
private fun DrawScope.drawTvBody(p: BodyPalette, knob: KnobPalette, turns: Float, highlight: Boolean, on: Boolean) {
    val w = size.width
    val h = size.height
    val corner = CornerRadius(w * TvGeometry.BODY_CORNER)

    // Bayangan kontak di lantai (di luar kotak, tepat di bawah bodi)
    drawOval(
        Brush.radialGradient(listOf(Color(Tokens.Retro.CONTACT_SHADOW), Color.Transparent), center = Offset(w / 2f, h), radius = w * 0.55f),
        topLeft = Offset(-w * 0.04f, h - h * 0.05f), size = Size(w * 1.08f, h * 0.12f),
    )
    // Bodi
    drawRoundRect(Brush.verticalGradient(listOf(p.top, p.bottom)), cornerRadius = corner)
    drawRoundRect(p.trim.copy(alpha = 0.5f), cornerRadius = corner, style = Stroke(width = w * 0.006f))
    // Sorotan tepi atas (volume plastik)
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), endY = h * 0.18f),
        cornerRadius = corner, size = Size(w, h * 0.18f),
    )

    // Bingkai layar (ceruk)
    val s = TvGeometry.SCREEN
    val bez = w * 0.018f
    drawRoundRect(
        Color.Black.copy(alpha = 0.45f),
        topLeft = Offset(s.left * w - bez, s.top * h - bez),
        size = Size(s.width * w + bez * 2, s.height * h + bez * 2),
        cornerRadius = CornerRadius(s.width * w * TvGeometry.SCREEN_CORNER + bez),
    )

    // Panel kontrol
    val pn = TvGeometry.PANEL
    drawRoundRect(
        p.panel,
        topLeft = Offset(pn.left * w, pn.top * h),
        size = Size(pn.width * w, pn.height * h),
        cornerRadius = CornerRadius(w * 0.02f),
    )
    if (p.wood) {
        // Serat kayu samar (deterministik)
        for (i in 0 until 9) {
            val y = pn.top * h + (i + 0.5f) * pn.height * h / 9f
            drawLine(Color.Black.copy(alpha = 0.10f), Offset(pn.left * w + w * 0.008f, y), Offset(pn.right * w - w * 0.008f, y + h * 0.006f * ((i % 3) - 1)), strokeWidth = w * 0.002f)
        }
    }

    // Knob seek: cincin skala + muka knob + jarum penanda
    val c = Offset(TvGeometry.KNOB_CX * w, TvGeometry.KNOB_CY * h)
    val r = TvGeometry.KNOB_R * w
    for (i in 0 until 24) {
        val a = i / 24f * 2 * PI
        val len = if (i % 6 == 0) r * 0.26f else r * 0.14f
        val o = Offset(c.x + cos(a).toFloat() * r * 1.18f, c.y + sin(a).toFloat() * r * 1.18f)
        val e = Offset(c.x + cos(a).toFloat() * (r * 1.18f + len), c.y + sin(a).toFloat() * (r * 1.18f + len))
        drawLine(Color.Black.copy(alpha = 0.35f), o, e, strokeWidth = w * 0.0025f)
    }
    drawCircle(Color.Black.copy(alpha = 0.30f), r * 1.06f, c + Offset(0f, r * 0.08f))
    drawCircle(Brush.radialGradient(listOf(knob.rim, knob.face), center = c - Offset(r * 0.3f, r * 0.3f), radius = r * 1.4f), r, c)
    if (highlight) drawCircle(Color(Tokens.Retro.AMBER).copy(alpha = 0.55f), r * 1.12f, c, style = Stroke(width = w * 0.006f))
    rotate(turns * 360f, c) {
        drawLine(knob.mark, c, Offset(c.x, c.y - r * 0.82f), strokeWidth = w * 0.006f, cap = StrokeCap.Round)
    }

    // Kisi speaker di bawah VFD
    val v = TvGeometry.VFD
    val gridTop = (v.bottom + 0.05f) * h
    val gridBottom = (pn.bottom - 0.08f) * h
    var y = gridTop
    while (y < gridBottom) {
        drawLine(Color.Black.copy(alpha = 0.32f), Offset((pn.left + 0.02f) * w, y), Offset((pn.right - 0.02f) * w, y), strokeWidth = w * 0.004f, cap = StrokeCap.Round)
        y += h * 0.028f
    }

    // Lampu daya: redup merah saat standby, amber saat menyala
    val led = Offset(TvGeometry.LED_CX * w, TvGeometry.LED_CY * h)
    val ledColor = if (on) Color(Tokens.Retro.AMBER) else Color(Tokens.Retro.STANDBY_LED)
    drawCircle(ledColor.copy(alpha = if (on) 0.35f else 0.22f), w * 0.016f, led)
    drawCircle(ledColor.copy(alpha = if (on) 1f else 0.7f), w * 0.007f, led)
}
