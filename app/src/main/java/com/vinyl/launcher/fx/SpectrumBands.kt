package com.vinyl.launcher.fx

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Mengubah data FFT mentah dari android.media.audiofx.Visualizer menjadi pita-pita spektrum berjarak logaritmik
 * (telinga mendengar frekuensi secara logaritmik) yang sudah dihaluskan untuk digambar. Murni, bisa diuji.
 *
 * Format byte FFT (dokumentasi Visualizer.getFft): `[Rf0, Rf(n/2), Rf1, If1, Rf2, If2, …]` — elemen 0 = bagian riil
 * DC, elemen 1 = bagian riil frekuensi Nyquist, lalu pasangan (riil, imajiner) bertanda 8-bit untuk bin 1..n/2-1.
 * Frekuensi bin k = k · fs / n, dengan n = ukuran tangkap (panjang larik) dan fs = laju sampel (Hz).
 *
 * Tidak thread-safe: panggil dari satu thread (di aplikasi: loop frame di thread utama, membaca data terbaru yang
 * diletakkan callback Visualizer secara @Volatile).
 */
class SpectrumAnalyzer(val bandCount: Int = DEFAULT_BANDS) {
    init { require(bandCount in 1..MAX_BANDS) { "bandCount di luar 1..$MAX_BANDS: $bandCount" } }

    /** Tingkat tiap pita 0..1, sudah dihaluskan. Larik ini dipakai ulang — salin bila perlu disimpan. */
    val bands = FloatArray(bandCount)
    /** Tingkat bass (di bawah [BASS_HZ]) 0..1. */
    var bass = 0f
        private set
    /** Energi keseluruhan 0..1 (campuran bass dan rata-rata), untuk menggerakkan cahaya & partikel. */
    var energy = 0f
        private set

    private val raw = FloatArray(bandCount)
    private var peak = MIN_PEAK

    // Tata letak pita untuk (ukuran tangkap, laju sampel) terakhir; dihitung ulang hanya bila keduanya berubah
    private var cfgSize = 0
    private var cfgFs = 0f
    private var binEdges = IntArray(bandCount + 1)
    private val centres = FloatArray(bandCount)

    init { configure(DEFAULT_SIZE, DEFAULT_FS) }

    private fun configure(size: Int, fs: Float) {
        if (size == cfgSize && fs == cfgFs) return
        cfgSize = size
        cfgFs = fs
        binEdges = binEdges(bandCount, fs, size)
        val binHz = fs / size
        val maxBin = size / 2 - 1
        for (b in 0 until bandCount) {
            val k0 = binEdges[b].coerceAtMost(maxBin)
            val k1 = (binEdges[b + 1] - 1).coerceIn(k0, maxBin)
            centres[b] = (k0 + k1) / 2f * binHz
        }
    }

    /** Hapus semua keadaan (mis. saat lagu berhenti lama). */
    fun clear() {
        bands.fill(0f)
        raw.fill(0f)
        bass = 0f
        energy = 0f
        peak = MIN_PEAK
    }

    /**
     * Proses satu tangkapan FFT.
     * @param samplingRate seperti dilaporkan Visualizer (milliHertz; nilai kecil dianggap Hz — lihat [sampleRateHz]).
     * @param dtSec selang sejak pemrosesan sebelumnya, untuk penghalusan serang/turun.
     * @return false bila data tidak bisa dipakai (ukuran bukan pangkat dua ≥ 16, atau data kosong) — tingkat turun pelan.
     */
    fun process(fft: ByteArray, samplingRate: Int, dtSec: Float): Boolean {
        val n = fft.size
        if (n < 16 || n and (n - 1) != 0) {
            decay(dtSec)
            return false
        }
        val fs = sampleRateHz(samplingRate)
        configure(n, fs)
        val maxBin = n / 2 - 1
        for (b in 0 until bandCount) {
            val k0 = binEdges[b].coerceAtMost(maxBin)
            val k1 = (binEdges[b + 1] - 1).coerceIn(k0, maxBin)
            var sumSq = 0f
            for (k in k0..k1) {
                val re = fft[2 * k].toFloat()
                val im = fft[2 * k + 1].toFloat()
                sumSq += re * re + im * im
            }
            val rms = sqrt(sumSq / (k1 - k0 + 1)) / FULL_SCALE
            raw[b] = level(rms, centres[b])
        }
        // Penguatan otomatis: normalkan terhadap puncak yang meluruh pelan, tetapi diam tetap diam (puncak ≥ MIN_PEAK)
        var frameMax = 0f
        for (v in raw) frameMax = max(frameMax, v)
        val dt = if (dtSec.isFinite()) dtSec.coerceIn(0f, MAX_DT) else 0f
        peak = max(max(MIN_PEAK, frameMax), peak * exp(-dt / PEAK_RELEASE_SEC))
        smooth(dt) { b -> (raw[b] / peak).coerceIn(0f, 1f) }
        summarize()
        return true
    }

    /** Tidak ada data baru (jeda, izin dicabut): tingkat turun ke nol dengan kecepatan turun yang sama. */
    fun decay(dtSec: Float) {
        val dt = if (dtSec.isFinite()) dtSec.coerceIn(0f, MAX_DT) else 0f
        smooth(dt) { 0f }
        raw.fill(0f)
        peak = max(MIN_PEAK, peak * exp(-dt / PEAK_RELEASE_SEC))
        summarize()
    }

    private inline fun smooth(dt: Float, target: (Int) -> Float) {
        val up = 1f - exp(-dt / ATTACK_SEC)
        val down = 1f - exp(-dt / RELEASE_SEC)
        for (b in 0 until bandCount) {
            val t = target(b)
            val cur = bands[b]
            bands[b] = (cur + (t - cur) * (if (t > cur) up else down)).coerceIn(0f, 1f)
        }
    }

    private fun summarize() {
        var bassSum = 0f
        var bassN = 0
        var all = 0f
        for (b in 0 until bandCount) {
            all += bands[b]
            if (centres[b] < BASS_HZ) { bassSum += bands[b]; bassN++ }
        }
        bass = if (bassN > 0) bassSum / bassN else bands[0]
        energy = (0.6f * bass + 0.4f * (all / bandCount)).coerceIn(0f, 1f)
    }

    companion object {
        const val DEFAULT_BANDS = 24
        const val MAX_BANDS = 64
        /** Pita pertama mulai di sini; di bawahnya hampir tak terdengar di speaker ponsel. */
        const val MIN_HZ = 40f
        const val MAX_HZ = 16_000f
        const val BASS_HZ = 200f
        const val DEFAULT_FS = 44_100f
        const val MIN_FS = 8_000f
        const val MAX_FS = 384_000f
        /** Nilai bertanda 8-bit maksimum; satu bin penuh ≈ sqrt(2)·128 tetapi 128 cukup untuk normalisasi. */
        const val FULL_SCALE = 128f
        /** Lantai dB: bin di bawah ini dianggap hening (derau kuantisasi 8-bit satu langkah ≈ -42 dB tidak ikut terlihat). */
        const val FLOOR_DB = -40f
        /** Kemiringan kompensasi spektrum musik (yang turun ±3 dB/oktaf) per oktaf di atas 500 Hz. */
        const val TILT_DB_PER_OCT = 3f
        const val MIN_PEAK = 0.3f
        /** Magnitudo (relatif skala penuh) di bawah ini = hening: 3 langkah 8-bit. */
        const val GATE = 3f / FULL_SCALE
        /** Ukuran tangkap awal sebelum data pertama (Visualizer.getCaptureSizeRange().max biasanya 1024). */
        const val DEFAULT_SIZE = 1024
        const val PEAK_RELEASE_SEC = 4f
        const val ATTACK_SEC = 0.04f
        const val RELEASE_SEC = 0.22f
        const val MAX_DT = 0.1f

        /**
         * Laju sampel dalam Hz dari nilai yang dilaporkan Visualizer (milliHertz, mis. 44_100_000). Nilai di bawah
         * 1.000.000 dianggap sudah Hz (jaga-jaga implementasi pabrikan yang berbeda); di luar 8–384 kHz → 44.100.
         */
        fun sampleRateHz(reported: Int): Float {
            val hz = if (reported >= 1_000_000) reported / 1000f else reported.toFloat()
            return if (hz in MIN_FS..MAX_FS) hz else DEFAULT_FS
        }

        /**
         * Batas pita dalam satuan BIN FFT (setengah terbuka: pita b memakai bin `[e[b], e[b+1])`), [count]+1 nilai.
         * Berjarak logaritmik dari [MIN_HZ] sampai min([MAX_HZ], 0,95·Nyquist), tetapi tiap pita minimal selebar satu
         * bin: pada ukuran tangkap kecil, bin rendah ±86 Hz sehingga pita logaritmik murni di bawah itu akan
         * menyala kembar. Bila bin tak cukup untuk [count] pita (ukuran sangat kecil), sisanya berbagi bin terdekat.
         */
        fun binEdges(count: Int, fs: Float, size: Int): IntArray {
            val maxBin = max(2, size / 2 - 1) + 1                         // batas atas eksklusif: bin 1..size/2-1
            val binHz = fs / size
            val topHz = min(MAX_HZ, fs / 2f * 0.95f)
            val top = (topHz / binHz).roundToInt().coerceIn(2, maxBin)
            val low = (MIN_HZ / binHz).roundToInt().coerceIn(1, top - 1)
            val e = IntArray(count + 1)
            e[0] = low
            for (i in 1..count) {
                val log = low * (top.toDouble() / low).pow(i.toDouble() / count)
                e[i] = max(log.roundToInt(), e[i - 1] + 1)
            }
            // Dorongan "minimal satu bin" bisa melewati batas atas: tarik kembali dari atas bila masih ada ruang
            e[count] = min(e[count], maxBin)
            for (i in count - 1 downTo 0) e[i] = min(e[i], max(e[i + 1] - 1, 1))
            for (i in 1..count) e[i] = max(e[i], e[i - 1])               // tak pernah menurun (ukuran sangat kecil)
            return e
        }

        /**
         * Magnitudo ternormalisasi → 0..1 lewat skala dB. Magnitudo di bawah [GATE] (derau kuantisasi 8-bit) dianggap
         * hening SEBELUM kompensasi kemiringan, supaya kompensasi tidak menaikkan derau menjadi batang.
         */
        internal fun level(magnitude: Float, centreHz: Float): Float {
            if (!magnitude.isFinite() || magnitude < GATE) return 0f
            val octaves = max(0f, ln(centreHz / 500f) / LN2)
            val db = 20f * log10(magnitude) + TILT_DB_PER_OCT * octaves
            return ((db - FLOOR_DB) / -FLOOR_DB).coerceIn(0f, 1f)
        }

        private val LN2 = ln(2f)
    }
}

/**
 * Animasi dekoratif untuk mode "Animasi dekoratif": pita yang naik-turun mengikuti denyut tempo tetap. Ini BUKAN
 * analisis suara — hanya hiasan yang menyala saat musik diputar — dan UI selalu menyebutnya begitu.
 */
object DecorativeSpectrum {
    /** Denyut per detik (≈ 100 bpm). */
    const val BEAT_HZ = 1.6667f

    /**
     * Isi [out] dengan tingkat 0..1 pada waktu [timeSec]. Saat [playing] false semua 0. Deterministik terhadap waktu.
     * @return energi 0..1 (dipakai cahaya & partikel seperti pada audio asli)
     */
    fun fill(timeSec: Float, playing: Boolean, out: FloatArray): Float {
        if (!playing || !timeSec.isFinite()) {
            out.fill(0f)
            return 0f
        }
        val n = out.size
        // Denyut: naik cepat, turun pelan (bentuk gigi gergaji yang dilembutkan)
        val beatPhase = (timeSec * BEAT_HZ) % 1f
        val pulse = exp(-beatPhase * 4.5f)
        var sum = 0f
        for (i in 0 until n) {
            val pos = if (n > 1) i / (n - 1f) else 0f              // 0 = bass, 1 = treble
            val bassWeight = (1f - pos) * (1f - pos)
            val wobble = 0.5f + 0.5f * kotlin.math.sin(timeSec * (1.3f + 0.37f * i) + i * 1.7f)
            val v = (0.12f + 0.55f * wobble * (1f - 0.5f * pos) + 0.6f * pulse * bassWeight).coerceIn(0f, 1f)
            out[i] = v
            sum += v
        }
        return (0.55f * pulse + 0.45f * (sum / max(1, n))).coerceIn(0f, 1f)
    }
}
