package com.vinyl.launcher.ui.room

import android.os.Build
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinyl.launcher.data.AlbumMotion
import com.vinyl.launcher.data.KnobStyle
import com.vinyl.launcher.data.TvBody
import com.vinyl.launcher.data.VfdColor
import com.vinyl.launcher.ui.theme.MonoFamily
import com.vinyl.launcher.ui.theme.OsdFamily
import com.vinyl.launcher.ui.theme.RetroColors
import com.vinyl.launcher.ui.theme.Tokens
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Bahan muka depan & kabinet TV untuk satu varian bodi. Dipakai juga oleh panggung (atap & sisi kabinet). */
@Immutable
internal data class TvMaterial(
    val face: Color,
    val faceLight: Color,
    val faceDark: Color,
    val bezel: Color,
    val bezelLight: Color,
    val panel: Color,
    val panelLight: Color,
    val panelDark: Color,
    /** Serat kayu digambar di kabinet. */
    val wood: Boolean,
)

internal fun tvMaterial(body: TvBody): TvMaterial = when (body) {
    // Kabinet kayu walnut gelap, bingkai layar plastik hitam, panel kontrol aluminium (TV konsol 80-an)
    TvBody.WALNUT -> TvMaterial(
        Color(0xFF5E3D26), Color(0xFF7C5538), Color(0xFF2F1E12),
        Color(0xFF141414), Color(0xFF34332F),
        Color(0xFF8F8C86), Color(0xFFC2BFB8), Color(0xFF5B5955), wood = true,
    )
    // Plastik dempul pudar menguning, bingkai coklat-abu gelap
    TvBody.CREAM -> TvMaterial(
        Color(0xFFC3B89E), Color(0xFFDCD2BA), Color(0xFF8E846D),
        Color(0xFF3A352E), Color(0xFF5B544A),
        Color(0xFFB2A88F), Color(0xFFCEC5AE), Color(0xFF857C66), wood = false,
    )
    TvBody.CHARCOAL -> TvMaterial(
        Color(0xFF2C2D2B), Color(0xFF45464A), Color(0xFF161716),
        Color(0xFF0E0F0F), Color(0xFF2A2B2C),
        Color(0xFF3B3C3E), Color(0xFF5A5B5E), Color(0xFF232425), wood = false,
    )
    TvBody.SPACE_AGE -> TvMaterial(
        Color(0xFFCC6E2C), Color(0xFFEE9A55), Color(0xFF86411A),
        Color(0xFFE7DFD1), Color(0xFFFFFFFF),
        Color(0xFFE0D7C6), Color(0xFFF5EFE3), Color(0xFFAFA592), wood = false,
    )
}

private data class KnobPalette(val face: Color, val rim: Color, val bar: Color, val shine: Color)

private fun knobPalette(style: KnobStyle): KnobPalette = when (style) {
    KnobStyle.BAKELITE -> KnobPalette(Color(0xFF2B2826), Color(0xFF0F0E0D), Color(0xFFD9D2C4), Color(0x55FFFFFF))
    KnobStyle.CHROME -> KnobPalette(Color(0xFFB4B5B0), Color(0xFF55564F), Color(0xFF1E1E1C), Color(0x99FFFFFF))
    KnobStyle.BRASS -> KnobPalette(Color(0xFFCDA75E), Color(0xFF7A5A22), Color(0xFF2E2108), Color(0x88FFF3D0))
}

/**
 * Muka depan TV tabung: kabinet (kayu walnut, plastik dempul, arang, atau oranye), bingkai layar tebal, kaca cembung
 * berisi cover (dengan judul bergaya teks VHS), dan kolom kontrol di kanan: kisi speaker, knob penala, knob SEEK (area
 * sentuhnya di [com.vinyl.launcher.ui.room.stage.SeekKnob]), VFD kecil, tombol daya & lampu. Semua posisi dari [TvGeometry].
 *
 * Dipakai di beranda (dengan atap & sisi kabinet digambar panggung), layar kunci, dan onboarding.
 *
 * @param vfdLines baris VFD; TV menampilkan baris pertama di VFD kecil panel kontrol
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
    /** Video milik pengguna di layar (menggantikan cover setelah frame pertamanya tampil); null = cover saja. */
    screenVideo: (@Composable () -> Unit)? = null,
    /**
     * Isi layar hidup (halaman film, game): menggantikan cover & judul. Lapisan ini TIDAK diberi efek tabung AGSL —
     * RenderEffect tidak boleh dipasang pada lapisan yang memuat WebView — tetapi kaca tabung (garis pindai, vignette,
     * kilau) tetap digambar di atasnya.
     */
    screenLive: (@Composable () -> Unit)? = null,
    /** TV dimatikan (mis. timer tidur selesai): gambar menyusut menjadi garis lalu titik, kemudian siaga. */
    poweredOff: Boolean = false,
) {
    val material = tvMaterial(body)
    val knobColors = knobPalette(knob)
    val crt = crtStrength.coerceIn(0f, 1f)

    // "Power on": garis horizontal mengembang menjadi gambar saat keluar dari siaga
    val power = remember { Animatable(if (mode == TvScreenMode.STANDBY) 0f else 1f) }
    LaunchedEffect(mode == TvScreenMode.STANDBY) {
        if (mode == TvScreenMode.STANDBY) power.snapTo(0f)
        else if (reduceMotion) power.snapTo(1f)
        else power.animateTo(1f, tween(Tokens.Motion.CRT_POWER_ON_MS, easing = LinearEasing))
    }
    // "Power off": gambar menyusut tegak menjadi garis, lalu mendatar menjadi titik terang, lalu gelap
    val off = remember { Animatable(if (poweredOff) 1f else 0f) }
    LaunchedEffect(poweredOff) {
        if (!poweredOff) off.snapTo(0f)
        else if (reduceMotion) off.snapTo(1f)
        else off.animateTo(1f, tween(Tokens.Motion.CRT_POWER_OFF_MS, easing = LinearEasing))
    }
    val dark = mode == TvScreenMode.STANDBY || (poweredOff && off.value >= 1f)
    // Lengkung & aberasi tabung (AGSL, Android 13+); null di versi lama atau bila efek tabung dimatikan
    val crtShader = remember(crt > 0f) {
        if (crt > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) CrtShader.createOrNull() else null
    }

    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        fun fx(f: Float): Dp = w * f
        fun fy(f: Float): Dp = h * f

        // Kabinet, bingkai, panel kontrol (bagian statis di-cache; knob digambar terpisah karena berputar)
        Box(
            Modifier
                .fillMaxSize()
                .clearAndSetSemantics { }
                .drawWithCache {
                    val grain = if (material.wood) woodGrain(size, seed = 7, lines = 26, vertical = false) else null
                    onDrawBehind { drawTvCabinetFace(material, grain) }
                },
        )
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            drawTvKnobs(knobColors, knobTurns, knobHighlight, on = !dark)
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
            if (!dark) {
                val p = power.value
                val q = off.value
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (crtShader != null && screenLive == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                renderEffect = crtShader.effect(size.width, size.height, crt)
                            }
                            if (q > 0f) {
                                scaleY = (1f - q / 0.6f).coerceAtLeast(0.012f)
                                scaleX = if (q < 0.6f) 1f else (1f - (q - 0.6f) / 0.4f).coerceAtLeast(0.02f)
                            } else {
                                scaleY = (0.015f + 0.985f * ((p - 0.25f) / 0.75f).coerceIn(0f, 1f)).coerceAtLeast(0.015f)
                                scaleX = 0.82f + 0.18f * (p / 0.4f).coerceIn(0f, 1f)
                            }
                        },
                ) {
                    if (screenLive != null) {
                        screenLive()
                    } else {
                        ScreenPicture(art, accent, motion, mode == TvScreenMode.MOTION)
                        screenVideo?.invoke()
                        // Sedikit redup agar warna terasa seperti tabung; judul memakai kotak teks hitam sendiri (selalu terbaca)
                        if (titleScrimAlpha > 0f) {
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (titleScrimAlpha * 0.45f).coerceIn(0f, 0.5f))))
                        }
                        ScreenCaption(title, artist, w)
                    }
                    screenOverlay()
                    // Garis/titik yang tersisa saat dimatikan menyala putih seperti tabung sungguhan
                    if (q > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = (q * 1.4f).coerceAtMost(0.9f))))
                }
                if (p < 1f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = (1f - p) * 0.35f)))
            } else if (mode == TvScreenMode.STANDBY && !poweredOff) {
                StandbyText(w)
            }
            Canvas(Modifier.fillMaxSize()) { drawCrtGlass(crt, standby = dark) }
        }

        // VFD kecil di panel kontrol (baris pertama)
        val vfd = TvGeometry.VFD
        val line = vfdLines.firstOrNull().orEmpty()
        Box(
            Modifier
                .offset(fx(vfd.left), fy(vfd.top))
                .size(fx(vfd.width), fy(vfd.height))
                .clip(RoundedCornerShape(2.dp))
                .background(RetroColors.VfdGlass)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            val color = if (vfdColor == VfdColor.GREEN) RetroColors.VfdGreen else RetroColors.VfdAmber
            // Bagian dari gambar TV: ukurannya mengikuti TV, bukan skala huruf sistem (kalau ikut, keluar jendela VFD)
            val size = ((w.value * 0.021f).coerceIn(6f, 16f) / LocalDensity.current.fontScale).sp
            Text(
                line, color = color, fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = size,
                lineHeight = size, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
            )
        }
    }
}

/** Judul & artis di pojok kiri bawah layar, gaya teks rekaman VHS: huruf OSD putih di atas kotak hitam. */
@Composable
private fun ScreenCaption(title: String, artist: String, tvWidth: Dp) {
    if (title.isBlank()) return
    // Skala huruf sistem ikut sampai 1,3× (lebih besar menutupi seluruh layar TV); judul lengkap tetap di TalkBack & lirik
    val fs = LocalDensity.current.fontScale
    val big = (tvWidth.value * 0.062f).coerceIn(13f, 54f) * fs.coerceAtMost(1.3f) / fs
    Column(
        Modifier.fillMaxSize().padding(start = tvWidth * 0.035f, end = tvWidth * 0.05f, bottom = tvWidth * 0.04f),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            title.uppercase(),
            color = Color.White,
            fontFamily = OsdFamily,
            fontSize = big.sp,
            lineHeight = (big * 0.95f).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = tvWidth * 0.62f)
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(horizontal = (big * 0.25f).dp, vertical = (big * 0.04f).dp),
        )
        if (artist.isNotBlank()) {
            Text(
                artist.uppercase(),
                color = Color.White,
                fontFamily = OsdFamily,
                fontSize = (big * 0.62f).sp,
                lineHeight = (big * 0.62f).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = (big * 0.12f).dp)
                    .widthIn(max = tvWidth * 0.62f)
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = (big * 0.25f).dp, vertical = (big * 0.03f).dp),
            )
        }
    }
}

/** Layar siaga: kaca gelap dengan tulisan redup (bukan layar kosong yang tampak rusak). */
@Composable
private fun StandbyText(tvWidth: Dp) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "NO TAPE",
            color = Color.White.copy(alpha = 0.22f),
            fontFamily = OsdFamily,
            fontSize = ((tvWidth.value * 0.05f).coerceIn(10f, 40f) / LocalDensity.current.fontScale).sp,
        )
    }
}

/** Cover di layar (Mode B) atau cover bergerak (Mode C). */
@Composable
private fun ScreenPicture(art: ImageBitmap?, accent: Color, motion: AlbumMotion, moving: Boolean) {
    // Fase animasi dibaca di graphicsLayer (fase gambar): cover bergerak tanpa rekomposisi tiap frame
    val phase: State<Float>? = if (moving) {
        rememberInfiniteTransition(label = "albumMotion")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Restart), label = "m")
    } else null
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val t = phase?.value ?: 0f
                val cycle = sin(t * 2 * PI).toFloat()
                if (phase != null) when (motion) {
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
        drawRect(Brush.radialGradient(listOf(Color(0xFF262B2C), Color(0xFF08090A)), center = Offset(w * 0.42f, h * 0.38f), radius = maxOf(w, h)))
    }
    if (k > 0f && !standby) {
        var y = 0f
        val line = Color.Black.copy(alpha = 0.16f * k)
        while (y < h) {
            drawLine(line, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            y += 3f
        }
    }
    // Tepi cembung lebih gelap
    drawRect(
        Brush.radialGradient(
            0f to Color.Transparent, 0.62f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.34f + 0.30f * k),
            center = Offset(w / 2f, h / 2f), radius = maxOf(w, h) * 0.74f,
        ),
    )
    // Kilau kaca: pita terang tipis di atas, pantulan miring lebar, dan garis kilap di tepi bawah
    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.11f), Color.Transparent), endY = h * 0.14f), size = Size(w, h * 0.14f))
    drawOval(
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.13f), Color.Transparent), start = Offset(0f, 0f), end = Offset(w * 0.5f, h * 0.5f)),
        topLeft = Offset(-w * 0.2f, -h * 0.3f), size = Size(w * 0.85f, h * 0.72f),
    )
    drawLine(Color.White.copy(alpha = 0.07f), Offset(w * 0.08f, h * 0.965f), Offset(w * 0.92f, h * 0.965f), strokeWidth = h * 0.006f, cap = StrokeCap.Round)
}

/**
 * Serat kayu: garis bergelombang lembut sepanjang [size], ditentukan [seed] (selalu sama untuk ukuran yang sama).
 * [vertical] = serat tegak (sisi kabinet), selain itu mendatar.
 */
internal fun woodGrain(size: Size, seed: Int, lines: Int, vertical: Boolean): Path {
    val p = Path()
    val len = if (vertical) size.height else size.width
    val span = if (vertical) size.width else size.height
    var s = seed * 1103515245 + 12345
    fun rnd(): Float { s = s * 1103515245 + 12345; return ((s ushr 8) and 0xFFFF) / 65535f }
    for (i in 0 until lines) {
        val base = span * (i + 0.5f + (rnd() - 0.5f) * 0.6f) / lines
        val amp = span / lines * (0.25f + rnd() * 0.6f)
        val waves = 1.5f + rnd() * 2.5f
        val phase = rnd() * 6.28f
        val steps = 24
        for (j in 0..steps) {
            val t = j / steps.toFloat()
            val along = t * len
            val across = base + amp * sin(phase + t * waves * 6.283f) + amp * 0.35f * sin(phase * 1.7f + t * waves * 15.1f)
            val x = if (vertical) across else along
            val y = if (vertical) along else across
            if (j == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
    }
    return p
}

private fun Rect.inflate(d: Float) = Rect(left - d, top - d, right + d, bottom + d)

private fun DrawScope.fr(r: FracRect) = Rect(r.left * size.width, r.top * size.height, r.right * size.width, r.bottom * size.height)

/** Kabinet muka depan, bingkai layar, ceruk kaca, panel kontrol dengan kisi speaker, VFD, tombol daya. */
private fun DrawScope.drawTvCabinetFace(m: TvMaterial, grain: Path?) {
    val w = size.width
    val h = size.height
    val r = CornerRadius(w * TvGeometry.BODY_CORNER)

    // Kabinet
    drawRoundRect(Brush.verticalGradient(0f to m.faceLight, 0.35f to m.face, 1f to m.faceDark), cornerRadius = r)
    if (grain != null) {
        drawPath(grain, Color.Black.copy(alpha = 0.22f), style = Stroke(width = w * 0.0022f))
        drawPath(grain, Color.White.copy(alpha = 0.05f), style = Stroke(width = w * 0.0010f))
    }
    // Pernis / kilap plastik: sorot miring lebar dari kiri atas (arah cahaya ruangan)
    drawRoundRect(
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent), start = Offset.Zero, end = Offset(w * 0.55f, h * 0.7f)),
        cornerRadius = r,
    )
    // Tepi bevel: atas terang, bawah gelap
    drawRoundRect(
        Brush.verticalGradient(0f to Color.White.copy(alpha = 0.30f), 0.04f to Color.Transparent, 0.95f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f)),
        cornerRadius = r, style = Stroke(width = w * 0.006f),
    )

    // Bingkai layar (tebal, membulat), dengan bibir terang di tepi luar
    val bz = fr(TvGeometry.BEZEL)
    val bzr = CornerRadius(bz.width * TvGeometry.SCREEN_CORNER * 1.08f)
    drawRoundRect(Color.Black.copy(alpha = 0.45f), topLeft = bz.topLeft + Offset(0f, w * 0.004f), size = bz.size, cornerRadius = bzr)
    drawRoundRect(
        Brush.verticalGradient(listOf(m.bezelLight, m.bezel, m.bezel, Color.Black)),
        topLeft = bz.topLeft, size = bz.size, cornerRadius = bzr,
    )
    drawRoundRect(Color.White.copy(alpha = 0.10f), topLeft = bz.topLeft, size = bz.size, cornerRadius = bzr, style = Stroke(width = w * 0.003f))
    // Ceruk kaca: cincin gelap yang dalam
    val sc = fr(TvGeometry.SCREEN)
    val scr = CornerRadius(sc.width * TvGeometry.SCREEN_CORNER)
    val ring = sc.inflate(w * 0.009f)
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.Black, Color(0xFF1C1B19), Color.Black)),
        topLeft = ring.topLeft, size = ring.size, cornerRadius = CornerRadius(scr.x + w * 0.009f),
    )

    // Panel kontrol
    val pn = fr(TvGeometry.PANEL)
    val pr = CornerRadius(w * 0.010f)
    drawRoundRect(Color.Black.copy(alpha = 0.40f), topLeft = pn.topLeft - Offset(w * 0.002f, w * 0.002f), size = Size(pn.width + w * 0.004f, pn.height + w * 0.004f), cornerRadius = pr)
    drawRoundRect(Brush.verticalGradient(listOf(m.panelLight, m.panel, m.panelDark)), topLeft = pn.topLeft, size = pn.size, cornerRadius = pr)
    // Garis sikat aluminium halus
    var y = pn.top + w * 0.004f
    while (y < pn.bottom) {
        drawLine(Color.White.copy(alpha = 0.035f), Offset(pn.left, y), Offset(pn.right, y), strokeWidth = 1f)
        y += w * 0.0045f
    }
    // Kisi speaker: celah membulat dengan bayangan di dalam dan sorot di bawah
    val gr = fr(TvGeometry.GRILLE)
    val slots = 9
    val slotH = gr.height / (slots * 1.9f)
    for (i in 0 until slots) {
        val top = gr.top + gr.height * i / slots
        drawRoundRect(Color.Black.copy(alpha = 0.78f), topLeft = Offset(gr.left, top), size = Size(gr.width, slotH), cornerRadius = CornerRadius(slotH / 2f))
        drawLine(Color.White.copy(alpha = 0.16f), Offset(gr.left + slotH / 2f, top + slotH * 1.25f), Offset(gr.right - slotH / 2f, top + slotH * 1.25f), strokeWidth = 1f)
    }
    // Bingkai VFD
    val vf = fr(TvGeometry.VFD).inflate(w * 0.004f)
    drawRoundRect(Color.Black.copy(alpha = 0.65f), topLeft = vf.topLeft, size = vf.size, cornerRadius = CornerRadius(w * 0.004f))
}

/** Knob penala (dekoratif) dan knob SEEK, tombol daya dan lampu. */
private fun DrawScope.drawTvKnobs(k: KnobPalette, turns: Float, highlight: Boolean, on: Boolean) {
    val w = size.width
    val h = size.height
    drawKnob(Offset(TvGeometry.TUNER_CX * w, TvGeometry.TUNER_CY * h), TvGeometry.TUNER_R * w, k, angle = -35f, highlight = false)
    val kc = Offset(TvGeometry.KNOB_CX * w, TvGeometry.KNOB_CY * h)
    val kr = TvGeometry.KNOB_R * w
    // Skala takik di sekeliling knob seek
    for (i in 0 until 24) {
        val a = i / 24f * 2 * PI
        val r0 = kr * 1.26f
        val r1 = kr * (if (i % 6 == 0) 1.42f else 1.34f)
        drawLine(
            Color.Black.copy(alpha = 0.45f),
            Offset(kc.x + cos(a).toFloat() * r0, kc.y + sin(a).toFloat() * r0),
            Offset(kc.x + cos(a).toFloat() * r1, kc.y + sin(a).toFloat() * r1),
            strokeWidth = w * 0.0022f,
        )
    }
    drawKnob(kc, kr, k, angle = turns * 360f - 30f, highlight = highlight)

    // Tombol daya & lampu
    val led = Offset(TvGeometry.LED_CX * w, TvGeometry.LED_CY * h)
    val btn = Offset(TvGeometry.POWER_CX * w, TvGeometry.POWER_CY * h)
    drawCircle(Color.Black.copy(alpha = 0.4f), w * 0.013f, btn + Offset(0f, w * 0.002f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFE2E2DD), Color(0xFF7E7F7A)), center = btn - Offset(w * 0.004f, w * 0.004f), radius = w * 0.016f), w * 0.011f, btn)
    val ledColor = if (on) Color(0xFFFF4A3A) else Color(Tokens.Retro.STANDBY_LED)
    if (on) drawCircle(Brush.radialGradient(listOf(ledColor.copy(alpha = 0.45f), Color.Transparent), center = led, radius = w * 0.018f), w * 0.018f, led)
    drawCircle(ledColor.copy(alpha = if (on) 1f else 0.55f), w * 0.0045f, led)
}

/** Knob bergerigi dengan penanda; [angle] derajat. */
private fun DrawScope.drawKnob(c: Offset, r: Float, k: KnobPalette, angle: Float, highlight: Boolean) {
    // Bayangan jatuh ke panel (cahaya dari kiri atas)
    drawCircle(Color.Black.copy(alpha = 0.38f), r * 1.12f, c + Offset(r * 0.10f, r * 0.16f))
    // Rok knob bergerigi
    drawCircle(k.rim, r * 1.06f, c)
    for (i in 0 until 40) {
        val a = i / 40f * 2 * PI
        drawLine(
            Color.Black.copy(alpha = 0.35f), Offset(c.x + cos(a).toFloat() * r * 0.92f, c.y + sin(a).toFloat() * r * 0.92f),
            Offset(c.x + cos(a).toFloat() * r * 1.06f, c.y + sin(a).toFloat() * r * 1.06f), strokeWidth = r * 0.05f,
        )
    }
    // Muka knob cekung: gradasi radial dengan sorot kiri atas
    drawCircle(Brush.radialGradient(listOf(k.face, k.rim), center = c - Offset(r * 0.38f, r * 0.42f), radius = r * 1.6f), r * 0.86f, c)
    drawCircle(Brush.radialGradient(listOf(k.shine, Color.Transparent), center = c - Offset(r * 0.32f, r * 0.36f), radius = r * 0.55f), r * 0.86f, c)
    if (highlight) drawCircle(Color(Tokens.Retro.AMBER).copy(alpha = 0.75f), r * 1.2f, c, style = Stroke(width = r * 0.10f))
    // Penanda
    rotate(angle, c) {
        drawLine(k.bar, Offset(c.x, c.y - r * 0.80f), Offset(c.x, c.y - r * 0.30f), strokeWidth = r * 0.16f, cap = StrokeCap.Round)
    }
}
