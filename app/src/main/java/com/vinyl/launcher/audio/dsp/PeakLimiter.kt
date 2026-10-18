package com.vinyl.launcher.audio.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow

/**
 * Pembatas puncak dengan pandangan ke depan (lookahead) agar sinyal tidak pernah melewati [thresholdDb]: penguatan
 * diturunkan perlahan SEBELUM puncak tiba (tanpa klik), lalu pulih pelan. Dengan headroom otomatis biasanya tidak pernah
 * bekerja; ia jaring pengaman bila ReplayGain/EQ menaikkan sinyal. Tanpa alokasi saat memproses.
 */
class PeakLimiter(
    sampleRate: Int,
    private val channels: Int,
    thresholdDb: Float = -0.3f,
    lookaheadMs: Float = 1.5f,
    releaseMs: Float = 80f,
) {
    private val threshold = 10.0.pow(thresholdDb / 20.0).toFloat()
    /** Panjang garis tunda (frame). */
    private val look = (sampleRate * lookaheadMs / 1000f).toInt().coerceAtLeast(1)
    private val delay = FloatArray(look * channels)
    private var pos = 0
    private var frame = 0L
    private var gain = 1f
    private val attack = (1.0 - exp(-4.0 / look)).toFloat()
    private val release = (1.0 - exp(-1.0 / (sampleRate * releaseMs / 1000.0))).toFloat()

    // Antrean monoton untuk maksimum geser jendela [frame − look, frame]: nomor frame & puncaknya
    private val cap = look + 2
    private val dqFrame = LongArray(cap)
    private val dqPeak = FloatArray(cap)
    private var dqHead = 0
    private var dqSize = 0

    /** Penurunan penguatan terbesar sejak [resetMeter] (dB, ≤ 0) — untuk indikator di layar. */
    @Volatile
    var maxReductionDb = 0f
        private set

    fun process(buf: FloatArray, frames: Int) {
        var idx = 0
        var minGain = 1f
        for (f in 0 until frames) {
            var p = 0f
            for (ch in 0 until channels) {
                val a = abs(buf[idx + ch])
                if (a > p) p = a
            }
            push(frame, p)
            val windowPeak = dqPeak[dqHead]
            val target = if (windowPeak > threshold) threshold / windowPeak else 1f
            gain += (target - gain) * (if (target < gain) attack else release)
            if (gain < minGain) minGain = gain
            // Keluarkan sampel tertunda dengan penguatan sekarang; sampel baru masuk garis tunda
            val d = pos * channels
            for (ch in 0 until channels) {
                val out = delay[d + ch] * gain
                delay[d + ch] = buf[idx + ch]
                // Pengaman terakhir: tidak pernah melewati ambang
                buf[idx + ch] = if (out > threshold) threshold else if (out < -threshold) -threshold else out
            }
            pos++
            if (pos == look) pos = 0
            idx += channels
            frame++
        }
        if (minGain < 1f) {
            val db = (20.0 * log10(minGain.toDouble())).toFloat()
            if (db < maxReductionDb) maxReductionDb = db
        }
    }

    private fun push(fr: Long, p: Float) {
        while (dqSize > 0) {
            val back = (dqHead + dqSize - 1) % cap
            if (dqPeak[back] <= p) dqSize-- else break
        }
        val at = (dqHead + dqSize) % cap
        dqFrame[at] = fr
        dqPeak[at] = p
        dqSize++
        while (dqFrame[dqHead] < fr - look) {
            dqHead = (dqHead + 1) % cap
            dqSize--
        }
    }

    fun resetMeter() {
        maxReductionDb = 0f
    }

    fun reset() {
        delay.fill(0f)
        pos = 0
        frame = 0
        gain = 1f
        dqHead = 0
        dqSize = 0
    }

    /** Latensi tambahan (frame) yang dimasukkan garis tunda. */
    val latencyFrames: Int get() = look
}
