package com.vinyl.launcher.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vinyl_settings")

/**
 * Sumber kebenaran tunggal untuk semua pengaturan.
 * Disimpan sebagai JSON di DataStore Preferences. Setiap nilai yang dibaca dibersihkan oleh
 * [SettingsSanitizer] sehingga data rusak/tidak masuk akal tidak pernah sampai ke UI.
 */
class SettingsRepository private constructor(private val context: Context) {

    private val key = stringPreferencesKey("settings_json")
    private val json = SettingsMigration.storeJson
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Folder gambar yang boleh dirujuk pengaturan (hasil salinan dari galeri). */
    private val imagesDir: File get() = File(context.filesDir, IMAGES_DIR)

    /** Aliran mentah dari DataStore (tanpa nilai awal palsu). */
    val flow: Flow<AppSettings> = context.dataStore.data
        .map { prefs -> prefs[key]?.let { decode(it) } ?: AppSettings() }

    /** Pengaturan terkini; dibagikan ke semua layar. */
    val settings: StateFlow<AppSettings> = flow.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private fun decode(raw: String): AppSettings =
        runCatching { SettingsMigration.decode(json, raw) }
            .map { SettingsSanitizer.sanitize(it, ::isAllowedImage) }
            .getOrDefault(AppSettings())

    /** Gambar hanya boleh berupa file biasa yang ada di folder internal aplikasi. */
    private fun isAllowedImage(path: String): Boolean = runCatching {
        val f = File(path)
        f.isFile && f.canonicalFile.parentFile == imagesDir.canonicalFile
    }.getOrDefault(false)

    /** Ambil nilai yang benar-benar tersimpan (menunggu DataStore selesai dibaca). */
    suspend fun current(): AppSettings = withContext(Dispatchers.IO) {
        context.dataStore.data.first()[key]?.let { decode(it) } ?: AppSettings()
    }

    /** Ubah pengaturan secara atomik. */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val now = prefs[key]?.let { decode(it) } ?: AppSettings()
            prefs[key] = json.encodeToString(AppSettings.serializer(), transform(now))
        }
    }

    /** Reset gaya ke default, tetap mempertahankan status onboarding & aplikasi tersembunyi. */
    suspend fun resetToDefaults() = update {
        AppSettings(onboardingDone = it.onboardingDone, coverEnabled = it.coverEnabled)
    }

    /** Pengaturan sebagai teks cadangan (tanpa path gambar lokal). */
    suspend fun exportBackup(): String = SettingsBackup.export(current())

    /** Pulihkan dari teks cadangan. Mengembalikan pesan kesalahan, atau null bila berhasil. */
    suspend fun importBackup(text: String): String? {
        val result = SettingsBackup.parse(text, current(), ::isAllowedImage)
        return when (result) {
            is SettingsBackup.ImportResult.Ok -> { update { result.settings }; null }
            is SettingsBackup.ImportResult.Error -> result.message
        }
    }

    /**
     * Menyalin gambar dari galeri ke penyimpanan internal agar tetap bisa dibaca
     * setelah izin URI sementara kedaluwarsa. Mengembalikan path file, atau null bila gagal
     * atau gambar lebih besar dari [MAX_IMAGE_BYTES].
     */
    /** Hapus satu gambar yang diimpor (hanya berkas di folder gambar internal aplikasi). Dipakai untuk foto poster. */
    suspend fun deleteImage(path: String): Boolean = withContext(Dispatchers.IO) {
        val f = File(path)
        isAllowedImage(path) && runCatching { f.delete() }.getOrDefault(false)
    }

    suspend fun importImage(uri: Uri, name: String): String? = withContext(Dispatchers.IO) {
        val out = File(imagesDir.apply { mkdirs() }, "${name.filter { it.isLetterOrDigit() }.take(24)}-${System.currentTimeMillis()}.img")
        val ok = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { sink ->
                    val buf = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_IMAGE_BYTES) error("gambar terlalu besar")
                        sink.write(buf, 0, n)
                    }
                }
                true
            } ?: false
        }.getOrDefault(false)
        // Gambar lama sengaja tidak dihapus: bisa jadi masih dipakai preset tema.
        if (ok) out.absolutePath else { out.delete(); null }
    }

    companion object {
        private const val IMAGES_DIR = "backgrounds"
        private const val MAX_IMAGE_BYTES = 30L * 1024 * 1024

        // Yang disimpan adalah applicationContext (hidup sepanjang proses), bukan Activity: tidak bocor
        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: SettingsRepository? = null
        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
