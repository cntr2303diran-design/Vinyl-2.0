package com.vinyl.launcher.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinyl.launcher.ui.theme.RetroColors
import com.vinyl.launcher.ui.theme.Tokens
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max
import kotlin.math.sin

/*
 * Layar game Bata di dalam TV tabung. Lapangan 16 × 13 satuan dengan pita skor di atasnya (tata letak yang sama dengan
 * Ular). Kendali: geser jari di layar TV menggeser palet (relatif, palet tidak melompat ke bawah jari); ketuk =
 * luncurkan / jeda / main lagi. Tombol kiri-kanan di deck dan semua aksi untuk pembaca layar ada di tingkat panggung.
 *
 * Bingkai demi bingkai, hanya gambar yang dibuat ulang: papan dibaca di fase gambar, sedangkan teks dan panel hanya
 * disusun ulang saat isinya (skor, nyawa, tingkat, fase) berubah.
 */

/** Geser jari sejauh 1 satuan lapangan menggeser palet sejauh ini (sedikit lebih jauh agar ibu jari tidak perlu menempuh seluruh TV). */
private const val PADDLE_GAIN = 1.25f

/** Jarak "ekor komet" di belakang bola, dalam detik gerak. */
private const val TRAIL_STEP_S = 0.016f

/** Isi layar TV untuk kaset Bata: papan dibaca dari [flow] di sini, sehingga hanya layar ini yang terpengaruh tiap bingkai. */
@Composable
internal fun BreakoutTvScreen(
    flow: StateFlow<BreakoutState?>,
    enabled: Boolean,
    motionAllowed: Boolean,
    onMove: (Float) -> Unit,
    onTap: () -> Unit,
) {
    val state = flow.collectAsStateWithLifecycle()
    BreakoutBoard(state, enabled, motionAllowed, onMove, onTap)
}

/** Yang dibaca teks dan panel; berubah jauh lebih jarang daripada papan. */
private data class BreakoutView(
    val score: Int,
    val best: Int,
    val lives: Int,
    val level: Int,
    val phase: GamePhase,
    val newBest: Boolean,
    val untouched: Boolean,
)

private enum class PanelSpot { CENTER, LOW }

private class BreakoutPanel(val text: GamePanelText, val spot: PanelSpot)

private fun panelFor(v: BreakoutView): BreakoutPanel? = when (v.phase) {
    GamePhase.RUNNING, GamePhase.WON -> null
    // Pembuka: cara bermain, di bagian bawah supaya dinding bata tetap terlihat
    GamePhase.READY ->
        if (v.untouched) BreakoutPanel(GamePanelText("BATA", listOf("GESER: GERAK PALET", "KETUK: LUNCURKAN")), PanelSpot.LOW)
        else BreakoutPanel(GamePanelText("TINGKAT ${v.level}", listOf("NYAWA ${v.lives}", "KETUK: LUNCURKAN")), PanelSpot.LOW)
    GamePhase.PAUSED -> BreakoutPanel(GamePanelText("JEDA", listOf("KETUK UNTUK LANJUT")), PanelSpot.CENTER)
    GamePhase.OVER -> BreakoutPanel(
        GamePanelText("GAME OVER", listOf("SKOR ${v.score}", "TINGKAT ${v.level}", "KETUK: MAIN LAGI"), if (v.newBest && v.score > 0) "REKOR BARU!" else null),
        PanelSpot.CENTER,
    )
}

/**
 * Papan permainan. [state] dibaca di sini secara lambat (di fase gambar dan lewat [derivedStateOf]). [enabled] false =
 * tidak menerima sentuhan (mode edit). [motionAllowed] false = tanpa ekor bola dan getar saat nyawa hilang.
 */
@Composable
internal fun BreakoutBoard(
    state: State<BreakoutState?>,
    enabled: Boolean,
    motionAllowed: Boolean,
    onMove: (Float) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val move by rememberUpdatedState(onMove)
    val tap by rememberUpdatedState(onTap)
    val view by remember(state) {
        derivedStateOf {
            state.value?.let { BreakoutView(it.score, it.shownBest, it.lives, it.level, it.phase, it.newBest, it.untouched) }
        }
    }

    // Papan bergetar sebentar saat satu nyawa hilang atau permainan berakhir
    val shake = remember { Animatable(0f) }
    val lives = view?.lives ?: 0
    val previousLives = remember { mutableIntStateOf(lives) }
    LaunchedEffect(lives) {
        if (motionAllowed && lives < previousLives.intValue) {
            shake.snapTo(1f)
            shake.animateTo(0f, tween(320, easing = LinearEasing))
        }
        previousLives.intValue = lives
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(Tokens.Game.BOARD_TOP), Color(Tokens.Game.BOARD_BOTTOM))))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    // Layar ini memakai seluruh gerakan jari: ketuk-lama / geser halaman beranda tidak ikut terpicu
                    val down = awaitFirstDown()
                    down.consume()
                    val slop = viewConfiguration.touchSlop
                    val cell = GameLayout.compute(size.width.toFloat(), size.height.toFloat(), Breakout.FIELD_COLS, Breakout.FIELD_ROWS).cell
                    var travelled = 0f
                    var pending = 0f
                    var dragging = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val delta = change.positionChange()
                        if (delta != Offset.Zero) {
                            travelled += delta.getDistance()
                            pending += delta.x
                            // Goyangan kecil saat mengetuk tidak menggeser palet; begitu jari benar-benar menggeser, jarak yang
                            // tertahan ikut dipakai sehingga tidak ada gerak yang hilang
                            if (!dragging && travelled > slop) dragging = true
                            if (dragging && pending != 0f && cell > 0f) {
                                move(pending / cell * PADDLE_GAIN)
                                pending = 0f
                            }
                        }
                        change.consume()
                        if (!change.pressed) {
                            if (!dragging) tap()
                            break
                        }
                    }
                }
            },
    ) {
        val v = view ?: return@BoxWithConstraints
        val fontScale = LocalDensity.current.fontScale
        val w = maxWidth.value
        val h = maxHeight.value
        val hud = h * GameLayout.HUD_FRACTION

        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            val s = state.value ?: return@Canvas
            drawField(s, shake.value, trail = motionAllowed)
        }

        GameHudBar(w, hud, fontScale, "SKOR ${v.score}", "REKOR ${v.best}")
        panelFor(v)?.let { p ->
            val spot = when (p.spot) {
                PanelSpot.CENTER -> Modifier.align(Alignment.Center)
                // Di atas palet (tepi atasnya di ±92% tinggi TV), di bawah dinding bata
                PanelSpot.LOW -> Modifier.align(Alignment.BottomCenter).padding(bottom = (h * 0.14f).dp)
            }
            GamePanel(p.text, w, fontScale, spot, compact = p.spot == PanelSpot.LOW && !v.untouched)
        }
    }
}

/** Menggambar lapangan: bingkai, bata, palet, bola dengan ekornya, dan nyawa di pita skor. */
private fun DrawScope.drawField(s: BreakoutState, shake: Float, trail: Boolean) {
    val lay = GameLayout.compute(size.width, size.height, Breakout.FIELD_COLS, Breakout.FIELD_ROWS)
    val c = lay.cell
    if (c <= 0f) return
    val over = s.phase == GamePhase.OVER
    val ox = lay.left + sin(shake * 28f) * shake * c * 0.45f
    val oy = lay.top

    drawRect(
        Color(Tokens.Game.SNAKE_TAIL).copy(alpha = 0.28f), Offset(ox, oy), Size(lay.boardWidth, lay.boardHeight),
        style = Stroke(width = max(1f, c * 0.05f)),
    )

    // Bata, baris atas lebih mahal; sorot tipis di tepi atas supaya terbaca sebagai bata, bukan hanya warna
    val inset = c * 0.05f
    val corner = CornerRadius(c * 0.12f)
    val rowColors = Tokens.Game.BRICK_ROWS
    for (row in 0 until Breakout.MAX_ROWS) {
        var color = Color(rowColors[row.coerceAtMost(rowColors.lastIndex)])
        if (over) color = color.copy(alpha = 0.45f)
        val top = oy + (Breakout.WALL_TOP + row * Breakout.BRICK_H) * c + inset
        val height = Breakout.BRICK_H * c - 2f * inset
        for (col in 0 until Breakout.COLS) {
            if (s.bricks and Breakout.bit(row, col) == 0L) continue
            val left = ox + col * Breakout.BRICK_W * c + inset
            val width = Breakout.BRICK_W * c - 2f * inset
            drawRoundRect(color, Offset(left, top), Size(width, height), corner)
            drawRect(Color.White.copy(alpha = if (over) 0.08f else 0.22f), Offset(left + corner.x, top + height * 0.12f), Size(width - 2f * corner.x, height * 0.16f))
        }
    }

    // Palet
    val paddle = Color(Tokens.Game.PADDLE)
    val pw = Breakout.PADDLE_W * c
    val ph = Breakout.PADDLE_H * c
    val px = ox + (s.paddleX - Breakout.PADDLE_W / 2f) * c
    val py = oy + Breakout.PADDLE_TOP * c
    drawRoundRect(
        Brush.verticalGradient(listOf(paddle, paddle.copy(alpha = 0.72f)), startY = py, endY = py + ph),
        Offset(px, py), Size(pw, ph), CornerRadius(ph * 0.5f),
    )

    // Bola: ekor komet di belakang arah geraknya selama berjalan, cahaya lembut, lalu bola putih
    val ball = Color(Tokens.Game.BALL)
    val bc = Offset(ox + s.ballX * c, oy + s.ballY * c)
    val br = Breakout.BALL_R * c
    if (trail && s.phase == GamePhase.RUNNING) {
        for (k in 1..3) {
            val back = Offset(s.ballVx, s.ballVy) * (k * TRAIL_STEP_S * c)
            drawCircle(ball.copy(alpha = 0.30f / k), br * (1f - 0.18f * k), bc - back)
        }
    }
    drawCircle(Brush.radialGradient(listOf(ball.copy(alpha = 0.35f), Color.Transparent), center = bc, radius = br * 2.4f), br * 2.4f, bc)
    drawCircle(ball, br, bc)

    // Nyawa: bola kecil di tengah pita skor; yang hilang tinggal cincin (terbaca tanpa bergantung pada warna)
    val ly = lay.hudHeight / 2f
    val lr = lay.hudHeight * 0.14f
    val gap = lr * 3.2f
    for (i in 0 until Breakout.START_LIVES) {
        val center = Offset(size.width / 2f + (i - (Breakout.START_LIVES - 1) / 2f) * gap, ly)
        if (i < s.lives) drawCircle(RetroColors.Amber, lr, center)
        else drawCircle(RetroColors.Amber.copy(alpha = 0.55f), lr, center, style = Stroke(width = max(1f, lr * 0.3f)))
    }
}
