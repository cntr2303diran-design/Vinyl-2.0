package com.vinyl.launcher.ui.room

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.vinyl.launcher.data.RetroSettings
import com.vinyl.launcher.data.RoomTone
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.ui.cassette.Cassette
import com.vinyl.launcher.ui.cassette.CassetteLabel
import com.vinyl.launcher.ui.cassette.KnobSeek
import com.vinyl.launcher.ui.theme.Ease
import com.vinyl.launcher.ui.theme.Tokens

/** Teks indikator yang mengelilingi TV. */
@Immutable
data class SceneStatus(
    val timeText: String?,
    val outputText: String,
    val vfdLines: List<String>,
)

/** Warna adegan untuk satu nada ruang. Teks >= 4,5:1 di atas dinding (diuji di RoomSceneColorsTest). */
@Immutable
data class SceneColors(
    val wallCenter: Color,
    val wallEdge: Color,
    val sideWall: Color,
    val floorNear: Color,
    val floorFar: Color,
    val cornerLine: Color,
    val label: Color,
    val value: Color,
    val icon: Color,
    val play: Color,
    val track: Color,
)

object SceneTheme {
    /** Ruang gelap seperti referensi. Label di referensi (#515254) hanya 2,4:1; dinaikkan agar >= 4,5:1. */
    val DIM = SceneColors(
        wallCenter = Color(0xFF2B3038), wallEdge = Color(0xFF0E0F12), sideWall = Color(0xFF111316),
        floorNear = Color(0xFF0C0D0F), floorFar = Color(0xFF191C21), cornerLine = Color(0xFF2A2E35),
        label = Color(0xFF8E9298), value = Color(0xFFD5D8DC), icon = Color(0xFF9A9EA3), play = Color(0xFFD6A64D),
        track = Color(0xFFD5D8DC),
    )
    /** Ruang putih (pilihan "Terang"). */
    val LIGHT = SceneColors(
        wallCenter = Color(0xFFFBFAF7), wallEdge = Color(0xFFE9E6DF), sideWall = Color(0xFFE2DED6),
        floorNear = Color(0xFFE4E0D8), floorFar = Color(0xFFEFEDE7), cornerLine = Color(0xFFD7D2C8),
        label = Color(0xFF5F5B55), value = Color(0xFF1E1D1B), icon = Color(0xFF55524C), play = Color(0xFF8A5A10),
        track = Color(0xFF1E1D1B),
    )

    fun of(tone: RoomTone): SceneColors = if (tone == RoomTone.LIGHT) LIGHT else DIM
}

/**
 * Beranda "ruang TV" mengikuti gambar referensi: dinding gelap yang diterangi cahaya layar, TV tabung krem di lantai
 * mengilap (dengan pantulan), kaset tergeletak di depan TV dengan gulungan yang berputar saat musik berjalan, tombol
 * sebelumnya / mundur 10 detik / putar-jeda / berikutnya mengapit kaset, garis progres di bawah kaset, volume di kanan
 * bawah, dan indikator TIME / Output di sudut atas. Semua posisi dari [SceneLayout] (pecahan layar).
 *
 * Ketuk TV → kamera mendekat pelan (dan lirik tampil di layar bila ada); ketuk lagi → menjauh. Ketuk knob kanan-tengah
 * → mode putar knob (lihat [KnobLayer]).
 */
@Composable
fun RoomScene(
    np: NowPlaying,
    retro: RetroSettings,
    tone: RoomTone,
    tvSize: Float,
    accent: Color,
    motionAllowed: Boolean,
    editing: Boolean,
    status: SceneStatus,
    positionMs: Long,
    titleScrimAlpha: Float,
    knobState: KnobState,
    volume: Float?,
    onVolume: (Float) -> Unit,
    onPrevious: () -> Unit,
    onRewind: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onTick: () -> Unit,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier,
    wallColor: Color? = null,
    wallGradient: Pair<Color, Color>? = null,
    wallPicture: ImageBitmap? = null,
    /** Posisi mendatar susunan −1 (kiri) .. 1 (kanan); lihat [SceneLayout.compute]. */
    tvPosition: Float = 0f,
    /** Tampilkan cover di layar TV (mati: warna aksen saja). */
    showCover: Boolean = true,
    /** Tampilkan judul & artis di layar TV. */
    showTitle: Boolean = true,
    /** TV "dimatikan" oleh timer tidur (animasi power-off, lalu siaga sampai musik diputar lagi). */
    tvOff: Boolean = false,
    /** Video milik pengguna untuk layar TV (hanya TV utama; pantulan di lantai tetap memakai cover). */
    screenVideo: (@Composable () -> Unit)? = null,
    /** Isi tambahan di layar TV (batang visualizer, lirik saat TV didekatkan); menerima status zoom TV. Lambda terakhir. */
    screenOverlay: @Composable (zoomed: Boolean) -> Unit = {},
) {
    val colors = SceneTheme.of(tone)
    val mode = TvScreen.mode(np.hasTrack, np.isPlaying, retro.albumMotion, motionAllowed)
    var tvZoomed by remember { mutableStateOf(false) }
    LaunchedEffect(editing, knobState.active) { if (editing || knobState.active) tvZoomed = false }
    val zoomSpec = tween<Float>(if (motionAllowed) Tokens.Motion.KNOB_ZOOM_IN_MS else 0, easing = Ease.standard)
    val knobZoom by animateFloatAsState(if (knobState.active) KNOB_ZOOM else 1f, zoomSpec, label = "knobZoom")
    val tvZoom by animateFloatAsState(if (tvZoomed) 1.16f else 1f, zoomSpec, label = "tvZoom")

    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val aspect = if (h.value > 0f) w.value / h.value else 1f
        val sc = SceneLayout.compute(aspect, tvSize, tvPosition)
        val tv = sc.tv
        val cas = sc.cassette
        val wPx = with(LocalDensity.current) { w.roundToPx() }
        val knobFx = tv.left + tv.width * TvGeometry.KNOB_CX
        val knobFy = tv.top + tv.height * TvGeometry.KNOB_CY
        val tvCx = tv.left + tv.width / 2f
        val tvCy = tv.top + tv.height / 2f

        // ---------- Ruangan
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            drawRoom(colors, sc, retro.glowIntensity, if (np.hasTrack) accent else Color(0xFFFFF1DC), wallColor, wallGradient)
        }
        if (wallPicture != null) {
            // Gambar pilihan pengguna diproyeksikan samar ke dinding belakang
            Image(
                wallPicture, null, contentScale = ContentScale.Crop,
                modifier = Modifier
                    .offset(w * (tvCx - tv.width * 0.75f), 0.dp)
                    .size(w * tv.width * 1.5f, h * (sc.floorY - 0.06f))
                    .graphicsLayer { alpha = 0.22f },
            )
        }

        // ---------- Kamera (TV, kaset, pantulan): zoom ke knob atau ke TV
        val camScale = if (knobState.active || knobZoom > 1.001f) knobZoom else tvZoom
        val camOrigin = if (knobState.active || knobZoom > 1.001f) TransformOrigin(knobFx, knobFy) else TransformOrigin(tvCx, tvCy)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = camScale
                    scaleY = camScale
                    transformOrigin = camOrigin
                },
        ) {
            // Pantulan TV di lantai mengilap (hanya ruang gelap)
            if (tone == RoomTone.DIM) {
                Reflection(Modifier.offset(w * tv.left, h * sc.floorY).size(w * tv.width, h * tv.height), alpha = 0.16f) {
                    TvFor(np, retro, mode, accent, status, motionAllowed, titleScrimAlpha, knobState, showCover, showTitle, tvOff)
                }
            }
            // TV
            Box(
                Modifier
                    .offset(w * tv.left, h * tv.top)
                    .size(w * tv.width, h * tv.height)
                    .semantics(mergeDescendants = true) {
                        contentDescription = if (mode == TvScreenMode.STANDBY) "TV, siaga. Tidak ada lagu"
                        else "TV menampilkan ${np.title}" + if (np.artist.isNotBlank()) " oleh ${np.artist}" else ""
                        stateDescription = if (!np.hasTrack) "Siaga" else if (np.isPlaying) "Diputar" else "Dijeda"
                        customActions = listOf(CustomAccessibilityAction("Buka lirik layar penuh") { onOpenLyrics(); true })
                    }
                    .clickable(
                        enabled = !editing,
                        onClickLabel = if (tvZoomed) "Jauhkan TV" else "Dekatkan TV",
                        role = Role.Button,
                    ) { tvZoomed = !tvZoomed },
            ) {
                TvFor(np, retro, mode, accent, status, motionAllowed, titleScrimAlpha, knobState, showCover, showTitle, tvOff, video = screenVideo) {
                    screenOverlay(tvZoomed)
                    if (tvZoomed) LyricsHint(onOpenLyrics)
                }
            }
            // Kaset tergeletak di lantai: kaset tegak dimiringkan ke belakang (perspektif dari referensi)
            val upright = w * cas.width / SceneLayout.CASSETTE_UPRIGHT_ASPECT
            val casLabel = remember(np.hasTrack, np.title, np.artist, np.art) {
                CassetteLabel(if (np.hasTrack) "${np.title}\u0000${np.artist}" else null, np.title, np.artist, np.art)
            }
            val progress = if (np.durationMs > 0) positionMs.toFloat() / np.durationMs else 0f
            val flat: @Composable (Modifier) -> Unit = { m ->
                Box(
                    m.graphicsLayer {
                        rotationX = 55f
                        cameraDistance = 14f * density
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    },
                ) {
                    Cassette(
                        label = casLabel, hasSession = np.hasSession, playing = np.isPlaying, progress = progress,
                        timeText = "", shell = retro.cassetteShell, speed = retro.reelSpeed, accent = accent,
                        motionAllowed = motionAllowed && !editing,
                        onPrevious = onPrevious, onPlayPause = onPlayPause, onNext = onNext,
                        modifier = Modifier.fillMaxSize(), showKeys = false,
                    )
                }
            }
            if (tone == RoomTone.DIM) {
                Reflection(Modifier.offset(w * cas.left, h * cas.bottom).size(w * cas.width, upright), alpha = 0.12f) {
                    flat(Modifier.fillMaxSize())
                }
            }
            flat(Modifier.offset(w * cas.left, h * cas.bottom - upright).size(w * cas.width, upright))
        }

        // Kontrol di luar kamera memudar saat kamera mendekat ke knob (agar tidak menumpuk di atas TV yang diperbesar)
        val chromeAlpha = (1f - (knobZoom - 1f) / (KNOB_ZOOM - 1f)).coerceIn(0f, 1f)
        if (chromeAlpha > 0f) Box(Modifier.fillMaxSize().graphicsLayer { alpha = chromeAlpha }) {
            // ---------- Tombol yang mengapit kaset
            val controls = listOf(
                Triple(Icons.Rounded.SkipPrevious, "Lagu sebelumnya", onPrevious),
                Triple(Icons.Rounded.Replay10, "Mundur 10 detik", onRewind),
                Triple(if (np.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (!np.hasSession) "Buka aplikasi musik" else if (np.isPlaying) "Jeda" else "Putar", onPlayPause),
                Triple(Icons.Rounded.SkipNext, "Lagu berikutnya", onNext),
            )
            controls.forEachIndexed { i, (icon, label, action) ->
                val enabled = !editing && (i == 2 || np.hasSession) && (i != 1 || np.canSeek)
                SceneButton(
                    icon, label, enabled,
                    tint = if (i == 2) colors.play else colors.icon,
                    modifier = Modifier.offset(w * sc.controlsX[i] - 24.dp, h * sc.controlsY - 24.dp),
                    onClick = action,
                )
            }

            // ---------- Progres
            ProgressLine(
                np, positionMs, colors,
                Modifier.offset(w * sc.progress.left, h * sc.progress.top - 24.dp).size(w * sc.progress.width, 48.dp),
                enabled = !editing, onSeek = onSeek,
            )
            // ---------- Volume (hanya volume media perangkat; dibaca & diatur lewat API standar)
            if (volume != null) {
                VolumeLine(
                    volume, colors,
                    Modifier.offset(w * sc.volume.left, h * sc.volume.top - 24.dp).size(w * sc.volume.width, 48.dp),
                    enabled = !editing, onVolume = onVolume,
                )
            }

            // ---------- Indikator sudut
            if (status.timeText != null) Corner("TIME", status.timeText, colors, Modifier.padding(start = 28.dp, top = 22.dp), end = false)
            Corner("Output", status.outputText, colors, Modifier.align(Alignment.TopEnd).padding(end = 28.dp, top = 22.dp), end = true)
        }

        // ---------- Knob seek (tidak ikut zoom kamera)
        KnobLayer(
            knobState, np, w * tv.left, h * tv.top, w * tv.width, h * tv.height,
            zoom = knobZoom, enabled = !editing, onSeek = onSeek, onTick = onTick,
            stageWidthPx = wPx, alwaysShowBubble = !tvZoomed,
        )
    }
}

@Composable
private fun TvFor(
    np: NowPlaying,
    retro: RetroSettings,
    mode: TvScreenMode,
    accent: Color,
    status: SceneStatus,
    motionAllowed: Boolean,
    titleScrimAlpha: Float,
    knobState: KnobState,
    showCover: Boolean,
    showTitle: Boolean,
    tvOff: Boolean,
    video: (@Composable () -> Unit)? = null,
    overlay: @Composable () -> Unit = {},
) {
    CrtTv(
        mode = mode, art = np.art.takeIf { showCover }, title = if (showTitle) np.title else "",
        artist = if (showTitle) np.artist else "", accent = accent,
        body = retro.tvBody, knob = retro.knobStyle, vfdColor = retro.vfdColor, vfdLines = status.vfdLines,
        crtStrength = retro.crtStrength, motion = retro.albumMotion, reduceMotion = !motionAllowed,
        titleScrimAlpha = titleScrimAlpha, knobTurns = knobState.turns, knobHighlight = knobState.active,
        modifier = Modifier.fillMaxSize(), screenOverlay = overlay, screenVideo = video, poweredOff = tvOff,
    )
}

/** Pantulan samar di lantai: isi dibalik vertikal lalu memudar menjauh dari lantai. */
@Composable
private fun Reflection(modifier: Modifier, alpha: Float, content: @Composable () -> Unit) {
    val layerAlpha = alpha
    Box(
        modifier
            .clearAndSetSemantics { }
            .graphicsLayer {
                scaleY = -1f
                this.alpha = layerAlpha
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                // Sebelum dibalik: bagian BAWAH isi (yang menyentuh lantai) paling jelas
                drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color.Black), blendMode = BlendMode.DstIn)
            },
    ) { content() }
}

/** Petunjuk kecil di layar saat TV didekatkan. */
@Composable
private fun LyricsHint(onOpenLyrics: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Text(
            "LIRIK ›",
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelLarge,
            letterSpacing = 0.12.em,
            modifier = Modifier
                .padding(bottom = 6.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = "Buka lirik layar penuh", role = Role.Button, onClick = onOpenLyrics)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun SceneButton(icon: ImageVector, label: String, enabled: Boolean, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun ProgressLine(np: NowPlaying, positionMs: Long, colors: SceneColors, modifier: Modifier, enabled: Boolean, onSeek: (Long) -> Unit) {
    val dur = np.durationMs
    val canSeek = enabled && np.canSeek && dur > 0
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val seek by rememberUpdatedState(onSeek)
    val frac = dragFrac ?: if (dur > 0) (positionMs.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val shown = if (dragFrac != null) (dragFrac!! * dur).toLong() else positionMs
    Box(
        modifier
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .semantics {
                contentDescription = "Posisi lagu"
                stateDescription = KnobSeek.bubble(positionMs, dur)
                if (dur > 0) progressBarRangeInfo = ProgressBarRangeInfo(positionMs.toFloat(), 0f..dur.toFloat())
                if (canSeek) setProgress { v -> seek(v.toLong().coerceIn(0L, dur)); true }
            }
            .pointerInput(canSeek, dur) {
                if (!canSeek) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragFrac = (o.x / widthPx).coerceIn(0f, 1f) },
                    onDragEnd = { dragFrac?.let { seek((it * dur).toLong()) }; dragFrac = null },
                    onDragCancel = { dragFrac = null },
                ) { ch, _ -> ch.consume(); dragFrac = (ch.position.x / widthPx).coerceIn(0f, 1f) }
            }
            .pointerInput(canSeek, dur) {
                if (!canSeek) return@pointerInput
                detectTapGestures { o -> seek(((o.x / widthPx).coerceIn(0f, 1f) * dur).toLong()) }
            },
    ) {
        Canvas(Modifier.fillMaxWidth().height(24.dp).align(Alignment.TopCenter).clearAndSetSemantics { }) {
            val y = size.height
            drawLine(colors.track.copy(alpha = 0.28f), Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
            if (dur > 0) {
                drawLine(colors.track.copy(alpha = 0.85f), Offset(0f, y), Offset(size.width * frac, y), strokeWidth = 2.dp.toPx())
                drawCircle(colors.track, 5.dp.toPx(), Offset(size.width * frac, y))
            }
        }
        if (dur > 0) {
            Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter).clearAndSetSemantics { }) {
                Text(KnobSeek.clock(shown), color = colors.label, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text(KnobSeek.clock(dur), color = colors.label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun VolumeLine(volume: Float, colors: SceneColors, modifier: Modifier, enabled: Boolean, onVolume: (Float) -> Unit) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    val set by rememberUpdatedState(onVolume)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Rounded.VolumeDown, null, tint = colors.icon, modifier = Modifier.size(18.dp))
        Box(
            Modifier
                .weight(1f)
                .height(48.dp)
                .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .semantics {
                    contentDescription = "Volume media"
                    progressBarRangeInfo = ProgressBarRangeInfo(volume, 0f..1f)
                    if (enabled) setProgress { v -> set(v.coerceIn(0f, 1f)); true }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures { ch, _ -> ch.consume(); set((ch.position.x / widthPx).coerceIn(0f, 1f)) }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { o -> set((o.x / widthPx).coerceIn(0f, 1f)) }
                },
        ) {
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
                val y = size.height / 2f
                drawLine(colors.track.copy(alpha = 0.28f), Offset(6.dp.toPx(), y), Offset(size.width - 6.dp.toPx(), y), strokeWidth = 2.dp.toPx())
                val x = 6.dp.toPx() + (size.width - 12.dp.toPx()) * volume.coerceIn(0f, 1f)
                drawLine(colors.track.copy(alpha = 0.85f), Offset(6.dp.toPx(), y), Offset(x, y), strokeWidth = 2.dp.toPx())
                drawCircle(colors.track, 6.dp.toPx(), Offset(x, y))
            }
        }
        Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = colors.icon, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Corner(label: String, value: String, colors: SceneColors, modifier: Modifier, end: Boolean) {
    Column(modifier.semantics(mergeDescendants = true) { }, horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
        Text(label, color = colors.label, style = MaterialTheme.typography.labelSmall, letterSpacing = 0.04.em)
        Text(
            value, color = colors.value, style = MaterialTheme.typography.bodyMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = if (end) TextAlign.End else TextAlign.Start,
            modifier = Modifier.padding(top = 2.dp).widthIn(max = 200.dp),
        )
    }
}

/** Dinding, sisi ruangan, lantai mengilap, dan cahaya layar TV. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoom(
    c: SceneColors,
    sc: SceneLayout.Scene,
    glowIntensity: Float,
    glow: Color,
    wallColor: Color?,
    wallGradient: Pair<Color, Color>?,
) {
    val w = size.width
    val h = size.height
    val tvCx = (sc.tv.left + sc.tv.width / 2f) * w
    val tvCy = (sc.tv.top + sc.tv.height / 2f) * h
    // Garis lantai di belakang TV (dinding bertemu lantai) — di referensi sedikit di atas dasar TV
    val backFloor = (sc.floorY - sc.tv.height * 0.16f) * h
    // Dinding belakang: paling terang di belakang TV, gelap ke tepi
    when {
        wallGradient != null -> drawRect(Brush.verticalGradient(listOf(wallGradient.first, wallGradient.second), endY = backFloor))
        wallColor != null -> drawRect(wallColor)
        else -> drawRect(Brush.radialGradient(listOf(c.wallCenter, c.wallEdge), center = Offset(tvCx, tvCy * 0.85f), radius = maxOf(w, h) * 0.62f))
    }
    // Sisi kiri/kanan ruangan: bidang gelap dengan sudut samar
    val side = w * 0.2f
    drawRect(Brush.horizontalGradient(listOf(c.sideWall.copy(alpha = 0.75f), Color.Transparent), endX = side), size = Size(side, h))
    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, c.sideWall.copy(alpha = 0.75f)), startX = w - side, endX = w), topLeft = Offset(w - side, 0f), size = Size(side, h))
    drawLine(c.cornerLine.copy(alpha = 0.18f), Offset(side * 1.05f, 0f), Offset(side * 1.05f, backFloor), strokeWidth = 1f)
    drawLine(c.cornerLine.copy(alpha = 0.18f), Offset(w - side * 1.05f, 0f), Offset(w - side * 1.05f, backFloor), strokeWidth = 1f)
    // Lantai mengilap
    drawRect(Brush.verticalGradient(listOf(c.floorFar, c.floorNear), startY = backFloor, endY = h), topLeft = Offset(0f, backFloor), size = Size(w, h - backFloor))
    drawLine(c.cornerLine.copy(alpha = 0.3f), Offset(0f, backFloor), Offset(w, backFloor), strokeWidth = 1f)
    // Kilap lantai di depan TV
    drawOval(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.05f), Color.Transparent), center = Offset(tvCx, sc.floorY * h), radius = sc.tv.width * w * 0.8f),
        topLeft = Offset(tvCx - sc.tv.width * w * 0.8f, backFloor), size = Size(sc.tv.width * w * 1.6f, (h - backFloor) * 0.9f),
    )
    // Cahaya layar: halo terang tepat di sekitar bodi TV (referensi) + cahaya lebar yang menerangi dinding
    val g = glowIntensity.coerceIn(0f, 1f)
    if (g > 0f) {
        val r = sc.tv.width * w * 0.95f
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFFF4E2).copy(alpha = 0.14f * g), glow.copy(alpha = 0.06f * g), Color.Transparent), center = Offset(tvCx, tvCy), radius = r),
            r, Offset(tvCx, tvCy), blendMode = BlendMode.Screen,
        )
        // Halo rapat: elips seukuran TV sedikit lebih besar, memudar cepat di luar bodi
        val hw = sc.tv.width * w * 0.62f
        val hh = sc.tv.height * h * 0.64f
        scale(hw / hh, 1f, Offset(tvCx, tvCy)) {
            drawCircle(
                Brush.radialGradient(
                    0f to Color(0xFFFFF6E6).copy(alpha = 0.34f * g), 0.72f to Color(0xFFFFF0DA).copy(alpha = 0.18f * g), 1f to Color.Transparent,
                    center = Offset(tvCx, tvCy), radius = hh,
                ),
                hh, Offset(tvCx, tvCy), blendMode = BlendMode.Screen,
            )
        }
    }
}
