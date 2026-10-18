package com.vinyl.launcher.audio.dsp

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Format sampel PCM berselang-seling: little-endian (urutan bawaan Android) dan big-endian (AIFF). */
enum class PcmFormat(val bytes: Int, val bits: Int) {
    U8(1, 8), S16(2, 16), S24(3, 24), S32(4, 32), F32(4, 32), S16BE(2, 16), S24BE(3, 24), S32BE(4, 32),
}

/**
 * Konversi PCM ↔ float (−1..1) tanpa alokasi di thread audio. Bilangan bulat dinormalkan dengan 2^(n−1) (konvensi
 * Android/ExoPlayer), jadi 16/24/32-bit kembali utuh bila tidak diproses.
 */
object Pcm {
    private const val S16 = 1f / 32768f
    private const val S24 = 1f / 8388608f
    private const val S32 = 1.0 / 2147483648.0

    /**
     * Baca sampel dari [src] (posisi..limit) ke [dst] mulai indeks 0, paling banyak [dst].size sampel; posisi [src] maju.
     * @return jumlah sampel (bukan frame) yang dibaca
     */
    fun read(src: ByteBuffer, format: PcmFormat, dst: FloatArray): Int {
        val big = format == PcmFormat.S16BE || format == PcmFormat.S24BE || format == PcmFormat.S32BE
        val b = src.order(if (big) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
        val n = minOf(dst.size, b.remaining() / format.bytes)
        when (format) {
            PcmFormat.S16BE -> for (i in 0 until n) dst[i] = b.short * S16
            PcmFormat.S32BE -> for (i in 0 until n) dst[i] = (b.int * S32).toFloat()
            PcmFormat.S24BE -> for (i in 0 until n) {
                val hi = b.get().toInt() // bertanda
                val mid = b.get().toInt() and 0xFF
                val lo = b.get().toInt() and 0xFF
                dst[i] = ((hi shl 16) or (mid shl 8) or lo) * S24
            }
            PcmFormat.U8 -> for (i in 0 until n) dst[i] = ((b.get().toInt() and 0xFF) - 128) / 128f
            PcmFormat.S16 -> for (i in 0 until n) dst[i] = b.short * S16
            PcmFormat.S24 -> for (i in 0 until n) {
                val lo = b.get().toInt() and 0xFF
                val mid = b.get().toInt() and 0xFF
                val hi = b.get().toInt() // bertanda
                dst[i] = ((hi shl 16) or (mid shl 8) or lo) * S24
            }
            PcmFormat.S32 -> for (i in 0 until n) dst[i] = (b.int * S32).toFloat()
            PcmFormat.F32 -> for (i in 0 until n) dst[i] = b.float
        }
        return n
    }

    /** Tulis [count] sampel float ke [dst] sebagai float 32-bit little-endian. */
    fun writeFloat(src: FloatArray, count: Int, dst: ByteBuffer) {
        val b = dst.order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) b.putFloat(src[i])
    }

    /**
     * Tulis [count] sampel sebagai 16-bit little-endian dengan dither TPDF (±1 LSB, segitiga) agar pembulatan ke 16-bit
     * menjadi derau halus, bukan distorsi. [seed] berubah tiap pemanggilan (dikembalikan untuk pemanggilan berikutnya).
     */
    fun writeS16Dithered(src: FloatArray, count: Int, dst: ByteBuffer, seed: Int): Int {
        val b = dst.order(ByteOrder.LITTLE_ENDIAN)
        var r = seed
        for (i in 0 until count) {
            r = r * 1664525 + 1013904223
            val u1 = (r ushr 9) * (1f / 8388608f)
            r = r * 1664525 + 1013904223
            val u2 = (r ushr 9) * (1f / 8388608f)
            val v = src[i] * 32767f + (u1 - u2)
            val q = Math.round(v).coerceIn(-32768, 32767)
            b.putShort(q.toShort())
        }
        return r
    }

    /** Tulis tanpa dither (untuk sampel 16-bit yang tidak diubah: kembali persis ke nilai aslinya). */
    fun writeS16(src: FloatArray, count: Int, dst: ByteBuffer) {
        val b = dst.order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) b.putShort(Math.round(src[i] * 32768f).coerceIn(-32768, 32767).toShort())
    }
}
