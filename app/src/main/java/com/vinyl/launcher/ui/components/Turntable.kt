package com.vinyl.launcher.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinyl.launcher.data.TonearmStyle
import com.vinyl.launcher.data.VinylStyle
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.ui.theme.Tokens
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt

/** Rasio lebar : tinggi area turntable (sampul + vinyl yang keluar + tonearm). */
const val TURNTABLE_ASPECT = 1.72f

/** Satu putaran vinyl dengan jari = maju/mundur sekian milidetik. */
private const val SCRUB_MS_PER_REV = 6000f

private enum class Target { NONE, COVER, VINYL, ARM }

/** Gaya visual turntable (subset dari HomeSettings agar komponen mudah dipakai ulang). */
data class TurntableStyle(
    val vinylStyle: VinylStyle = VinylStyle.CLASSIC,
    val tonearmStyle: TonearmStyle = TonearmStyle.SILVER,
    val coverVisible: Boolean = true,
    val coverRounded: Boolean = true,
    val coverShadow: Boolean = true,
    val rpm: Int = 33,
)

/**
 * Turntable utama: sampul album di kiri, vinyl setengah keluar ke kanan, tonearm di kanan atas.
 *
 * Animasi:
 * - Play: vinyl meluncur keluar → tonearm turun → vinyl berakselerasi ke 33/45 RPM.
 * - Pause: tonearm naik → vinyl melambat & berhenti → masuk sebagian ke sampul.
 *
 * Interaksi (semua dalam satu detektor agar area yang bertumpuk tidak saling menghalangi):
 * - Ketuk sampul = play/pause (atau buka aplikasi musik jika tidak ada lagu)
 * - Geser sampul ke kiri/kanan = next/previous
 * - Seret tonearm = seek
 * - Putar vinyl dengan jari = maju/mundur
 */
@Composable
fun Turntable(
    nowPlaying: NowPlaying,
    style: TurntableStyle,
    labelColor: Color,
    discTint: Color,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    onTogglePlay: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onOpenMusic: () -> Unit = {},
    /** Dipanggil saat jarum mendarat di piringan (untuk suara & getar). */
    onNeedleDown: () -> Unit = {},
    /** Dipanggil saat lengan terangkat. */
    onNeedleUp: () -> Unit = {},
    /** Klik kecil saat vinyl diputar dengan jari atau tonearm melewati tepi piringan. */
    onTick: () -> Unit = {},
) {
    val tick by rememberUpdatedState(onTick)
    val np by rememberUpdatedState(nowPlaying)
    val togglePlay by rememberUpdatedState(onTogglePlay)
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    val seek by rememberUpdatedState(onSeek)
    val openMusic by rememberUpdatedState(onOpenMusic)
    val hasTrack = nowPlaying.hasTrack
    val playing = nowPlaying.isPlaying && hasTrack
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // ---------- Status animasi ----------
    var angle by remember { mutableFloatStateOf(0f) }          // derajat
    var velocity by remember { mutableFloatStateOf(0f) }       // derajat/detik
    var progress by remember { mutableFloatStateOf(nowPlaying.progress) }
    var scrubbing by remember { mutableStateOf(false) }
    var armDragTheta by remember { mutableStateOf<Float?>(null) }
    var previewMs by remember { mutableStateOf<Long?>(null) }   // posisi pratinjau saat seek
    val coverOffset = remember { Animatable(0f) }

    val degPerSec = (if (style.rpm == 45) 45f else 33.333f) * 6f

    val slide by animateFloatAsState(
        targetValue = if (playing) 0.62f else 0.42f,
        animationSpec = tween(800, delayMillis = if (playing) 0 else 650, easing = FastOutSlowInEasing),
        label = "slide",
    )
    // Ala Apple Music: sampul sedikit mengecil saat pause, kembali penuh saat play
    val coverScale by animateFloatAsState(
        targetValue = if (playing || !hasTrack) 1f else 0.955f,
        animationSpec = tween(Tokens.Motion.SLOW_MS, easing = FastOutSlowInEasing),
        label = "coverScale",
    )
    // Posisi lengan: 0 = istirahat, 1 = jarum di piringan. Memakai Animatable agar bisa
    // "dipatok" langsung ketika pengguna mengangkat / menaruh lengan dengan tangan.
    val engageAnim = remember { Animatable(if (playing) 1f else 0f) }
    LaunchedEffect(playing) {
        if (playing) kotlinx.coroutines.delay(450)
        engageAnim.animateTo(if (playing) 1f else 0f, tween(650, easing = FastOutSlowInEasing))
    }
    val engage = engageAnim.value
    /** Status saat lengan diseret: true = jarum di luar piringan (dilepas → jeda). */
    var armOffRecord by remember { mutableStateOf(false) }

    // Loop frame: kecepatan putar naik/turun halus; hanya berjalan saat perlu (hemat baterai)
    LaunchedEffect(playing, degPerSec) {
        var last = 0L
        while (isActive) {
            withFrameNanos { t ->
                val dt = if (last == 0L) 0f else (t - last) / 1_000_000_000f
                last = t
                val target = if (playing) degPerSec else 0f
                // Akselerasi ±0.8 dtk saat mulai, deselerasi ±1.5 dtk saat berhenti
                val accel = if (target > velocity) degPerSec / 0.8f else degPerSec / 1.5f
                velocity = when {
                    velocity < target -> min(target, velocity + accel * dt)
                    else -> maxOf(target, velocity - accel * dt)
                }
                if (!scrubbing) angle = (angle + velocity * dt) % 360f
                if (armDragTheta == null && previewMs == null) progress = np.progress
            }
            if (!playing && velocity <= 0f) break
        }
    }
    LaunchedEffect(nowPlaying) { if (armDragTheta == null) progress = nowPlaying.progress }

    // Suara/getar jarum: hanya saat status BERUBAH (bukan saat layar pertama kali tampil)
    val needleDown by rememberUpdatedState(onNeedleDown)
    val needleUp by rememberUpdatedState(onNeedleUp)
    var lastPlaying by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(playing) {
        val prev = lastPlaying
        lastPlaying = playing
        if (prev == null || prev == playing) return@LaunchedEffect
        if (playing) {
            kotlinx.coroutines.delay(1080) // vinyl meluncur (0 ms) + tonearm turun (450 + 650 ms)
            needleDown()
        } else {
            needleUp()
        }
    }

    BoxWithConstraints(
        modifier.semantics {
            contentDescription = if (hasTrack) "${nowPlaying.title} oleh ${nowPlaying.artist}" else "Tidak ada yang diputar"
            if (hasTrack) stateDescription = if (playing) "Sedang diputar" else "Dijeda"
            // Ketuk/geser/seret hanya gestur; pembaca layar mendapat aksi setara
            if (interactive && hasTrack) {
                onClick(label = if (playing) "Jeda" else "Putar") { togglePlay(); true }
                customActions = listOf(
                    CustomAccessibilityAction("Lagu berikutnya") { next(); true },
                    CustomAccessibilityAction("Lagu sebelumnya") { previous(); true },
                )
            }
        }
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val s = min(w / TURNTABLE_ASPECT, h)                 // sisi sampul (px)
        val discR = s * 0.47f
        val ox = (w - s * TURNTABLE_ASPECT) / 2f
        val oy = (h - s) / 2f
        val cy = oy + s / 2f
        val discCx = ox + s / 2f + slide * s
        val playCx = ox + s / 2f + 0.62f * s
        val geo = remember(s, ox, oy) {
            val pivot = Offset(playCx + discR * 1.08f, cy - discR * 0.86f)
            val c = Offset(playCx, cy)
            TonearmGeometry(pivot, (pivot - c).getDistance(), c, discR)
        }
        val coverRect = Rect(ox, oy, ox + s, oy + s)

        // Sudut lengan: istirahat ↔ posisi sesuai progress lagu
        val playTheta = geo.thetaForProgress(progress)
        val armTheta = armDragTheta ?: (TonearmGeometry.REST_THETA + (playTheta - TonearmGeometry.REST_THETA) * engage)
        val lift = if (armDragTheta != null) 0.6f else 1f - engage
        val armSlop = with(density) { 30.dp.toPx() }
        // Nilai yang berubah tiap frame dibaca lewat State agar detektor gestur tidak memakai nilai basi
        val discCxNow by rememberUpdatedState(discCx)
        val armThetaNow by rememberUpdatedState(armTheta)

        val gestureModifier = if (!interactive) Modifier else Modifier.pointerInput(s, ox, oy, hasTrack, style.coverVisible) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pos = down.position
                val discCenter = Offset(discCxNow, cy)
                val target = when {
                    hasTrack && geo.distanceToArm(pos, armThetaNow) < armSlop -> Target.ARM
                    style.coverVisible && coverRect.contains(pos) -> Target.COVER
                    (pos - discCenter).getDistance() < discR -> Target.VINYL
                    !style.coverVisible && coverRect.contains(pos) -> Target.VINYL
                    else -> Target.NONE
                }
                if (target == Target.NONE) return@awaitEachGesture
                down.consume()

                var moved = false
                var totalDx = 0f
                var lastAngle = atan2(pos.y - discCenter.y, pos.x - discCenter.x)
                var scrubDeg = 0f
                var lastScrubStep = 0
                val startMs = np.positionAt()
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if (!moved && (change.position - pos).getDistance() > viewConfiguration.touchSlop) moved = true
                    if (!moved) continue
                    change.consume()
                    when (target) {
                        Target.COVER -> {
                            totalDx += change.position.x - change.previousPosition.x
                            if (hasTrack) scope.launch { coverOffset.snapTo(totalDx * 0.6f) }
                        }
                        Target.ARM -> {
                            val t = geo.thetaToward(change.position)
                                .coerceIn(TonearmGeometry.REST_THETA - 0.12f, geo.thetaForProgress(1f))
                            armDragTheta = t
                            // Jarum di luar alur terluar = lengan "diangkat" dari piringan
                            val offRecord = geo.needleRadius(t) > geo.discRadius * (TonearmGeometry.OUTER + 0.03f)
                            if (offRecord != armOffRecord) tick()
                            armOffRecord = offRecord
                            if (!offRecord) {
                                val p = geo.progressForRadius(geo.needleRadius(t))
                                progress = p
                                previewMs = if (np.canSeek && np.durationMs > 0) (p * np.durationMs).toLong() else null
                            } else {
                                previewMs = null
                            }
                        }
                        Target.VINYL -> {
                            scrubbing = true
                            val a = atan2(change.position.y - discCenter.y, change.position.x - discCenter.x)
                            var d = Math.toDegrees((a - lastAngle).toDouble()).toFloat()
                            if (d > 180f) d -= 360f
                            if (d < -180f) d += 360f
                            lastAngle = a
                            scrubDeg += d
                            val step = (scrubDeg / 30f).toInt()
                            if (step != lastScrubStep) { lastScrubStep = step; tick() }
                            angle = (angle + d) % 360f
                            if (hasTrack && np.canSeek) {
                                val ms = (startMs + scrubDeg / 360f * SCRUB_MS_PER_REV).toLong()
                                previewMs = if (np.durationMs > 0) ms.coerceIn(0, np.durationMs) else ms.coerceAtLeast(0)
                                if (np.durationMs > 0) progress = previewMs!!.toFloat() / np.durationMs
                            }
                        }
                        Target.NONE -> Unit
                    }
                }

                // ---- Jari diangkat ----
                when (target) {
                    Target.COVER -> {
                        if (!moved) {
                            if (hasTrack) togglePlay() else openMusic()
                        } else if (hasTrack && abs(totalDx) > s * 0.22f) {
                            if (totalDx < 0) next() else previous()
                        }
                        scope.launch { coverOffset.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f)) }
                    }
                    Target.ARM -> {
                        val wasPlaying = np.isPlaying
                        if (armOffRecord) {
                            // Lengan diangkat dari piringan → jeda (lengan langsung diam di posisi istirahat)
                            scope.launch { engageAnim.snapTo(0f) }
                            if (wasPlaying) togglePlay()
                        } else if (moved) {
                            // Lengan ditaruh di piringan → lompat ke posisi itu & putar
                            previewMs?.let { seek(it) }
                            scope.launch { engageAnim.snapTo(1f) }
                            if (!wasPlaying) {
                                togglePlay()
                                // Jika aplikasi musik menolak memutar, kembalikan lengan ke istirahat
                                scope.launch {
                                    kotlinx.coroutines.delay(2500)
                                    if (!np.isPlaying) engageAnim.animateTo(0f, tween(Tokens.Motion.SLOW_MS))
                                }
                            }
                        }
                        armDragTheta = null
                        armOffRecord = false
                    }
                    Target.VINYL -> {
                        if (moved) previewMs?.let { seek(it) }
                        else if (!hasTrack) openMusic()
                        scrubbing = false
                    }
                    Target.NONE -> Unit
                }
                previewMs = null
            }
        }

        Box(Modifier.fillMaxSize().then(gestureModifier)) {
            // ---------- Vinyl (di belakang sampul) ----------
            val discSizeDp = with(density) { (discR * 2).toDp() }
            VinylDisc(
                modifier = Modifier
                    .offset { IntOffset((discCx - discR).roundToInt(), (cy - discR).roundToInt()) }
                    .size(discSizeDp),
                rotation = angle,
                style = style.vinylStyle,
                labelColor = labelColor,
                discTint = discTint,
                label = VinylLabel.Text(
                    title = if (hasTrack) nowPlaying.title else "Tidak ada yang diputar",
                    subtitle = if (hasTrack) nowPlaying.artist else "Ketuk untuk membuka musik",
                ),
                art = nowPlaying.art,
            )

            // ---------- Sampul album ----------
            if (style.coverVisible) {
                val coverDp = with(density) { s.toDp() }
                AlbumCover(
                    art = if (hasTrack) nowPlaying.art else null,
                    rounded = style.coverRounded,
                    shadow = style.coverShadow,
                    placeholderText = if (hasTrack) nowPlaying.album.ifBlank { nowPlaying.title } else "Tidak ada yang diputar",
                    modifier = Modifier
                        .offset { IntOffset(ox.roundToInt(), oy.roundToInt()) }
                        .size(coverDp)
                        .graphicsLayer {
                            translationX = coverOffset.value
                            rotationZ = coverOffset.value / s * 5f
                            scaleX = coverScale
                            scaleY = coverScale
                        },
                )
            }

            // ---------- Tonearm ----------
            Canvas(Modifier.fillMaxSize()) {
                drawTonearm(geo, armTheta, lift, style.tonearmStyle)
            }

            // ---------- Indikator waktu saat seek ----------
            if (armDragTheta != null && (armOffRecord || previewMs == null)) {
                NeedleHint(
                    text = if (armOffRecord) (if (nowPlaying.isPlaying) "Lepas untuk menjeda" else "Taruh di piringan untuk memutar")
                    else "Lepas untuk memutar",
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
            previewMs?.let { ms ->
                NeedleHint(
                    text = "${formatMs(ms)}  /  ${formatMs(nowPlaying.durationMs)}",
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

/**
 * Petunjuk saat tonearm diseret: kapsul gelap pekat dengan teks putih tebal di bagian atas
 * area turntable, supaya terbaca jelas di atas sampul atau latar apa pun.
 */
@Composable
private fun NeedleHint(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Text(
        text = text,
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .padding(top = 4.dp)
            .clip(shape)
            .background(Color(0xF2000000))
            .border(1.dp, Color.White.copy(alpha = 0.4f), shape)
            .padding(horizontal = 20.dp, vertical = 11.dp),
    )
}

/** Sampul album persegi: sudut membulat halus, bayangan dua lapis (lembut + tajam) ala Apple Music. */
@Composable
fun AlbumCover(
    art: ImageBitmap?,
    rounded: Boolean,
    shadow: Boolean,
    placeholderText: String,
    modifier: Modifier = Modifier,
) {
    val shape = if (rounded) RoundedCornerShape(percent = 4) else RectangleShape
    Box(
        modifier
            .then(
                if (shadow) Modifier
                    .shadow(40.dp, shape, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.7f))
                    .shadow(6.dp, shape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.4f))
                else Modifier
            )
            .clip(shape)
            .background(Color(0xFF1E1E22))
    ) {
        Crossfade(targetState = art, animationSpec = tween(700), label = "cover") { img ->
            if (img != null) {
                Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                NeutralSleeve(placeholderText)
            }
        }
        // Garis tepi sangat tipis agar sampul gelap tetap terpisah dari latar gelap
        Box(Modifier.fillMaxSize().border(0.6.dp, Color.White.copy(alpha = 0.10f), shape))
    }
}

/** Sampul netral saat tidak ada musik: lengan kertas gelap dengan cincin vinyl tercetak. */
@Composable
private fun NeutralSleeve(text: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF2A2A2F), Color(0xFF17171A)))),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * 0.30f
            for (i in 0..5) drawCircle(Color.White.copy(alpha = 0.035f), r * (0.45f + i * 0.11f), style = Stroke(1.2f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.30f), modifier = Modifier.size(44.dp))
            Text(
                text,
                color = Color.White.copy(alpha = 0.55f),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
