package com.vinyl.launcher.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Membaca keadaan audio perangkat dari API PUBLIK Android saja (tanpa izin tambahan) dan menerbitkannya sebagai
 * [Snapshot]. Setiap nilai berlabel sumbernya ([Reading]). Data yang tidak dibuka Android untuk aplikasi biasa
 * (codec Bluetooth aktif, bit depth keluaran, resampling, baterai perangkat Bluetooth) TIDAK diisi di sini; itu urusan
 * Akses lanjutan (laporan diagnostik sistem) dan tanpa itu tampil "Tidak tersedia".
 *
 * Callback perangkat didaftarkan dengan hitungan pemakai ([acquire]/[release]) per lifecycle layar, jadi tidak ada
 * pendengar yang tertinggal saat beranda tidak terlihat.
 */
object AudioMonitor {

    data class Snapshot(
        val devices: List<OutputDevice> = emptyList(),
        val inputs: List<String> = emptyList(),
        val active: ActiveOutput = ActiveOutput(null, Reading.unavailable("Belum dibaca")),
        /** Sample rate keluaran bawaan sistem (bukan format lagu, bukan jaminan format aktif). */
        val nativeRate: Reading = Reading.unavailable("Belum dibaca"),
        val latency: Reading = Reading.unavailable("Belum dibaca"),
        /** Kemampuan perangkat keluaran aktif: sample rate & format yang DIDUKUNG. */
        val capability: Reading = Reading.unavailable("Belum dibaca"),
        /** Pemutaran bit-perfect untuk USB (Android 14+): didukung atau tidak. */
        val bitPerfect: Reading = Reading.unavailable("Belum dibaca"),
        val spatial: Reading = Reading.unavailable("Belum dibaca"),
        val headTracking: Reading = Reading.unavailable("Belum dibaca"),
        val musicActive: Boolean = false,
        /** Waktu (ms) keluaran aktif terakhir berganti; dipakai VFD untuk memprioritaskan nama keluaran baru. */
        val activeChangedAt: Long = Long.MIN_VALUE,
    )

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    private var users = 0
    private var manager: AudioManager? = null
    private val main = Handler(Looper.getMainLooper())

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refresh()
    }

    /** Mulai memantau (dipanggil saat layar yang butuh data tampil). Aman dipanggil berulang. */
    fun acquire(context: Context) {
        val am = manager ?: context.applicationContext.getSystemService(AudioManager::class.java) ?: return
        manager = am
        if (users++ == 0) am.registerAudioDeviceCallback(callback, main)
        refresh()
    }

    /** Berhenti memantau bila tidak ada lagi pemakai. */
    fun release() {
        if (users <= 0) return
        if (--users == 0) manager?.unregisterAudioDeviceCallback(callback)
    }

    /** Baca ulang semua nilai (callback perangkat memanggilnya; konsol juga saat dibuka). */
    fun refresh() {
        val am = manager ?: return
        val devices = runCatching { am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.isSink }.map(::toDevice) }.getOrDefault(emptyList())
        val inputs = runCatching {
            am.getDevices(AudioManager.GET_DEVICES_INPUTS).map { it.productName?.toString().orEmpty().ifBlank { AudioRoutes.label(kindOf(it.type)) } }.distinct()
        }.getOrDefault(emptyList())
        val routed: List<Int>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { am.getAudioDevicesForAttributes(MEDIA).map { it.id } }.getOrNull()
        } else null
        val active = AudioRoutes.active(devices, routed)

        val rate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
        val frames = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull()
        val nativeRate = if (rate != null && rate > 0) Reading.reported(AudioRoutes.khz(rate), "Sample rate keluaran bawaan sistem") else Reading.unavailable("Tidak dilaporkan sistem")

        val activeInfo = active.device?.let { d -> runCatching { am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == d.id } }.getOrNull() }
        val previous = _state.value
        _state.value = Snapshot(
            devices = devices,
            inputs = inputs,
            active = active,
            nativeRate = nativeRate,
            latency = AudioRoutes.bufferLatency(frames, rate),
            capability = capability(activeInfo),
            bitPerfect = bitPerfect(am, activeInfo),
            spatial = spatial(am),
            headTracking = headTracking(am),
            musicActive = runCatching { am.isMusicActive }.getOrDefault(false),
            activeChangedAt = if (previous.active.device?.profileKey != active.device?.profileKey && previous.active.device != null)
                System.currentTimeMillis() else previous.activeChangedAt,
        )
    }

    private val MEDIA: AudioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()

    private fun toDevice(d: AudioDeviceInfo): OutputDevice = OutputDevice(
        id = d.id,
        kind = kindOf(d.type),
        // Nama dari AudioDeviceInfo (tanpa izin Bluetooth); kosong → label jenis
        name = d.productName?.toString().orEmpty().trim().ifBlank { AudioRoutes.label(kindOf(d.type)) },
        address = runCatching { d.address }.getOrDefault("").orEmpty(),
        sampleRates = d.sampleRates.toList(),
        channelCounts = d.channelCounts.toList(),
    )

    /** Konstanta TYPE_* hanyalah angka yang ditanam saat kompilasi; aman dibandingkan di Android versi lama. */
    @SuppressLint("InlinedApi")
    fun kindOf(type: Int): DeviceKind = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> DeviceKind.SPEAKER
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, AudioDeviceInfo.TYPE_TELEPHONY -> DeviceKind.EARPIECE
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_AUX_LINE, AudioDeviceInfo.TYPE_DOCK_ANALOG -> DeviceKind.WIRED
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> DeviceKind.BLUETOOTH
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> DeviceKind.BLUETOOTH_CALL
        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_BROADCAST -> DeviceKind.BLE_AUDIO
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> DeviceKind.USB
        AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC -> DeviceKind.HDMI
        AudioDeviceInfo.TYPE_HEARING_AID -> DeviceKind.HEARING_AID
        else -> DeviceKind.OTHER
    }

    private fun capability(d: AudioDeviceInfo?): Reading {
        if (d == null) return Reading.unavailable("Tidak ada keluaran aktif yang diketahui")
        val rates = d.sampleRates.filter { it > 0 }.sorted()
        val depths = d.encodings.toList().mapNotNull(::depthOf).distinct()
        if (rates.isEmpty() && depths.isEmpty()) {
            return Reading.reported("Format apa saja", "Perangkat tidak membatasi daftar format (biasanya di-mix sistem)")
        }
        val r = if (rates.isEmpty()) "" else rates.first().let(AudioRoutes::khz) + if (rates.size > 1) "–" + AudioRoutes.khz(rates.last()) else ""
        val text = listOf(r, depths.joinToString("/")).filter { it.isNotBlank() }.joinToString(" · ")
        return Reading.reported(text, "Kemampuan perangkat, BUKAN format yang sedang dipakai")
    }

    private fun depthOf(encoding: Int): String? = when (encoding) {
        AudioFormat.ENCODING_PCM_16BIT -> "16-bit"
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> "24-bit"
        AudioFormat.ENCODING_PCM_32BIT -> "32-bit"
        AudioFormat.ENCODING_PCM_FLOAT -> "float"
        else -> null
    }

    private fun bitPerfect(am: AudioManager, d: AudioDeviceInfo?): Reading {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return Reading.unavailable("Butuh Android 14")
        if (d == null || kindOf(d.type) != DeviceKind.USB) return Reading.unavailable("Hanya berlaku untuk perangkat USB")
        val list = runCatching { am.getSupportedMixerAttributes(d) }.getOrNull() ?: return Reading.unavailable("Sistem menolak pertanyaan")
        val ok = list.any { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
        return Reading.reported(
            if (ok) "Didukung" else "Tidak didukung",
            "Bit-perfect hanya aktif bila aplikasi musik memintanya; launcher tidak bisa menyalakannya",
        )
    }

    private fun spatial(am: AudioManager): Reading {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S_V2) return Reading.unavailable("Butuh Android 12L")
        val s = runCatching { am.spatializer }.getOrNull() ?: return Reading.unavailable("Sistem tidak menyediakan spatializer")
        return when {
            !s.isAvailable -> Reading.reported("Tidak tersedia untuk keluaran ini", "Dilaporkan sistem")
            s.isEnabled -> Reading.reported("Aktif", "Dilaporkan sistem")
            else -> Reading.reported("Tersedia, mati", "Nyalakan di pengaturan suara sistem")
        }
    }

    private fun headTracking(am: AudioManager): Reading {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return Reading.unavailable("Butuh Android 13")
        val s = runCatching { am.spatializer }.getOrNull() ?: return Reading.unavailable("Sistem tidak menyediakan spatializer")
        return Reading.reported(if (s.isHeadTrackerAvailable) "Tersedia" else "Tidak tersedia")
    }
}
