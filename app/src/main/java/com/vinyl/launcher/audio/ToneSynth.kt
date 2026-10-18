package com.vinyl.launcher.audio

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Nada tes kanal kiri/kanan untuk panel Diagnostik (satu-satunya suara yang diputar launcher sendiri di console).
 * Murni (tanpa Android) agar keamanan volumenya bisa diuji: puncak dibatasi [MAX_DBFS], dengan fade masuk/keluar agar
 * tidak berbunyi "klik", dan kanal seberang benar-benar senyap.
 */
object ToneSynth {
    enum class Side { LEFT, RIGHT }

    const val RATE = 48_000
    /** Batas atas level nada (dBFS). −18 dBFS cukup jelas tetapi jauh dari penuh. */
    const val MAX_DBFS = -18f
    const val FADE_MS = 30

    /** Frekuensi berbeda per sisi agar mudah dibedakan telinga: kiri 440 Hz, kanan 660 Hz. */
    fun frequency(side: Side): Float = if (side == Side.LEFT) 440f else 660f

    /**
     * PCM 16-bit stereo berselang (L, R, L, R, ...) berisi nada di [side] saja.
     * @param dbfs level puncak; dipotong ke [MAX_DBFS] bila lebih keras.
     */
    fun stereo(side: Side, ms: Int = 1200, dbfs: Float = -20f, rate: Int = RATE): ShortArray {
        val frames = (rate.toLong() * ms.coerceIn(50, 5000) / 1000).toInt()
        val amp = 32767.0 * 10.0.pow(min(dbfs, MAX_DBFS) / 20.0)
        val fade = (rate * FADE_MS / 1000).coerceAtLeast(1)
        val f = frequency(side)
        val out = ShortArray(frames * 2)
        val ch = if (side == Side.LEFT) 0 else 1
        for (i in 0 until frames) {
            val env = min(1.0, min(i.toDouble() / fade, (frames - 1 - i).toDouble() / fade)).coerceAtLeast(0.0)
            out[i * 2 + ch] = (sin(2 * PI * f * i / rate) * amp * env).roundToInt().toShort()
        }
        return out
    }
}
