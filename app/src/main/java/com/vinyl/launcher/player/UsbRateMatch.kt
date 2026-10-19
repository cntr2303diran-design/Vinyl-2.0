package com.vinyl.launcher.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Android 14+ dengan DAC USB: minta mixer Android berjalan di laju sampel lagu yang sedang diputar
 * (AudioManager.setPreferredMixerAttributes, perilaku DEFAULT), sehingga Android tidak me-resample 44,1 kHz → 48 kHz.
 * Ini BUKAN bit-perfect: volume Android dan suara lain tetap dicampur. Hanya untuk aplikasi ini (uid sendiri), media
 * (USAGE_MEDIA), dan perangkat USB — batasan dari Android. Dipanggil dari thread audio saat format lagu dikonfigurasi,
 * sebelum AudioTrack baru dibuka.
 */
class UsbRateMatch(context: Context) {
    private val am = context.applicationContext.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()

    @Volatile
    var enabled = false

    /** Perangkat yang sedang kita atur (untuk dibersihkan); ditulis thread audio, dibaca thread utama. */
    @Volatile
    private var appliedTo: AudioDeviceInfo? = null

    /** Laju & status permintaan terakhir: lagu berikutnya dengan laju yang sama tidak meminta ulang (tanpa jeda). */
    @Volatile
    private var applied: Pair<Int, String>? = null

    /** Siapkan mixer untuk [sampleRate]; mengembalikan teks status jujur (kosong bila fitur mati). */
    fun prepare(sampleRate: Int): String {
        if (!enabled) {
            clear()
            return ""
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return "Butuh Android 14 atau lebih baru"
        return prepare34(sampleRate)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun prepare34(sampleRate: Int): String {
        val m = am ?: return "Audio sistem tidak tersedia"
        val dev = m.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }
        if (dev == null) {
            clear()
            return "Tidak ada DAC USB tersambung"
        }
        // Mixer sudah berjalan di laju ini untuk DAC yang sama: meminta ulang bisa membuka ulang keluaran (bunyi putus
        // di sambungan lagu gapless), jadi tidak diulang
        val last = applied
        if (appliedTo?.id == dev.id && last != null && last.first == sampleRate) return last.second
        return try {
            val pick = m.getSupportedMixerAttributes(dev)
                .filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_DEFAULT && it.format.sampleRate == sampleRate }
                .maxByOrNull { rank(it.format.encoding) }
            if (pick == null) {
                clear()
                "DAC tidak menyediakan ${LocalFormat.khz(sampleRate)} kHz untuk mixer"
            } else if (m.setPreferredMixerAttributes(attrs, dev, pick)) {
                appliedTo = dev
                "Mixer USB ${LocalFormat.khz(sampleRate)} kHz ${bits(pick.format.encoding)} (tanpa resampling Android)"
                    .also { applied = sampleRate to it }
            } else {
                "Ditolak sistem"
            }
        } catch (e: RuntimeException) {
            Log.w("VinylUsbRate", "Mixer USB tidak bisa diatur: ${e.javaClass.simpleName}")
            "Ditolak sistem"
        }
    }

    /** Kembalikan mixer USB ke pengaturan sistem. */
    fun clear() {
        applied = null
        val dev = appliedTo ?: return
        appliedTo = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { am?.clearPreferredMixerAttributes(attrs, dev) }
        }
    }

    private fun rank(encoding: Int): Int = when (encoding) {
        AudioFormat.ENCODING_PCM_32BIT -> 4
        AudioFormat.ENCODING_PCM_FLOAT -> 3
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 2
        AudioFormat.ENCODING_PCM_16BIT -> 1
        else -> 0
    }

    private fun bits(encoding: Int): String = when (encoding) {
        AudioFormat.ENCODING_PCM_32BIT -> "32-bit"
        AudioFormat.ENCODING_PCM_FLOAT -> "float"
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> "24-bit"
        AudioFormat.ENCODING_PCM_16BIT -> "16-bit"
        else -> ""
    }
}
