package com.vinyl.launcher.ui.game

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Game "Bata" (Breakout) untuk kaset game di laci. Murni (tanpa Android) agar semua aturannya diuji.
 *
 * Lapangan 16 × 13 satuan dengan sumbu y ke bawah, sama dengan papan Ular, sehingga tata letak layar TV dipakai bersama.
 * Bola memantul di dinding kiri, kanan, dan langit-langit; paling bawah ada palet yang digeser pemain; di atas ada
 * dinding bata (maks. 8 × 7, disimpan sebagai satu bilangan Long: satu bit per bata). Bola yang lolos di bawah palet
 * menghabiskan satu nyawa. Dinding yang habis dipecahkan membuka tingkat berikutnya (susunan lain, bola lebih cepat);
 * permainan tidak pernah "menang", hanya berakhir saat nyawa habis.
 *
 * Fisika berjalan dengan langkah tetap kecil (maks. 1/120 detik): hasilnya tidak bergantung pada laju bingkai layar, dan
 * bola yang paling cepat pun bergerak jauh lebih pendek dari tebal satu bata per langkah, jadi tidak pernah menembusnya.
 * Tidak memakai bilangan acak: arah luncur bergantian kiri/kanan, susunan dinding ditentukan tingkatnya.
 */

/** Kejadian pada satu bingkai, untuk bunyi dan getar. */
enum class BreakoutEvent { WALL, PADDLE, BRICK, LOST_BALL, LEVEL_UP, GAME_OVER }

/**
 * @param paddleX pusat palet
 * @param ballX pusat bola; saat [GamePhase.READY] bola duduk di atas palet dan ikut bergeser
 * @param bricks satu bit per bata: bit `baris * COLS + kolom`, menyala = bata masih ada
 * @param lives nyawa tersisa, termasuk bola yang sedang main
 * @param hits bata yang sudah pecah di tingkat ini; makin banyak makin cepat bolanya
 * @param serveRight luncuran berikutnya miring ke kanan (bergantian tiap luncuran)
 * @param best rekor sampai permainan ini berakhir (rekor permainan yang sedang berjalan = maks([best], [score]))
 * @param newBest permainan ini yang memecahkan rekor
 */
data class BreakoutState(
    val paddleX: Float,
    val ballX: Float,
    val ballY: Float,
    val ballVx: Float,
    val ballVy: Float,
    val bricks: Long,
    val level: Int,
    val lives: Int,
    val score: Int,
    val phase: GamePhase,
    val best: Int,
    val newBest: Boolean,
    val hits: Int,
    val serveRight: Boolean,
) {
    /** Rekor yang ditampilkan: bertambah seketika saat skor melampauinya. */
    val shownBest: Int get() = maxOf(best, score)

    val brickCount: Int get() = bricks.countOneBits()

    /** Permainan baru yang belum disentuh: papan pembuka dengan penjelasan kendali. */
    val untouched: Boolean get() = score == 0 && level == 1 && lives == Breakout.START_LIVES
}

/** Hasil satu bingkai: keadaan baru dan kejadian di dalamnya (urut waktu). */
data class BreakoutStep(val state: BreakoutState, val events: List<BreakoutEvent>)

fun BreakoutState.hud(): GameHud = GameHud(phase, score, shownBest, lives = lives, level = level)

object Breakout {
    /** Lapangan dalam satuan; sama dengan papan Ular (petak), sehingga [GameLayout] dipakai bersama. */
    const val FIELD_COLS = 16
    const val FIELD_ROWS = 13
    const val FIELD_W = 16f
    const val FIELD_H = 13f
    const val COLS = 8
    const val MAX_ROWS = 7
    const val BRICK_W = FIELD_W / COLS
    const val BRICK_H = 0.7f

    /** Tepi atas dinding bata. */
    const val WALL_TOP = 1.3f

    const val PADDLE_W = 3f
    const val PADDLE_H = 0.45f

    /** Tepi atas palet; bola yang bawahnya melewati garis ini dari atas, di atas palet, memantul. */
    const val PADDLE_TOP = 11.8f
    const val BALL_R = 0.3f
    const val START_LIVES = 3

    /** Geser palet untuk satu ketukan tombol deck. */
    const val NUDGE = 2f

    /** Palet lebih lebar sedikit dari badannya saat menangkap bola di tepi (pemain tidak dihukum oleh jari yang besar). */
    private const val CATCH_SLACK = BALL_R * 0.6f

    /** Sudut pantul terjauh dari tegak lurus, di ujung palet. */
    const val MAX_ANGLE = 1.05f

    /** Sudut luncur dari tegak lurus. */
    private const val SERVE_ANGLE = 0.38f

    /** Pecahan kecepatan yang harus tegak lurus: bola tidak boleh melaju nyaris mendatar tanpa akhir. */
    const val MIN_VERTICAL = 0.30f

    const val BASE_SPEED = 8.5f
    const val LEVEL_SPEED = 0.7f
    const val HIT_SPEED = 0.05f
    const val MAX_SPEED = 14.5f

    /** Langkah fisika terbesar (detik) dan bingkai terpanjang yang masih disimulasikan utuh (setelahnya waktu dibuang). */
    const val SUBSTEP_S = 1f / 120f
    const val MAX_FRAME_S = 0.05f

    /** Nilai tambahan untuk memecahkan seluruh dinding. */
    const val LEVEL_BONUS = 10

    private const val EPS = 1e-4f

    /** Kecepatan bola (satuan per detik) untuk tingkat [level] setelah [hits] bata pecah di tingkat itu. */
    fun speed(level: Int, hits: Int): Float =
        min(MAX_SPEED, BASE_SPEED + LEVEL_SPEED * (level.coerceAtLeast(1) - 1) + HIT_SPEED * hits.coerceAtLeast(0))

    /** Nilai satu bata di [row]: baris atas paling mahal (5, 4, 3, 2), selebihnya 1. */
    fun rowPoints(row: Int): Int = max(1, 5 - row)

    fun bit(row: Int, col: Int): Long = 1L shl (row * COLS + col)

    /** Jumlah baris dinding pada [level]: 5, 6, lalu 7. */
    fun rowsFor(level: Int): Int = (4 + level.coerceAtLeast(1)).coerceAtMost(MAX_ROWS)

    /**
     * Susunan dinding pada [level]; empat bentuk yang berputar: penuh, lorong di tengah, papan catur, corong. Tingkat
     * berikutnya memakai bentuk yang sama dengan bola yang lebih cepat.
     */
    fun wall(level: Int): Long {
        val l = level.coerceAtLeast(1)
        val rows = rowsFor(l)
        var mask = 0L
        for (r in 0 until rows) for (c in 0 until COLS) {
            val present = when ((l - 1) % 4) {
                0 -> true
                1 -> c != COLS / 2 - 1 && c != COLS / 2
                2 -> (r + c) % 2 == 0
                else -> c >= r / 2 && c <= COLS - 1 - r / 2
            }
            if (present) mask = mask or bit(r, c)
        }
        return mask
    }

    /** Permainan baru: palet di tengah, bola di atasnya, tiga nyawa, dinding tingkat 1, menunggu luncuran. */
    fun newGame(best: Int): BreakoutState = onPaddle(
        paddleX = FIELD_W / 2f, bricks = wall(1), level = 1, lives = START_LIVES, score = 0, phase = GamePhase.READY,
        best = best.coerceAtLeast(0), newBest = false, hits = 0, serveRight = true,
    )

    /**
     * Geser palet sejauh [dx] satuan (negatif = kiri), tertahan di dinding. Saat siap, bola ikut. Saat dijeda atau
     * berakhir palet diam (tidak bisa menata ulang saat permainan berhenti).
     */
    fun movePaddle(s: BreakoutState, dx: Float): BreakoutState {
        if (s.phase != GamePhase.READY && s.phase != GamePhase.RUNNING) return s
        val half = PADDLE_W / 2f
        val x = (s.paddleX + dx).coerceIn(half, FIELD_W - half)
        if (x == s.paddleX) return s
        return if (s.phase == GamePhase.READY) s.copy(paddleX = x, ballX = x) else s.copy(paddleX = x)
    }

    /** Satu ketukan tombol deck: geser palet [NUDGE] ke kiri ([direction] < 0) atau kanan. */
    fun nudge(s: BreakoutState, direction: Int): BreakoutState =
        if (direction == 0) s else movePaddle(s, if (direction < 0) -NUDGE else NUDGE)

    /** Ketuk: luncurkan bola, jeda, lanjut; setelah berakhir, permainan baru dengan rekor yang dibawa. */
    fun tap(s: BreakoutState): BreakoutState = when (s.phase) {
        GamePhase.READY -> launch(s)
        GamePhase.PAUSED -> s.copy(phase = GamePhase.RUNNING)
        GamePhase.RUNNING -> s.copy(phase = GamePhase.PAUSED)
        GamePhase.OVER, GamePhase.WON -> newGame(s.best)
    }

    /** Jeda bila sedang berjalan (ruang TV ditinggalkan, aplikasi ke latar). */
    fun pause(s: BreakoutState): BreakoutState = if (s.phase == GamePhase.RUNNING) s.copy(phase = GamePhase.PAUSED) else s

    /**
     * Majukan permainan [dtSec] detik (dipotong ke [MAX_FRAME_S]). Tidak berbuat apa-apa kecuali [GamePhase.RUNNING];
     * berhenti lebih awal bila bola hilang atau dinding habis.
     */
    fun advance(s: BreakoutState, dtSec: Float): BreakoutStep {
        if (s.phase != GamePhase.RUNNING) return BreakoutStep(s, emptyList())
        val events = ArrayList<BreakoutEvent>(0)
        var cur = s
        var left = if (dtSec.isNaN()) 0f else dtSec.coerceIn(0f, MAX_FRAME_S)
        while (left > 1e-6f && cur.phase == GamePhase.RUNNING) {
            val h = min(left, SUBSTEP_S)
            cur = step(cur, h, events)
            left -= h
        }
        return BreakoutStep(cur, events)
    }

    private fun launch(s: BreakoutState): BreakoutState {
        val sp = speed(s.level, s.hits)
        val side = if (s.serveRight) 1f else -1f
        return s.copy(
            phase = GamePhase.RUNNING,
            ballVx = side * sp * sin(SERVE_ANGLE),
            ballVy = -sp * cos(SERVE_ANGLE),
            serveRight = !s.serveRight,
        )
    }

    /** Bola duduk di atas palet, diam, menunggu diluncurkan. */
    private fun onPaddle(
        paddleX: Float, bricks: Long, level: Int, lives: Int, score: Int, phase: GamePhase, best: Int, newBest: Boolean,
        hits: Int, serveRight: Boolean,
    ) = BreakoutState(
        paddleX = paddleX, ballX = paddleX, ballY = PADDLE_TOP - BALL_R, ballVx = 0f, ballVy = 0f, bricks = bricks,
        level = level, lives = lives, score = score, phase = phase, best = best, newBest = newBest, hits = hits,
        serveRight = serveRight,
    )

    private fun step(s: BreakoutState, h: Float, events: MutableList<BreakoutEvent>): BreakoutState {
        val r = BALL_R
        var x = s.ballX + s.ballVx * h
        var y = s.ballY + s.ballVy * h
        var vx = s.ballVx
        var vy = s.ballVy
        var bricks = s.bricks
        var score = s.score
        var hits = s.hits
        var bounced = false

        // Dinding dan langit-langit
        if (x - r < 0f) {
            x = r; vx = abs(vx); bounced = true; events += BreakoutEvent.WALL
        } else if (x + r > FIELD_W) {
            x = FIELD_W - r; vx = -abs(vx); bounced = true; events += BreakoutEvent.WALL
        }
        if (y - r < 0f) {
            y = r; vy = abs(vy); bounced = true; events += BreakoutEvent.WALL
        }

        // Palet: hanya bola yang turun dan baru melewati garis atas palet pada langkah ini
        val reach = PADDLE_W / 2f + CATCH_SLACK
        if (vy > 0f && s.ballY + r <= PADDLE_TOP + EPS && y + r >= PADDLE_TOP && abs(x - s.paddleX) <= reach) {
            val offset = ((x - s.paddleX) / reach).coerceIn(-1f, 1f)
            val angle = offset * MAX_ANGLE
            val sp = speed(s.level, hits)
            vx = sp * sin(angle)
            vy = -sp * cos(angle)
            y = PADDLE_TOP - r
            bounced = true
            events += BreakoutEvent.PADDLE
        }

        // Bata: hanya yang paling dekat dengan pusat bola yang pecah pada satu langkah (tidak memantul dua kali)
        val c0 = floor((x - r) / BRICK_W).toInt().coerceAtLeast(0)
        val c1 = floor((x + r) / BRICK_W).toInt().coerceAtMost(COLS - 1)
        val r0 = floor((y - r - WALL_TOP) / BRICK_H).toInt().coerceAtLeast(0)
        val r1 = floor((y + r - WALL_TOP) / BRICK_H).toInt().coerceAtMost(MAX_ROWS - 1)
        var nearest = Float.MAX_VALUE
        var hitRow = -1
        var hitCol = -1
        var hitDx = 0f
        var hitDy = 0f
        for (row in r0..r1) for (col in c0..c1) {
            if (bricks and bit(row, col) == 0L) continue
            val left = col * BRICK_W
            val top = WALL_TOP + row * BRICK_H
            val dx = x - x.coerceIn(left, left + BRICK_W)
            val dy = y - y.coerceIn(top, top + BRICK_H)
            val d2 = dx * dx + dy * dy
            if (d2 <= r * r && d2 < nearest) {
                nearest = d2; hitRow = row; hitCol = col; hitDx = dx; hitDy = dy
            }
        }
        if (hitRow >= 0) {
            bricks = bricks and bit(hitRow, hitCol).inv()
            score += rowPoints(hitRow)
            hits += 1
            when {
                // Pusat bola di dalam bata (setelah loncatan besar): anggap kena dari bawah/atas
                nearest == 0f -> vy = -vy
                abs(hitDx) > abs(hitDy) -> {
                    vx = if (hitDx > 0f) abs(vx) else -abs(vx)
                    x += (if (hitDx > 0f) r else -r) - hitDx
                }
                else -> {
                    vy = if (hitDy > 0f) abs(vy) else -abs(vy)
                    y += (if (hitDy > 0f) r else -r) - hitDy
                }
            }
            bounced = true
            events += BreakoutEvent.BRICK
        }

        // Kecepatan mengikuti tingkat dan jumlah bata pecah; bola tidak boleh nyaris mendatar
        if (bounced) {
            val sp = speed(s.level, hits)
            var len = sqrt(vx * vx + vy * vy)
            if (len < EPS) {
                vx = 0f; vy = -sp; len = sp
            }
            vx *= sp / len
            vy *= sp / len
            val minVy = sp * MIN_VERTICAL
            if (abs(vy) < minVy) {
                vy = if (vy < 0f) -minVy else minVy
                vx = (if (vx < 0f) -1f else 1f) * sqrt(max(0f, sp * sp - vy * vy))
            }
        }

        // Dinding habis → tingkat berikutnya
        if (bricks == 0L) {
            events += BreakoutEvent.LEVEL_UP
            val level = s.level + 1
            return onPaddle(
                paddleX = s.paddleX, bricks = wall(level), level = level, lives = s.lives, score = score + LEVEL_BONUS,
                phase = GamePhase.READY, best = s.best, newBest = s.newBest, hits = 0, serveRight = s.serveRight,
            )
        }

        // Bola lolos di bawah palet
        if (y - r > FIELD_H) {
            val lives = s.lives - 1
            if (lives <= 0) {
                events += BreakoutEvent.GAME_OVER
                return onPaddle(
                    paddleX = s.paddleX, bricks = bricks, level = s.level, lives = 0, score = score, phase = GamePhase.OVER,
                    best = max(s.best, score), newBest = s.newBest || score > s.best, hits = hits, serveRight = s.serveRight,
                )
            }
            events += BreakoutEvent.LOST_BALL
            return onPaddle(
                paddleX = s.paddleX, bricks = bricks, level = s.level, lives = lives, score = score, phase = GamePhase.READY,
                best = s.best, newBest = s.newBest, hits = hits, serveRight = s.serveRight,
            )
        }

        return s.copy(ballX = x, ballY = y, ballVx = vx, ballVy = vy, bricks = bricks, score = score, hits = hits)
    }
}
