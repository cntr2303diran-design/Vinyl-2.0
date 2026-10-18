package com.vinyl.launcher.fx

import com.vinyl.launcher.ui.theme.Tokens
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Simulasi partikel debu/bokeh yang melayang naik pelan — murni dan deterministik (benih sama + urutan dt sama
 * = hasil sama), sehingga bisa diuji dan tidak berkedip aneh saat komposisi ulang.
 *
 * - Koordinat satuan 0..1 (x ke kanan, y ke bawah) tidak bergantung resolusi; penggambar mengalikan dengan ukuran.
 * - Jumlah dibatasi [capacity]; tidak ada alokasi per frame (larik tetap).
 * - [energy] (0..1, dari spektrum atau animasi dekoratif) mempercepat gerak dan menebalkan kilau sedikit.
 * - Alpha puncak per partikel dibatasi [Tokens.A11y.FX_PEAK] karena partikel berada di bawah peredam latar,
 *   yang menghitung kontras teks dengan anggaran itu (lihat BackdropDim.withFx).
 */
class ParticleField(val capacity: Int = DEFAULT_CAPACITY, seed: Long = DEFAULT_SEED) {
    init { require(capacity in 1..MAX_CAPACITY) { "capacity di luar 1..$MAX_CAPACITY: $capacity" } }

    private val rnd = Random(seed)

    val x = FloatArray(capacity)
    val y = FloatArray(capacity)
    /** 0 = jauh (kecil, pelan, redup), 1 = dekat (besar, cepat, lebih terang). */
    val depth = FloatArray(capacity)
    private val phase = FloatArray(capacity)
    private val sway = FloatArray(capacity)

    init {
        for (i in 0 until capacity) spawn(i, anywhere = true)
    }

    private fun spawn(i: Int, anywhere: Boolean) {
        x[i] = rnd.nextFloat()
        y[i] = if (anywhere) rnd.nextFloat() else 1f + MARGIN * rnd.nextFloat()
        depth[i] = rnd.nextFloat()
        phase[i] = rnd.nextFloat() * TWO_PI
        sway[i] = 0.4f + rnd.nextFloat() * 0.8f
    }

    /** Majukan simulasi [dtSec] detik (dibatasi 0..[MAX_DT]) dengan [energy] 0..1. */
    fun step(dtSec: Float, energy: Float) {
        if (!dtSec.isFinite()) return
        val dt = dtSec.coerceIn(0f, MAX_DT)
        val e = if (energy.isFinite()) energy.coerceIn(0f, 1f) else 0f
        val boost = 1f + ENERGY_SPEED * e
        for (i in 0 until capacity) {
            val d = depth[i]
            y[i] -= (BASE_SPEED + SPEED_BY_DEPTH * d) * boost * dt
            phase[i] += (0.8f + d) * (1f + e) * dt
            if (phase[i] > TWO_PI) phase[i] -= TWO_PI
            x[i] += sin(phase[i]) * SWAY_AMPLITUDE * sway[i] * dt
            if (x[i] < -MARGIN) x[i] += 1f + 2f * MARGIN else if (x[i] > 1f + MARGIN) x[i] -= 1f + 2f * MARGIN
            if (y[i] < -MARGIN) spawn(i, anywhere = false)
        }
    }

    /** Jari-jari sebagai pecahan sisi layar terpendek. */
    fun radius(i: Int, energy: Float = 0f): Float {
        val e = if (energy.isFinite()) energy.coerceIn(0f, 1f) else 0f
        return (MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * depth[i]) * (1f + 0.25f * e)
    }

    /** Alpha putih 0..[MAX_ALPHA]; berkedip pelan, lebih terang untuk partikel dekat dan saat energi tinggi. */
    fun alpha(i: Int, energy: Float = 0f): Float {
        val e = if (energy.isFinite()) energy.coerceIn(0f, 1f) else 0f
        val twinkle = 0.65f + 0.35f * sin(phase[i])                     // 0,3..1
        val base = 0.45f + 0.55f * depth[i]                             // 0,45..1
        val lift = 0.7f + 0.3f * e                                      // 0,7..1
        return (MAX_ALPHA * twinkle * base * lift).coerceIn(0f, MAX_ALPHA)
    }

    companion object {
        const val DEFAULT_CAPACITY = 36
        const val MAX_CAPACITY = 96
        const val DEFAULT_SEED = 20_241_018L
        /** Tidak pernah lebih dari anggaran efek satu lapisan. */
        const val MAX_ALPHA = Tokens.A11y.FX_PEAK
        const val MAX_DT = 0.1f
        const val MARGIN = 0.05f
        const val BASE_SPEED = 0.012f
        const val SPEED_BY_DEPTH = 0.03f
        const val ENERGY_SPEED = 2.2f
        const val SWAY_AMPLITUDE = 0.02f
        const val MIN_RADIUS = 0.004f
        const val MAX_RADIUS = 0.016f
        private const val TWO_PI = (2.0 * PI).toFloat()
    }
}
