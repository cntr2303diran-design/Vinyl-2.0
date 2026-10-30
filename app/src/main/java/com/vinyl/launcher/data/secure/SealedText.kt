package com.vinyl.launcher.data.secure

import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.Base64

/** Tempat menyimpan beberapa nilai teks bernama. Di aplikasi: SharedPreferences ([PrefsSlots]); di uji unit: peta biasa. */
interface TextSlots {
    fun get(key: String): String?

    /** Terapkan semua perubahan sekaligus; nilai null = hapus kuncinya. */
    fun change(changes: Map<String, String?>)
}

class PrefsSlots(private val prefs: () -> SharedPreferences) : TextSlots {
    override fun get(key: String): String? = prefs().getString(key, null)

    override fun change(changes: Map<String, String?>) {
        prefs().edit {
            for ((key, value) in changes) {
                if (value == null) remove(key) else putString(key, value)
            }
        }
    }
}

/**
 * Satu teks yang disimpan tersegel ([Vault]) di [TextSlots], sebagai Base64 dari blob. Teks polos TIDAK pernah ditulis.
 *
 * Migrasi: bila [legacyKey] masih berisi teks polos dari versi lama dan belum ada versi tersegelnya, teks itu disegel, dibuka
 * kembali untuk memastikan hasilnya terbaca, dan baru sesudah itu yang polos dihapus (dalam satu perubahan atomik). Bila
 * penyegelan gagal, yang polos dibiarkan (tidak dibuang) dan dicoba lagi pada pembacaan berikutnya.
 */
class SealedText(
    private val slots: TextSlots,
    private val vault: Vault,
    private val purpose: VaultPurpose,
    private val sealedKey: String,
    private val legacyKey: String? = null,
) {
    /** Isi tersimpan; null bila kosong atau tidak terbaca (segel rusak/diubah, kunci hilang). */
    fun read(): String? {
        val stored = slots.get(sealedKey)
        if (stored != null) return unseal(stored)
        val legacy = legacyKey?.let { slots.get(it) } ?: return null
        write(legacy, verify = true)
        return legacy
    }

    /**
     * Simpan [text] (null = hapus semuanya, termasuk sisa teks polos lama). Mengembalikan false bila tidak bisa disegel
     * (kunci tidak tersedia): tidak ada yang diubah dan pemanggil harus memberi tahu pengguna, bukan menyimpan versi polos.
     * [verify]: buka kembali hasil segel dan bandingkan sebelum menerapkannya (dipakai saat migrasi).
     */
    fun write(text: String?, verify: Boolean = false): Boolean {
        val changes = LinkedHashMap<String, String?>()
        legacyKey?.let { changes[it] = null }
        if (text == null) {
            changes[sealedKey] = null
            slots.change(changes)
            return true
        }
        val plain = text.toByteArray(Charsets.UTF_8)
        val blob = vault.seal(purpose, plain) ?: return false
        if (verify) {
            val back = vault.open(purpose, blob)
            if (back == null || !back.contentEquals(plain)) return false
        }
        changes[sealedKey] = Base64.getEncoder().encodeToString(blob)
        slots.change(changes)
        return true
    }

    private fun unseal(stored: String): String? {
        val blob = try {
            Base64.getDecoder().decode(stored)
        } catch (e: IllegalArgumentException) {
            return null
        }
        val plain = vault.open(purpose, blob) ?: return null
        return String(plain, Charsets.UTF_8)
    }
}
