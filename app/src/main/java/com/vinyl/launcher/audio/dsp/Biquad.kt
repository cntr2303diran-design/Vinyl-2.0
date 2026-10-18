package com.vinyl.launcher.audio.dsp

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Filter biquad untuk EQ parametrik pemutar bawaan. Rumus koefisien dari "Audio EQ Cookbook" (Robert Bristow-Johnson),
 * rumus yang juga dipakai Equalizer APO dan AutoEQ, sehingga preset AutoEQ terdengar sama. Murni (tanpa Android) agar
 * respons frekuensinya diuji.
 */

/** Jenis filter, sesuai singkatan Equalizer APO / AutoEQ: PK, LSC, HSC, LP, HP. */
@Serializable
enum class FilterType { PEAK, LOW_SHELF, HIGH_SHELF, LOW_PASS, HIGH_PASS }

/** Satu pita EQ parametrik. [q] untuk shelf/pass memakai definisi Q cookbook (0,707 = Butterworth). */
@Serializable
data class EqBand(
    val type: FilterType = FilterType.PEAK,
    val freq: Float = 1000f,
    val gainDb: Float = 0f,
    val q: Float = 0.707f,
    val enabled: Boolean = true,
) {
    companion object {
        const val MIN_FREQ = 10f
        const val MAX_FREQ = 22_000f
        const val MIN_GAIN = -24f
        const val MAX_GAIN = 24f
        const val MIN_Q = 0.1f
        const val MAX_Q = 20f

        /** Nilai liar (NaN, di luar batas) dibatasi agar filter selalu stabil. */
        fun clamp(b: EqBand): EqBand = b.copy(
            freq = if (b.freq.isNaN()) 1000f else b.freq.coerceIn(MIN_FREQ, MAX_FREQ),
            gainDb = if (b.gainDb.isNaN()) 0f else b.gainDb.coerceIn(MIN_GAIN, MAX_GAIN),
            q = if (b.q.isNaN()) 0.707f else b.q.coerceIn(MIN_Q, MAX_Q),
        )
    }
}

/** Koefisien biquad yang sudah dinormalkan (a0 = 1). */
data class BiquadCoefs(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {

    /** Besar respons (dB) pada [freq] Hz untuk laju sampel [sampleRate]. */
    fun magnitudeDb(freq: Double, sampleRate: Double): Double {
        val w = 2.0 * PI * freq / sampleRate
        val c1 = cos(w); val s1 = sin(w)
        val c2 = cos(2 * w); val s2 = sin(2 * w)
        // H(e^jw) = (b0 + b1 e^-jw + b2 e^-2jw) / (1 + a1 e^-jw + a2 e^-2jw)
        val nr = b0 + b1 * c1 + b2 * c2
        val ni = -(b1 * s1 + b2 * s2)
        val dr = 1.0 + a1 * c1 + a2 * c2
        val di = -(a1 * s1 + a2 * s2)
        val num = nr * nr + ni * ni
        val den = (dr * dr + di * di).coerceAtLeast(1e-300)
        return 10.0 * log10((num / den).coerceAtLeast(1e-300))
    }

    /** Kutub di dalam lingkaran satuan (filter stabil). */
    val stable: Boolean get() = kotlin.math.abs(a2) < 1.0 && kotlin.math.abs(a1) < 1.0 + a2

    companion object {
        val IDENTITY = BiquadCoefs(1.0, 0.0, 0.0, 0.0, 0.0)

        /**
         * Rancang filter [band] untuk [sampleRate]. Frekuensi di atas ±0,49 × laju sampel dibatasi (mis. pita 20 kHz pada
         * 44,1 kHz tetap aman). Pita mati atau tanpa efek → [IDENTITY].
         */
        fun design(band: EqBand, sampleRate: Double): BiquadCoefs {
            val b = EqBand.clamp(band)
            if (!b.enabled) return IDENTITY
            if ((b.type == FilterType.PEAK || b.type == FilterType.LOW_SHELF || b.type == FilterType.HIGH_SHELF) && b.gainDb == 0f) {
                return IDENTITY
            }
            val f0 = b.freq.toDouble().coerceAtMost(sampleRate * 0.49)
            val w0 = 2.0 * PI * f0 / sampleRate
            val cw = cos(w0)
            val sw = sin(w0)
            val q = b.q.toDouble()
            val alpha = sw / (2.0 * q)
            val a = 10.0.pow(b.gainDb / 40.0)
            val (n, d) = when (b.type) {
                FilterType.PEAK -> doubleArrayOf(1 + alpha * a, -2 * cw, 1 - alpha * a) to doubleArrayOf(1 + alpha / a, -2 * cw, 1 - alpha / a)
                FilterType.LOW_SHELF -> {
                    val sa = 2 * sqrt(a) * alpha
                    doubleArrayOf(
                        a * ((a + 1) - (a - 1) * cw + sa), 2 * a * ((a - 1) - (a + 1) * cw), a * ((a + 1) - (a - 1) * cw - sa),
                    ) to doubleArrayOf((a + 1) + (a - 1) * cw + sa, -2 * ((a - 1) + (a + 1) * cw), (a + 1) + (a - 1) * cw - sa)
                }
                FilterType.HIGH_SHELF -> {
                    val sa = 2 * sqrt(a) * alpha
                    doubleArrayOf(
                        a * ((a + 1) + (a - 1) * cw + sa), -2 * a * ((a - 1) + (a + 1) * cw), a * ((a + 1) + (a - 1) * cw - sa),
                    ) to doubleArrayOf((a + 1) - (a - 1) * cw + sa, 2 * ((a - 1) - (a + 1) * cw), (a + 1) - (a - 1) * cw - sa)
                }
                FilterType.LOW_PASS -> doubleArrayOf((1 - cw) / 2, 1 - cw, (1 - cw) / 2) to doubleArrayOf(1 + alpha, -2 * cw, 1 - alpha)
                FilterType.HIGH_PASS -> doubleArrayOf((1 + cw) / 2, -(1 + cw), (1 + cw) / 2) to doubleArrayOf(1 + alpha, -2 * cw, 1 - alpha)
            }
            val a0 = d[0]
            return BiquadCoefs(n[0] / a0, n[1] / a0, n[2] / a0, d[1] / a0, d[2] / a0)
        }
    }
}
