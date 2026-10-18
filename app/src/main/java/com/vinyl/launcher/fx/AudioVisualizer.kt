package com.vinyl.launcher.fx

import android.media.audiofx.AudioEffect
import android.media.audiofx.Visualizer
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keadaan visualizer audio asli yang dibagi ke seluruh proses (layar utama menulis, Pengaturan membaca), supaya
 * Pengaturan bisa jujur menjelaskan kalau perangkat menolak — tidak ada animasi palsu yang menutupi kegagalan.
 */
object VisualizerStatus {
    enum class State {
        /** Tidak sedang berjalan (mati, jeda, atau layar tidak terlihat). */
        IDLE,
        /** Menangkap spektrum keluaran audio. */
        LIVE,
        /** Perangkat atau sistem menolak membuka Visualizer (lihat [AudioVisualizer.start]). */
        UNAVAILABLE,
    }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    internal fun set(value: State) { _state.value = value }
}

/** Satu tangkapan FFT beserta umurnya. */
class FftCapture(val fft: ByteArray, val samplingRateMilliHz: Int, val ageMs: Long)

/**
 * Pembaca spektrum keluaran audio memakai [Visualizer] pada sesi 0 (campuran keluaran sistem). Peladen musik tidak
 * membagikan ID sesi audio ke aplikasi lain, jadi sesi 0 satu-satunya jalan bagi launcher.
 *
 * Yang dibaca HANYA data FFT (magnitudo per frekuensi) campuran keluaran — bukan mikrofon dan bukan gelombang mentah
 * (callback gelombang tidak diminta). Android tetap mewajibkan izin RECORD_AUDIO untuk Visualizer; izin ini diminta
 * hanya saat pengguna memilih "Audio asli" dan tidak pernah dipakai untuk merekam. Data tidak disimpan dan tidak
 * dikirim ke mana pun.
 *
 * Kegagalan apa pun (izin dicabut, perangkat tak mendukung, engine audio menolak) berakhir di
 * [VisualizerStatus.State.UNAVAILABLE]; pemanggil tidak boleh menggantinya dengan animasi tiruan.
 *
 * Panggil [start]/[stop] dari thread utama mengikuti lifecycle (hidup hanya saat layar utama terlihat dan musik diputar).
 */
class AudioVisualizer {
    private var visualizer: Visualizer? = null

    @Volatile private var fft: ByteArray? = null
    @Volatile private var rateMilliHz: Int = 0
    @Volatile private var stampMs: Long = 0L

    private val listener = object : Visualizer.OnDataCaptureListener {
        override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit

        override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
            if (fft == null || fft.isEmpty()) return
            // Larik milik Visualizer dipakai ulang; salin sebelum dibaca thread lain
            this@AudioVisualizer.fft = fft.copyOf()
            rateMilliHz = samplingRate
            stampMs = SystemClock.elapsedRealtime()
        }
    }

    val isRunning: Boolean get() = visualizer != null

    /**
     * Membuka Visualizer. Izin RECORD_AUDIO HARUS sudah diberikan (dijaga [FxPlan]); tanpa izin sistem menolak dan hasilnya
     * UNAVAILABLE. Mengembalikan true bila menangkap.
     */
    fun start(): Boolean {
        if (visualizer != null) return true
        // Percobaan baru: keterangan "tidak tersedia" dari percobaan lalu tidak boleh tampil sementara kita mencoba lagi
        if (VisualizerStatus.state.value == VisualizerStatus.State.UNAVAILABLE) VisualizerStatus.set(VisualizerStatus.State.IDLE)
        var v: Visualizer? = null
        try {
            v = Visualizer(0)
            val range = Visualizer.getCaptureSizeRange()                 // [min, maks], pangkat dua
            val size = CAPTURE_SIZE.coerceIn(range[0], range[1])
            check(v.setCaptureSize(size) == AudioEffect.SUCCESS) { "setCaptureSize gagal" }
            val rate = minOf(Visualizer.getMaxCaptureRate(), MAX_RATE_MILLI_HZ)
            // Hanya FFT: waveform=false sehingga sampel mentah tidak pernah dikirim ke aplikasi
            check(v.setDataCaptureListener(listener, rate, false, true) == AudioEffect.SUCCESS) { "setDataCaptureListener gagal" }
            check(v.setEnabled(true) == AudioEffect.SUCCESS) { "setEnabled gagal" }
            visualizer = v
            fft = null
            stampMs = 0L
            VisualizerStatus.set(VisualizerStatus.State.LIVE)
            return true
        } catch (e: Exception) {
            // UnsupportedOperationException / RuntimeException (engine tak bisa dibuat, termasuk izin ditolak),
            // IllegalStateException (state engine salah), SecurityException. Pesan teknis cukup di log perangkat ini.
            Log.w(TAG, "Visualizer tidak bisa dibuka: ${e.javaClass.simpleName}")
            runCatching { v?.release() }
            visualizer = null
            VisualizerStatus.set(VisualizerStatus.State.UNAVAILABLE)
            return false
        }
    }

    /** Menutup Visualizer dan melepas engine audio. Aman dipanggil berulang. */
    fun stop() {
        val v = visualizer
        visualizer = null
        fft = null
        if (v != null) {
            runCatching { v.setEnabled(false) }
            runCatching { v.setDataCaptureListener(null, 0, false, false) }
            runCatching { v.release() }
        }
        // UNAVAILABLE dipertahankan agar Pengaturan tetap bisa menjelaskan kegagalan terakhir
        if (VisualizerStatus.state.value == VisualizerStatus.State.LIVE) VisualizerStatus.set(VisualizerStatus.State.IDLE)
    }

    /** Tangkapan terbaru, atau null bila belum ada. */
    fun latest(): FftCapture? {
        val f = fft ?: return null
        return FftCapture(f, rateMilliHz, SystemClock.elapsedRealtime() - stampMs)
    }

    private companion object {
        const val TAG = "VinylVisualizer"
        /** 1024 byte → 512 bin kompleks; dipotong ke rentang yang didukung perangkat. */
        const val CAPTURE_SIZE = 1024
        /** 30 Hz cukup untuk batang yang halus. */
        const val MAX_RATE_MILLI_HZ = 30_000
    }
}
