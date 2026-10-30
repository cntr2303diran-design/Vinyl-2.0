package com.vinyl.launcher.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Satu pintasan kontak pada telepon putar: nama untuk ditampilkan dan alamat kontak (content://…lookup/…). */
@Serializable
data class PhoneFavorite(val digit: Int, val name: String, val uri: String)

/**
 * Aturan pintasan telepon putar (murni, teruji). Angka 1–9 boleh diisi kontak; 0 selalu membuka aplikasi telepon.
 * Hanya alamat kontak milik penyedia Kontak sistem yang diterima, supaya berkas yang rusak atau disusupi tidak bisa membuat
 * aplikasi membuka alamat lain.
 */
object PhoneBookRules {
    const val MIN_DIGIT = 1
    const val MAX_DIGIT = 9
    const val MAX_NAME = 60
    const val MAX_URI = 512
    private const val CONTACTS_PREFIX = "content://com.android.contacts/"

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    fun isDigit(d: Int) = d in MIN_DIGIT..MAX_DIGIT

    fun isContactUri(uri: String) = uri.startsWith(CONTACTS_PREFIX) && uri.length <= MAX_URI && uri.none { it.isISOControl() }

    /** Pintasan yang bersih, atau null bila angka di luar 1–9, alamat bukan alamat kontak sistem, atau nama kosong. */
    fun clean(f: PhoneFavorite): PhoneFavorite? {
        val name = f.name.filterNot { it.isISOControl() }.trim().take(MAX_NAME)
        if (!isDigit(f.digit) || !isContactUri(f.uri) || name.isEmpty()) return null
        return PhoneFavorite(f.digit, name, f.uri)
    }

    fun encode(favorites: Collection<PhoneFavorite>): String =
        json.encodeToString(ListSerializer(PhoneFavorite.serializer()), favorites.mapNotNull(::clean))

    /** Berkas rusak atau kosong = tidak ada pintasan. Satu angka dipakai sekali (yang terakhir menang). */
    fun decode(raw: String?): Map<Int, PhoneFavorite> {
        if (raw.isNullOrBlank()) return emptyMap()
        val list = runCatching { json.decodeFromString(ListSerializer(PhoneFavorite.serializer()), raw) }.getOrDefault(emptyList())
        val out = LinkedHashMap<Int, PhoneFavorite>()
        for (f in list) clean(f)?.let { out[it.digit] = it }
        return out
    }
}

/**
 * Pintasan kontak telepon putar di ruangan. Disimpan hanya di SharedPreferences privat aplikasi ini: nama dan alamat kontak
 * adalah data pribadi, jadi tidak ikut berkas cadangan atau preset, tidak dikirim ke mana pun, dan dihapus bersama data
 * aplikasi atau lewat [clearAll]. Berkasnya baru dibuat saat pintasan pertama diisi.
 */
class PhoneBook private constructor(private val context: Context) {
    private val prefs get() = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _favorites = MutableStateFlow(PhoneBookRules.decode(prefs.getString(KEY, null)))

    /** Pintasan sekarang, per angka. */
    val favorites: StateFlow<Map<Int, PhoneFavorite>> = _favorites.asStateFlow()

    fun set(f: PhoneFavorite): Boolean {
        val clean = PhoneBookRules.clean(f) ?: return false
        save(_favorites.value + (clean.digit to clean))
        return true
    }

    fun clear(digit: Int) {
        if (digit in _favorites.value) save(_favorites.value - digit)
    }

    fun clearAll() {
        _favorites.value = emptyMap()
        prefs.edit { clear() }
    }

    private fun save(map: Map<Int, PhoneFavorite>) {
        _favorites.value = map
        prefs.edit { putString(KEY, PhoneBookRules.encode(map.values.sortedBy { it.digit })) }
    }

    companion object {
        private const val FILE = "retro_phone"
        private const val KEY = "favorites"
        @Volatile private var instance: PhoneBook? = null

        fun get(context: Context): PhoneBook =
            instance ?: synchronized(this) { instance ?: PhoneBook(context.applicationContext).also { instance = it } }
    }
}
