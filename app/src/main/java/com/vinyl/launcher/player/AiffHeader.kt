package com.vinyl.launcher.player

/** Format PCM di dalam berkas AIFF / AIFF-C, sesudah header dibaca. */
data class AiffFormat(
    val channels: Int,
    /** Jumlah frame menurut header (bisa salah di berkas yang terpotong; pemutar memakai ukuran chunk data). */
    val frames: Long,
    val bytesPerSample: Int,
    val sampleRate: Int,
    /** Sampel float 32-bit (AIFF-C "fl32"); selain itu bilangan bulat bertanda. */
    val float: Boolean,
    /** AIFF-C "sowt" menyimpan sampel little-endian; selebihnya big-endian dan perlu dibalik untuk Android. */
    val littleEndian: Boolean,
) {
    val bytesPerFrame: Int get() = channels * bytesPerSample
}

sealed interface AiffParse {
    data class Ok(val format: AiffFormat) : AiffParse

    /** Valid, tapi bukan PCM yang bisa diputar (mis. IMA4, u-law, 8-bit, 64-bit float). */
    data class Unsupported(val reason: String) : AiffParse

    data class Malformed(val reason: String) : AiffParse
}

/**
 * Pengurai header AIFF/AIFF-C (Apple/SGI; semua angka big-endian). Murni Kotlin supaya bisa diuji tanpa Android; urusan
 * membaca aliran data ada di [AiffExtractor].
 */
object AiffHeader {
    const val MAX_CHANNELS = 8
    const val MAX_SAMPLE_RATE = 768_000

    /** 12 byte pertama: "FORM", ukuran, lalu "AIFF" atau "AIFC". */
    fun isAiff(head: ByteArray): Boolean = head.size >= 12 && fourcc(head, 0) == "FORM" && fourcc(head, 8).let { it == "AIFF" || it == "AIFC" }

    fun isAifc(head: ByteArray): Boolean = head.size >= 12 && fourcc(head, 8) == "AIFC"

    fun fourcc(b: ByteArray, off: Int): String = String(CharArray(4) { (b[off + it].toInt() and 0xFF).toChar() })

    fun u16(b: ByteArray, off: Int): Int = ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    fun u32(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xFF) shl 24) or ((b[off + 1].toLong() and 0xFF) shl 16) or
            ((b[off + 2].toLong() and 0xFF) shl 8) or (b[off + 3].toLong() and 0xFF)

    /**
     * Bilangan 80-bit IEEE 754 extended (tanda 1 bit, eksponen 15 bit berbias 16383, mantissa 64 bit dengan bit bulat
     * eksplisit) → Double. Dipakai untuk laju sampel.
     */
    fun extended(b: ByteArray, off: Int): Double {
        val se = u16(b, off)
        val negative = se and 0x8000 != 0
        val exponent = se and 0x7FFF
        var mantissa = 0L
        for (i in 0 until 8) mantissa = (mantissa shl 8) or (b[off + 2 + i].toLong() and 0xFF)
        if (exponent == 0 && mantissa == 0L) return 0.0
        if (exponent == 0x7FFF) return if (mantissa shl 1 == 0L) Double.POSITIVE_INFINITY else Double.NaN
        // Mantissa tak bertanda 64 bit → Double (dua bagian 32 bit agar tidak jadi negatif)
        val hi = (mantissa ushr 32).toDouble()
        val lo = (mantissa and 0xFFFFFFFFL).toDouble()
        val value = Math.scalb(hi * 4294967296.0 + lo, exponent - 16383 - 63)
        return if (negative) -value else value
    }

    /**
     * Isi chunk "COMM". [aifc] = berkasnya AIFF-C (ada jenis kompresi sesudah laju sampel).
     * @param body isi chunk (tanpa header 8 byte), [size] byte pertama dipakai
     */
    fun parseComm(body: ByteArray, size: Int, aifc: Boolean): AiffParse {
        if (size < 18 || body.size < size) return AiffParse.Malformed("chunk COMM terlalu pendek")
        val channels = u16(body, 0)
        val frames = u32(body, 2)
        val sampleBits = u16(body, 6)
        val rate = extended(body, 8)
        if (channels !in 1..MAX_CHANNELS) {
            return if (channels == 0) AiffParse.Malformed("jumlah kanal 0") else AiffParse.Unsupported("$channels kanal")
        }
        if (!(rate >= 1.0 && rate <= MAX_SAMPLE_RATE)) return AiffParse.Malformed("laju sampel tidak masuk akal")
        var float = false
        var little = false
        var bits = sampleBits
        if (aifc) {
            if (size < 22) return AiffParse.Malformed("jenis kompresi AIFF-C hilang")
            when (val type = fourcc(body, 18)) {
                "NONE", "twos", "in24", "in32" -> Unit
                "sowt" -> little = true
                "fl32", "FL32" -> {
                    float = true
                    bits = 32
                }
                else -> return AiffParse.Unsupported("kompresi $type")
            }
        }
        if (bits !in 9..32) return AiffParse.Unsupported("$bits-bit")
        if (float && sampleBits != 32) return AiffParse.Malformed("fl32 harus 32-bit")
        val bytes = (bits + 7) / 8
        return AiffParse.Ok(AiffFormat(channels, frames, bytes, Math.round(rate).toInt(), float, little))
    }

    /**
     * Balik urutan byte tiap sampel di [buf] (0 until [length]) dari big-endian ke little-endian, tempat yang sama.
     * [length] kelipatan [bytesPerSample]; sisa yang tidak utuh dibiarkan.
     */
    fun swapToLittleEndian(buf: ByteArray, length: Int, bytesPerSample: Int) {
        var i = 0
        val end = length - length % bytesPerSample
        when (bytesPerSample) {
            2 -> while (i < end) {
                val t = buf[i]; buf[i] = buf[i + 1]; buf[i + 1] = t
                i += 2
            }
            3 -> while (i < end) {
                val t = buf[i]; buf[i] = buf[i + 2]; buf[i + 2] = t
                i += 3
            }
            4 -> while (i < end) {
                val a = buf[i]; val b = buf[i + 1]
                buf[i] = buf[i + 3]; buf[i + 1] = buf[i + 2]; buf[i + 2] = b; buf[i + 3] = a
                i += 4
            }
            else -> Unit
        }
    }
}
