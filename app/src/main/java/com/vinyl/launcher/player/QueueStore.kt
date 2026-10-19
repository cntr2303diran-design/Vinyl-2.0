package com.vinyl.launcher.player

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Penyimpanan antrean pemutar di penyimpanan internal aplikasi (SharedPreferences, tidak ikut cadangan: allowBackup
 * mati). Terikat pada folder musik yang sedang dipilih; mengganti folder membuat antrean lama tidak berlaku.
 *
 * Daftar id (bisa ribuan) ditulis hanya saat antrean berubah dan di thread latar; posisi (kecil) sering ditulis lewat
 * apply() yang tidak memblokir.
 */
object QueueStore {
    private const val FILE = "player_queue"
    private const val K_FOLDER = "folder"
    private const val K_IDS = "ids"
    private const val K_CURRENT = "current"
    private const val K_POSITION = "position"

    private fun prefs(context: Context): SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Tulis ulang seluruh antrean (panggil dari thread latar). Daftar kosong menghapus antrean tersimpan. */
    fun saveQueue(context: Context, folderUri: String, ids: List<String>, currentId: String?, positionMs: Long) {
        if (ids.isEmpty()) {
            clear(context)
            return
        }
        val encoded = QueueRules.encodeIds(ids)
        prefs(context).edit {
            putString(K_FOLDER, folderUri)
            putString(K_IDS, encoded)
            putString(K_CURRENT, currentId)
            putLong(K_POSITION, positionMs.coerceAtLeast(0L))
        }
    }

    /** Perbarui hanya lagu yang sedang diputar dan posisinya (murah). Tidak melakukan apa-apa bila belum ada antrean. */
    fun savePosition(context: Context, folderUri: String, currentId: String?, positionMs: Long) {
        val p = prefs(context)
        if (p.getString(K_FOLDER, null) != folderUri) return
        p.edit {
            putString(K_CURRENT, currentId)
            putLong(K_POSITION, positionMs.coerceAtLeast(0L))
        }
    }

    /** Antrean tersimpan untuk [folderUri], atau null bila tidak ada / milik folder lain / rusak. */
    fun load(context: Context, folderUri: String?): QueueSnapshot? {
        if (folderUri == null) return null
        val p = prefs(context)
        if (p.getString(K_FOLDER, null) != folderUri) return null
        val ids = QueueRules.decodeIds(p.getString(K_IDS, null)) ?: return null
        return QueueSnapshot(ids, p.getString(K_CURRENT, null), p.getLong(K_POSITION, 0L))
    }

    /** Ada antrean tersimpan (milik folder apa pun)? Murah: tidak mengurai daftar id. */
    fun hasSaved(context: Context): Boolean = prefs(context).getString(K_IDS, null).let { !it.isNullOrEmpty() && it != "[]" }

    fun clear(context: Context) {
        prefs(context).edit { clear() }
    }
}
