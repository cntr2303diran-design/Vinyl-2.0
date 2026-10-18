package com.vinyl.launcher.data

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.graphics.scale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Satu lagu di koleksi (riwayat putar). */
@Serializable
data class PlayRecord(
    val key: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val pkg: String? = null,
    val firstPlayed: Long,
    val lastPlayed: Long,
    val plays: Int = 1,
    /** Perkiraan total waktu didengarkan (ms). */
    val listenedMs: Long = 0,
    /** Path thumbnail cover (JPEG kecil di penyimpanan internal). */
    val artFile: String? = null,
    /** Riwayat waktu putar terakhir (maks 50) untuk statistik mingguan. */
    val playTimes: List<Long> = emptyList(),
)

/**
 * Koleksi piringan: riwayat lagu yang pernah diputar, disimpan sebagai JSON di
 * penyimpanan internal (maks 600 lagu, yang paling lama tidak diputar dibuang).
 */
object HistoryRepository {
    private const val MAX = 600
    private lateinit var dir: File
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null
    private var loaded = false

    private val _records = MutableStateFlow<List<PlayRecord>>(emptyList())
    /** Urut dari yang terakhir diputar. */
    val records: StateFlow<List<PlayRecord>> = _records.asStateFlow()

    fun init(context: Context) {
        if (loaded) return
        dir = context.filesDir
        loaded = true
        scope.launch {
            val f = File(dir, "history.json")
            if (f.exists()) {
                _records.value = runCatching {
                    json.decodeFromString(ListSerializer(PlayRecord.serializer()), f.readText())
                }.getOrDefault(emptyList()).sortedByDescending { it.lastPlayed }
            }
        }
    }

    /** Catat satu kali putar. Dipanggil HistoryRecorder setelah lagu didengarkan ≥30 detik. */
    fun recordPlay(key: String, title: String, artist: String, album: String, pkg: String?, art: ImageBitmap?) {
        if (!loaded) return
        val now = System.currentTimeMillis()
        scope.launch {
            val artFile = art?.let { saveThumb(key, it) }
            _records.update { list ->
                val existing = list.firstOrNull { it.key == key }
                val updated = existing?.copy(
                    lastPlayed = now,
                    plays = existing.plays + 1,
                    artFile = artFile ?: existing.artFile,
                    playTimes = (existing.playTimes + now).takeLast(50),
                ) ?: PlayRecord(key, title, artist, album, pkg, now, now, 1, 0, artFile, listOf(now))
                (listOf(updated) + list.filterNot { it.key == key }).take(MAX)
            }
            scheduleSave()
        }
    }

    /** Tambahkan waktu dengar ke lagu (dipanggil saat pause/ganti lagu). */
    fun addListening(key: String, ms: Long) {
        if (!loaded || ms <= 0) return
        _records.update { list -> list.map { if (it.key == key) it.copy(listenedMs = it.listenedMs + ms) else it } }
        scheduleSave()
    }

    fun clear() {
        _records.value = emptyList()
        scheduleSave()
    }

    private fun saveThumb(key: String, art: ImageBitmap): String? = runCatching {
        val covers = File(dir, "covers").apply { mkdirs() }
        val f = File(covers, "${key.hashCode().toUInt()}.jpg")
        if (!f.exists()) {
            val bmp = art.asAndroidBitmap()
            val small = bmp.scale(200, (200f * bmp.height / bmp.width).toInt().coerceAtLeast(1))
            f.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        }
        f.absolutePath
    }.getOrNull()

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1500)
            runCatching {
                val tmp = File(dir, "history.json.tmp")
                tmp.writeText(json.encodeToString(ListSerializer(PlayRecord.serializer()), _records.value))
                tmp.renameTo(File(dir, "history.json"))
            }
        }
    }
}
