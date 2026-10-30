package com.vinyl.launcher.data

import android.annotation.SuppressLint
import android.content.Context
import com.vinyl.launcher.data.secure.PrefsSlots
import com.vinyl.launcher.data.secure.SealedText
import com.vinyl.launcher.data.secure.SerialWorker
import com.vinyl.launcher.data.secure.SystemVault
import com.vinyl.launcher.data.secure.VaultPurpose
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
 * Pintasan kontak telepon putar di ruangan. Nama dan alamat kontak adalah data pribadi, jadi disimpan TERENKRIPSI
 * (AES-256-GCM, kunci di Android Keystore; lihat [com.vinyl.launcher.data.secure.Vault]) di SharedPreferences privat
 * aplikasi ini saja: tidak ikut berkas cadangan atau preset, tidak dikirim ke mana pun, dan dihapus bersama data aplikasi
 * atau lewat [clearAll]. Berkas lama dari versi sebelumnya (masih polos) otomatis disegel saat dibaca pertama kali, lalu yang
 * polos dihapus.
 *
 * Membaca dan menulis (operasi Keystore) berjalan di antrean latar yang berurutan; [favorites] kosong sesaat setelah
 * aplikasi dibuka sampai pembacaan selesai. Bila enkripsi tidak tersedia di perangkat ini, perubahan TIDAK disimpan
 * (tidak ada versi polos) dan [saveFailed] menyala supaya layar bisa memberi tahu pengguna.
 */
class PhoneBook internal constructor(private val text: SealedText, private val worker: SerialWorker) {
    private val _favorites = MutableStateFlow<Map<Int, PhoneFavorite>>(emptyMap())
    private val _saveFailed = MutableStateFlow(false)
    // Hanya disentuh di dalam antrean [worker]
    private var loaded = false

    /** Pintasan sekarang, per angka. */
    val favorites: StateFlow<Map<Int, PhoneFavorite>> = _favorites.asStateFlow()

    /** true bila perubahan terakhir gagal disimpan karena penyimpanan terenkripsi tidak tersedia di perangkat ini. */
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    init {
        worker.post { ensureLoaded() }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _favorites.value = PhoneBookRules.decode(text.read())
    }

    /** Antrekan pengisian pintasan. false bila angka, alamat, atau nama tidak sah (tidak ada yang berubah). */
    fun set(f: PhoneFavorite): Boolean {
        val clean = PhoneBookRules.clean(f) ?: return false
        worker.post {
            ensureLoaded()
            commit(_favorites.value + (clean.digit to clean))
        }
        return true
    }

    fun clear(digit: Int) {
        worker.post {
            ensureLoaded()
            if (digit in _favorites.value) commit(_favorites.value - digit)
        }
    }

    fun clearAll() {
        worker.post {
            loaded = true
            _favorites.value = emptyMap()
            _saveFailed.value = false
            text.write(null)
        }
    }

    private fun commit(next: Map<Int, PhoneFavorite>) {
        val ok = text.write(if (next.isEmpty()) null else PhoneBookRules.encode(next.values.sortedBy { it.digit }))
        if (ok) _favorites.value = next
        _saveFailed.value = !ok
    }

    companion object {
        private const val FILE = "retro_phone"
        private const val KEY_SEALED = "favorites_sealed"
        // Kunci versi lama yang menyimpan JSON polos; hanya dibaca untuk migrasi lalu dihapus
        private const val KEY_LEGACY = "favorites"
        // Yang disimpan adalah applicationContext (hidup sepanjang proses), bukan Activity: tidak bocor
        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: PhoneBook? = null

        fun get(context: Context): PhoneBook =
            instance ?: synchronized(this) { instance ?: create(context.applicationContext).also { instance = it } }

        private fun create(app: Context): PhoneBook {
            val slots = PrefsSlots { app.getSharedPreferences(FILE, Context.MODE_PRIVATE) }
            return PhoneBook(SealedText(slots, SystemVault.vault, VaultPurpose.PHONE, KEY_SEALED, KEY_LEGACY), SerialWorker())
        }
    }
}
