package com.vinyl.launcher.ui.game

import kotlin.math.abs
import kotlin.random.Random

/*
 * Game "Ular" (Snake) untuk kaset game di laci. Murni (tanpa Android) agar semua aturannya diuji: papan tanpa dinding
 * (ular muncul di sisi seberang), mati hanya bila menabrak tubuhnya sendiri, makin cepat setiap makan, dan belokan yang
 * datang lebih cepat daripada satu langkah ditampung (maks. dua) supaya usapan cepat tidak hilang.
 */

/** Arah gerak ular di papan (y bertambah ke bawah). */
enum class Dir(val dx: Int, val dy: Int) {
    UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

    val opposite: Dir
        get() = when (this) {
            UP -> DOWN
            DOWN -> UP
            LEFT -> RIGHT
            RIGHT -> LEFT
        }
}

data class Cell(val x: Int, val y: Int)

/** Kejadian pada satu langkah, untuk bunyi dan getar. */
enum class SnakeEvent { NONE, ATE, DIED, WON }

/**
 * @param body sel tubuh, kepala paling depan
 * @param queue belokan yang menunggu giliran, paling banyak [Snake.MAX_QUEUE]
 * @param food null hanya bila papan penuh
 * @param best rekor sampai permainan ini berakhir (rekor permainan yang sedang berjalan = maks([best], [score]))
 * @param newBest permainan ini yang memecahkan rekor
 */
data class SnakeState(
    val cols: Int,
    val rows: Int,
    val body: List<Cell>,
    val dir: Dir,
    val queue: List<Dir>,
    val food: Cell?,
    val score: Int,
    val phase: GamePhase,
    val best: Int,
    val newBest: Boolean,
) {
    val head: Cell get() = body.first()

    /** Rekor yang ditampilkan: bertambah seketika saat skor melampauinya. */
    val shownBest: Int get() = maxOf(best, score)
}

data class SnakeStep(val state: SnakeState, val event: SnakeEvent)

fun SnakeState.hud(): GameHud = GameHud(phase, score, shownBest)

object Snake {
    const val COLS = 16
    const val ROWS = 13
    const val START_LENGTH = 3
    const val MAX_QUEUE = 2

    /** Jeda antar langkah: mulai lambat, makin cepat setiap makan, berhenti mempercepat di [FASTEST_MS]. */
    const val SLOWEST_MS = 230L
    const val FASTEST_MS = 90L
    const val SPEEDUP_MS = 7L

    fun tickMs(score: Int): Long = (SLOWEST_MS - SPEEDUP_MS * score.coerceAtLeast(0)).coerceAtLeast(FASTEST_MS)

    /** Permainan baru: ular sepanjang [START_LENGTH] di tengah papan menghadap kanan, menunggu gerakan pertama. */
    fun newGame(best: Int, random: Random = Random.Default, cols: Int = COLS, rows: Int = ROWS): SnakeState {
        val y = rows / 2
        val headX = cols / 2
        val body = List(START_LENGTH) { Cell(wrap(headX - it, cols), y) }
        return SnakeState(
            cols = cols, rows = rows, body = body, dir = Dir.RIGHT, queue = emptyList(),
            food = spawnFood(body, cols, rows, random), score = 0, phase = GamePhase.READY,
            best = best.coerceAtLeast(0), newBest = false,
        )
    }

    /**
     * Belok ke [d]. READY dan PAUSED langsung berjalan (gerakan pertama ikut dipakai bila bukan berbalik arah); saat
     * OVER/WON diabaikan (mulai ulang hanya lewat [tap]) agar usapan terakhir tidak memulai permainan baru tanpa sengaja.
     * Belokan sama dengan arah terakhir atau berbalik 180° diabaikan.
     */
    fun turn(s: SnakeState, d: Dir): SnakeState {
        if (s.phase == GamePhase.OVER || s.phase == GamePhase.WON) return s
        val last = s.queue.lastOrNull() ?: s.dir
        val accepted = d != last && d != last.opposite && s.queue.size < MAX_QUEUE
        val queue = if (accepted) s.queue + d else s.queue
        val phase = if (s.phase == GamePhase.READY || s.phase == GamePhase.PAUSED) GamePhase.RUNNING else s.phase
        return s.copy(queue = queue, phase = phase)
    }

    /** Ketuk: mulai, jeda, lanjut; setelah selesai, mulai permainan baru dengan rekor yang dibawa. */
    fun tap(s: SnakeState, random: Random = Random.Default): SnakeState = when (s.phase) {
        GamePhase.READY, GamePhase.PAUSED -> s.copy(phase = GamePhase.RUNNING)
        GamePhase.RUNNING -> s.copy(phase = GamePhase.PAUSED)
        GamePhase.OVER, GamePhase.WON -> newGame(s.best, random, s.cols, s.rows)
    }

    /** Jeda bila sedang berjalan (ruang TV ditinggalkan, aplikasi ke latar). */
    fun pause(s: SnakeState): SnakeState = if (s.phase == GamePhase.RUNNING) s.copy(phase = GamePhase.PAUSED) else s

    /** Satu langkah. Tidak berbuat apa-apa kecuali [GamePhase.RUNNING]. */
    fun step(s: SnakeState, random: Random = Random.Default): SnakeStep {
        if (s.phase != GamePhase.RUNNING) return SnakeStep(s, SnakeEvent.NONE)
        val d = s.queue.firstOrNull() ?: s.dir
        val queue = s.queue.drop(1)
        val head = s.body.first()
        val next = Cell(wrap(head.x + d.dx, s.cols), wrap(head.y + d.dy, s.rows))
        val ate = next == s.food
        // Ekor ikut bergeser kecuali ular bertambah panjang, jadi sel yang baru ditinggalkan ekor boleh dimasuki
        val grown = if (ate) s.body else s.body.dropLast(1)
        if (next in grown) {
            val score = s.score
            return SnakeStep(
                s.copy(dir = d, queue = queue, phase = GamePhase.OVER, best = maxOf(s.best, score), newBest = s.newBest || score > s.best),
                SnakeEvent.DIED,
            )
        }
        val body = listOf(next) + grown
        if (!ate) return SnakeStep(s.copy(body = body, dir = d, queue = queue), SnakeEvent.NONE)
        val score = s.score + 1
        val food = spawnFood(body, s.cols, s.rows, random)
        if (food == null) {
            return SnakeStep(
                s.copy(body = body, dir = d, queue = queue, food = null, score = score, phase = GamePhase.WON, best = maxOf(s.best, score), newBest = s.newBest || score > s.best),
                SnakeEvent.WON,
            )
        }
        return SnakeStep(s.copy(body = body, dir = d, queue = queue, food = food, score = score), SnakeEvent.ATE)
    }

    /** Sel kosong acak untuk makanan; null bila papan penuh. */
    fun spawnFood(body: List<Cell>, cols: Int, rows: Int, random: Random): Cell? {
        val taken = body.toHashSet()
        val free = ArrayList<Cell>(cols * rows - taken.size)
        for (y in 0 until rows) for (x in 0 until cols) {
            val c = Cell(x, y)
            if (c !in taken) free += c
        }
        return if (free.isEmpty()) null else free[random.nextInt(free.size)]
    }

    private fun wrap(v: Int, n: Int): Int = ((v % n) + n) % n
}

/**
 * Mengubah gerak jari menjadi belokan: setiap kali jarak tempuh (searah sumbu terpanjang) melewati [threshold], satu arah
 * dikeluarkan dan hitungan diulang, sehingga satu usapan panjang yang berbelok tetap menghasilkan beberapa belokan.
 */
class SwipeSteer(private val threshold: Float) {
    private var ax = 0f
    private var ay = 0f

    fun reset() {
        ax = 0f
        ay = 0f
    }

    /** Tambahkan pergeseran jari; hasilnya arah bila ambang terlewati, selain itu null. */
    fun drag(dx: Float, dy: Float): Dir? {
        ax += dx
        ay += dy
        val absX = abs(ax)
        val absY = abs(ay)
        if (maxOf(absX, absY) < threshold) return null
        val d = if (absX >= absY) (if (ax > 0f) Dir.RIGHT else Dir.LEFT) else (if (ay > 0f) Dir.DOWN else Dir.UP)
        reset()
        return d
    }
}
