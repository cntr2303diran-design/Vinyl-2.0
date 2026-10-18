package com.vinyl.launcher.audio.dsp

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow

/**
 * Crossfeed untuk headphone: sebagian bass dari kanal seberang dicampurkan (seperti mendengar speaker), mengurangi
 * kesan suara "di dalam kepala" pada rekaman stereo lebar. Algoritma Bauer stereophonic-to-binaural (bs2b, Boris
 * Mikhaylov, lisensi MIT) — ditulis ulang dari rumusnya: lowpass satu kutub untuk umpan silang dan highboost untuk
 * kanal sendiri, lalu penguatan total agar volume tetap.
 */
@Serializable
enum class CrossfeedLevel(val cutHz: Double, val feedDb: Double, val label: String) {
    /** Bawaan bs2b: 700 Hz, 4,5 dB. */
    NATURAL(700.0, 4.5, "Alami (700 Hz, 4,5 dB)"),
    /** Gaya ampli headphone Chu Moy: 700 Hz, 6 dB. */
    STRONG(700.0, 6.0, "Kuat (700 Hz, 6 dB)"),
    /** Gaya Jan Meier: 650 Hz, 9,5 dB. */
    WIDE_SPEAKERS(650.0, 9.5, "Speaker lebar (650 Hz, 9,5 dB)"),
}

class Crossfeed(level: CrossfeedLevel, sampleRate: Int) {
    private val a0Lo: Double
    private val b1Lo: Double
    private val a0Hi: Double
    private val a1Hi: Double
    private val b1Hi: Double
    private val gain: Double
    private var loL = 0.0; private var loR = 0.0
    private var hiL = 0.0; private var hiR = 0.0
    private var inL = 0.0; private var inR = 0.0

    init {
        val gbLo = level.feedDb * -5.0 / 6.0 - 3.0
        val gbHi = level.feedDb / 6.0 - 3.0
        val gLo = 10.0.pow(gbLo / 20.0)
        val gHi = 1.0 - 10.0.pow(gbHi / 20.0)
        val fcHi = level.cutHz * 2.0.pow((gbLo - 20.0 * log10(gHi)) / 12.0)
        val sr = sampleRate.coerceIn(8_000, 768_000).toDouble()
        var x = exp(-2.0 * PI * level.cutHz / sr)
        b1Lo = x
        a0Lo = gLo * (1.0 - x)
        x = exp(-2.0 * PI * fcHi / sr)
        b1Hi = x
        a0Hi = 1.0 - gHi * (1.0 - x)
        a1Hi = -x
        gain = 1.0 / (1.0 - gHi + gLo)
    }

    /** Hanya untuk stereo berselang-seling. */
    fun process(buf: FloatArray, frames: Int) {
        var i = 0
        for (f in 0 until frames) {
            val l = buf[i].toDouble()
            val r = buf[i + 1].toDouble()
            loL = a0Lo * l + b1Lo * loL
            loR = a0Lo * r + b1Lo * loR
            hiL = a0Hi * l + a1Hi * inL + b1Hi * hiL
            hiR = a0Hi * r + a1Hi * inR + b1Hi * hiR
            inL = l
            inR = r
            buf[i] = ((hiL + loR) * gain).toFloat()
            buf[i + 1] = ((hiR + loL) * gain).toFloat()
            i += 2
        }
        if (kotlin.math.abs(loL) < 1e-25) loL = 0.0
        if (kotlin.math.abs(loR) < 1e-25) loR = 0.0
        if (kotlin.math.abs(hiL) < 1e-25) hiL = 0.0
        if (kotlin.math.abs(hiR) < 1e-25) hiR = 0.0
    }

    fun reset() {
        loL = 0.0; loR = 0.0; hiL = 0.0; hiR = 0.0; inL = 0.0; inR = 0.0
    }
}
