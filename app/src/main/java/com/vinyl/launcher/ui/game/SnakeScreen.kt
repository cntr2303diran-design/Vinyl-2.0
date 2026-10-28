package com.vinyl.launcher.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinyl.launcher.ui.theme.RetroColors
import com.vinyl.launcher.ui.theme.Tokens
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/*
 * Layar game Ular di dalam TV tabung. Papan 16 × 13 petak persegi dengan pita skor di atasnya; tidak ada dinding (ular
 * muncul di sisi seberang). Kendali: usap di layar TV (satu usapan panjang yang berbelok memberi beberapa belokan),
 * ketuk = mulai / jeda / main lagi. Tombol panah di deck dan semua aksi untuk pembaca layar ada di tingkat panggung.
 */

private const val TWO_PI = 6.2831855f

/** Isi layar TV untuk kaset game: papan dibaca dari [flow] di sini, sehingga hanya layar ini yang disusun ulang tiap langkah. */
@Composable
internal fun SnakeTvScreen(
    flow: StateFlow<SnakeState?>,
    enabled: Boolean,
    motionAllowed: Boolean,
    onTurn: (Dir) -> Unit,
    onTap: () -> Unit,
) {
    val state by flow.collectAsStateWithLifecycle()
    state?.let { SnakeBoard(it, enabled, motionAllowed, onTurn, onTap) }
}

private fun panelFor(s: SnakeState): GamePanelText? = when (s.phase) {
    GamePhase.RUNNING -> null
    GamePhase.READY -> GamePanelText("ULAR", listOf("USAP UNTUK BELOK", "KETUK UNTUK MULAI"))
    GamePhase.PAUSED -> GamePanelText("JEDA", listOf("KETUK UNTUK LANJUT"))
    GamePhase.OVER -> GamePanelText("GAME OVER", listOf("SKOR ${s.score}", "KETUK: MAIN LAGI"), if (s.newBest && s.score > 0) "REKOR BARU!" else null)
    GamePhase.WON -> GamePanelText("MENANG!", listOf("PAPAN PENUH", "SKOR ${s.score}", "KETUK: MAIN LAGI"), if (s.newBest) "REKOR BARU!" else null)
}

/**
 * Papan permainan. [enabled] false = tidak menerima sentuhan (mode edit). [motionAllowed] false = tanpa denyut makanan,
 * cincin saat makan, dan getar saat kalah (animasi dimatikan / hemat daya).
 */
@Composable
internal fun SnakeBoard(
    state: SnakeState,
    enabled: Boolean,
    motionAllowed: Boolean,
    onTurn: (Dir) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val turn by rememberUpdatedState(onTurn)
    val tap by rememberUpdatedState(onTap)

    // Efek kecil: cincin saat makan, papan bergetar saat kalah. Dibaca di fase gambar, tanpa menyusun ulang.
    val pop = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(state.score) {
        if (motionAllowed && state.score > 0) {
            pop.snapTo(1f)
            pop.animateTo(0f, tween(260, easing = LinearEasing))
        }
    }
    val over = state.phase == GamePhase.OVER
    LaunchedEffect(over) {
        if (motionAllowed && over) {
            shake.snapTo(1f)
            shake.animateTo(0f, tween(320, easing = LinearEasing))
        }
    }
    // Makanan berdenyut hanya selama permainan berjalan
    val pulse: State<Float>? = if (motionAllowed && state.phase == GamePhase.RUNNING) {
        rememberInfiniteTransition(label = "food")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "pulse")
    } else null

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(Tokens.Game.BOARD_TOP), Color(Tokens.Game.BOARD_BOTTOM))))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val steer = SwipeSteer(max(viewConfiguration.touchSlop, 16.dp.toPx()))
                awaitEachGesture {
                    // Layar ini memakai seluruh gerakan jari: ketuk-lama / geser halaman beranda tidak ikut terpicu
                    val down = awaitFirstDown()
                    down.consume()
                    steer.reset()
                    val slop = viewConfiguration.touchSlop
                    var travelled = 0f
                    var turned = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val moved = change.positionChange()
                        if (moved != Offset.Zero) {
                            travelled += moved.getDistance()
                            steer.drag(moved.x, moved.y)?.let { direction ->
                                turned = true
                                turn(direction)
                            }
                        }
                        change.consume()
                        if (!change.pressed) {
                            if (!turned && travelled <= slop * 2f) tap()
                            break
                        }
                    }
                }
            },
    ) {
        val fontScale = LocalDensity.current.fontScale
        val w = maxWidth.value
        val hud = maxHeight.value * GameLayout.HUD_FRACTION

        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            drawBoard(state, pulse?.value ?: 0f, pop.value, shake.value)
        }

        // Pita skor dan panel pesan: huruf bagian dari gambar TV, ukurannya mengikuti TV (lihat GameChrome)
        GameHudBar(w, hud, fontScale, "SKOR ${state.score}", "REKOR ${state.shownBest}")
        panelFor(state)?.let { panel -> GamePanel(panel, w, fontScale, Modifier.align(Alignment.Center)) }
    }
}

/** Menggambar papan: bingkai tipis, titik petak, makanan, ular (ekor dulu, kepala di atas), mata, dan cincin makan. */
private fun DrawScope.drawBoard(s: SnakeState, pulse: Float, pop: Float, shake: Float) {
    val lay = GameLayout.compute(size.width, size.height, s.cols, s.rows)
    val c = lay.cell
    if (c <= 0f) return
    val dead = s.phase == GamePhase.OVER
    val ox = lay.left + sin(shake * 28f) * shake * c * 0.45f
    val oy = lay.top
    fun center(cell: Cell) = Offset(ox + (cell.x + 0.5f) * c, oy + (cell.y + 0.5f) * c)

    // Bingkai tipis; tepinya menyambung ke sisi seberang (tidak ada dinding)
    drawRect(
        Color(Tokens.Game.SNAKE_TAIL).copy(alpha = 0.28f), Offset(ox, oy), Size(lay.boardWidth, lay.boardHeight),
        style = Stroke(width = max(1f, c * 0.05f)),
    )
    val dot = Color(Tokens.Game.GRID_DOT)
    for (y in 0 until s.rows) for (x in 0 until s.cols) {
        drawCircle(dot, c * 0.05f, Offset(ox + (x + 0.5f) * c, oy + (y + 0.5f) * c))
    }

    // Makanan: bola amber berdenyut dengan tepi gelap (bentuknya beda dari ular, tidak bergantung pada warna)
    s.food?.let { food ->
        val p = center(food)
        val r = c * (0.30f + 0.035f * sin(pulse * TWO_PI))
        val amber = RetroColors.Amber
        drawCircle(Brush.radialGradient(listOf(amber.copy(alpha = 0.40f), Color.Transparent), center = p, radius = c * 0.9f), c * 0.9f, p)
        drawCircle(amber, r, p)
        drawCircle(Color.Black.copy(alpha = 0.55f), r, p, style = Stroke(width = c * 0.06f))
        drawCircle(Color.White.copy(alpha = 0.55f), r * 0.28f, p - Offset(r * 0.32f, r * 0.34f))
    }

    // Ular: dari ekor ke kepala; ruas yang bersebelahan disambung (lintas tepi papan tidak disambung)
    val n = s.body.size
    val inset = c * 0.09f
    val side = c - 2f * inset
    val corner = CornerRadius(c * 0.28f)
    for (i in n - 1 downTo 0) {
        val cell = s.body[i]
        val t = if (n > 1) i / (n - 1f) else 0f
        var color = lerp(Color(Tokens.Game.SNAKE_HEAD), Color(Tokens.Game.SNAKE_TAIL), t)
        if (dead) color = lerp(color, Color(Tokens.Game.SNAKE_DEAD), 0.7f)
        drawRoundRect(color, Offset(ox + cell.x * c + inset, oy + cell.y * c + inset), Size(side, side), corner)
        if (i + 1 < n) {
            val next = s.body[i + 1]
            val dx = next.x - cell.x
            val dy = next.y - cell.y
            if (abs(dx) + abs(dy) == 1) {
                if (dy == 0) {
                    val x0 = minOf(cell.x, next.x)
                    drawRect(color, Offset(ox + (x0 + 1) * c - inset, oy + cell.y * c + inset), Size(2f * inset, side))
                } else {
                    val y0 = minOf(cell.y, next.y)
                    drawRect(color, Offset(ox + cell.x * c + inset, oy + (y0 + 1) * c - inset), Size(side, 2f * inset))
                }
            }
        }
    }

    // Mata di kepala, menghadap arah jalan; silang saat kalah
    val head = center(s.head)
    val forward = Offset(s.dir.dx.toFloat(), s.dir.dy.toFloat())
    val perp = Offset(-forward.y, forward.x)
    val eyeR = c * 0.11f
    for (sign in listOf(-1f, 1f)) {
        val e = head + forward * (c * 0.14f) + perp * (sign * c * 0.19f)
        if (dead) {
            val k = eyeR * 0.9f
            drawLine(Color.Black, e - Offset(k, k), e + Offset(k, k), strokeWidth = c * 0.06f)
            drawLine(Color.Black, e - Offset(k, -k), e + Offset(k, -k), strokeWidth = c * 0.06f)
        } else {
            drawCircle(Color.White, eyeR, e)
            drawCircle(Color(Tokens.Game.BOARD_TOP), eyeR * 0.5f, e + forward * (eyeR * 0.35f))
        }
    }

    // Cincin yang mengembang di kepala saat makan
    if (pop > 0f) {
        drawCircle(RetroColors.Amber.copy(alpha = pop * 0.85f), c * (0.55f + 1.3f * (1f - pop)), head, style = Stroke(width = c * 0.09f))
    }
}
