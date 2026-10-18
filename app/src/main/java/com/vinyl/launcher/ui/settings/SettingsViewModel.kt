package com.vinyl.launcher.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vinyl.launcher.data.AppInfo
import com.vinyl.launcher.data.AppSettings
import com.vinyl.launcher.data.AppsRepository
import com.vinyl.launcher.data.SettingsBackup
import com.vinyl.launcher.data.SettingsRepository
import com.vinyl.launcher.data.UsageStore
import com.vinyl.launcher.util.readAtMost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** ViewModel halaman Pengaturan: semua perubahan langsung disimpan ke DataStore. */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository.get(app)
    val settings: StateFlow<AppSettings> = repo.settings
    val apps: StateFlow<List<AppInfo>> = AppsRepository.get(app).apps

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repo.update(transform) }
    }

    /** Hapus riwayat peluncuran untuk "Sering dipakai". */
    fun clearAppUsage() = UsageStore.get(getApplication()).clear()

    fun reset() {
        viewModelScope.launch { repo.resetToDefaults() }
    }

    /** Tulis cadangan ke [uri] (dipilih pengguna lewat pemilih berkas). Mengembalikan pesan hasil. */
    suspend fun exportBackupTo(uri: Uri): String = withContext(Dispatchers.IO) {
        val ok = runCatching {
            val text = repo.exportBackup()
            val out = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
            out?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
        }.getOrDefault(false)
        if (ok) "Cadangan disimpan." else "Gagal menyimpan cadangan."
    }

    /** Baca cadangan dari [uri] lalu terapkan. Mengembalikan pesan hasil (berhasil atau alasan gagal). */
    suspend fun importBackupFrom(uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = runCatching {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readAtMost(SettingsBackup.MAX_BYTES) }
        }.getOrNull()
            ?: return@withContext "Berkas tidak bisa dibaca atau terlalu besar untuk sebuah cadangan TV Launcher."
        repo.importBackup(bytes.toString(Charsets.UTF_8)) ?: "Pengaturan dipulihkan. Latar gambar dan foto widget perlu dipilih ulang."
    }
}
