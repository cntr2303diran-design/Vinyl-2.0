package com.vinyl.launcher.ui.room

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.ui.cassette.KnobSeek
import com.vinyl.launcher.ui.theme.MonoFamily
import com.vinyl.launcher.ui.theme.RetroColors
import com.vinyl.launcher.ui.theme.Tokens
import kotlin.math.roundToInt

/** Keadaan knob TV: mode putar aktif, putaran fisik knob (untuk jarumnya), dan posisi pratinjau. */
@Stable
class KnobState {
    var active by mutableStateOf(false)
    /** Putaran knob yang terakumulasi; knob tetap di sudut terakhirnya seperti knob sungguhan. */
    var turns by mutableFloatStateOf(0f)
    var previewMs by mutableStateOf<Long?>(null)
    var touching by mutableStateOf(false)

    fun exit() { active = false; previewMs = null; touching = false }
}

/** Zoom kamera saat mode knob aktif (knob tetap di tempat; ruang membesar di sekitarnya). */
const val KNOB_ZOOM = 2.0f

/**
 * Lapisan interaksi knob, digambar di atas panggung (tidak ikut di-zoom). Ketuk knob → mode putar (kamera mendekat).
 * Putar jari melingkar di sekitar knob: searah jarum jam maju, berlawanan mundur; gelembung waktu tampil. Lepas jari →
 * posisi dikirim ke aplikasi musik (bila sesi mendukung seek). Ketuk area kosong atau tombol Kembali → keluar.
 *
 * @param tvLeft..tvHeight kotak TV dalam panggung (dp), dari [RoomLayout]
 */
@Composable
fun KnobLayer(
    state: KnobState,
    np: NowPlaying,
    tvLeft: Dp,
    tvTop: Dp,
    tvWidth: Dp,
    tvHeight: Dp,
    zoom: Float,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    onTick: () -> Unit,
    /** Lebar panggung (px) untuk menaruh gelembung di sisi yang muat. */
    stageWidthPx: Int = Int.MAX_VALUE,
    /** Tampilkan gelembung waktu juga di luar mode putar (referensi menampilkannya terus di samping knob). */
    alwaysShowBubble: Boolean = false,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(np)
    val seek by rememberUpdatedState(onSeek)
    val tick by rememberUpdatedState(onTick)

    val cx = tvLeft + tvWidth * TvGeometry.KNOB_CX
    val cy = tvTop + tvHeight * TvGeometry.KNOB_CY
    val knobR = tvWidth * TvGeometry.KNOB_R
    // Area sentuh minimal 48dp walau knob tergambar kecil
    val target = maxOf(48.dp, knobR * 2.6f)

    BackHandler(enabled = state.active) { state.exit() }

    // Mode putar: seluruh panggung menangkap gerakan melingkar
    if (state.active) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(cx, cy) {
                    val c = with(density) { Offset(cx.toPx(), cy.toPx()) }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val np0 = current
                        val startMs = np0.positionAt()
                        var prevAngle = KnobSeek.angle(down.position.x, down.position.y, c.x, c.y)
                        var turns = 0.0
                        var moved = false
                        var lastDetent = 0L
                        state.touching = true
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            if (!moved && (ch.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                            if (!moved) continue
                            ch.consume()
                            val a = KnobSeek.angle(ch.position.x, ch.position.y, c.x, c.y)
                            val d = KnobSeek.deltaTurns(prevAngle, a)
                            prevAngle = a
                            turns += d
                            state.turns += d.toFloat()
                            if (np0.canSeek) {
                                state.previewMs = KnobSeek.target(startMs, turns, np0.durationMs)
                                val det = KnobSeek.detent(turns)
                                if (det != lastDetent) {
                                    lastDetent = det
                                    if (KnobSeek.atEdge(startMs, turns, np0.durationMs)) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    } else {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        tick()
                                    }
                                }
                            }
                        }
                        state.touching = false
                        if (!moved) {
                            state.exit()                      // ketuk area kosong = keluar
                        } else if (np0.canSeek) {
                            state.previewMs?.let { seek(it) }
                        }
                    }
                },
        )
    }

    // Titik sentuh knob (juga titik masuk TalkBack)
    val pos = np.positionAt()
    val canSeek = np.canSeek && np.durationMs > 0
    Box(
        Modifier
            .offset(cx - target / 2, cy - target / 2)
            .size(target)
            .clip(CircleShape)
            .semantics {
                contentDescription = "Knob posisi lagu"
                stateDescription = if (canSeek) KnobSeek.bubble(pos, np.durationMs) else "Tidak bisa digeser"
                if (canSeek) {
                    progressBarRangeInfo = ProgressBarRangeInfo(pos.toFloat(), 0f..np.durationMs.toFloat())
                    setProgress { v -> seek(v.toLong().coerceIn(0L, np.durationMs)); true }
                    customActions = listOf(
                        CustomAccessibilityAction("Maju 10 detik") { seek(KnobSeek.target(pos, KnobSeek.A11Y_STEP_MS.toDouble() / KnobSeek.MS_PER_TURN, np.durationMs)); true },
                        CustomAccessibilityAction("Mundur 10 detik") { seek(KnobSeek.target(pos, -KnobSeek.A11Y_STEP_MS.toDouble() / KnobSeek.MS_PER_TURN, np.durationMs)); true },
                    )
                }
            }
            .clickable(
                enabled = enabled && np.hasTrack && !state.active,
                onClickLabel = "Putar knob untuk menggeser posisi lagu",
                role = Role.Button,
            ) { state.active = true },
    )

    // Gelembung waktu di samping kanan knob (seperti referensi); pindah ke kiri bila tidak muat
    var bubbleSize by remember { mutableStateOf(IntSize.Zero) }
    AnimatedVisibility(
        visible = state.active || (alwaysShowBubble && np.hasTrack && np.durationMs > 0),
        enter = fadeIn(tween(Tokens.Motion.FAST_MS)),
        exit = fadeOut(tween(Tokens.Motion.FAST_MS)),
        modifier = Modifier.offset {
            val right = (cx + knobR * zoom + 14.dp).roundToPx()
            val left = (cx - knobR * zoom - 14.dp).roundToPx() - bubbleSize.width
            val x = if (right + bubbleSize.width <= stageWidthPx) right else left
            IntOffset(x.coerceAtLeast(0), (cy.roundToPx() - bubbleSize.height / 2).coerceAtLeast(0))
        },
    ) {
        val text = when {
            !np.canSeek -> "Aplikasi musik ini tidak mengizinkan geser posisi"
            else -> KnobSeek.bubble(state.previewMs ?: pos, np.durationMs)
        }
        Text(
            text,
            color = Color(0xFF1C1D1F),
            fontSize = if (np.canSeek) 14.sp else 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .onSizeChanged { bubbleSize = it }
                .widthIn(max = 260.dp)
                .background(Color(0xFFE9E9E6), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
