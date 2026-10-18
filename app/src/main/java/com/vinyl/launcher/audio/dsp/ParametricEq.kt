package com.vinyl.launcher.audio.dsp

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/** EQ parametrik tersimpan: nama (mis. nama headphone dari AutoEQ), preamp, dan pita. */
@Serializable
data class ParametricProfile(
    val name: String = "Datar",
    val preampDb: Float = 0f,
    val bands: List<EqBand> = emptyList(),
) {
    companion object {
        const val MAX_BANDS = 20
        const val MIN_PREAMP = -30f
        const val MAX_PREAMP = 12f

        fun clamp(p: ParametricProfile): ParametricProfile = p.copy(
            name = p.name.filterNot { it.isISOControl() }.trim().take(60).ifBlank { "Tanpa nama" },
            preampDb = if (p.preampDb.isNaN()) 0f else p.preampDb.coerceIn(MIN_PREAMP, MAX_PREAMP),
            bands = p.bands.take(MAX_BANDS).map(EqBand::clamp),
        )
    }
}

object EqMath {
    /** Kisi frekuensi log 20 Hz–20 kHz (1/24 oktaf) untuk mengukur respons. */
    val GRID: DoubleArray = run {
        val n = (ln(1000.0) / ln(2.0) * 24).toInt() + 1
        DoubleArray(n) { 20.0 * 2.0.pow(it / 24.0) }
    }

    /** Respons total (dB) semua pita pada [freq]. Laju 48 kHz cukup untuk tampilan & perhitungan headroom. */
    fun responseDb(bands: List<EqBand>, freq: Double, sampleRate: Double = 48_000.0): Double =
        bands.sumOf { BiquadCoefs.design(it, sampleRate).magnitudeDb(freq, sampleRate) }

    /** Penguatan terbesar (dB, ≥ 0) di kisi: berapa banyak sinyal bisa naik karena EQ. */
    fun maxBoostDb(bands: List<EqBand>, sampleRate: Double = 48_000.0): Double {
        if (bands.none { it.enabled }) return 0.0
        val coefs = bands.map { BiquadCoefs.design(it, sampleRate) }
        var m = 0.0
        for (f in GRID) {
            if (f >= sampleRate * 0.49) break
            m = max(m, coefs.sumOf { it.magnitudeDb(f, sampleRate) })
        }
        return m
    }

    /**
     * Preamp yang dipakai: pilihan pengguna, dan bila [autoHeadroom] menyala paling tinggi −(penguatan EQ terbesar)
     * sehingga EQ tidak pernah membuat sinyal penuh melewati 0 dBFS.
     */
    fun effectivePreampDb(profile: ParametricProfile, autoHeadroom: Boolean, sampleRate: Double = 48_000.0): Float {
        val p = ParametricProfile.clamp(profile)
        if (!autoHeadroom) return p.preampDb
        return minOf(p.preampDb.toDouble(), -maxBoostDb(p.bands, sampleRate)).toFloat()
    }

    fun dbToLinear(db: Float): Float = 10.0.pow(db / 20.0).toFloat()
}

/**
 * Kaskade biquad untuk sinyal float berselang-seling (L, R, L, R, ...). Status filter disimpan dalam double per kanal
 * (stabil untuk pita bass pada laju sampel tinggi). Tidak ada alokasi saat memproses (aman untuk thread audio).
 */
class BiquadCascade(coefs: List<BiquadCoefs>, private val channels: Int) {
    private val n = coefs.size
    private val c = DoubleArray(n * 5).also { a ->
        coefs.forEachIndexed { i, k -> a[i * 5] = k.b0; a[i * 5 + 1] = k.b1; a[i * 5 + 2] = k.b2; a[i * 5 + 3] = k.a1; a[i * 5 + 4] = k.a2 }
    }
    private val z1 = DoubleArray(n * channels)
    private val z2 = DoubleArray(n * channels)

    val isEmpty: Boolean get() = n == 0

    fun process(buf: FloatArray, frames: Int) {
        if (n == 0) return
        var idx = 0
        for (f in 0 until frames) {
            for (ch in 0 until channels) {
                var x = buf[idx].toDouble()
                var k = ch
                var j = 0
                for (i in 0 until n) {
                    val y = c[j] * x + z1[k]
                    z1[k] = c[j + 1] * x - c[j + 3] * y + z2[k]
                    z2[k] = c[j + 2] * x - c[j + 4] * y
                    x = y
                    k += channels
                    j += 5
                }
                buf[idx] = x.toFloat()
                idx++
            }
        }
        // Buang nilai subnormal saat sinyal hening (subnormal memperlambat CPU)
        for (i in z1.indices) {
            if (abs(z1[i]) < 1e-25) z1[i] = 0.0
            if (abs(z2[i]) < 1e-25) z2[i] = 0.0
        }
    }

    fun reset() {
        z1.fill(0.0)
        z2.fill(0.0)
    }
}

/** Bantuan tampilan & penyuntingan pita EQ parametrik (skala frekuensi logaritmik, label gaya Equalizer APO). */
object EqEditor {
    private const val F_MIN = 20.0
    private const val F_RANGE = 1000.0 // 20 Hz × 1000 = 20 kHz

    /** 20 Hz–20 kHz → 0..1 (logaritmik, seperti sumbu equalizer). */
    fun freqToSlider(f: Float): Float = (kotlin.math.ln(f.coerceIn(20f, 20_000f) / F_MIN) / kotlin.math.ln(F_RANGE)).toFloat()

    /** 0..1 → frekuensi, dibulatkan ke dua angka penting (mis. 1 234 → 1 200 Hz). */
    fun sliderToFreq(v: Float): Float {
        val f = F_MIN * F_RANGE.pow(v.coerceIn(0f, 1f).toDouble())
        val mag = 10.0.pow(kotlin.math.floor(kotlin.math.log10(f)) - 1)
        return (kotlin.math.round(f / mag) * mag).toFloat().coerceIn(20f, 20_000f)
    }

    fun short(t: FilterType): String = when (t) {
        FilterType.PEAK -> "PK"
        FilterType.LOW_SHELF -> "LS"
        FilterType.HIGH_SHELF -> "HS"
        FilterType.LOW_PASS -> "LP"
        FilterType.HIGH_PASS -> "HP"
    }

    fun freqText(f: Float): String =
        if (f >= 1000f) "%.1f kHz".format(java.util.Locale.ROOT, f / 1000f).replace(".0 ", " ").replace('.', ',') else "${f.toInt()} Hz"

    fun gainText(db: Float): String {
        val r = kotlin.math.round(db * 10f) / 10f
        val sign = if (r > 0) "+" else if (r < 0) "−" else ""
        return sign + "%.1f".format(java.util.Locale.ROOT, kotlin.math.abs(r)).replace('.', ',') + " dB"
    }

    /** "PK 2,2 kHz −3,4 dB Q 1,29" */
    fun label(b: EqBand): String {
        val gain = if (b.type == FilterType.LOW_PASS || b.type == FilterType.HIGH_PASS) "" else " " + gainText(b.gainDb)
        return short(b.type) + " " + freqText(b.freq) + gain + " Q " + "%.2f".format(java.util.Locale.ROOT, b.q).replace('.', ',')
    }

    /** Pita baru: peaking 0 dB di oktaf yang belum dipakai (mulai 1 kHz). */
    fun newBand(existing: List<EqBand>): EqBand {
        val candidates = listOf(1000f, 250f, 4000f, 60f, 8000f, 500f, 2000f, 125f, 16000f, 31f)
        val f = candidates.firstOrNull { c -> existing.none { kotlin.math.abs(kotlin.math.ln(it.freq / c)) < 0.35 } } ?: 1000f
        return EqBand(FilterType.PEAK, f, 0f, 1f)
    }
}
