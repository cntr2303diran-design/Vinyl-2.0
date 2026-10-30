package com.vinyl.launcher.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Jenis catatan buku harian. */
@Serializable
enum class DiaryKind { MUSIC, FILM, GAME }

/**
 * Satu baris buku harian. Baris kedua ([artist]): artis untuk musik, "Skor N" untuk game, kosong untuk film. Film hanya
 * dicatat dengan nama kaset di label (tanpa alamat situs atau halaman); game dengan nama game dan skornya.
 * Catatan lama (tanpa [kind]) terbaca sebagai musik.
 */
@Serializable
data class DiaryEntry(val at: Long, val title: String, val artist: String = "", val kind: DiaryKind = DiaryKind.MUSIC)

/** Entri satu hari, terbaru dulu. */
data class DiaryDay(val date: LocalDate, val entries: List<DiaryEntry>)

/**
 * Apa yang direkam. [on] = saklar utama (mati secara bawaan). Saklar per jenis menyala secara bawaan tetapi baru berlaku
 * bila saklar utama menyala, jadi menyalakan buku harian merekam musik, film, dan game kecuali jenis tertentu dimatikan.
 */
data class DiaryFlags(
    val on: Boolean = false,
    val music: Boolean = true,
    val film: Boolean = true,
    val games: Boolean = true,
) {
    fun accepts(kind: DiaryKind): Boolean = on && enabled(kind)

    fun enabled(kind: DiaryKind): Boolean = when (kind) {
        DiaryKind.MUSIC -> music
        DiaryKind.FILM -> film
        DiaryKind.GAME -> games
    }

    fun withKind(kind: DiaryKind, enabled: Boolean): DiaryFlags = when (kind) {
        DiaryKind.MUSIC -> copy(music = enabled)
        DiaryKind.FILM -> copy(film = enabled)
        DiaryKind.GAME -> copy(games = enabled)
    }
}

/** Aturan buku harian (murni, teruji). */
object DiaryRules {
    const val MAX_ENTRIES = 500
    const val MAX_TEXT = 120
    /** Lagu atau film yang sama dalam jarak ini dianggap satu kali (pemicu ganda). Ronde game tidak diperiksa: tiap ronde nyata. */
    const val DEDUPE_MS = 60_000L

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    fun label(kind: DiaryKind): String = when (kind) {
        DiaryKind.MUSIC -> "Musik"
        DiaryKind.FILM -> "Film"
        DiaryKind.GAME -> "Game"
    }

    fun clean(e: DiaryEntry): DiaryEntry? {
        val title = e.title.filterNot { it.isISOControl() }.trim().take(MAX_TEXT)
        if (title.isEmpty() || e.at <= 0L) return null
        return DiaryEntry(e.at, title, e.artist.filterNot { it.isISOControl() }.trim().take(MAX_TEXT), e.kind)
    }

    /** Daftar (lama → baru) sesudah menambah [e]; tidak berubah bila kosong/ganda. Yang terlama dibuang bila lewat [MAX_ENTRIES]. */
    fun append(list: List<DiaryEntry>, e: DiaryEntry): List<DiaryEntry> {
        val c = clean(e) ?: return list
        // Catatan yang persis sama (waktu, jenis, judul, keterangan) tidak pernah disimpan dua kali, juga untuk game
        if (c in list) return list
        val last = list.lastOrNull()
        if (c.kind != DiaryKind.GAME && last != null && last.kind == c.kind && last.title == c.title && last.artist == c.artist && c.at - last.at in 0..DEDUPE_MS) return list
        return (list + c).takeLast(MAX_ENTRIES)
    }

    fun encode(list: List<DiaryEntry>): String = json.encodeToString(ListSerializer(DiaryEntry.serializer()), list.takeLast(MAX_ENTRIES))

    /** Isi yang rusak jadi kosong; entri tak sah dan kembar persis dibuang; urut lama → baru dan dibatasi [MAX_ENTRIES]. */
    fun decode(raw: String?): List<DiaryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        val list = runCatching { json.decodeFromString(ListSerializer(DiaryEntry.serializer()), raw) }.getOrDefault(emptyList())
        return list.mapNotNull(::clean).distinct().sortedBy { it.at }.takeLast(MAX_ENTRIES)
    }

    /** Kelompok per hari (menurut [zone]), hari terbaru dulu dan di dalam hari juga terbaru dulu. */
    fun groupByDay(list: List<DiaryEntry>, zone: ZoneId): List<DiaryDay> =
        list.sortedByDescending { it.at }
            .groupBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }
            .map { (date, entries) -> DiaryDay(date, entries) }
            .sortedByDescending { it.date }
}

/** Tempat saklar perekaman disimpan (tidak rahasia: hanya hidup/mati). Di aplikasi SharedPreferences; di uji unit peta biasa. */
interface DiaryFlagStore {
    fun read(): DiaryFlags
    fun write(flags: DiaryFlags)
}

class PrefsDiaryFlags(private val prefs: () -> SharedPreferences) : DiaryFlagStore {
    override fun read(): DiaryFlags {
        val p = prefs()
        return DiaryFlags(
            on = p.getBoolean(KEY_ON, false),
            music = p.getBoolean(KEY_MUSIC, true),
            film = p.getBoolean(KEY_FILM, true),
            games = p.getBoolean(KEY_GAMES, true),
        )
    }

    override fun write(flags: DiaryFlags) {
        prefs().edit {
            putBoolean(KEY_ON, flags.on)
            putBoolean(KEY_MUSIC, flags.music)
            putBoolean(KEY_FILM, flags.film)
            putBoolean(KEY_GAMES, flags.games)
        }
    }

    private companion object {
        // "recording" adalah kunci versi lama (hanya satu saklar); artinya sama dengan saklar utama sekarang
        const val KEY_ON = "recording"
        const val KEY_MUSIC = "rec_music"
        const val KEY_FILM = "rec_film"
        const val KEY_GAMES = "rec_games"
    }
}

/**
 * Penyimpanan buku harian. Hanya merekam bila pengguna menyalakannya (saklar utama bawaan MATI), dan hanya jenis yang
 * dinyalakan: musik (judul, artis, waktu), film (nama kaset dan waktu; tanpa alamat situs), game (nama dan skor ronde).
 * Isinya TERENKRIPSI (AES-256-GCM, kunci di Android Keystore) di SharedPreferences "retro_diary" di perangkat ini saja:
 * tidak ikut berkas cadangan atau preset, tidak dikirim ke mana pun, dan hilang bersama data aplikasi atau lewat [clearAll].
 * Catatan lama yang masih polos disegel otomatis saat dibaca pertama kali. Mematikan perekaman tidak menghapus isi.
 *
 * Membaca dan menulis berjalan di antrean latar yang berurutan; [entries] kosong sesaat setelah aplikasi dibuka sampai
 * pembacaan selesai. Bila enkripsi tidak tersedia di perangkat ini, catatan TIDAK disimpan dan [saveFailed] menyala.
 */
class DiaryStore internal constructor(
    private val text: SealedText,
    private val flagStore: DiaryFlagStore,
    private val worker: SerialWorker,
) {
    private val _entries = MutableStateFlow<List<DiaryEntry>>(emptyList())
    private val _flags = MutableStateFlow(flagStore.read())
    private val _saveFailed = MutableStateFlow(false)
    // Hanya disentuh di dalam antrean [worker]
    private var loaded = false

    /** Urut lama → baru. */
    val entries: StateFlow<List<DiaryEntry>> = _entries.asStateFlow()
    val flags: StateFlow<DiaryFlags> = _flags.asStateFlow()

    /** true bila catatan terakhir gagal disimpan karena penyimpanan terenkripsi tidak tersedia di perangkat ini. */
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    init {
        worker.post { ensureLoaded() }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _entries.value = DiaryRules.decode(text.read())
    }

    fun setRecording(on: Boolean) = updateFlags { it.copy(on = on) }

    fun setKind(kind: DiaryKind, enabled: Boolean) = updateFlags { it.withKind(kind, enabled) }

    private fun updateFlags(change: (DiaryFlags) -> DiaryFlags) {
        val next = change(_flags.value)
        if (next == _flags.value) return
        _flags.value = next
        flagStore.write(next)
    }

    /** Catat satu baris bila perekaman dan jenisnya menyala. */
    fun record(kind: DiaryKind, title: String, detail: String = "", at: Long = System.currentTimeMillis()) {
        if (!_flags.value.accepts(kind)) return
        worker.post {
            ensureLoaded()
            val next = DiaryRules.append(_entries.value, DiaryEntry(at, title, detail, kind))
            if (next !== _entries.value) commit(next)
        }
    }

    fun delete(entry: DiaryEntry) {
        worker.post {
            ensureLoaded()
            val next = _entries.value.filterNot { it == entry }
            if (next.size != _entries.value.size) commit(next)
        }
    }

    fun clearAll() {
        worker.post {
            loaded = true
            _entries.value = emptyList()
            _saveFailed.value = false
            text.write(null)
        }
    }

    private fun commit(next: List<DiaryEntry>) {
        val ok = text.write(if (next.isEmpty()) null else DiaryRules.encode(next))
        if (ok) _entries.value = next
        _saveFailed.value = !ok
    }

    companion object {
        private const val FILE = "retro_diary"
        private const val KEY_SEALED = "entries_sealed"
        // Kunci versi lama yang menyimpan JSON polos; hanya dibaca untuk migrasi lalu dihapus
        private const val KEY_LEGACY = "entries"
        // Yang disimpan adalah applicationContext (hidup sepanjang proses), bukan Activity: tidak bocor
        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: DiaryStore? = null

        fun get(context: Context): DiaryStore =
            instance ?: synchronized(this) { instance ?: create(context.applicationContext).also { instance = it } }

        private fun create(app: Context): DiaryStore {
            val prefs = { app.getSharedPreferences(FILE, Context.MODE_PRIVATE) }
            val text = SealedText(PrefsSlots(prefs), SystemVault.vault, VaultPurpose.DIARY, KEY_SEALED, KEY_LEGACY)
            return DiaryStore(text, PrefsDiaryFlags(prefs), SerialWorker())
        }

        /** Dipanggil perekam riwayat (tanpa Context); tidak melakukan apa-apa sebelum [get] dipanggil dari Application. */
        fun recordIfOn(title: String, artist: String) {
            instance?.record(DiaryKind.MUSIC, title, artist)
        }

        /** Satu kaset film dimasukkan ke deck: dicatat dengan nama kaset saja. */
        fun recordFilmIfOn(cassetteName: String) {
            instance?.record(DiaryKind.FILM, cassetteName)
        }

        /** Satu ronde game selesai atau ditinggalkan: dicatat dengan nama game dan keterangan skornya. */
        fun recordGameIfOn(gameTitle: String, detail: String) {
            instance?.record(DiaryKind.GAME, gameTitle, detail)
        }
    }
}
