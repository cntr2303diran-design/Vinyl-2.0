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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Path
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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vinyl.launcher.data.AlbumMotion
import com.vinyl.launcher.data.KnobStyle
import com.vinyl.launcher.data.TvBody
import com.vinyl.launcher.data.VfdColor
import com.vinyl.launcher.ui.theme.InterDisplay
import com.vinyl.launcher.ui.theme.MonoFamily
import com.vinyl.launcher.ui.theme.RetroColors
import com.vinyl.launcher.ui.theme.Tokens
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Warna satu varian bodi TV. */
private data class BodyPalette(
    val top: Color, val bottom: Color, val inner: Color, val panel: Color, val base: Color, val edge: Color,
)

private fun bodyPalette(body: TvBody): BodyPalette = when (body) {
    // Referensi: plastik krem pudar dengan alas sedikit lebih gelap
    TvBody.CREAM -> BodyPalette(Color(0xFFE6DCC4), Color(0xFFCFC2A5), Color(0xFFD8CCB1), Color(0xFFD3C6A9), Color(0xFFBDAE8F), Color(0xFF9C8E72))
    TvBody.WALNUT -> BodyPalette(Color(0xFF7A563D), Color(0xFF5A3D2A), Color(0xFF6A4A33), Color(0xFFCDB894), Color(0xFF4A3121), Color(0xFF3A2619))
    TvBody.CHARCOAL -> BodyPalette(Color(0xFF4A4C48), Color(0xFF2E302D), Color(0xFF3B3D3A), Color(0xFF555752), Color(0xFF262825), Color(0xFF1A1B19))
    TvBody.SPACE_AGE -> BodyPalette(Color(0xFFDD8C4B), Color(0xFFB9672E), Color(0xFFCA7A3D), Color(0xFFE9DCC4), Color(0xFF9F5525), Color(0xFF7E4219))
}

private data class KnobPalette(val face: Color, val rim: Color, val bar: Color)

private fun knobPalette(style: KnobStyle): KnobPalette = when (style) {
    KnobStyle.BAKELITE -> KnobPalette(Color(0xFF2E2A27), Color(0xFF4A433D), Color(0xFF181614))
    // Referensi: knob logam abu dengan palang gelap
    KnobStyle.CHROME -> KnobPalette(Color(0xFF85867F), Color(0xFF3F403C), Color(0xFF262624))
    KnobStyle.BRASS -> KnobPalette(Color(0xFFC9A55A), Color(0xFF8F6E2F), Color(0xFF3A2A10))
}

/**
 * TV tabung krem orisinal mengikuti gambar referensi: bodi tebal membulat, ceruk layar yang dalam, layar cembung
 * dengan judul lagu di atas cover, panel kanan berisi knob penala, knob seek (berpenanda segitiga), panel kayu dengan
 * VFD tiga baris, tombol daya, alas, dan kaki. Semua posisi dari [TvGeometry] sehingga area sentuh knob sejajar.
 *
 * @param vfdLines tiga baris VFD (mis. PLAY / OUT 48 kHz / BUDS PRO)
 * @param knobTurns putaran knob seek (jarum mengikuti jari)
 */
@Composable
fun CrtTv(
    mode: TvScreenMode,
    art: ImageBitmap?,
    title: String,
    artist: String,
    accent: Color,
    body: TvBody,
    knob: KnobStyle,
    vfdColor: VfdColor,
    vfdLines: List<String>,
    crtStrength: Float,
    motion: AlbumMotion,
    reduceMotion: Boolean,
    titleScrimAlpha: Float,
    modifier: Modifier = Modifier,
    knobTurns: Float = 0f,
    knobHighlight: Boolean = false,
    screenOverlay: @Composable () -> Unit = {},
) {
    val palette = bodyPalette(body)
    val knobColors = knobPalette(knob)
    val crt = crtStrength.coerceIn(0f, 1f)

    // "Power on": garis horizontal mengembang menjadi gambar saat keluar dari siaga
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

        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            drawTvBody(palette, knobColors, knobTurns, knobHighlight, on = mode != TvScreenMode.STANDBY)
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
                            scaleY = (0.015f + 0.985f * ((p - 0.25f) / 0.75f).coerceIn(0f, 1f)).coerceAtLeast(0.015f)
                            scaleX = 0.82f + 0.18f * (p / 0.4f).coerceIn(0f, 1f)
                        },
                ) {
                    ScreenPicture(art, accent, motion, mode == TvScreenMode.MOTION)
                    // Judul & artis di atas cover (referensi), dengan peredam yang menjamin kontras putih
                    if (titleScrimAlpha > 0f) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = titleScrimAlpha.coerceIn(0f, 1f))))
                    }
                    ScreenTitle(title, artist, w)
                    screenOverlay()
                }
                if (p < 1f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = (1f - p) * 0.35f)))
            }
            Canvas(Modifier.fillMaxSize()) { drawCrtGlass(crt, standby = mode == TvScreenMode.STANDBY) }
        }

        // VFD tiga baris di panel kayu
        val vfd = TvGeometry.VFD
        Column(
            Modifier
                .offset(fx(vfd.left), fy(vfd.top))
                .size(fx(vfd.width), fy(vfd.height))
                .clip(RoundedCornerShape(2.dp))
                .background(RetroColors.VfdGlass)
                .padding(horizontal = 3.dp, vertical = 2.dp)
                .clearAndSetSemantics { },
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            val color = if (vfdColor == VfdColor.GREEN) Color(0xFFC6DD7E) else RetroColors.VfdAmber
            val size = (w.value * 0.0165f).coerceIn(6f, 15f).sp
            vfdLines.take(3).forEach { line ->
                Text(
                    line, color = color, fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = size,
                    lineHeight = size, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

/** Judul besar & artis kecil di tengah layar, seperti kartu judul siaran. */
@Composable
private fun ScreenTitle(title: String, artist: String, tvWidth: Dp) {
    if (title.isBlank()) return
    Column(
        Modifier.fillMaxSize().padding(horizontal = tvWidth * 0.05f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val big = (tvWidth.value * 0.094f).coerceIn(16f, 80f).sp
        Text(
            title.uppercase(),
            color = Color.White,
            fontFamily = InterDisplay,
            fontWeight = FontWeight.Bold,
            fontSize = big,
            lineHeight = big * 0.94f,
            letterSpacing = 0.01.em,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        if (artist.isNotBlank()) {
            Text(
                artist.uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = (big.value * 0.27f).coerceAtLeast(9f).sp,
                letterSpacing = 0.06.em,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = tvWidth * 0.07f),
            )
        }
    }
}

/** Cover di layar (Mode B) atau cover bergerak (Mode C). */
@Composable
private fun ScreenPicture(art: ImageBitmap?, accent: Color, motion: AlbumMotion, moving: Boolean) {
    val t = if (moving) {
        val tr = rememberInfiniteTransition(label = "albumMotion")
        val v by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Restart), label = "m")
        v
    } else 0f
    val cycle = sin(t * 2 * PI).toFloat()
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
            // Lagu tanpa cover: warna aksen saja (judul tetap ditulis di atasnya), bukan gambar tiruan
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(accent.copy(alpha = 0.85f), Color(0xFF14171B)))))
        }
    }
}

/** Kaca CRT: scanline, vignette, kilau cembung. [k] kekuatan efek 0..1. */
private fun DrawScope.drawCrtGlass(k: Float, standby: Boolean) {
    val w = size.width
    val h = size.height
    if (standby) {
        drawRect(Brush.radialGradient(listOf(Color(0xFF2C3133), Color(0xFF0B0C0D)), center = Offset(w * 0.42f, h * 0.38f), radius = maxOf(w, h)))
    }
    if (k > 0f && !standby) {
        var y = 0f
        val line = Color.Black.copy(alpha = 0.14f * k)
        while (y < h) {
            drawLine(line, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            y += 3f
        }
    }
    // Tepi cembung lebih gelap
    drawRect(
        Brush.radialGradient(
            0f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.30f + 0.30f * k),
            center = Offset(w / 2f, h / 2f), radius = maxOf(w, h) * 0.74f,
        ),
    )
    // Kilau kaca: pita terang tipis di atas & pantulan miring
    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), endY = h * 0.12f), size = Size(w, h * 0.12f))
    drawOval(
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent), start = Offset(0f, 0f), end = Offset(w * 0.45f, h * 0.45f)),
        topLeft = Offset(-w * 0.18f, -h * 0.28f), size = Size(w * 0.8f, h * 0.68f),
    )
}

/** Bodi TV, ceruk layar, panel kontrol, dua knob, panel kayu, tombol, alas & kaki. */
private fun DrawScope.drawTvBody(p: BodyPalette, knob: KnobPalette, turns: Float, highlight: Boolean, on: Boolean) {
    val w = size.width
    val h = size.height
    val corner = CornerRadius(w * TvGeometry.BODY_CORNER)

    // Kaki (sedikit di luar bodi bawah)
    for (x in listOf(0.13f, 0.84f)) {
        drawRoundRect(p.base.copy(alpha = 1f), topLeft = Offset(w * x, h * 0.975f), size = Size(w * 0.05f, h * 0.03f), cornerRadius = CornerRadius(w * 0.006f))
    }
    // Bodi
    drawRoundRect(Brush.verticalGradient(listOf(p.top, p.bottom)), size = Size(w, h * 0.985f), cornerRadius = corner)
    // Alas sedikit lebih gelap
    drawRoundRect(
        Brush.verticalGradient(listOf(p.base.copy(alpha = 0f), p.base.copy(alpha = 0.85f)), startY = h * (TvGeometry.BASE_TOP - 0.03f), endY = h * 0.985f),
        topLeft = Offset(0f, h * (TvGeometry.BASE_TOP - 0.03f)), size = Size(w, h * (0.985f - TvGeometry.BASE_TOP + 0.03f)), cornerRadius = corner,
    )
    drawLine(p.edge.copy(alpha = 0.5f), Offset(w * 0.03f, h * TvGeometry.BASE_TOP), Offset(w * 0.97f, h * TvGeometry.BASE_TOP), strokeWidth = w * 0.002f)
    // Tepi bodi & sorotan atas (volume plastik)
    drawRoundRect(p.edge.copy(alpha = 0.55f), size = Size(w, h * 0.985f), cornerRadius = corner, style = Stroke(width = w * 0.004f))
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), endY = h * 0.06f),
        size = Size(w, h * 0.06f), cornerRadius = corner,
    )
    // Volume bodi: sisi kiri/kanan lebih gelap (plastik melengkung), sorotan lembut di sepertiga kiri atas
    drawRoundRect(
        Brush.horizontalGradient(
            0f to Color.Black.copy(alpha = 0.20f), 0.07f to Color.Transparent, 0.35f to Color.White.copy(alpha = 0.06f),
            0.9f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.26f),
        ),
        size = Size(w, h * 0.985f), cornerRadius = corner,
    )
    drawRoundRect(
        Brush.verticalGradient(0f to Color.Transparent, 0.75f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.18f)),
        size = Size(w, h * 0.985f), cornerRadius = corner,
    )
    // Bingkai dalam (sedikit lebih gelap dari bodi) mengelilingi layar
    val b = TvGeometry.BEZEL
    drawRoundRect(
        p.inner,
        topLeft = Offset(b.left * w - w * 0.012f, b.top * h - w * 0.012f),
        size = Size(b.width * w + w * 0.024f, b.height * h + w * 0.024f),
        cornerRadius = CornerRadius(b.width * w * TvGeometry.SCREEN_CORNER + w * 0.012f),
    )
    // Ceruk layar yang dalam: gelap di tepi, gradasi ke dalam
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFF2B2620), Color(0xFF4A4236), Color(0xFF231F1A))),
        topLeft = Offset(b.left * w, b.top * h), size = Size(b.width * w, b.height * h),
        cornerRadius = CornerRadius(b.width * w * TvGeometry.SCREEN_CORNER),
    )

    // Panel kontrol
    val pn = TvGeometry.PANEL
    drawRoundRect(p.panel, topLeft = Offset(pn.left * w, pn.top * h), size = Size(pn.width * w, pn.height * h), cornerRadius = CornerRadius(w * 0.008f))
    drawRoundRect(p.edge.copy(alpha = 0.6f), topLeft = Offset(pn.left * w, pn.top * h), size = Size(pn.width * w, pn.height * h), cornerRadius = CornerRadius(w * 0.008f), style = Stroke(width = w * 0.002f))
    // Strip kayu atas & panel kayu VFD
    val wood = Color(0xFF4B2F1D)
    val wt = TvGeometry.WOOD_TOP
    drawRect(Brush.verticalGradient(listOf(Color(0xFF5E3C25), wood)), topLeft = Offset(wt.left * w, wt.top * h), size = Size(wt.width * w, wt.height * h))
    val wv = TvGeometry.WOOD_VFD
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF654B37), Color(0xFF4E3A2A))), topLeft = Offset(wv.left * w, wv.top * h), size = Size(wv.width * w, wv.height * h), cornerRadius = CornerRadius(w * 0.004f))
    for (i in 0 until 6) {
        val yy = (wv.top + wv.height * (i + 0.5f) / 6f) * h
        drawLine(Color.Black.copy(alpha = 0.10f), Offset(wv.left * w, yy), Offset(wv.right * w, yy + h * 0.004f * ((i % 3) - 1)), strokeWidth = w * 0.0015f)
    }

    // Knob penala (dekoratif) dan knob seek
    drawKnob(Offset(TvGeometry.TUNER_CX * w, TvGeometry.TUNER_CY * h), TvGeometry.TUNER_R * w, knob, angle = -35f, highlight = false)
    // Penanda segitiga di atas knob seek
    val kc = Offset(TvGeometry.KNOB_CX * w, TvGeometry.KNOB_CY * h)
    val kr = TvGeometry.KNOB_R * w
    val tri = Path().apply {
        moveTo(kc.x - kr * 0.14f, kc.y - kr * 1.38f); lineTo(kc.x + kr * 0.14f, kc.y - kr * 1.38f); lineTo(kc.x, kc.y - kr * 1.2f); close()
    }
    drawPath(tri, Color(0xFF3A342B))
    drawKnob(kc, kr, knob, angle = turns * 360f - 30f, highlight = highlight)

    // Tombol daya & lampu
    val led = Offset(TvGeometry.LED_CX * w, TvGeometry.LED_CY * h)
    drawCircle(Color.Black.copy(alpha = 0.25f), w * 0.013f, led + Offset(0f, w * 0.002f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFDADBD6), Color(0xFF8A8B86)), center = led - Offset(w * 0.004f, w * 0.004f), radius = w * 0.014f), w * 0.011f, led)
    val ledColor = if (on) Color(Tokens.Retro.AMBER) else Color(Tokens.Retro.STANDBY_LED)
    drawCircle(ledColor.copy(alpha = if (on) 0.9f else 0.6f), w * 0.003f, Offset(led.x, led.y - w * 0.024f))
}

/** Knob bergerigi dengan palang penunjuk; [angle] derajat. */
private fun DrawScope.drawKnob(c: Offset, r: Float, k: KnobPalette, angle: Float, highlight: Boolean) {
    // Ceruk & bayangan
    drawCircle(Color.Black.copy(alpha = 0.30f), r * 1.18f, c + Offset(r * 0.06f, r * 0.12f))
    drawCircle(Color.Black.copy(alpha = 0.18f), r * 1.14f, c)
    // Gerigi tepi
    for (i in 0 until 36) {
        val a = i / 36f * 2 * PI
        drawLine(
            k.rim, Offset(c.x + cos(a).toFloat() * r * 0.92f, c.y + sin(a).toFloat() * r * 0.92f),
            Offset(c.x + cos(a).toFloat() * r * 1.04f, c.y + sin(a).toFloat() * r * 1.04f), strokeWidth = r * 0.06f,
        )
    }
    drawCircle(Brush.radialGradient(listOf(k.face.copy(alpha = 1f), k.rim), center = c - Offset(r * 0.35f, r * 0.35f), radius = r * 1.5f), r * 0.9f, c)
    if (highlight) drawCircle(Color(Tokens.Retro.AMBER).copy(alpha = 0.6f), r * 1.2f, c, style = Stroke(width = r * 0.09f))
    // Palang penunjuk
    rotate(angle, c) {
        drawLine(k.bar, Offset(c.x, c.y - r * 0.74f), Offset(c.x, c.y + r * 0.74f), strokeWidth = r * 0.24f, cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.25f), Offset(c.x - r * 0.06f, c.y - r * 0.7f), Offset(c.x - r * 0.06f, c.y + r * 0.1f), strokeWidth = r * 0.05f, cap = StrokeCap.Round)
    }
}
