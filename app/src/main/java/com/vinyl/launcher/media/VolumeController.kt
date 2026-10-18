package com.vinyl.launcher.media

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager

/**
 * Pengatur volume media untuk sleep timer dan pergantian lembut. Memakai AudioManager dengan flag 0, jadi tidak ada
 * panel volume yang muncul dan TIDAK butuh izin. Hanya menyentuh aliran musik (STREAM_MUSIC).
 */
internal object VolumeController {
    private var audio: AudioManager? = null
    private var store: VolumeGuardStore? = null

    fun init(context: Context) {
        if (audio != null) return
        val app = context.applicationContext
        audio = app.getSystemService(AudioManager::class.java)
        store = VolumeGuardStore(app)
    }

    /** False bila volume tidak bisa diatur (perangkat volume tetap, mis. sebagian TV/mobil) atau belum diinisialisasi. */
    val canControl: Boolean get() = audio?.let { !it.isVolumeFixed } ?: false

    fun current(): Int = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0

    fun max(): Int = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0

    /** Atur volume ke [index] (dijepit 0..max). Mengembalikan false bila sistem menolak. */
    fun set(index: Int): Boolean {
        val am = audio ?: return false
        return try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, index.coerceIn(0, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)), 0)
            true
        } catch (e: SecurityException) {
            false   // mis. kebijakan Jangan Ganggu / perangkat terkelola
        }
    }

    val guard: VolumeGuardStore get() = checkNotNull(store) { "VolumeController.init belum dipanggil" }
}

/**
 * Catatan volume asli di penyimpanan lokal. Bila proses mati saat volume sedang dipudarkan, catatan ini membuat
 * volume bisa dipulihkan saat aplikasi hidup lagi (keputusan aman-tidaknya ada di VolumeGuardPlan).
 * Berisi dua angka kecil; tidak ada data pribadi.
 */
@SuppressLint("ApplySharedPref") // commit() disengaja: catatan harus tertulis SEBELUM volume diubah (lihat save())
internal class VolumeGuardStore(context: Context) {
    class Guard(val original: Int, val lastSet: Int)

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Panggil dari thread latar belakang (memakai commit agar tertulis sebelum volume diubah). */
    fun save(original: Int, lastSet: Int) {
        prefs.edit().putBoolean(ACTIVE, true).putInt(ORIGINAL, original).putInt(LAST_SET, lastSet).commit()
    }

    fun updateLastSet(lastSet: Int) {
        prefs.edit().putInt(LAST_SET, lastSet).commit()
    }

    fun read(): Guard? =
        if (prefs.getBoolean(ACTIVE, false)) Guard(prefs.getInt(ORIGINAL, 0), prefs.getInt(LAST_SET, 0)) else null

    fun clear() {
        prefs.edit().clear().commit()
    }

    private companion object {
        const val FILE = "vinyl_volume_guard"
        const val ACTIVE = "active"
        const val ORIGINAL = "original"
        const val LAST_SET = "last_set"
    }
}
