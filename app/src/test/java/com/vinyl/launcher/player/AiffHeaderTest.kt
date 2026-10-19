package com.vinyl.launcher.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiffHeaderTest {
    private fun parse(channels: Int, frames: Long, bits: Int, rate: Int, type: String? = null): AiffParse {
        val comm = AiffFixture.comm(channels, frames, bits, rate, type)
        // lepas header chunk 8 byte: yang dibaca pengurai hanyalah isinya
        val body = comm.copyOfRange(8, comm.size)
        val size = AiffHeader.u32(comm, 4).toInt()
        return AiffHeader.parseComm(body, size, aifc = type != null)
    }

    @Test
    fun extendedFloatDecodesCommonSampleRatesExactly() {
        for (rate in listOf(8000, 11025, 22050, 32000, 44100, 48000, 88200, 96000, 176400, 192000, 352800, 384000)) {
            assertEquals(rate.toDouble(), AiffHeader.extended(AiffFixture.extended(rate), 0), 0.0)
        }
        assertEquals(0.0, AiffHeader.extended(ByteArray(10), 0), 0.0)
        // tanda negatif
        val neg = AiffFixture.extended(44100).also { it[0] = (it[0].toInt() or 0x80).toByte() }
        assertEquals(-44100.0, AiffHeader.extended(neg, 0), 0.0)
        // tak hingga / NaN tidak menjadi laju sampel yang sah
        val inf = ByteArray(10).also { it[0] = 0x7F; it[1] = 0xFF.toByte(); it[2] = 0x80.toByte() }
        assertTrue(AiffHeader.extended(inf, 0).isInfinite())
    }

    @Test
    fun recognizesFormAndTheTwoFormTypes() {
        val aiff = AiffFixture.simple(2, 16, 44100, ByteArray(8))
        assertTrue(AiffHeader.isAiff(aiff))
        assertFalse(AiffHeader.isAifc(aiff))
        val aifc = AiffFixture.form(true, AiffFixture.comm(2, 0, 16, 44100, "NONE"), AiffFixture.ssnd(ByteArray(0)))
        assertTrue(AiffHeader.isAiff(aifc))
        assertTrue(AiffHeader.isAifc(aifc))
        assertFalse(AiffHeader.isAiff("RIFF....WAVE".toByteArray()))
        assertFalse(AiffHeader.isAiff("FORM....8SVX".toByteArray()))
        assertFalse(AiffHeader.isAiff(ByteArray(5)))
    }

    @Test
    fun plainAiffPcmFormats() {
        assertEquals(AiffParse.Ok(AiffFormat(2, 100, 2, 44100, float = false, littleEndian = false)), parse(2, 100, 16, 44100))
        assertEquals(AiffParse.Ok(AiffFormat(1, 5, 3, 96000, float = false, littleEndian = false)), parse(1, 5, 24, 96000))
        assertEquals(AiffParse.Ok(AiffFormat(2, 5, 4, 192000, float = false, littleEndian = false)), parse(2, 5, 32, 192000))
        // 20-bit disimpan rata-kiri dalam 3 byte
        assertEquals(3, (parse(2, 5, 20, 48000) as AiffParse.Ok).format.bytesPerSample)
        assertEquals(6, (parse(2, 5, 24, 48000) as AiffParse.Ok).format.bytesPerFrame)
    }

    @Test
    fun aifcCompressionTypes() {
        assertEquals(false, (parse(2, 5, 24, 48000, "NONE") as AiffParse.Ok).format.littleEndian)
        assertEquals(false, (parse(2, 5, 24, 48000, "twos") as AiffParse.Ok).format.littleEndian)
        assertEquals(true, (parse(2, 5, 16, 44100, "sowt") as AiffParse.Ok).format.littleEndian)
        val f = (parse(2, 5, 32, 44100, "fl32") as AiffParse.Ok).format
        assertTrue(f.float)
        assertEquals(4, f.bytesPerSample)
        assertTrue(parse(2, 5, 16, 44100, "ima4") is AiffParse.Unsupported)
        assertTrue(parse(2, 5, 8, 8000, "ulaw") is AiffParse.Unsupported)
        assertTrue(parse(2, 5, 64, 44100, "fl64") is AiffParse.Unsupported)
        // fl32 yang mengaku 16-bit rusak
        assertTrue(parse(2, 5, 16, 44100, "fl32") is AiffParse.Malformed)
    }

    @Test
    fun rejectsWhatCannotBePlayedOrIsBroken() {
        assertTrue(parse(2, 5, 8, 44100) is AiffParse.Unsupported)
        assertTrue(parse(2, 5, 1, 44100) is AiffParse.Unsupported)
        assertTrue(parse(0, 5, 16, 44100) is AiffParse.Malformed)
        assertTrue(parse(9, 5, 16, 44100) is AiffParse.Unsupported)
        assertTrue(AiffHeader.parseComm(ByteArray(10), 10, false) is AiffParse.Malformed)
        // laju sampel nol / terlalu tinggi
        val zero = AiffFixture.comm(2, 5, 16, 44100).copyOfRange(8, 26).also { for (i in 8 until 18) it[i] = 0 }
        assertTrue(AiffHeader.parseComm(zero, 18, false) is AiffParse.Malformed)
        assertTrue(parse(2, 5, 16, 3_000_000) is AiffParse.Malformed)
        // AIFF-C tanpa jenis kompresi
        assertTrue(AiffHeader.parseComm(AiffFixture.comm(2, 5, 16, 44100).copyOfRange(8, 26), 18, true) is AiffParse.Malformed)
    }

    @Test
    fun byteOrderIsReversedPerSample() {
        val two = byteArrayOf(1, 2, 3, 4, 5)
        AiffHeader.swapToLittleEndian(two, 5, 2)
        assertEquals(listOf<Byte>(2, 1, 4, 3, 5), two.toList()) // byte terakhir (sampel tak utuh) dibiarkan
        val three = byteArrayOf(1, 2, 3, 4, 5, 6)
        AiffHeader.swapToLittleEndian(three, 6, 3)
        assertEquals(listOf<Byte>(3, 2, 1, 6, 5, 4), three.toList())
        val four = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        AiffHeader.swapToLittleEndian(four, 8, 4)
        assertEquals(listOf<Byte>(4, 3, 2, 1, 8, 7, 6, 5), four.toList())
        // hanya [length] byte pertama diubah
        val part = byteArrayOf(1, 2, 3, 4)
        AiffHeader.swapToLittleEndian(part, 2, 2)
        assertEquals(listOf<Byte>(2, 1, 3, 4), part.toList())
    }
}
