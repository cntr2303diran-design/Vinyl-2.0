package com.vinyl.launcher.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorUtil
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput

/**
 * Pengurai AIFF / AIFF-C PCM untuk Media3 (yang bawaannya tidak punya). Sampel big-endian dibalik ke little-endian
 * di sini, jadi ke pemutar tampil seperti WAV biasa: audio/raw 16/24/32-bit atau float 32-bit. AIFF-C "sowt"
 * (little-endian) diteruskan apa adanya. Kompresi lain (IMA4, u-law, …), 8-bit, dan float 64-bit tidak didukung dan
 * ditolak dengan galat "kontainer tidak didukung".
 *
 * Seperti WavExtractor: sampel dikirim sekitar 100 ms per potong, pencarian posisi tepat sampai satu frame.
 * Chunk "COMM" harus muncul sebelum "SSND" (urutan yang dipakai semua penulis AIFF umum); chunk lain dilewati.
 */
@OptIn(UnstableApi::class)
class AiffExtractor : Extractor {
    private enum class State { HEADER, CHUNKS, DATA }

    private var output: ExtractorOutput? = null
    private var track: TrackOutput? = null
    private var state = State.HEADER
    private var aifc = false
    private var format: AiffFormat? = null
    private var dataStart = C.INDEX_UNSET.toLong()
    private var dataEnd = C.INDEX_UNSET.toLong()
    private var startTimeUs = 0L
    private var outputFrames = 0L
    private var scratch = ByteArray(TARGET_SAMPLE_BYTES_MIN)

    override fun sniff(input: ExtractorInput): Boolean {
        val head = ByteArray(12)
        return ExtractorUtil.peekFullyQuietly(input, head, 0, head.size, true) && AiffHeader.isAiff(head)
    }

    override fun init(output: ExtractorOutput) {
        this.output = output
        track = output.track(0, C.TRACK_TYPE_AUDIO)
        output.endTracks()
    }

    override fun seek(position: Long, timeUs: Long) {
        // Header sudah dibaca → sesudah loncat langsung ke data; posisi 0 diselesaikan di read() (lompat ke awal data)
        state = if (format == null) State.HEADER else State.DATA
        startTimeUs = timeUs
        outputFrames = 0L
    }

    override fun release() = Unit

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        check(output != null && track != null) { "init() belum dipanggil" }
        return when (state) {
            State.HEADER -> {
                val head = ByteArray(12)
                input.readFully(head, 0, head.size)
                if (!AiffHeader.isAiff(head)) throw ParserException.createForMalformedContainer("Bukan berkas AIFF", null)
                aifc = AiffHeader.isAifc(head)
                state = State.CHUNKS
                Extractor.RESULT_CONTINUE
            }
            State.CHUNKS -> {
                readChunk(input)
                Extractor.RESULT_CONTINUE
            }
            State.DATA -> readData(input)
        }
    }

    private fun readChunk(input: ExtractorInput) {
        val header = ByteArray(8)
        // Berkas habis sebelum SSND ditemukan: tidak ada audio
        if (!input.readFully(header, 0, header.size, true)) {
            throw ParserException.createForMalformedContainer("Chunk SSND tidak ditemukan", null)
        }
        val id = AiffHeader.fourcc(header, 0)
        val size = AiffHeader.u32(header, 4)
        when (id) {
            "COMM" -> {
                if (size > MAX_COMM_BYTES) throw ParserException.createForMalformedContainer("Chunk COMM terlalu besar", null)
                val body = ByteArray(size.toInt())
                input.readFully(body, 0, body.size)
                if (size % 2 != 0L) input.skipFully(1)
                when (val parsed = AiffHeader.parseComm(body, body.size, aifc)) {
                    is AiffParse.Ok -> format = parsed.format
                    is AiffParse.Unsupported -> throw ParserException.createForUnsupportedContainerFeature("AIFF: ${parsed.reason}")
                    is AiffParse.Malformed -> throw ParserException.createForMalformedContainer("AIFF: ${parsed.reason}", null)
                }
            }
            "SSND" -> {
                val f = format ?: throw ParserException.createForMalformedContainer("Chunk SSND sebelum COMM", null)
                if (size < 8) throw ParserException.createForMalformedContainer("Chunk SSND terlalu pendek", null)
                val sub = ByteArray(8)
                input.readFully(sub, 0, sub.size)
                val offset = AiffHeader.u32(sub, 0)
                if (offset > size - 8) throw ParserException.createForMalformedContainer("Offset SSND di luar chunk", null)
                if (offset > 0) input.skipFully(offset.toInt())
                dataStart = input.position
                var bytes = size - 8 - offset
                // Jumlah frame di header membatasi data (ada penulis yang menambah sampah di ujung chunk)
                if (f.frames > 0) bytes = minOf(bytes, f.frames * f.bytesPerFrame)
                var end = dataStart + bytes
                val length = input.length
                if (length != C.LENGTH_UNSET.toLong() && end > length) end = length
                dataEnd = end - (end - dataStart) % f.bytesPerFrame
                start(f)
                state = State.DATA
            }
            else -> {
                var skip = size + (size and 1L)
                // Chunk besar yang dilewati (mis. gambar, ID3): sebagian-sebagian agar muat di Int
                while (skip > 0) {
                    val step = minOf(skip, Int.MAX_VALUE.toLong()).toInt()
                    input.skipFully(step)
                    skip -= step
                }
            }
        }
    }

    private fun targetBytes(f: AiffFormat): Int = maxOf(f.bytesPerFrame, f.sampleRate / TARGET_SAMPLES_PER_SECOND * f.bytesPerFrame)

    private fun start(f: AiffFormat) {
        val out = checkNotNull(output)
        val trackOut = checkNotNull(track)
        val encoding = when {
            f.float -> C.ENCODING_PCM_FLOAT
            f.bytesPerSample == 2 -> C.ENCODING_PCM_16BIT
            f.bytesPerSample == 3 -> C.ENCODING_PCM_24BIT
            else -> C.ENCODING_PCM_32BIT
        }
        val bitrate = f.sampleRate * f.bytesPerFrame * 8
        val targetBytes = targetBytes(f)
        if (scratch.size < targetBytes) scratch = ByteArray(targetBytes)
        val seekMap = AiffSeekMap(f.sampleRate, f.bytesPerFrame, dataStart, dataEnd)
        out.seekMap(seekMap)
        trackOut.format(
            Format.Builder()
                .setContainerMimeType("audio/aiff")
                .setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setAverageBitrate(bitrate)
                .setPeakBitrate(bitrate)
                .setMaxInputSize(targetBytes)
                .setChannelCount(f.channels)
                .setSampleRate(f.sampleRate)
                .setPcmEncoding(encoding)
                .build(),
        )
        trackOut.durationUs(seekMap.getDurationUs())
    }

    private fun readData(input: ExtractorInput): Int {
        val f = checkNotNull(format)
        val trackOut = checkNotNull(track)
        // Sesudah seek ke posisi 0 pembaca masih di awal berkas: lompat ke awal data
        if (input.position < dataStart) {
            input.skipFully((dataStart - input.position).toInt())
        }
        val left = dataEnd - input.position
        val frameBytes = f.bytesPerFrame
        if (left < frameBytes) return Extractor.RESULT_END_OF_INPUT
        val n = (minOf(targetBytes(f).toLong(), left).toInt() / frameBytes) * frameBytes
        if (scratch.size < n) scratch = ByteArray(n)
        if (!input.readFully(scratch, 0, n, true)) return Extractor.RESULT_END_OF_INPUT
        if (!f.littleEndian) AiffHeader.swapToLittleEndian(scratch, n, f.bytesPerSample)
        trackOut.sampleData(ParsableByteArray(scratch, n), n)
        val timeUs = startTimeUs + Util.scaleLargeTimestamp(outputFrames, C.MICROS_PER_SECOND, f.sampleRate.toLong())
        trackOut.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, n, 0, null)
        outputFrames += n / frameBytes
        return Extractor.RESULT_CONTINUE
    }

    /** Peta posisi: ukuran frame tetap, jadi waktu ↔ byte dihitung langsung. */
    private class AiffSeekMap(
        private val sampleRate: Int,
        private val bytesPerFrame: Int,
        private val dataStart: Long,
        dataEnd: Long,
    ) : SeekMap {
        private val frames = (dataEnd - dataStart) / bytesPerFrame
        private val duration = Util.scaleLargeTimestamp(frames, C.MICROS_PER_SECOND, sampleRate.toLong())

        override fun isSeekable() = true

        override fun getDurationUs() = duration

        override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
            val frame = Util.constrainValue(timeUs * sampleRate / C.MICROS_PER_SECOND, 0L, maxOf(frames - 1, 0L))
            val at = Util.scaleLargeTimestamp(frame, C.MICROS_PER_SECOND, sampleRate.toLong())
            return SeekMap.SeekPoints(SeekPoint(at, dataStart + frame * bytesPerFrame))
        }
    }

    private companion object {
        /** Satu potong sampel ≈ 100 ms (seperti WavExtractor). */
        const val TARGET_SAMPLES_PER_SECOND = 10
        const val TARGET_SAMPLE_BYTES_MIN = 4096
        const val MAX_COMM_BYTES = 1024L
    }
}
