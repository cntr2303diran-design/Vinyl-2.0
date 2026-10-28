package com.vinyl.launcher.data

import android.annotation.SuppressLint
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.filmResumeDataStore: DataStore<Preferences> by preferencesDataStore(name = "vinyl_film_resume")

/**
 * Titik lanjut kaset film (halaman terakhir + menit terakhir; aturannya di [FilmResume]). Disimpan di DataStore
 * terpisah dari pengaturan (sengaja TIDAK masuk berkas cadangan), hanya di perangkat ini, tidak pernah dikirim ke mana
 * pun, dan bisa dihapus sepenuhnya lewat [clear] (Pengaturan → Laci kaset).
 *
 * Semua tulis dan baca lewat satu antrean berurutan: pembacaan ([current]) sesudah penulisan ([record]) selalu melihat
 * penulisan itu, dan dua penulisan berurutan tidak saling menimpa dengan data yang lebih tua.
 */
class FilmResumeStore private constructor(private val context: Context) {
    private val key = stringPreferencesKey("film_resume_json")
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (job in queue) {
                try {
                    job()
                } catch (e: IOException) {
                    // Titik lanjut hanyalah kenyamanan; gagal menulis atau membaca tidak boleh mengganggu film
                }
            }
        }
    }

    /** Titik lanjut terkini (untuk tanda "lanjut" di baki); kosong sampai penyimpanan selesai dibaca. */
    val points: StateFlow<FilmResumeData> = context.filmResumeDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { decode(it[key]) }
        .stateIn(scope, SharingStarted.Eagerly, FilmResumeData())

    private fun decode(raw: String?): FilmResumeData =
        raw?.let { runCatching { json.decodeFromString(FilmResumeData.serializer(), it) }.getOrNull() } ?: FilmResumeData()

    /** Yang benar-benar tersimpan sekarang (menunggu penulisan yang sudah diantrekan). Data rusak/tak terbaca = kosong. */
    suspend fun current(): FilmResumeData {
        val result = CompletableDeferred<FilmResumeData>()
        queue.trySend {
            val read = try {
                decode(context.filmResumeDataStore.data.first()[key])
            } catch (e: IOException) {
                FilmResumeData()
            }
            result.complete(read)
        }
        return result.await()
    }

    /** Catat keadaan [cassette] (aturannya di [FilmResume.record]). Tidak memblokir pemanggil. */
    fun record(cassette: FilmCassette, pageUrl: String?, positionMs: Long?, durationMs: Long?, ended: Boolean) {
        val now = System.currentTimeMillis()
        change { FilmResume.record(it, cassette, pageUrl, positionMs, durationMs, ended, now) }
    }

    /** Lupakan titik kaset [id] (kaset dihapus, atau pengguna meminta). */
    fun forget(id: String) = change { FilmResume.forget(it, id) }

    /** Buang titik yang kasetnya sudah tidak ada, yang kedaluwarsa, atau yang alamatnya tidak lagi sah. */
    fun prune(cassettes: List<FilmCassette>) = change { FilmResume.sanitize(it, cassettes, System.currentTimeMillis()) }

    /** Hapus semua titik lanjut. */
    fun clear() {
        queue.trySend { context.filmResumeDataStore.edit { it.remove(key) } }
    }

    // DataStore.edit berjalan serial (transaksional); antrean di atas menjaga urutan pemanggilan
    private fun change(transform: (FilmResumeData) -> FilmResumeData) {
        queue.trySend {
            context.filmResumeDataStore.edit { prefs ->
                val before = decode(prefs[key])
                val after = transform(before)
                if (after != before) {
                    if (after.points.isEmpty()) prefs.remove(key)
                    else prefs[key] = json.encodeToString(FilmResumeData.serializer(), after)
                }
            }
        }
    }

    companion object {
        // Yang disimpan adalah applicationContext (hidup sepanjang proses), bukan Activity: tidak bocor
        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: FilmResumeStore? = null
        fun get(context: Context): FilmResumeStore =
            instance ?: synchronized(this) {
                instance ?: FilmResumeStore(context.applicationContext).also { instance = it }
            }
    }
}
