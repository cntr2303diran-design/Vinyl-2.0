package com.vinyl.launcher.ui.cassette

import java.util.Locale
import com.vinyl.launcher.data.ReelSpeed
import com.vinyl.launcher.ui.theme.Tokens
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/*
 * Model murni kaset & knob (tanpa Compose) sehingga perilakunya bisa diuji:
 *  - KnobSeek  : putaran knob TV → posisi lagu (menggantikan scrub piringan & tonearm)
 *  - Reels     : jari-jari pita di dua gulungan mengikuti progres lagu, kecepatan putar gulungan
 *  - CassetteSwap : animasi ganti kaset yang tidak pernah menunda musik dan menggabung skip beruntun
 */

object KnobSeek {
    /** Satu putaran penuh knob = 30 detik lagu. Cukup halus untuk presisi, cukup cepat untuk lagu 4–5 menit. */
    const val MS_PER_TURN = 30_000L
    /** Takik (detent) per putaran: tiap takik terasa sebagai getar halus = 1,25 detik. */
    const val DETENTS_PER_TURN = 24
    /** Langkah tombol aksesibilitas maju/mundur. */
    const val A11Y_STEP_MS = 10_000L

    /** Sudut (radian) titik [x],[y] terhadap pusat [cx],[cy]; 0 = kanan, searah jarum jam positif (sumbu y ke bawah). */
    fun angle(x: Float, y: Float, cx: Float, cy: Float): Double = atan2((y - cy).toDouble(), (x - cx).toDouble())

    /**
     * Selisih sudut terkecil dari [from] ke [to] dalam putaran (−0,5..0,5); positif = searah jarum jam. Melewati
     * batas ±π tidak membuat lompatan.
     */
    fun deltaTurns(from: Double, to: Double): Double {
        var d = to - from
        while (d > PI) d -= 2 * PI
        while (d < -PI) d += 2 * PI
        return d / (2 * PI)
    }

    /** Posisi baru setelah knob diputar [turns] putaran dari [startMs], dibatasi 0..[durationMs]. */
    fun target(startMs: Long, turns: Double, durationMs: Long): Long {
        val raw = startMs + (turns * MS_PER_TURN).toLong()
        return if (durationMs > 0) raw.coerceIn(0L, durationMs) else raw.coerceAtLeast(0L)
    }

    /** Nomor takik untuk [turns] (dipakai untuk getar: getar saat nomor berubah). */
    fun detent(turns: Double): Long = kotlin.math.floor(turns * DETENTS_PER_TURN).toLong()

    /** True bila posisi tertahan di ujung lagu (respons visual/getar "mentok"). */
    fun atEdge(startMs: Long, turns: Double, durationMs: Long): Boolean {
        val raw = startMs + (turns * MS_PER_TURN).toLong()
        return raw <= 0L || (durationMs > 0 && raw >= durationMs)
    }

    /** Teks gelembung waktu, mis. "02:34 / 04:18". Durasi tak diketahui → hanya posisi. */
    fun bubble(posMs: Long, durationMs: Long): String =
        if (durationMs > 0) "${clock(posMs)} / ${clock(durationMs)}" else clock(posMs)

    fun clock(ms: Long): String {
        val s = (ms.coerceAtLeast(0L) / 1000L)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, sec) else "%02d:%02d".format(Locale.ROOT, m, sec)
    }
}

object Reels {
    /** Jari-jari inti gulungan (hub) dan jari-jari pita penuh, dalam pecahan jari-jari maksimum yang tersedia. */
    const val HUB = 0.42f
    const val FULL = 1.0f

    /**
     * Jari-jari pita di gulungan kiri (pemasok) dan kanan (penggulung) pada [progress] 0..1. Luas pita kekal: pita yang
     * pindah dari kiri ke kanan menjaga jumlah luas cincin, jadi jari-jari berubah menurut akar, bukan linier.
     */
    fun radii(progress: Float): Pair<Float, Float> {
        val p = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)
        val hub2 = HUB * HUB
        val area = FULL * FULL - hub2
        val left = sqrt(hub2 + (1f - p) * area)
        val right = sqrt(hub2 + p * area)
        return left to right
    }

    /** Putaran per menit gulungan pada pengaturan [speed]. */
    fun rpm(speed: ReelSpeed): Float = if (speed == ReelSpeed.FAST) Tokens.Motion.REEL_RPM_FAST else Tokens.Motion.REEL_RPM_NORMAL

    /**
     * Kecepatan sudut (putaran/detik) gulungan berjari-jari [radius] agar kecepatan pita tetap: gulungan kecil berputar
     * lebih cepat. Dinormalkan supaya gulungan setengah penuh berputar tepat [rpm].
     */
    fun turnsPerSecond(radius: Float, rpm: Float): Float {
        val ref = sqrt((HUB * HUB + FULL * FULL) / 2f)
        val r = radius.coerceAtLeast(HUB)
        return rpm / 60f * ref / r
    }
}

/**
 * Animasi ganti kaset. Aturan:
 *  1. Perintah lagu berikutnya/sebelumnya dikirim pemanggil SEKETIKA; model ini hanya menggambar.
 *  2. Urutan normal: kaset lama keluar ([Tokens.Motion.CASSETTE_EJECT_MS]) → kaset baru masuk ([Tokens.Motion.CASSETTE_INSERT_MS]).
 *  3. Bila lagu berganti lagi selama animasi, tahap keluar dilewati dan kaset TERBARU langsung masuk dengan durasi
 *     singkat ([Tokens.Motion.CASSETTE_RAPID_MS]) — animasi tidak pernah menumpuk.
 *  4. Animasi dimatikan → kaset langsung berganti.
 */
class CassetteSwap(initialKey: String?) {
    enum class Phase { IDLE, EJECT, INSERT }

    /** Keadaan untuk satu frame: [shownKey] lagu yang labelnya tampil di kaset, [progress] 0..1 tahap berjalan. */
    data class Frame(val phase: Phase, val progress: Float, val shownKey: String?)

    private var shown: String? = initialKey
    private var pending: String? = null
    private var phase = Phase.IDLE
    private var phaseStart = 0L
    private var phaseLength = 0L

    /** Lagu berganti ke [key] pada [nowMs]. Kunci yang sama diabaikan. */
    fun onTrack(key: String?, nowMs: Long, animate: Boolean) {
        if (key == (pending ?: shown)) return
        if (!animate) {
            shown = key; pending = null; phase = Phase.IDLE
            return
        }
        when (phase) {
            Phase.IDLE -> {
                pending = key
                start(Phase.EJECT, nowMs, Tokens.Motion.CASSETTE_EJECT_MS.toLong())
            }
            Phase.EJECT, Phase.INSERT -> {
                // Skip beruntun: langsung masukkan kaset terbaru, singkat
                shown = key
                pending = null
                start(Phase.INSERT, nowMs, Tokens.Motion.CASSETTE_RAPID_MS.toLong())
            }
        }
    }

    fun frame(nowMs: Long): Frame {
        if (phase == Phase.IDLE) return Frame(Phase.IDLE, 1f, shown)
        var p = progressAt(nowMs)
        if (phase == Phase.EJECT && p >= 1f) {
            shown = pending
            pending = null
            start(Phase.INSERT, phaseStart + phaseLength, Tokens.Motion.CASSETTE_INSERT_MS.toLong())
            p = progressAt(nowMs)
        }
        if (phase == Phase.INSERT && p >= 1f) {
            phase = Phase.IDLE
            return Frame(Phase.IDLE, 1f, shown)
        }
        return Frame(phase, p, shown)
    }

    /** True selama animasi berjalan (pemanggil butuh frame). */
    val running: Boolean get() = phase != Phase.IDLE

    private fun start(p: Phase, at: Long, length: Long) {
        phase = p; phaseStart = at; phaseLength = length.coerceAtLeast(1L)
    }

    private fun progressAt(now: Long): Float = ((now - phaseStart).toFloat() / phaseLength).coerceIn(0f, 1f)
}
