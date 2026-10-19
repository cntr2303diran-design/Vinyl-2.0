package com.vinyl.launcher.player

import java.io.ByteArrayOutputStream

/** Pembuat berkas AIFF / AIFF-C kecil untuk tes (tanpa berkas contoh di repo). */
object AiffFixture {
    private fun be16(v: Int) = byteArrayOf((v shr 8).toByte(), v.toByte())
    private fun be32(v: Long) = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())

    /** Bilangan bulat positif → 80-bit IEEE extended. */
    fun extended(value: Int): ByteArray {
        require(value > 0)
        val msb = 31 - Integer.numberOfLeadingZeros(value)
        val exponent = 16383 + msb
        val mantissa = value.toLong() shl (63 - msb)
        val out = ByteArray(10)
        out[0] = (exponent shr 8).toByte()
        out[1] = exponent.toByte()
        for (i in 0 until 8) out[2 + i] = (mantissa ushr (56 - 8 * i)).toByte()
        return out
    }

    fun chunk(id: String, body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id.toByteArray(Charsets.ISO_8859_1))
        out.write(be32(body.size.toLong()))
        out.write(body)
        if (body.size % 2 != 0) out.write(0)
        return out.toByteArray()
    }

    fun comm(channels: Int, frames: Long, bits: Int, rate: Int, aifcType: String? = null): ByteArray {
        val body = ByteArrayOutputStream()
        body.write(be16(channels))
        body.write(be32(frames))
        body.write(be16(bits))
        body.write(extended(rate))
        if (aifcType != null) {
            body.write(aifcType.toByteArray(Charsets.ISO_8859_1))
            // nama kompresi sebagai pstring (panjang + teks, dipadatkan ke genap)
            body.write(byteArrayOf(0, 0))
        }
        return chunk("COMM", body.toByteArray())
    }

    fun ssnd(data: ByteArray, offset: Int = 0, declaredSize: Long? = null): ByteArray {
        val body = ByteArrayOutputStream()
        body.write(be32(offset.toLong()))
        body.write(be32(0))
        body.write(ByteArray(offset) { 0x7F })
        body.write(data)
        val bytes = body.toByteArray()
        if (declaredSize == null) return chunk("SSND", bytes)
        // Ukuran di header lebih besar dari isi sebenarnya (berkas terpotong)
        val out = ByteArrayOutputStream()
        out.write("SSND".toByteArray(Charsets.ISO_8859_1))
        out.write(be32(declaredSize))
        out.write(bytes)
        return out.toByteArray()
    }

    fun form(aifc: Boolean, vararg chunks: ByteArray): ByteArray {
        val inner = ByteArrayOutputStream()
        inner.write((if (aifc) "AIFC" else "AIFF").toByteArray(Charsets.ISO_8859_1))
        for (c in chunks) inner.write(c)
        val out = ByteArrayOutputStream()
        out.write("FORM".toByteArray(Charsets.ISO_8859_1))
        out.write(be32(inner.size().toLong()))
        out.write(inner.toByteArray())
        return out.toByteArray()
    }

    /** AIFF PCM biasa big-endian. */
    fun simple(channels: Int, bits: Int, rate: Int, data: ByteArray): ByteArray {
        val frames = data.size.toLong() / (channels * ((bits + 7) / 8))
        return form(false, comm(channels, frames, bits, rate), ssnd(data))
    }
}
