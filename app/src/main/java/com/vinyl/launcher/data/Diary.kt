package com.vinyl.launcher.data

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Satu baris buku harian: lagu apa yang diputar kapan. */
@Serializable
data class DiaryEntry(val at: Long, val title: String, val artist: String = "")

/** Entri satu hari, terbaru dulu. */
data class DiaryDay(val date: LocalDate, val entries: List<DiaryEntry>)

/** Aturan buku harian (murni, teruji). */
object DiaryRules {
    const val MAX_ENTRIES = 500
    const val MAX_TEXT = 120
    /** Lagu yang sama dalam jarak ini dianggap satu kali putar (pemicu ganda). */
    const val DEDUPE_MS = 60_000L

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    fun clean(e: DiaryEntry): DiaryEntry? {
        val title = e.title.filterNot { it.isISOControl() }.trim().take(MAX_TEXT)
        if (title.isEmpty() || e.at <= 0L) return null
        return DiaryEntry(e.at, title, e.artist.filterNot { it.isISOControl() }.trim().take(MAX_TEXT))
    }

    /** Daftar (lama → baru) sesudah menambah [e]; tidak berubah bila kosong/ganda. Yang terlama dibuang bila lewat [MAX_ENTRIES]. */
    fun append(list: List<DiaryEntry>, e: DiaryEntry): List<DiaryEntry> {
        val c = clean(e) ?: return list
        val last = list.lastOrNull()
        if (last != null && last.title == c.title && last.artist == c.artist && c.at - last.at in 0..DEDUPE_MS) return list
        return (list + c).takeLast(MAX_ENTRIES)
    }

    fun encode(list: List<DiaryEntry>): String = json.encodeToString(ListSerializer(DiaryEntry.serializer()), list.takeLast(MAX_ENTRIES))

    /** Isi yang rusak jadi kosong; entri tak sah dibuang; urut lama → baru dan dibatasi [MAX_ENTRIES]. */
    fun decode(raw: String?): List<DiaryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        val list = runCatching { json.decodeFromString(ListSerializer(DiaryEntry.serializer()), raw) }.getOrDefault(emptyList())
        return list.mapNotNull(::clean).sortedBy { it.at }.takeLast(MAX_ENTRIES)
    }

    /** Kelompok per hari (menurut [zone]), hari terbaru dulu dan di dalam hari juga terbaru dulu. */
    fun groupByDay(list: List<DiaryEntry>, zone: ZoneId): List<DiaryDay> =
        list.sortedByDescending { it.at }
            .groupBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }
            .map { (date, entries) -> DiaryDay(date, entries) }
            .sortedByDescending { it.date }
}

/**
 * Penyimpanan buku harian kaset. Hanya merekam bila pengguna menyalakannya (default MATI). Disimpan di SharedPreferences
 * "retro_diary" di perangkat ini saja: tidak ikut berkas cadangan atau preset, tidak dikirim ke mana pun, dan hilang bersama
 * data aplikasi atau lewat [clearAll]. Mematikan perekaman tidak menghapus isi yang sudah ada.
 */
class DiaryStore private constructor(private val context: Context) {
    private val prefs get() = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(DiaryRules.decode(prefs.getString(KEY_ENTRIES, null)))
    private val _recording = MutableStateFlow(prefs.getBoolean(KEY_RECORDING, false))

    /** Urut lama → baru. */
    val entries: StateFlow<List<DiaryEntry>> = _entries.asStateFlow()
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    fun setRecording(on: Boolean) {
        _recording.value = on
        prefs.edit { putBoolean(KEY_RECORDING, on) }
    }

    /** Catat satu lagu bila perekaman menyala. */
    fun record(title: String, artist: String, at: Long = System.currentTimeMillis()) {
        if (!_recording.value) return
        val next = DiaryRules.append(_entries.value, DiaryEntry(at, title, artist))
        if (next === _entries.value) return
        save(next)
    }

    fun delete(entry: DiaryEntry) {
        val next = _entries.value.filterNot { it == entry }
        if (next.size != _entries.value.size) save(next)
    }

    fun clearAll() {
        _entries.value = emptyList()
        prefs.edit { remove(KEY_ENTRIES) }
    }

    private fun save(list: List<DiaryEntry>) {
        _entries.value = list
        prefs.edit { putString(KEY_ENTRIES, DiaryRules.encode(list)) }
    }

    companion object {
        private const val FILE = "retro_diary"
        private const val KEY_ENTRIES = "entries"
        private const val KEY_RECORDING = "recording"
        // Yang disimpan adalah applicationContext (hidup sepanjang proses), bukan Activity: tidak bocor
        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: DiaryStore? = null

        fun get(context: Context): DiaryStore =
            instance ?: synchronized(this) { instance ?: DiaryStore(context.applicationContext).also { instance = it } }

        /** Dipanggil perekam riwayat (tanpa Context); tidak melakukan apa-apa sebelum [get] dipanggil dari Application. */
        fun recordIfOn(title: String, artist: String) {
            instance?.record(title, artist)
        }
    }
}
