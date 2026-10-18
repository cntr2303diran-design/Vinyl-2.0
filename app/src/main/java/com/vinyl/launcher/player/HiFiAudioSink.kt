package com.vinyl.launcher.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.vinyl.launcher.audio.dsp.DspChain
import com.vinyl.launcher.audio.dsp.DspPlan
import com.vinyl.launcher.audio.dsp.Pcm
import com.vinyl.launcher.audio.dsp.PcmFormat
import com.vinyl.launcher.audio.dsp.ReplayGain
import com.vinyl.launcher.audio.dsp.ReplayGainMode
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Sink audio pemutar bawaan: menerima PCM hasil dekoder (16/24/32-bit atau float), mengubahnya ke float 32-bit,
 * menjalankan rantai suara ([DspChain]: ReplayGain, EQ parametrik, crossfeed, pembatas), lalu meneruskan float ke
 * DefaultAudioSink (AudioTrack float, tanpa pemotongan ke 16-bit) — atau 16-bit ber-dither bila keluaran resolusi tinggi
 * dimatikan. Saat rantai tidak mengubah apa pun, data diteruskan apa adanya (bit-transparan sampai mixer Android).
 *
 * Kontrak AudioSink dipatuhi: buffer masukan dianggap selesai hanya setelah seluruh hasil olahannya diterima sink di
 * belakangnya; sisa olahan disimpan dan dikirim pada panggilan berikutnya.
 */
@OptIn(UnstableApi::class)
class HiFiAudioSink(sink: AudioSink, private val usbRate: UsbRateMatch? = null) : ForwardingAudioSink(sink) {

    private var inputPcm: PcmFormat? = null
    private var channels = 2
    private var sampleRate = 48_000
    private var inputFormat: Format? = null
    private var outputFloat = true
    private var chain: DspChain? = null
    private var chainVersion = -1
    private var gainDb = 0f

    private var scratch = FloatArray(0)
    /** Buffer dekoder yang sedang diteruskan apa adanya dan belum habis diterima sink di belakang. */
    private var direct: ByteBuffer? = null
    private var pending: ByteBuffer? = null
    private var pendingUs = 0L
    private var nextUs = 0L
    private var tailQueued = false
    private var ditherSeed = 0x2545F491

    override fun supportsFormat(format: Format): Boolean = getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun getFormatSupport(format: Format): Int {
        // Semua PCM yang bisa diolah rantai diterima langsung; dengan float diminta, dekoder FLAC/ALAC/MP3/AAC
        // mengeluarkan float (24-bit tetap utuh)
        if (MimeTypes.AUDIO_RAW == format.sampleMimeType && pcmOf(format.pcmEncoding) != null) {
            return AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        }
        return super.getFormatSupport(format)
    }

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        pending = null
        direct = null
        tailQueued = false
        val pcm = if (MimeTypes.AUDIO_RAW == inputFormat.sampleMimeType) pcmOf(inputFormat.pcmEncoding) else null
        if (pcm == null) {
            // Bukan PCM (passthrough/offload): diteruskan tanpa olahan
            this.inputPcm = null
            chain = null
            super.configure(inputFormat, specifiedBufferSize, outputChannels)
            HiFi.updatePath { it.copy(decoded = "passthrough", output = "passthrough", transparent = true) }
            return
        }
        this.inputPcm = pcm
        this.inputFormat = inputFormat
        channels = inputFormat.channelCount.coerceAtLeast(1)
        sampleRate = inputFormat.sampleRate.coerceAtLeast(8_000)
        val dsp = HiFi.dsp
        outputFloat = dsp.floatOutput
        gainDb = replayGainDb(inputFormat.metadata, dsp)
        rebuildChain(dsp)
        // Mixer USB disiapkan SEBELUM AudioTrack untuk format ini dibuka
        usbRate?.let { u -> u.prepare(sampleRate).let { status -> HiFi.updatePath { it.copy(usbRate = status) } } }
        val out = inputFormat.buildUpon().setPcmEncoding(if (outputFloat) C.ENCODING_PCM_FLOAT else C.ENCODING_PCM_16BIT).build()
        super.configure(out, 0, outputChannels)
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        val pcm = inputPcm ?: return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        // Buffer yang sudah mulai diteruskan apa adanya harus diselesaikan dengan cara yang sama (kontrak AudioSink)
        if (direct === buffer) return passDirect(buffer, presentationTimeUs, encodedAccessUnitCount)
        // 1. Kirim dulu sisa olahan buffer sebelumnya
        if (!drainPending()) return false
        if (!buffer.hasRemaining()) return true
        if (HiFi.dsp.version != chainVersion) rebuildChain(HiFi.dsp)
        val c = chain ?: return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        val frameBytes = pcm.bytes * channels
        val frames = buffer.remaining() / frameBytes
        if (frames == 0) {
            buffer.position(buffer.limit())
            return true
        }
        nextUs = presentationTimeUs + frames * 1_000_000L / sampleRate
        // 2. Tanpa olahan dan format sama dengan keluaran: teruskan buffer aslinya (bit-transparan)
        val sameFormat = (outputFloat && pcm == PcmFormat.F32) || (!outputFloat && pcm == PcmFormat.S16)
        if (c.isIdentity && sameFormat) return passDirect(buffer, presentationTimeUs, encodedAccessUnitCount)
        // 3. Olah ke float, jalankan rantai, tulis hasilnya ke buffer tertunda
        val samples = frames * channels
        if (scratch.size < samples) scratch = FloatArray(samples)
        Pcm.read(buffer, pcm, scratch)
        buffer.position(buffer.limit())
        c.process(scratch, frames)
        val out = output(samples)
        writeOut(samples, out, transparentSource = c.isIdentity)
        out.flip()
        pendingUs = presentationTimeUs
        return drainPending()
    }

    private fun passDirect(buffer: ByteBuffer, presentationTimeUs: Long, count: Int): Boolean {
        val done = super.handleBuffer(buffer, presentationTimeUs, count)
        direct = if (done) null else buffer
        return done
    }

    /** Sisa olahan terkirim semua? */
    private fun drainPending(): Boolean {
        val p = pending ?: return true
        if (!p.hasRemaining()) return true
        super.handleBuffer(p, pendingUs, 1)
        return !p.hasRemaining()
    }

    private fun output(samples: Int): ByteBuffer {
        val bytes = samples * (if (outputFloat) 4 else 2)
        val cur = pending
        val b = if (cur != null && cur.capacity() >= bytes) cur else ByteBuffer.allocateDirect(maxOf(bytes, 16 * 1024)).order(ByteOrder.nativeOrder())
        b.clear()
        pending = b
        return b
    }

    private fun writeOut(samples: Int, out: ByteBuffer, transparentSource: Boolean) {
        if (outputFloat) {
            Pcm.writeFloat(scratch, samples, out)
        } else if (transparentSource && inputPcm == PcmFormat.S16) {
            Pcm.writeS16(scratch, samples, out)
        } else {
            ditherSeed = Pcm.writeS16Dithered(scratch, samples, out, ditherSeed)
        }
    }

    override fun playToEndOfStream() {
        if (inputPcm != null) {
            if (!drainPending()) return
            if (!tailQueued) {
                tailQueued = true
                val c = chain
                if (c != null && c.latencyFrames > 0) {
                    val samples = c.latencyFrames * channels
                    if (scratch.size < samples) scratch = FloatArray(samples)
                    val frames = c.drain(scratch)
                    val out = output(frames * channels)
                    writeOut(frames * channels, out, transparentSource = false)
                    out.flip()
                    pendingUs = nextUs
                    if (!drainPending()) return
                }
            }
        }
        super.playToEndOfStream()
    }

    override fun isEnded(): Boolean = (pending?.hasRemaining() != true) && super.isEnded()

    override fun hasPendingData(): Boolean = (pending?.hasRemaining() == true) || super.hasPendingData()

    override fun flush() {
        pending = null
        direct = null
        tailQueued = false
        chain?.reset()
        super.flush()
    }

    override fun reset() {
        pending = null
        direct = null
        tailQueued = false
        chain = null
        chainVersion = -1
        super.reset()
    }

    private fun rebuildChain(dsp: HiFi.Dsp) {
        chainVersion = dsp.version
        val pcm = inputPcm ?: return
        // Keluaran float/16-bit dipilih saat configure; perubahan sakelar berlaku di lagu berikutnya
        val cfg = DspPlan.config(gainDb, dsp.eq, dsp.autoHeadroom, dsp.crossfeed)
        val c = DspChain(sampleRate, channels, cfg)
        chain = c
        HiFi.updatePath {
            it.copy(
                decoded = describe(pcm),
                output = if (outputFloat) "float 32-bit" else if (c.isIdentity && pcm == PcmFormat.S16) "16-bit" else "16-bit + dither",
                replayGainDb = gainDb.takeIf { dsp.replayGain.mode != ReplayGainMode.OFF },
                eqName = dsp.eq?.name,
                crossfeed = cfg.crossfeed?.takeIf { channels == 2 },
                limiter = cfg.limiter,
                transparent = c.isIdentity,
            )
        }
    }

    private fun replayGainDb(metadata: Metadata?, dsp: HiFi.Dsp): Float {
        if (dsp.replayGain.mode == ReplayGainMode.OFF) return 0f
        return ReplayGain.gainDb(ReplayGain.fromTags(tagPairs(metadata)), dsp.replayGain)
    }

    companion object {
        fun pcmOf(encoding: Int): PcmFormat? = when (encoding) {
            C.ENCODING_PCM_8BIT -> PcmFormat.U8
            C.ENCODING_PCM_16BIT -> PcmFormat.S16
            C.ENCODING_PCM_24BIT -> PcmFormat.S24
            C.ENCODING_PCM_32BIT -> PcmFormat.S32
            C.ENCODING_PCM_FLOAT -> PcmFormat.F32
            C.ENCODING_PCM_16BIT_BIG_ENDIAN -> PcmFormat.S16BE
            C.ENCODING_PCM_24BIT_BIG_ENDIAN -> PcmFormat.S24BE
            C.ENCODING_PCM_32BIT_BIG_ENDIAN -> PcmFormat.S32BE
            else -> null
        }

        fun describe(p: PcmFormat): String = if (p == PcmFormat.F32) "float 32-bit" else "PCM ${p.bits}-bit"

        /** Tag kunci→nilai dari metadata berkas: VorbisComment (FLAC/Ogg/Opus), TXXX (MP3), atom "----" iTunes (M4A). */
        fun tagPairs(metadata: Metadata?): List<Pair<String, String>> {
            if (metadata == null) return emptyList()
            val out = ArrayList<Pair<String, String>>()
            for (i in 0 until metadata.length()) {
                when (val e = metadata.get(i)) {
                    is VorbisComment -> out += e.key to e.value
                    is TextInformationFrame -> {
                        val d = e.description
                        val v = e.values.firstOrNull()
                        if (e.id == "TXXX" && d != null && v != null) out += d to v
                    }
                    is InternalFrame -> out += e.description to e.text
                }
            }
            return out
        }
    }
}
