package com.vinyl.launcher.audio.dsp

/**
 * Rantai olah sinyal pemutar bawaan, semuanya float 32-bit: penguatan (ReplayGain + preamp EQ) → EQ parametrik →
 * crossfeed → pembatas puncak. Dibuat ulang setiap format atau pengaturan berubah (status filter mulai dari nol).
 */
data class DspConfig(
    /** Penguatan total sebelum EQ (ReplayGain lagu ini + preamp EQ efektif), dB. */
    val gainDb: Float = 0f,
    val eqBands: List<EqBand> = emptyList(),
    val crossfeed: CrossfeedLevel? = null,
    /** Pembatas puncak; diputuskan [DspPlan.limiterNeeded]. */
    val limiter: Boolean = false,
)

class DspChain(sampleRate: Int, val channels: Int, val config: DspConfig) {
    private val gain = ReplayGain.linear(config.gainDb)
    private val eq = BiquadCascade(config.eqBands.filter { it.enabled }.map { BiquadCoefs.design(it, sampleRate.toDouble()) }.filter { it != BiquadCoefs.IDENTITY }, channels)
    private val crossfeed = config.crossfeed?.takeIf { channels == 2 }?.let { Crossfeed(it, sampleRate) }
    private val limiter = if (config.limiter) PeakLimiter(sampleRate, channels) else null

    /** Tidak ada yang mengubah sampel: data bisa diteruskan apa adanya (bit-transparan untuk float). */
    val isIdentity: Boolean = gain == 1f && eq.isEmpty && crossfeed == null && limiter == null

    /** Latensi (frame) yang ditambahkan pembatas; sisa ekornya dikeluarkan dengan [drain]. */
    val latencyFrames: Int get() = limiter?.latencyFrames ?: 0

    val limiterReductionDb: Float get() = limiter?.maxReductionDb ?: 0f

    fun process(buf: FloatArray, frames: Int) {
        if (isIdentity) return
        if (gain != 1f) for (i in 0 until frames * channels) buf[i] *= gain
        eq.process(buf, frames)
        crossfeed?.process(buf, frames)
        limiter?.process(buf, frames)
    }

    /** Keluarkan sisa garis tunda pembatas di akhir aliran: [buf] diisi hening lalu diproses. */
    fun drain(buf: FloatArray): Int {
        val frames = minOf(latencyFrames, buf.size / channels)
        if (frames == 0) return 0
        buf.fill(0f, 0, frames * channels)
        process(buf, frames)
        return frames
    }

    fun reset() {
        eq.reset()
        crossfeed?.reset()
        limiter?.reset()
    }
}

/** Keputusan murni dari pengaturan & tag ke [DspConfig]. */
object DspPlan {
    /**
     * @param rgDb penguatan ReplayGain lagu ini (0 bila mati)
     * @param eq profil EQ; null/mati = tanpa EQ
     * @param autoHeadroom turunkan preamp sebesar penguatan EQ terbesar
     */
    fun config(rgDb: Float, eq: ParametricProfile?, autoHeadroom: Boolean, crossfeed: CrossfeedLevel?): DspConfig {
        val bands = eq?.let { ParametricProfile.clamp(it).bands.filter { b -> b.enabled } }.orEmpty()
        val preamp = eq?.let { EqMath.effectivePreampDb(it, autoHeadroom) } ?: 0f
        val gainDb = rgDb + preamp
        return DspConfig(gainDb, bands, crossfeed, limiterNeeded(gainDb, bands))
    }

    /** Pembatas hanya dipakai bila rantai bisa menaikkan sinyal di atas 0 dBFS (penguatan + EQ > 0 dB). */
    fun limiterNeeded(gainDb: Float, bands: List<EqBand>): Boolean =
        gainDb + EqMath.maxBoostDb(bands) > 0.05
}
