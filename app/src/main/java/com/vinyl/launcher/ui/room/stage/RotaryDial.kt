package com.vinyl.launcher.ui.room.stage

import kotlin.math.abs
import kotlin.math.atan2

/**
 * Hitungan cakram telepon putar (murni, teruji). Sudut dalam derajat, searah jarum jam dari atas (0..360). Cakram diputar
 * searah jarum jam sampai lubang angka menyentuh penahan jari di [STOP_DEG]; angka 1 menempuh jarak terpendek dan 0 terjauh
 * (sepuluh lubang, jarak antar lubang [SPACING]). Lepas jari sebelum sampai = tidak ada angka yang terhitung.
 */
object RotaryDial {
    /** Letak penahan jari (jam 4). */
    const val STOP_DEG = 120f
    /** Jarak putar angka 1 sampai penahan, dan jarak antar lubang. */
    const val FIRST_TRAVEL = 62f
    const val SPACING = 28f
    /** Toleransi sentuhan terhadap tengah lubang (lebih kecil dari separuh [SPACING], jadi lubang tidak tumpang tindih). */
    const val HOLE_TOL = 13f
    /** Sisa jarak ke penahan yang masih dianggap sampai. */
    const val DONE_MARGIN = 8f

    fun norm(a: Float): Float {
        if (a.isNaN() || a.isInfinite()) return 0f
        var x = a % 360f
        if (x < 0f) x += 360f
        return x
    }

    /** Selisih bertanda terpendek dari [from] ke [to] (-180..180); positif = searah jarum jam. */
    fun signedDelta(from: Float, to: Float): Float {
        var d = norm(to - from)
        if (d > 180f) d -= 360f
        return d
    }

    /** Sudut titik ([x], [y]) terhadap pusat ([cx], [cy]); 0 = tepat di atas pusat. */
    fun angleOf(cx: Float, cy: Float, x: Float, y: Float): Float =
        norm(Math.toDegrees(atan2((x - cx).toDouble(), (cy - y).toDouble())).toFloat())

    /** Jarak putar yang diperlukan angka [digit] (0 = sepuluh). */
    fun travel(digit: Int): Float {
        val n = if (digit == 0) 10 else digit.coerceIn(1, 9)
        return FIRST_TRAVEL + SPACING * (n - 1)
    }

    /** Sudut tengah lubang angka [digit] saat cakram belum diputar. */
    fun holeAngle(digit: Int): Float = norm(STOP_DEG - travel(digit))

    /** Angka yang lubangnya ada di [angle] (dalam toleransi), atau null bila di antara lubang atau di dekat penahan jari. */
    fun digitAt(angle: Float): Int? {
        var best: Int? = null
        var bestDiff = Float.MAX_VALUE
        for (d in 0..9) {
            val diff = abs(signedDelta(holeAngle(d), angle))
            if (diff < bestDiff) { best = d; bestDiff = diff }
        }
        return best.takeIf { bestDiff <= HOLE_TOL }
    }

    /**
     * Jarak putar baru setelah jari berpindah dari sudut [prev] ke [now]: ditambah gerak searah jarum jam, dikurangi gerak
     * berlawanan, dijepit antara 0 dan [travel] angka itu (cakram tidak bisa diputar melewati penahan atau mundur dari awal).
     */
    fun advance(total: Float, prev: Float, now: Float, digit: Int): Float =
        (total + signedDelta(prev, now)).coerceIn(0f, travel(digit))

    fun completes(total: Float, digit: Int): Boolean = total >= travel(digit) - DONE_MARGIN
}
