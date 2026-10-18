package com.vinyl.launcher.fx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.random.Random

class SpectrumAnalyzerTest {
    private val fs = 44_100_000          // seperti dilaporkan Visualizer: milliHertz
    private val n = 512

    /** FFT sintetis berisi satu nada murni pada [hz]; susunan byte persis seperti dokumentasi Visualizer.getFft. */
    private fun tone(hz: Float, amp: Int = 100, size: Int = n, rateMilliHz: Int = fs): ByteArray {
        val out = ByteArray(size)
        val k = (hz * size / (rateMilliHz / 1000f)).roundToInt().coerceIn(1, size / 2 - 1)
        out[2 * k] = amp.toByte()
        return out
    }

    private fun converge(a: SpectrumAnalyzer, fft: ByteArray, frames: Int = 120, dt: Float = 0.016f, rate: Int = fs) {
        repeat(frames) { a.process(fft, rate, dt) }
    }

    private fun peakBand(a: SpectrumAnalyzer) = a.bands.indices.maxByOrNull { a.bands[it] }!!

    @Test fun aToneLightsTheBandThatContainsItsBin() {
        for (size in listOf(256, 512, 1024)) {
            val edges = SpectrumAnalyzer.binEdges(24, 44_100f, size)
            for (hz in listOf(150f, 250f, 1000f, 3000f, 8000f, 14000f)) {
                val a = SpectrumAnalyzer()
                converge(a, tone(hz, size = size))
                val bin = (hz * size / 44_100f).roundToInt().coerceIn(1, size / 2 - 1)
                val expectedBand = (0 until 24).first { bin >= edges[it] && bin < edges[it + 1] }
                assertEquals("ukuran $size, $hz Hz (bin $bin)", expectedBand, peakBand(a))
                assertTrue("pita pemenang mendekati penuh: ${a.bands[expectedBand]}", a.bands[expectedBand] > 0.9f)
            }
        }
    }

    @Test fun lowBandsDoNotLightUpInTwinsAtCoarseResolution() {
        // Regresi: dengan 512 titik (86 Hz/bin) sepuluh pita bass pertama dulu memakai bin yang sama dan menyala kembar
        for (size in listOf(256, 512, 1024)) {
            val e = SpectrumAnalyzer.binEdges(24, 44_100f, size)
            val widths = (0 until 24).map { e[it + 1] - e[it] }
            assertTrue("ukuran $size: lebar pita $widths", widths.all { it >= 1 })
            val a = SpectrumAnalyzer()
            converge(a, tone(100f, size = size))
            assertTrue("hanya satu pita yang memuat bin nada", a.bands.count { it > 0.5f } == 1)
        }
    }

    @Test fun higherTonesLightHigherBands() {
        var prev = -1
        for (hz in listOf(60f, 150f, 400f, 1200f, 4000f, 12000f)) {
            val a = SpectrumAnalyzer()
            converge(a, tone(hz))
            val b = peakBand(a)
            assertTrue("$hz Hz pita $b <= $prev", b > prev)
            prev = b
        }
    }

    @Test fun silenceStaysSilentAndQuantisationNoiseIsBelowTheFloor() {
        val a = SpectrumAnalyzer()
        converge(a, ByteArray(n))
        assertTrue(a.bands.all { it == 0f }); assertEquals(0f, a.energy, 0f); assertEquals(0f, a.bass, 0f)
        val noise = ByteArray(n) { if (it % 2 == 0) 1 else -1 }                     // satu langkah 8-bit
        converge(a, noise)
        assertTrue("derau satu LSB tidak boleh terlihat: ${a.bands.toList()}", a.bands.all { it < 0.01f })
        assertTrue(a.energy < 0.01f)
    }

    @Test fun dcAndNyquistSlotsAreIgnored() {
        val f = ByteArray(n); f[0] = 127; f[1] = 127
        val a = SpectrumAnalyzer()
        converge(a, f)
        assertTrue(a.bands.all { it == 0f })
    }

    @Test fun quietAndLoudTonesBothFillTheDisplayButSilenceIsNotAmplified() {
        val quiet = SpectrumAnalyzer(); converge(quiet, tone(1000f, amp = 8), frames = 400)
        val loud = SpectrumAnalyzer(); converge(loud, tone(1000f, amp = 120), frames = 400)
        assertTrue("pelan: ${quiet.bands.max()}", quiet.bands.max() > 0.9f)
        assertTrue("keras: ${loud.bands.max()}", loud.bands.max() > 0.9f)
        converge(quiet, ByteArray(n), frames = 400)
        assertTrue(quiet.bands.all { it < 0.01f })
    }

    @Test fun attackIsFasterThanRelease() {
        val a = SpectrumAnalyzer()
        converge(a, tone(1000f), frames = 400)                     // sampai penuh
        val full = a.bands.max()
        a.clear()
        a.process(tone(1000f), fs, SpectrumAnalyzer.ATTACK_SEC)
        val rise = a.bands.max()
        // tinggi penuh lalu hening selama durasi yang sama
        converge(a, tone(1000f), frames = 400)
        a.process(ByteArray(n), fs, SpectrumAnalyzer.ATTACK_SEC)
        val fall = full - a.bands.max()
        assertTrue("naik=$rise turun=$fall", rise > 0.5f * full && fall < 0.3f * full)
    }

    @Test fun releaseFallsSmoothlyToZero() {
        val a = SpectrumAnalyzer()
        converge(a, tone(500f), frames = 200)
        var prev = a.bands.max()
        repeat(300) {
            a.decay(0.016f)
            val now = a.bands.max()
            assertTrue(now <= prev + 1e-6f)
            prev = now
        }
        assertTrue("setelah 4,8 detik masih $prev", prev < 0.01f)
        assertTrue(a.energy < 0.01f && a.bass < 0.01f)
    }

    @Test fun bassToneDrivesBassAndEnergyMoreThanTrebleTone() {
        val bass = SpectrumAnalyzer(); converge(bass, tone(80f))
        val treble = SpectrumAnalyzer(); converge(treble, tone(8000f))
        assertTrue("bass=${bass.bass}", bass.bass > 0.2f)
        assertTrue("treble bass=${treble.bass}", treble.bass < 0.02f)
        assertTrue(bass.energy > treble.energy)
        assertTrue(bass.energy in 0f..1f && treble.energy in 0f..1f)
    }

    @Test fun invalidSizesAreRejectedAndLevelsDecay() {
        val a = SpectrumAnalyzer()
        converge(a, tone(1000f))
        val before = a.bands.max()
        for (bad in listOf(0, 1, 8, 15, 100, 300, 1000)) {
            assertFalse("ukuran $bad", a.process(ByteArray(bad), fs, 0.016f))
        }
        assertTrue(a.bands.max() < before)
        assertTrue(a.process(ByteArray(16), fs, 0.016f))
        assertTrue(a.process(ByteArray(1024), fs, 0.016f))
    }

    @Test fun randomBytesNeverProduceOutOfRangeOrNaN() {
        val rnd = Random(42)
        val a = SpectrumAnalyzer(bandCount = 32)
        repeat(300) {
            val f = ByteArray(if (it % 3 == 0) 1024 else 256).also { b -> rnd.nextBytes(b) }
            f[(it * 2) % f.size] = Byte.MIN_VALUE
            a.process(f, listOf(fs, 48_000_000, 16_000_000, 0, -1, Int.MAX_VALUE)[it % 6], listOf(0.016f, 0.3f, -1f, Float.NaN, 0f)[it % 5])
            for (v in a.bands) assertTrue("band $v", v.isFinite() && v in 0f..1f)
            assertTrue(a.energy.isFinite() && a.energy in 0f..1f)
            assertTrue(a.bass.isFinite() && a.bass in 0f..1f)
        }
    }

    @Test fun clearForgetsEverythingIncludingTheGain() {
        val a = SpectrumAnalyzer()
        converge(a, tone(1000f, amp = 120))
        a.clear()
        assertTrue(a.bands.all { it == 0f } && a.energy == 0f && a.bass == 0f)
    }

    @Test fun sampleRateIsInterpretedInMilliHertzWithSafeFallbacks() {
        assertEquals(44_100f, SpectrumAnalyzer.sampleRateHz(44_100_000), 0f)
        assertEquals(48_000f, SpectrumAnalyzer.sampleRateHz(48_000_000), 0f)
        assertEquals(16_000f, SpectrumAnalyzer.sampleRateHz(16_000_000), 0f)
        assertEquals("sudah Hz", 44_100f, SpectrumAnalyzer.sampleRateHz(44_100), 0f)
        for (bad in listOf(0, -1, 100, 7_999, 999_999 + 1 - 1, Int.MAX_VALUE, Int.MIN_VALUE, 500_000)) {
            val hz = SpectrumAnalyzer.sampleRateHz(bad)
            assertTrue("$bad -> $hz", hz in SpectrumAnalyzer.MIN_FS..SpectrumAnalyzer.MAX_FS)
        }
        assertEquals(SpectrumAnalyzer.DEFAULT_FS, SpectrumAnalyzer.sampleRateHz(0), 0f)
        assertEquals(SpectrumAnalyzer.DEFAULT_FS, SpectrumAnalyzer.sampleRateHz(Int.MAX_VALUE), 0f)
    }

    @Test fun theSampleRateDecidesWhichFrequencyABinMeans() {
        // Nada 3 kHz pada laju sampel berbeda jatuh di bin berbeda, tetapi pita pemenang selalu memuat bin itu menurut fs
        for (rate in listOf(16_000_000, 44_100_000, 48_000_000)) {
            val fsHz = rate / 1000f
            val edges = SpectrumAnalyzer.binEdges(24, fsHz, n)
            val a = SpectrumAnalyzer()
            converge(a, tone(3000f, rateMilliHz = rate), rate = rate)
            val bin = (3000f * n / fsHz).roundToInt()
            assertEquals("fs=$fsHz", (0 until 24).first { bin >= edges[it] && bin < edges[it + 1] }, peakBand(a))
        }
        // Bin yang sama, fs lebih rendah → frekuensinya lebih rendah → pita (di skala frekuensi yang sama) lebih rendah
        val hiBin = ByteArray(n).also { it[2 * 100] = 100 }
        val at48 = SpectrumAnalyzer(); converge(at48, hiBin, rate = 48_000_000)
        val at16 = SpectrumAnalyzer(); converge(at16, hiBin, rate = 16_000_000)
        val centre48 = 100 * 48_000f / n; val centre16 = 100 * 16_000f / n
        assertTrue(centre16 < centre48)
        assertTrue("fs=16k: ${peakBand(at16)}, fs=48k: ${peakBand(at48)}", peakBand(at16) <= peakBand(at48))
    }

    @Test fun binEdgesAreOrderedStayInRangeAndFollowTheLogScaleOnceBinsAreFine() {
        for (fsHz in listOf(8_000f, 16_000f, 44_100f, 48_000f, 96_000f)) for (size in listOf(16, 64, 256, 512, 1024)) for (count in listOf(1, 8, 24, 64)) {
            val e = SpectrumAnalyzer.binEdges(count, fsHz, size)
            val tag = "fs=$fsHz size=$size count=$count e=${e.toList()}"
            assertEquals(count + 1, e.size)
            assertTrue(tag, e[0] >= 1)
            assertTrue(tag, e.last() <= size / 2)
            for (i in 1 until e.size) assertTrue(tag, e[i] >= e[i - 1])
            val binHz = fsHz / size
            val topBins = (minOf(SpectrumAnalyzer.MAX_HZ, fsHz / 2 * 0.95f) / binHz).roundToInt()
            if (count < topBins) assertTrue(tag, (e.last() - 1) * binHz <= minOf(SpectrumAnalyzer.MAX_HZ, fsHz / 2 * 0.95f) + binHz)
            if (count < topBins && count <= size / 2 - 2) for (i in 1 until e.size) assertTrue("cukup bin: harus naik tegas; $tag", e[i] > e[i - 1])
        }
        // Pada resolusi halus, tepi mengikuti skala logaritmik (rasio hampir tetap pada pita atas)
        val e = SpectrumAnalyzer.binEdges(24, 44_100f, 1024)
        val r1 = e[20].toFloat() / e[19]; val r2 = e[22].toFloat() / e[21]
        assertEquals(r1, r2, 0.1f)
        assertTrue(e[0] * 44_100f / 1024 >= 40f * 0.5f)
    }

    @Test fun bandCountIsBounded() {
        for (bad in listOf(0, -1, SpectrumAnalyzer.MAX_BANDS + 1)) {
            try { SpectrumAnalyzer(bad); org.junit.Assert.fail("bandCount $bad seharusnya ditolak") } catch (_: IllegalArgumentException) {}
        }
        assertEquals(1, SpectrumAnalyzer(1).bands.size)
        val one = SpectrumAnalyzer(1); converge(one, tone(1000f)); assertTrue(one.bands[0] > 0.5f)
    }

    @Test fun levelCompensatesTheMusicTiltAboveFiveHundredHertz() {
        val low = SpectrumAnalyzer.level(0.05f, 400f)
        val high = SpectrumAnalyzer.level(0.05f, 8000f)
        assertTrue("$low vs $high", high > low)
        assertEquals(0f, SpectrumAnalyzer.level(0f, 1000f), 0f)
        assertEquals("di bawah gerbang = hening walau frekuensinya tinggi", 0f, SpectrumAnalyzer.level(SpectrumAnalyzer.GATE * 0.99f, 16000f), 0f)
        assertTrue(SpectrumAnalyzer.level(SpectrumAnalyzer.GATE, 16000f) > 0f)
        assertEquals(0f, SpectrumAnalyzer.level(Float.NaN, 1000f), 0f)
        assertEquals(0f, SpectrumAnalyzer.level(-1f, 1000f), 0f)
        assertEquals(1f, SpectrumAnalyzer.level(1f, 400f), 1e-6f)
        assertEquals(1f, SpectrumAnalyzer.level(9f, 400f), 0f)
    }

    // ---------------------------------------------------------------- animasi dekoratif & napas

    @Test fun decorativeIsSilentUnlessPlaying() {
        val out = FloatArray(24) { 0.5f }
        assertEquals(0f, DecorativeSpectrum.fill(1.2f, false, out), 0f)
        assertTrue(out.all { it == 0f })
        out.fill(0.5f)
        assertEquals(0f, DecorativeSpectrum.fill(Float.NaN, true, out), 0f)
        assertTrue(out.all { it == 0f })
    }

    @Test fun decorativeStaysInRangeMovesAndIsDeterministic() {
        val a = FloatArray(24); val b = FloatArray(24)
        val seen = HashSet<Float>()
        for (i in 0..2000) {
            val t = i * 0.016f
            val ea = DecorativeSpectrum.fill(t, true, a)
            val eb = DecorativeSpectrum.fill(t, true, b)
            assertEquals(a.toList(), b.toList()); assertEquals(ea, eb, 0f)
            assertTrue(ea in 0f..1f)
            for (v in a) assertTrue("v=$v", v.isFinite() && v in 0f..1f)
            seen.add(a[3])
        }
        assertTrue("tidak bergerak", seen.size > 100)
    }

    @Test fun decorativeKicksOnTheBeatAndFadesBeforeTheNext() {
        val a = FloatArray(24); val b = FloatArray(24)
        val period = 1f / DecorativeSpectrum.BEAT_HZ
        DecorativeSpectrum.fill(period * 3f + 0.001f, true, a)           // tepat di awal denyut
        DecorativeSpectrum.fill(period * 3f + period * 0.8f, true, b)    // menjelang denyut berikut
        assertTrue("bass di denyut ${a[0]} vs sebelum denyut ${b[0]}", a[0] > b[0] + 0.15f)
    }

    @Test fun decorativeHandlesTinyArrays() {
        assertEquals(0f, DecorativeSpectrum.fill(1f, true, FloatArray(0)), 1f)
        val one = FloatArray(1); DecorativeSpectrum.fill(1f, true, one); assertTrue(one[0] in 0f..1f)
    }

    @Test fun aQuietSignalIsNotAmplifiedToFullScaleEvenAfterTheGainHasRelaxed() {
        // Nada pelan di atas ambang hening, pada pita selebar satu bin (tanpa kompensasi kemiringan: < 500 Hz)
        val edges = SpectrumAnalyzer.binEdges(24, 44_100f, n)
        val k = 2
        val band = (0 until 24).first { k >= edges[it] && k < edges[it + 1] }
        assertEquals("prasyarat: pita selebar satu bin", 1, edges[band + 1] - edges[band])
        val a = SpectrumAnalyzer()
        // 30 detik: penguatan otomatis sempat meluruh jauh di bawah lantai MIN_PEAK bila lantainya tidak dijaga
        converge(a, tone(150f, amp = 4), frames = 300, dt = 0.1f)
        val v = a.bands[band]
        assertTrue("pelan tetap tampak pelan, bukan penuh: $v", v in 0.7f..0.95f)
    }

    @Test fun theImaginaryPartCountsTowardsMagnitude() {
        val edges = SpectrumAnalyzer.binEdges(24, 44_100f, n)
        val k = 2
        val band = (0 until 24).first { k >= edges[it] && k < edges[it + 1] }
        assertEquals("prasyarat: pita selebar satu bin", 1, edges[band + 1] - edges[band])
        fun lit(re: Int, im: Int): Float {
            val a = SpectrumAnalyzer()
            val f = ByteArray(n)
            f[2 * k] = re.toByte(); f[2 * k + 1] = im.toByte()
            converge(a, f)
            return a.bands[band]
        }
        assertTrue("energi di bagian imajiner harus terbaca: ${lit(0, 4)}", lit(0, 4) > 0.5f)
        assertEquals("magnitudo sama = tingkat sama", lit(4, 0), lit(0, 4), 1e-6f)
        assertEquals("3-4-5: magnitudo kompleks, bukan jumlah", lit(5, 0), lit(3, 4), 1e-3f)
    }
}
