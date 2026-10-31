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
import java.net.URI

/** Satu stasiun radio internet milik pengguna: nama untuk ditampilkan dan alamat aliran (https). */
@Serializable
data class RadioStation(val name: String, val url: String)

/**
 * Aturan daftar stasiun radio (murni, teruji). Hanya alamat https yang diterima (aplikasi memang melarang lalu lintas
 * polos), tanpa nama pengguna/kata sandi di dalam alamat, tanpa spasi atau karakter kendali. Daftar paling banyak
 * [MAX_STATIONS] dan satu alamat dipakai sekali. Tidak ada stasiun bawaan: daftar sepenuhnya isian pengguna.
 */
object RadioRules {
    const val MAX_STATIONS = 12
    const val MAX_NAME = 40
    const val MAX_URL = 300

    /** Awalan id media untuk stasiun radio; sisanya adalah alamat aliran itu sendiri. */
    const val ID_PREFIX = "radio:"

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    fun isRadioId(id: String?): Boolean = id != null && id.startsWith(ID_PREFIX)

    fun idOf(station: RadioStation): String = ID_PREFIX + station.url

    fun urlOf(id: String): String = id.removePrefix(ID_PREFIX)

    /** true bila [url] alamat https yang sah untuk aliran. */
    fun isStreamUrl(url: String): Boolean {
        if (url.isEmpty() || url.length > MAX_URL) return false
        if (url.any { it.isISOControl() || it.isWhitespace() }) return false
        val uri = try { URI(url) } catch (_: Exception) { return false }
        return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    }

    /** Stasiun yang bersih, atau null bila nama kosong atau alamat bukan https yang sah. */
    fun clean(s: RadioStation): RadioStation? {
        val name = s.name.filterNot { it.isISOControl() }.trim().take(MAX_NAME)
        val url = s.url.trim()
        if (name.isEmpty() || !isStreamUrl(url)) return null
        return RadioStation(name, url)
    }

    /** Daftar bersih: yang tidak sah dibuang, alamat kembar (yang terakhir menang), paling banyak [MAX_STATIONS]. */
    fun sanitize(list: Collection<RadioStation>): List<RadioStation> {
        val out = LinkedHashMap<String, RadioStation>()
        for (s in list) clean(s)?.let { out[it.url] = it }
        return out.values.take(MAX_STATIONS)
    }

    /**
     * Daftar sesudah [s] ditambahkan; alamat yang sama hanya memperbarui namanya. null bila [s] tidak sah atau daftar
     * penuh (alamat baru tidak muat).
     */
    fun added(list: List<RadioStation>, s: RadioStation): List<RadioStation>? {
        val c = clean(s) ?: return null
        val at = list.indexOfFirst { it.url == c.url }
        if (at >= 0) return list.toMutableList().also { it[at] = c }
        if (list.size >= MAX_STATIONS) return null
        return list + c
    }

    /** Stasiun sesudah yang beralamat [currentUrl] (melingkar); yang pertama bila [currentUrl] tidak ada di daftar. */
    fun next(list: List<RadioStation>, currentUrl: String?): RadioStation? {
        if (list.isEmpty()) return null
        val at = list.indexOfFirst { it.url == currentUrl }
        return list[(at + 1) % list.size]
    }

    /** Letak jarum penala 0..1 untuk stasiun ke-[index] dari [count] (tersebar merata di skala). */
    fun needleAt(index: Int, count: Int): Float =
        if (count <= 1) 0.5f else 0.1f + 0.8f * (index.coerceIn(0, count - 1).toFloat() / (count - 1))

    fun encode(list: Collection<RadioStation>): String = json.encodeToString(ListSerializer(RadioStation.serializer()), sanitize(list))

    /** Berkas rusak atau kosong = tidak ada stasiun. */
    fun decode(raw: String?): List<RadioStation> {
        if (raw.isNullOrBlank()) return emptyList()
        val list = runCatching { json.decodeFromString(ListSerializer(RadioStation.serializer()), raw) }.getOrDefault(emptyList())
        return sanitize(list)
    }
}

/**
 * Daftar stasiun radio di meja. Nama dan alamat stasiun mencerminkan selera pengguna, jadi disimpan TERENKRIPSI
 * (AES-256-GCM, kunci di Android Keystore) di SharedPreferences privat aplikasi ini saja: tidak ikut cadangan atau
 * preset dan tidak dikirim ke mana pun. Alamat hanya dihubungi saat pengguna menyalakan radio. Pola penyimpanannya sama
 * dengan [PhoneBook]: baca/tulis di antrean latar berurutan, [stations] kosong sesaat sesudah aplikasi dibuka, dan bila
 * enkripsi tidak tersedia perubahan TIDAK disimpan ([saveFailed] menyala).
 */
class RadioStationBook internal constructor(private val text: SealedText, private val worker: SerialWorker) {
    private val _stations = MutableStateFlow<List<RadioStation>>(emptyList())
    private val _saveFailed = MutableStateFlow(false)
    // Hanya disentuh di dalam antrean [worker]
    private var loaded = false

    val stations: StateFlow<List<RadioStation>> = _stations.asStateFlow()

    /** true bila perubahan terakhir gagal disimpan karena penyimpanan terenkripsi tidak tersedia di perangkat ini. */
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    init {
        worker.post { ensureLoaded() }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _stations.value = RadioRules.decode(text.read())
    }

    /** Antrekan penambahan. false bila stasiun tidak sah atau daftar sudah penuh (tidak ada yang berubah). */
    fun add(s: RadioStation): Boolean {
        if (RadioRules.added(_stations.value, s) == null) return false
        worker.post {
            ensureLoaded()
            RadioRules.added(_stations.value, s)?.let(::commit)
        }
        return true
    }

    fun remove(url: String) {
        worker.post {
            ensureLoaded()
            if (_stations.value.any { it.url == url }) commit(_stations.value.filterNot { it.url == url })
        }
    }

    fun clearAll() {
        worker.post {
            loaded = true
            _stations.value = emptyList()
            _saveFailed.value = false
            text.write(null)
        }
    }

    private fun commit(next: List<RadioStation>) {
        val ok = text.write(if (next.isEmpty()) null else RadioRules.encode(next))
        if (ok) _stations.value = next
        _saveFailed.value = !ok
    }

    companion object {
        private const val FILE = "retro_radio"
        private const val KEY_SEALED = "stations_sealed"

        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: RadioStationBook? = null

        fun get(context: Context): RadioStationBook =
            instance ?: synchronized(this) { instance ?: create(context.applicationContext).also { instance = it } }

        private fun create(app: Context): RadioStationBook {
            val slots = PrefsSlots { app.getSharedPreferences(FILE, Context.MODE_PRIVATE) }
            return RadioStationBook(SealedText(slots, SystemVault.vault, VaultPurpose.RADIO, KEY_SEALED), SerialWorker())
        }
    }
}
