package com.vinyl.launcher.ui.room.stage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.system.StatusText
import com.vinyl.launcher.ui.cassette.KnobSeek
import com.vinyl.launcher.ui.theme.OsdFamily
import com.vinyl.launcher.ui.theme.Tokens
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Keadaan knob SEEK: putaran jarum, pratinjau posisi, dan apakah sedang disentuh. */
@Stable
class SeekState {
    var touching by mutableStateOf(false)
    /** Putaran yang terakumulasi; knob tetap di sudut terakhirnya seperti knob sungguhan. */
    var turns by mutableFloatStateOf(0f)
    /** Posisi pratinjau selama/sesaat setelah knob diputar; null = tidak ditampilkan. */
    var previewMs by mutableStateOf<Long?>(null)
    var startMs by mutableLongStateOf(0L)

    /** Arah pratinjau terhadap posisi awal (untuk "▶▶ FF" / "◀◀ REW" di OSD). */
    val deltaMs: Long? get() = previewMs?.let { it - startMs }
}

/**
 * Knob SEEK di panel TV. TV tetap diam (tidak ada zoom): putar jari melingkar di sekitar knob — searah jarum jam maju,
 * berlawanan mundur — dan posisi tampil besar di layar TV ([SeekOsd]). Jari terlalu dekat ke pusat knob → gerakan
 * naik/turun yang dipakai (knob kecil di ponsel). Lepas → posisi dikirim ke aplikasi musik.
 *
 * @param center pusat knob di layar (dp), [radius] jari-jari gambar knob (dp)
 */
@Composable
fun SeekKnob(
    state: SeekState,
    np: NowPlaying,
    center: Offset,
    radius: Float,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    onTick: () -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(np)
    val seek by rememberUpdatedState(onSeek)
    val tick by rememberUpdatedState(onTick)
    val target: Dp = maxOf(48f, radius * 2.8f).dp
    val canSeek = np.canSeek && np.durationMs > 0

    // Pratinjau tetap tampil sebentar setelah jari dilepas, lalu hilang
    LaunchedEffect(state.touching, state.previewMs) {
        if (!state.touching && state.previewMs != null) {
            delay(Tokens.Motion.SEEK_OSD_HOLD_MS.toLong())
            state.previewMs = null
        }
    }

    val pos = np.positionAt()
    Box(
        Modifier
            .offset((center.x.dp - target / 2), (center.y.dp - target / 2))
            .size(target)
            .clip(CircleShape)
            .semantics {
                contentDescription = "Knob posisi lagu"
                stateDescription = if (canSeek) KnobSeek.bubble(pos, np.durationMs) else "Tidak bisa digeser"
                if (canSeek && enabled) {
                    progressBarRangeInfo = ProgressBarRangeInfo(pos.toFloat(), 0f..np.durationMs.toFloat())
                    setProgress { v -> seek(v.toLong().coerceIn(0L, np.durationMs)); true }
                    customActions = listOf(
                        CustomAccessibilityAction("Maju 10 detik") { seek((pos + KnobSeek.A11Y_STEP_MS).coerceAtMost(np.durationMs)); true },
                        CustomAccessibilityAction("Mundur 10 detik") { seek((pos - KnobSeek.A11Y_STEP_MS).coerceAtLeast(0L)); true },
                    )
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val c = Offset(size.width / 2f, size.height / 2f)
                val rPx = with(density) { radius.dp.toPx() }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val np0 = current
                    state.startMs = np0.positionAt()
                    state.touching = true
                    var prev = down.position
                    var prevAngle = KnobSeek.angle(prev.x, prev.y, c.x, c.y)
                    var turns = 0.0
                    var moved = false
                    var lastDetent = 0L
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        if (!moved && (ch.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                        if (!moved) continue
                        ch.consume()
                        val p = ch.position
                        val d = if ((p - c).getDistance() < rPx * 0.7f) {
                            // Terlalu dekat ke pusat: geser naik = maju (seperti knob di aplikasi audio)
                            ((prev.y - p.y) / (rPx * 6f)).toDouble()
                        } else {
                            KnobSeek.deltaTurns(prevAngle, KnobSeek.angle(p.x, p.y, c.x, c.y))
                        }
                        prevAngle = KnobSeek.angle(p.x, p.y, c.x, c.y)
                        prev = p
                        turns += d
                        state.turns += d.toFloat()
                        if (np0.canSeek && np0.durationMs > 0) {
                            state.previewMs = KnobSeek.target(state.startMs, turns, np0.durationMs)
                            val det = KnobSeek.detent(turns)
                            if (det != lastDetent) {
                                lastDetent = det
                                if (KnobSeek.atEdge(state.startMs, turns, np0.durationMs)) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                } else {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    tick()
                                }
                            }
                        }
                    }
                    state.touching = false
                    if (moved && np0.canSeek && np0.durationMs > 0) {
                        state.previewMs?.let { if (abs(it - state.startMs) >= 500) seek(it) }
                    } else if (!moved) {
                        // Ketuk: tampilkan posisi sekarang sebentar di layar TV
                        state.previewMs = np0.positionAt()
                    }
                }
            },
    )
}

/**
 * Tampilan maju/mundur di layar TV saat knob diputar: arah pita, waktu besar, garis posisi, dan gangguan tracking —
 * seperti VCR yang sedang mencari. Teks putih di atas kotak hitam (selalu terbaca).
 */
@Composable
fun SeekOsd(state: SeekState, durationMs: Long, tvWidth: Dp) {
    val preview = state.previewMs ?: return
    val delta = state.deltaMs ?: 0L
    val t = when {
        delta > 0 -> StatusText.Transport.FORWARD
        delta < 0 -> StatusText.Transport.REWIND
        else -> StatusText.Transport.PLAY
    }
    // Mengikuti ukuran TV; skala huruf sistem ikut sampai 1,3× agar kotak tetap di dalam layar TV
    val fs = LocalDensity.current.fontScale
    val big = (tvWidth.value * 0.07f).coerceIn(14f, 60f) * fs.coerceAtMost(1.3f) / fs
    Box(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        // Garis gangguan pita selama mencari
        if (delta != 0L) Canvas(Modifier.fillMaxSize()) {
            val step = size.height / 7f
            for (i in 0 until 6) {
                val y = step * (i + 1) + (state.turns * 37f + i * 13f) % step
                drawRect(Color.White.copy(alpha = 0.10f), topLeft = Offset(0f, y), size = Size(size.width, 2.5f))
            }
        }
        Column(
            Modifier
                .align(Alignment.Center)
                .width(IntrinsicSize.Max)
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = (big * 0.5f).dp, vertical = (big * 0.2f).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((big * 0.15f).dp),
        ) {
            Text(StatusText.modeLine(t), color = Color.White, fontFamily = OsdFamily, fontSize = (big * 0.7f).sp)
            Text(
                KnobSeek.clock(preview) + if (durationMs > 0) " / " + KnobSeek.clock(durationMs) else "",
                color = Color.White, fontFamily = OsdFamily, fontSize = big.sp,
            )
            if (durationMs > 0) {
                val frac = (preview.toFloat() / durationMs).coerceIn(0f, 1f)
                Canvas(Modifier.fillMaxWidth().height((big * 0.22f).dp)) {
                    drawRect(Color.White.copy(alpha = 0.3f))
                    drawRect(Color.White, size = Size(size.width * frac, size.height))
                }
            }
        }
    }
}
