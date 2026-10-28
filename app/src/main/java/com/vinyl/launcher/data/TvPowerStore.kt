package com.vinyl.launcher.data

import android.content.Context
import androidx.core.content.edit

/**
 * Apakah TV terakhir dimatikan dengan tombol dayanya, untuk pilihan "Ingat tombol daya TV"
 * ([RetroSettings.rememberTvPower]; aturannya di [TvPowerRules]).
 *
 * Satu nilai ya/tidak di SharedPreferences privat aplikasi: hanya di perangkat ini, tidak ikut berkas cadangan maupun
 * preset (ini keadaan, bukan pengaturan), dan tidak ikut cadangan sistem (allowBackup mati). Berkasnya baru dibuat saat
 * TV pertama kali dimatikan dengan pilihan itu menyala; bagi yang tidak memakainya tidak ada apa-apa di penyimpanan.
 */
object TvPowerStore {
    private const val FILE = "tv_power"
    private const val KEY_OFF = "off"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** True bila TV tersimpan dalam keadaan mati. Satu pembacaan kecil. */
    fun isOff(context: Context): Boolean = prefs(context).getBoolean(KEY_OFF, false)

    /** Simpan [off]; tidak menulis apa pun bila nilainya sudah sama. Penulisan tidak memblokir (apply). */
    fun setOff(context: Context, off: Boolean) {
        val p = prefs(context)
        if (p.getBoolean(KEY_OFF, false) == off) return
        p.edit { putBoolean(KEY_OFF, off) }
    }
}
