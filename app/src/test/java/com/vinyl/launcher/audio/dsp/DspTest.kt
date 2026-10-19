package com.vinyl.launcher.audio.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class DspTest {
    private val fs = 48_000.0

    @Test
    fun peakingFilterHitsItsGainAtTheCenterAndLeavesFarFrequenciesAlone() {
        val c = BiquadCoefs.design(EqBand(FilterType.PEAK, 1000f, 6f, 1.41f), fs)
        assertEquals(6.0, c.magnitudeDb(1000.0, fs), 0.01)
        assertEquals(0.0, c.magnitudeDb(30.0, fs), 0.1)
        assertEquals(0.0, c.magnitudeDb(18_000.0, fs), 0.1)
        assertTrue(c.stable)
    }

    @Test
    fun shelvesAndPassesHaveTheTextbookShape() {
        val low = BiquadCoefs.design(EqBand(FilterType.LOW_SHELF, 105f, 6f, 0.7f), fs)
        assertEquals(6.0, low.magnitudeDb(15.0, fs), 0.3)
        assertEquals(0.0, low.magnitudeDb(5000.0, fs), 0.05)
        val high = BiquadCoefs.design(EqBand(FilterType.HIGH_SHELF, 10_000f, -3f, 0.7f), fs)
        assertEquals(-3.0, high.magnitudeDb(20_000.0, fs), 0.4)
        assertEquals(0.0, high.magnitudeDb(200.0, fs), 0.05)
        val lp = BiquadCoefs.design(EqBand(FilterType.LOW_PASS, 2000f, 0f, 0.7071f), fs)
        assertEquals(-3.01, lp.magnitudeDb(2000.0, fs), 0.05)
        val hp = BiquadCoefs.design(EqBand(FilterType.HIGH_PASS, 80f, 0f, 0.7071f), fs)
        assertEquals(-3.01, hp.magnitudeDb(80.0, fs), 0.05)
        assertTrue(hp.magnitudeDb(20.0, fs) < -20.0)
    }

    @Test
    fun filtersStayStableAtEveryAllowedSettingAndSampleRate() {
        for (rate in listOf(8000.0, 44_100.0, 48_000.0, 96_000.0, 192_000.0, 384_000.0)) for (type in FilterType.entries)
            for (f in listOf(10f, 20f, 1000f, 20_000f, 22_000f)) for (q in listOf(0.1f, 0.707f, 20f)) for (g in listOf(-24f, 6f, 24f)) {
                val c = BiquadCoefs.design(EqBand(type, f, g, q), rate)
                assertTrue("$rate $type $f $q $g", c.stable)
            }
        // Nilai liar dibatasi, bukan merusak filter
        assertTrue(BiquadCoefs.design(EqBand(FilterType.PEAK, Float.NaN, Float.NaN, Float.NaN), fs).stable)
    }

    private fun sine(freq: Double, frames: Int, amp: Float, channels: Int = 2): FloatArray =
        FloatArray(frames * channels) { i -> (amp * sin(2 * PI * freq * (i / channels) / fs)).toFloat() }

    private fun rms(buf: FloatArray, from: Int, to: Int, channels: Int = 2, ch: Int = 0): Double {
        var s = 0.0
        var n = 0
        var i = from * channels + ch
        while (i < to * channels) { s += buf[i] * buf[i].toDouble(); n++; i += channels }
        return sqrt(s / n)
    }

    @Test
    fun cascadeProcessesARealSignalWithTheDesignedGain() {
        val buf = sine(1000.0, 48_000, 0.25f)
        val before = rms(buf, 24_000, 48_000)
        BiquadCascade(listOf(BiquadCoefs.design(EqBand(FilterType.PEAK, 1000f, 6f, 1f), fs)), 2).process(buf, 48_000)
        val gainDb = 20 * log10(rms(buf, 24_000, 48_000) / before)
        assertEquals(6.0, gainDb, 0.05)
        // Kanal kanan diproses sama
        assertEquals(rms(buf, 24_000, 48_000, ch = 0), rms(buf, 24_000, 48_000, ch = 1), 1e-6)
    }

    @Test
    fun flatSettingsLeaveSamplesBitIdentical() {
        val chain = DspChain(48_000, 2, DspPlan.config(0f, ParametricProfile(bands = listOf(EqBand(gainDb = 0f))), true, null))
        assertTrue(chain.isIdentity)
        val buf = sine(440.0, 1000, 0.9f)
        val copy = buf.copyOf()
        chain.process(buf, 1000)
        assertTrue(buf.contentEquals(copy))
    }

    @Test
    fun crossfeedKeepsMonoAtUnityAndFeedsTheOppositeChannelAtItsLevel() {
        val frames = 48_000
        // DC mono: tetap 1
        val mono = FloatArray(frames * 2) { 0.5f }
        Crossfeed(CrossfeedLevel.NATURAL, 48_000).process(mono, frames)
        assertEquals(0.5f, mono[frames * 2 - 2], 1e-3f)
        assertEquals(0.5f, mono[frames * 2 - 1], 1e-3f)
        // DC hanya di kiri: kanal kanan menerima −4,5 dB relatif kanal kiri
        val hard = FloatArray(frames * 2) { if (it % 2 == 0) 0.5f else 0f }
        Crossfeed(CrossfeedLevel.NATURAL, 48_000).process(hard, frames)
        val ratioDb = 20 * log10(hard[frames * 2 - 1] / hard[frames * 2 - 2].toDouble())
        assertEquals(-4.5, ratioDb, 0.05)
        // Treble (8 kHz) hampir tidak menyeberang
        val hi = FloatArray(frames * 2) { i -> if (i % 2 == 0) (0.5 * sin(2 * PI * 8000 * (i / 2) / fs)).toFloat() else 0f }
        Crossfeed(CrossfeedLevel.NATURAL, 48_000).process(hi, frames)
        assertTrue(20 * log10(rms(hi, 24_000, 48_000, ch = 1) / rms(hi, 24_000, 48_000, ch = 0)) < -15.0)
    }

    @Test
    fun limiterNeverLetsPeaksPastTheThresholdAndIsTransparentBelowIt() {
        val lim = PeakLimiter(48_000, 2)
        val loud = sine(100.0, 48_000, 2.0f)
        lim.process(loud, 48_000)
        val thr = Math.pow(10.0, -0.3 / 20).toFloat()
        assertTrue(loud.all { abs(it) <= thr + 1e-6f })
        assertTrue(lim.maxReductionDb < -5f)
        // Sinyal pelan: keluar sama persis, tertunda sebesar lookahead
        val quiet = PeakLimiter(48_000, 2)
        val buf = sine(440.0, 4800, 0.5f)
        val copy = buf.copyOf()
        quiet.process(buf, 4800)
        val lag = quiet.latencyFrames
        for (f in lag until 4800) {
            assertEquals(copy[(f - lag) * 2], buf[f * 2], 1e-6f)
        }
        assertEquals(0f, quiet.maxReductionDb)
    }

    @Test
    fun chainDrainsTheLimiterTailSoNoAudioIsLost() {
        val cfg = DspConfig(gainDb = 3f, limiter = true)
        val chain = DspChain(48_000, 2, cfg)
        val buf = FloatArray(2000 * 2) { 0.1f }
        chain.process(buf, 2000)
        val tail = FloatArray(1024 * 2)
        val n = chain.drain(tail)
        assertEquals(chain.latencyFrames, n)
        // Ekor berisi sisa sinyal 0,1 × +3 dB, bukan hening
        assertEquals(0.1f * ReplayGain.linear(3f), tail[0], 1e-4f)
    }

    @Test
    fun gaplessRetuneChangesOnlyTheGainAndKeepsFilterStateRunning() {
        // EQ hanya memotong & ReplayGain negatif → tanpa pembatas, jadi rantai linear dan bisa dibandingkan persis
        val bands = listOf(EqBand(FilterType.PEAK, 200f, -6f, 1.0f), EqBand(FilterType.HIGH_SHELF, 6000f, -3f, 0.7f))
        val first = DspConfig(gainDb = -2f, eqBands = bands)
        val next = DspConfig(gainDb = -7f, eqBands = bands)
        assertFalse(DspPlan.limiterNeeded(first.gainDb, bands) || DspPlan.limiterNeeded(next.gainDb, bands))
        fun tone(from: Int, n: Int) = FloatArray(n * 2) { i -> (0.5 * sin(2 * PI * 150.0 * (from + i / 2) / fs)).toFloat() }

        val kept = DspChain(48_000, 2, first)
        val reference = DspChain(48_000, 2, first)
        val a1 = tone(0, 512); kept.process(a1, 512)
        val b1 = tone(0, 512); reference.process(b1, 512)
        // Lagu berikutnya: hanya penguatan yang berbeda → rantai yang sama diteruskan
        assertTrue(kept.retune(48_000, 2, next))
        assertEquals(next, kept.config)
        // Pembanding tanpa retune menerima masukan yang sudah dikali selisih penguatannya: hasilnya harus identik,
        // artinya status filter tidak dikosongkan di sambungan (tidak ada klik)
        val a2 = tone(512, 512); kept.process(a2, 512)
        val step = ReplayGain.linear(-5f)
        val b2 = tone(512, 512).map { it * step }.toFloatArray(); reference.process(b2, 512)
        for (i in a2.indices) assertEquals(b2[i], a2[i], 1e-5f)
        // Rantai baru (status nol) berbeda nyata pada sampel pertama sambungan
        val fresh = DspChain(48_000, 2, next)
        val c2 = tone(512, 512); fresh.process(c2, 512)
        assertTrue(abs(c2[0] - a2[0]) > 1e-3f)

        // Susunan lain tidak bisa diteruskan
        assertFalse(kept.retune(44_100, 2, next))
        assertFalse(kept.retune(48_000, 1, next))
        assertFalse(kept.retune(48_000, 2, next.copy(eqBands = bands.take(1))))
        assertFalse(kept.retune(48_000, 2, next.copy(limiter = true)))
        assertFalse(kept.retune(48_000, 2, next.copy(crossfeed = CrossfeedLevel.NATURAL)))
        assertEquals(next, kept.config)
    }

    @Test
    fun replayGainParsesTagsInTheirCommonSpellings() {
        assertEquals(-7.21f, ReplayGain.parseGain("-7.21 dB")!!, 1e-4f)
        assertEquals(-7.21f, ReplayGain.parseGain("−7,21 dB")!!, 1e-4f)
        assertEquals(1.5f, ReplayGain.parseGain("+1.5")!!, 1e-4f)
        assertNull(ReplayGain.parseGain("loud"))
        assertNull(ReplayGain.parseGain("-99 dB"))
        assertEquals(0.988f, ReplayGain.parsePeak("0.988")!!, 1e-6f)
        assertNull(ReplayGain.parsePeak("-1"))
        // R128 Q7.8 relatif −23 LUFS → acuan ReplayGain (+5 dB)
        assertEquals(-2f + 5f, ReplayGain.parseR128("-512")!!, 1e-4f)
        val tags = ReplayGain.fromTags(listOf("replaygain_track_gain" to "-6.00 dB", "REPLAYGAIN_TRACK_PEAK" to "0.5", "REPLAYGAIN_ALBUM_GAIN" to "-8 dB"))
        assertEquals(-6f, tags.trackGainDb!!, 1e-4f)
        assertEquals(-8f, tags.albumGainDb!!, 1e-4f)
    }

    @Test
    fun replayGainPicksTheRightValueAndNeverClipsWhenAsked() {
        val tags = ReplayGainTags(trackGainDb = 4f, trackPeak = 0.9f, albumGainDb = -2f, albumPeak = 1.0f)
        assertEquals(-2f, ReplayGain.gainDb(tags, ReplayGainSettings(mode = ReplayGainMode.ALBUM)), 1e-4f)
        // +4 dB × puncak 0,9 akan clipping → dibatasi ke 20·log10(1/0,9) ≈ 0,915 dB
        assertEquals(0.915f, ReplayGain.gainDb(tags, ReplayGainSettings(mode = ReplayGainMode.TRACK)), 1e-3f)
        assertEquals(4f, ReplayGain.gainDb(tags, ReplayGainSettings(mode = ReplayGainMode.TRACK, preventClipping = false)), 1e-4f)
        assertEquals(0f, ReplayGain.gainDb(tags, ReplayGainSettings(mode = ReplayGainMode.OFF)), 0f)
        // Album tanpa nilai album → nilai lagu; tanpa tag sama sekali → penguatan berkas tanpa tag
        assertEquals(0.915f, ReplayGain.gainDb(tags.copy(albumGainDb = null), ReplayGainSettings(mode = ReplayGainMode.ALBUM)), 1e-3f)
        assertEquals(-6f, ReplayGain.gainDb(ReplayGainTags(), ReplayGainSettings()), 0f)
    }

    @Test
    fun autoEqParametricFilesImportWithPreampTypesAndBandwidths() {
        val text = """
            # contoh buatan
            Preamp: -6.4 dB
            Filter 1: ON LSC Fc 105 Hz Gain 6.2 dB Q 0.70
            Filter 2: ON PK Fc 2245 Hz Gain -3,1 dB Q 1.20
            Filter 3: OFF PK Fc 5000 Hz Gain 2 dB Q 4
            Filter 4: ON HSC Fc 10000 Hz Gain -2.0 dB Q 0.70
            Filter 5: ON PK Fc 300 Hz Gain 1 dB BW Oct 1
            Filter 6: ON XYZ Fc 300 Hz Gain 1 dB Q 1
        """.trimIndent()
        val r = AutoEqImport.parse(text, "Headphone uji")
        assertNotNull(r)
        r!!
        assertEquals(-6.4f, r.profile.preampDb, 1e-4f)
        assertEquals(5, r.profile.bands.size)
        assertEquals(FilterType.LOW_SHELF, r.profile.bands[0].type)
        assertEquals(-3.1f, r.profile.bands[1].gainDb, 1e-4f) // "-3,1" ditulis dengan koma desimal
        assertFalse(r.profile.bands[2].enabled)
        assertEquals(1.414f, r.profile.bands[4].q, 0.01f) // 1 oktaf ≈ Q 1,41
        assertEquals(1, r.skipped.size)
        // Ekspor lalu impor ulang = sama
        val again = AutoEqImport.parse(AutoEqImport.export(r.profile), "Headphone uji")!!.profile
        assertEquals(r.profile.bands.size, again.bands.size)
        assertEquals(r.profile.bands[3].gainDb, again.bands[3].gainDb, 0.05f)
        assertNull(AutoEqImport.parse("bukan berkas EQ", "x"))
    }

    @Test
    fun autoHeadroomLowersThePreampByTheLargestBoost() {
        val p = ParametricProfile(preampDb = 0f, bands = listOf(EqBand(FilterType.PEAK, 60f, 6f, 1f)))
        assertEquals(-6f, EqMath.effectivePreampDb(p, autoHeadroom = true), 0.1f)
        assertEquals(0f, EqMath.effectivePreampDb(p, autoHeadroom = false), 0f)
        // Preamp pengguna yang lebih rendah dipertahankan
        assertEquals(-9f, EqMath.effectivePreampDb(p.copy(preampDb = -9f), autoHeadroom = true), 0f)
        // Rantai dengan headroom otomatis tidak perlu pembatas; tanpa headroom perlu
        assertFalse(DspPlan.config(0f, p, autoHeadroom = true, crossfeed = null).limiter)
        assertTrue(DspPlan.config(0f, p, autoHeadroom = false, crossfeed = null).limiter)
        assertTrue(DspPlan.config(3f, null, autoHeadroom = true, crossfeed = null).limiter)
    }

    @Test
    fun pcmConversionsAreExactForEveryIntegerWidth() {
        val b16 = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putShort(Short.MIN_VALUE).putShort(16384).flip() as ByteBuffer
        val out = FloatArray(8)
        assertEquals(2, Pcm.read(b16, PcmFormat.S16, out))
        assertEquals(-1f, out[0], 0f)
        assertEquals(0.5f, out[1], 0f)
        // 24-bit: −8388608 = −1, 4194304 = 0,5 (urutan byte kecil-dulu)
        val b24 = ByteBuffer.wrap(byteArrayOf(0, 0, 0x80.toByte(), 0, 0, 0x40))
        assertEquals(2, Pcm.read(b24, PcmFormat.S24, out))
        assertEquals(-1f, out[0], 0f)
        assertEquals(0.5f, out[1], 0f)
        val b32 = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(1 shl 30).flip() as ByteBuffer
        assertEquals(1, Pcm.read(b32, PcmFormat.S32, out))
        assertEquals(0.5f, out[0], 0f)
        val f = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        Pcm.writeFloat(floatArrayOf(0.25f, -0.75f), 2, f)
        f.flip()
        assertEquals(2, Pcm.read(f, PcmFormat.F32, out))
        assertEquals(-0.75f, out[1], 0f)
        // Big-endian (AIFF)
        val be = ByteBuffer.wrap(byteArrayOf(0x40, 0, 0x80.toByte(), 0, 0, 0x40, 0, 0))
        val one = FloatArray(1)
        assertEquals(1, Pcm.read(be, PcmFormat.S16BE, one))
        assertEquals(0.5f, one[0], 0f)
        assertEquals(1, Pcm.read(be, PcmFormat.S24BE, one))
        assertEquals(-1f, one[0], 0f)
        // 16-bit tanpa olahan kembali persis; dengan dither selisihnya paling banyak 1 LSB
        val src = shortArrayOf(-32768, -1, 0, 1, 12345, 32767)
        val f16 = FloatArray(src.size) { src[it] / 32768f }
        val o = ByteBuffer.allocate(src.size * 2)
        Pcm.writeS16(f16, src.size, o)
        o.flip()
        for (v in src) assertEquals(v, o.order(ByteOrder.LITTLE_ENDIAN).short)
        val od = ByteBuffer.allocate(src.size * 2)
        Pcm.writeS16Dithered(f16, src.size, od, 7)
        od.flip()
        for (v in src) assertTrue(abs(od.order(ByteOrder.LITTLE_ENDIAN).short - v) <= 2)
        // Setiap nilai 24-bit selamat melewati float (mantisa 24 bit)
        for (v in listOf(-8388608, -1, 0, 1, 1234567, 8388607)) {
            val bb = ByteBuffer.wrap(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte()))
            Pcm.read(bb, PcmFormat.S24, out)
            assertEquals(v.toDouble(), out[0] * 8388608.0, 0.0)
        }
    }

    @Test
    fun editorScaleIsLogarithmicAndLabelsReadLikeEqualizerApo() {
        assertEquals(0f, EqEditor.freqToSlider(20f), 1e-6f)
        assertEquals(1f, EqEditor.freqToSlider(20_000f), 1e-6f)
        assertEquals(0.5f, EqEditor.freqToSlider(632.5f), 0.01f)
        assertEquals(1000f, EqEditor.sliderToFreq(EqEditor.freqToSlider(1000f)), 0.5f)
        assertEquals(1200f, EqEditor.sliderToFreq(EqEditor.freqToSlider(1234f)), 0.5f)
        assertEquals("PK 2,2 kHz −3,4 dB Q 1,29", EqEditor.label(EqBand(FilterType.PEAK, 2245f, -3.4f, 1.29f)))
        assertEquals("LS 105 Hz +6,0 dB Q 0,70", EqEditor.label(EqBand(FilterType.LOW_SHELF, 105f, 6f, 0.7f)))
        assertEquals("HP 80 Hz Q 0,71", EqEditor.label(EqBand(FilterType.HIGH_PASS, 80f, 0f, 0.707f)))
        assertEquals("1 kHz", EqEditor.freqText(1000f))
        // Pita baru tidak menumpuk di frekuensi yang sudah dipakai
        assertEquals(250f, EqEditor.newBand(listOf(EqBand(freq = 1000f))).freq)
    }
}
