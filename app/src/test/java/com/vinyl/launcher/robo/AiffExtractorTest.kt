package com.vinyl.launcher.robo

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vinyl.launcher.player.AiffExtractor
import com.vinyl.launcher.player.AiffFixture
import com.vinyl.launcher.player.PlayerExtractors
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.EOFException

/**
 * Menjalankan [AiffExtractor] sungguhan lewat antarmuka Media3 (DefaultExtractorInput + ByteArrayDataSource) dan
 * memeriksa apa yang diterima pemutar: format PCM, urutan byte little-endian, durasi, cap waktu, dan pencarian posisi.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class AiffExtractorTest {
    private class Recorder : ExtractorOutput, TrackOutput {
        var format: Format? = null
        var seekMap: SeekMap? = null
        var duration = C.TIME_UNSET
        var ended = false
        val data = ByteArrayOutputStream()
        val times = ArrayList<Long>()
        val sizes = ArrayList<Int>()

        override fun track(id: Int, type: Int): TrackOutput = this
        override fun endTracks() { ended = true }
        override fun seekMap(seekMap: SeekMap) { this.seekMap = seekMap }

        override fun durationUs(durationUs: Long) { duration = durationUs }
        override fun format(format: Format) { this.format = format }

        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int {
            val tmp = ByteArray(length)
            val n = input.read(tmp, 0, length)
            if (n == C.RESULT_END_OF_INPUT) {
                if (allowEndOfInput) return n
                throw EOFException()
            }
            data.write(tmp, 0, n)
            return n
        }

        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
            this.data.write(data.data, data.position, length)
            data.skipBytes(length)
        }

        override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
            times.add(timeUs)
            sizes.add(size)
        }
    }

    private fun input(bytes: ByteArray, position: Long = 0): DefaultExtractorInput {
        val source = ByteArrayDataSource(bytes)
        source.open(DataSpec(Uri.EMPTY, position, C.LENGTH_UNSET.toLong()))
        return DefaultExtractorInput(source, position, bytes.size.toLong())
    }

    private fun drain(extractor: Extractor, input: DefaultExtractorInput) {
        val holder = PositionHolder()
        while (true) {
            val r = extractor.read(input, holder)
            if (r == Extractor.RESULT_END_OF_INPUT) return
            check(r == Extractor.RESULT_CONTINUE) { "hasil tak terduga $r" }
        }
    }

    /** Sniff + init + baca sampai habis. */
    private fun extract(bytes: ByteArray): Recorder {
        val out = Recorder()
        val extractor = AiffExtractor()
        val input = input(bytes)
        assertTrue("sniff", extractor.sniff(input))
        extractor.init(out)
        drain(extractor, input)
        return out
    }

    private fun pcm(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun sixteenBitStereoBecomesLittleEndianRawPcm() {
        // 4 frame stereo 16-bit big-endian: (0x0102, 0x0304) ...
        val be = pcm(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10)
        val out = extract(AiffFixture.simple(2, 16, 44100, be))
        val f = out.format!!
        assertEquals(MimeTypes.AUDIO_RAW, f.sampleMimeType)
        assertEquals(C.ENCODING_PCM_16BIT, f.pcmEncoding)
        assertEquals(2, f.channelCount)
        assertEquals(44100, f.sampleRate)
        assertTrue(out.ended)
        assertArrayEquals(pcm(0x02, 0x01, 0x04, 0x03, 0x06, 0x05, 0x08, 0x07, 0x0A, 0x09, 0x0C, 0x0B, 0x0E, 0x0D, 0x10, 0x0F), out.data.toByteArray())
        assertEquals(Util.scaleLargeTimestamp(4, C.MICROS_PER_SECOND, 44100), out.seekMap!!.durationUs)
        assertEquals(out.seekMap!!.durationUs, out.duration)
    }

    @Test
    fun twentyFourBitAndThirtyTwoBitKeepAllBits() {
        val d24 = pcm(0x11, 0x22, 0x33, 0x44, 0x55, 0x66)
        val o24 = extract(AiffFixture.simple(1, 24, 96000, d24))
        assertEquals(C.ENCODING_PCM_24BIT, o24.format!!.pcmEncoding)
        assertEquals(96000, o24.format!!.sampleRate)
        assertArrayEquals(pcm(0x33, 0x22, 0x11, 0x66, 0x55, 0x44), o24.data.toByteArray())

        val d32 = pcm(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val o32 = extract(AiffFixture.simple(1, 32, 192000, d32))
        assertEquals(C.ENCODING_PCM_32BIT, o32.format!!.pcmEncoding)
        assertArrayEquals(pcm(0x04, 0x03, 0x02, 0x01, 0x08, 0x07, 0x06, 0x05), o32.data.toByteArray())
    }

    @Test
    fun aifcFloatIsSwappedAndSowtIsLeftAlone() {
        val data = pcm(0x3F, 0x80, 0x00, 0x00) // 1.0f big-endian
        val flt = AiffFixture.form(true, AiffFixture.comm(1, 1, 32, 48000, "fl32"), AiffFixture.ssnd(data))
        val o = extract(flt)
        assertEquals(C.ENCODING_PCM_FLOAT, o.format!!.pcmEncoding)
        assertArrayEquals(pcm(0x00, 0x00, 0x80, 0x3F), o.data.toByteArray())

        val le = pcm(0x02, 0x01, 0x04, 0x03)
        val sowt = AiffFixture.form(true, AiffFixture.comm(1, 2, 16, 44100, "sowt"), AiffFixture.ssnd(le))
        assertArrayEquals(le, extract(sowt).data.toByteArray())
    }

    @Test
    fun skipsUnknownChunksIncludingOddSizedOnesAndHonoursTheSoundDataOffset() {
        val data = pcm(0x00, 0x01, 0x00, 0x02)
        val bytes = AiffFixture.form(
            false,
            AiffFixture.chunk("NAME", "tiga".toByteArray().copyOf(3)), // ukuran ganjil → ada byte padding
            AiffFixture.comm(1, 2, 16, 44100),
            AiffFixture.chunk("ANNO", "catatan".toByteArray()),
            AiffFixture.ssnd(data, offset = 6),
            AiffFixture.chunk("ID3 ", ByteArray(40) { 9 }), // sesudah data: tidak boleh ikut diputar
        )
        val out = extract(bytes)
        assertArrayEquals(pcm(0x01, 0x00, 0x02, 0x00), out.data.toByteArray())
    }

    @Test
    fun largeFilesAreCutIntoAboutHundredMillisecondPiecesWithRunningTimestamps() {
        val frames = 10_000
        val data = ByteArray(frames * 2) { (it % 251).toByte() }
        val out = extract(AiffFixture.simple(1, 16, 44100, data))
        assertEquals(data.size, out.data.size())
        assertTrue("beberapa potongan", out.sizes.size >= 2)
        assertEquals(data.size, out.sizes.sum())
        assertEquals(0L, out.times.first())
        // cap waktu naik tepat sesuai jumlah frame sebelumnya
        var frame = 0L
        for (i in out.times.indices) {
            assertEquals(Util.scaleLargeTimestamp(frame, C.MICROS_PER_SECOND, 44100), out.times[i])
            frame += out.sizes[i] / 2
        }
    }

    @Test
    fun truncatedFileEndsCleanlyAtTheLastWholeFrame() {
        val data = ByteArray(2000) { 1 }
        val full = AiffFixture.form(false, AiffFixture.comm(1, 1000, 16, 44100), AiffFixture.ssnd(data))
        val cut = full.copyOf(full.size - 501) // 1499 byte data tersisa → 749 frame utuh
        val out = extract(cut)
        assertEquals(1498, out.data.size())
    }

    @Test
    fun seekMapPointsAtTheRightFrame() {
        val frames = 88_200 // 2 detik pada 44,1 kHz
        val data = ByteArray(frames * 4) { (it % 200).toByte() }
        val bytes = AiffFixture.simple(2, 16, 44100, data)
        val out = extract(bytes)
        val map = out.seekMap!!
        assertTrue(map.isSeekable)
        assertEquals(2_000_000L, map.durationUs)
        val p = map.getSeekPoints(500_000L).first
        assertEquals(500_000L, p.timeUs)
        // awal data = 12 (FORM) + 8 + 18 (COMM) + 8 (SSND) + 8 (offset, blockSize)
        assertEquals(12L + 8 + 18 + 8 + 8 + 22_050L * 4, p.position)
        // di luar rentang → dijepit ke frame terakhir / pertama
        assertEquals(0L, map.getSeekPoints(-5L).first.timeUs)
        assertTrue(map.getSeekPoints(99_000_000L).first.position <= bytes.size)
    }

    @Test
    fun seekingMidwayContinuesFromThatFrameWithTheRightTimestamp() {
        val frames = 20_000
        val data = ByteArray(frames * 2) { (it % 241).toByte() }
        val bytes = AiffFixture.simple(1, 16, 44100, data)
        val first = Recorder()
        val extractor = AiffExtractor()
        val input1 = input(bytes)
        assertTrue(extractor.sniff(input1))
        extractor.init(first)
        // baca sampai format & peta posisi tersedia
        val holder = PositionHolder()
        while (first.seekMap == null) extractor.read(input1, holder)
        val point = first.seekMap!!.getSeekPoints(250_000L).first
        first.data.reset()
        first.times.clear()
        first.sizes.clear()

        extractor.seek(point.position, point.timeUs)
        drain(extractor, input(bytes, point.position))
        val startFrame = (point.position - (12L + 8 + 18 + 8 + 8)) / 2
        assertEquals(point.timeUs, first.times.first())
        assertEquals(Util.scaleLargeTimestamp(startFrame, C.MICROS_PER_SECOND, 44100), point.timeUs)
        // data yang diterima = sisa data mulai frame itu, dibalik byte-nya
        val expected = data.copyOfRange((startFrame * 2).toInt(), data.size)
        for (i in expected.indices step 2) { val t = expected[i]; expected[i] = expected[i + 1]; expected[i + 1] = t }
        assertArrayEquals(expected, first.data.toByteArray())
    }

    @Test
    fun seekingBackToTheStartAfterAFullReadReplaysFromTheFirstFrame() {
        val data = ByteArray(400) { (it % 100).toByte() }
        val bytes = AiffFixture.simple(1, 16, 8000, data)
        val out = Recorder()
        val extractor = AiffExtractor()
        val in1 = input(bytes)
        assertTrue(extractor.sniff(in1))
        extractor.init(out)
        drain(extractor, in1)
        val firstPass = out.data.toByteArray()
        out.data.reset()
        out.times.clear()
        extractor.seek(0, 0)
        drain(extractor, input(bytes, 0))
        assertArrayEquals(firstPass, out.data.toByteArray())
        assertEquals(0L, out.times.first())
    }

    @Test
    fun rejectsWhatItCannotPlay() {
        val ima = AiffFixture.form(true, AiffFixture.comm(2, 10, 16, 44100, "ima4"), AiffFixture.ssnd(ByteArray(34)))
        assertParserError(ima)
        val eightBit = AiffFixture.simple(1, 8, 8000, ByteArray(100))
        assertParserError(eightBit)
        val ssndFirst = AiffFixture.form(false, AiffFixture.ssnd(ByteArray(8)), AiffFixture.comm(1, 4, 16, 44100))
        assertParserError(ssndFirst)
        val noSound = AiffFixture.form(false, AiffFixture.comm(1, 4, 16, 44100))
        assertParserError(noSound)
    }

    private fun assertParserError(bytes: ByteArray) {
        try {
            extract(bytes)
            fail("seharusnya ParserException")
        } catch (e: ParserException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun sniffAcceptsOnlyAiff() {
        val wav = "RIFF".toByteArray() + ByteArray(4) + "WAVE".toByteArray() + ByteArray(20)
        assertFalse(AiffExtractor().sniff(input(wav)))
        assertFalse(AiffExtractor().sniff(input(ByteArray(3))))
        assertFalse(AiffExtractor().sniff(input(ByteArray(0))))
        assertTrue(AiffExtractor().sniff(input(AiffFixture.simple(1, 16, 44100, ByteArray(4)))))
    }

    @Test
    fun theSharedFactoryOffersTheAiffExtractorFirstAndTheStandardOnesAfterIt() {
        val list = PlayerExtractors.factory.createExtractors()
        assertTrue(list.first() is AiffExtractor)
        assertTrue("pengurai bawaan ikut", list.size > 5)
        // setiap pemanggilan membuat instans baru (pengurai menyimpan keadaan)
        assertTrue(list.first() !== PlayerExtractors.factory.createExtractors().first())
    }
}
