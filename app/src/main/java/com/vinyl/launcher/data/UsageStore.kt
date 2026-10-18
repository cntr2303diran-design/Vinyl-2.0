package com.vinyl.launcher.data

import android.annotation.SuppressLint
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vinyl.launcher.drawer.UsageData
import com.vinyl.launcher.drawer.UsageRanking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.usageDataStore: DataStore<Preferences> by preferencesDataStore(name = "vinyl_usage")

/**
 * Riwayat peluncuran aplikasi untuk baris "Sering dipakai". Hanya kunci aplikasi dan cap waktu, disimpan di
 * DataStore terpisah dari pengaturan (sengaja TIDAK masuk berkas cadangan), tidak pernah dikirim ke mana pun,
 * dan bisa dihapus sepenuhnya lewat [clear].
 */
class UsageStore private constructor(private val context: Context) {
    private val key = stringPreferencesKey("usage_json")
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Riwayat terkini, sudah dibersihkan dari nilai rusak. */
    val usage: StateFlow<UsageData> = context.usageDataStore.data
        .map { decode(it[key]) }
        .stateIn(scope, SharingStarted.Eagerly, UsageData())

    private fun decode(raw: String?): UsageData {
        val parsed = raw?.let { runCatching { json.decodeFromString(UsageData.serializer(), it) }.getOrNull() } ?: return UsageData()
        return UsageRanking.sanitize(parsed, System.currentTimeMillis())
    }

    /** Catat satu peluncuran [appKey]. Tidak memblokir pemanggil. */
    fun record(appKey: String) = update { UsageRanking.record(it, appKey, System.currentTimeMillis()) }

    /** Lupakan aplikasi yang sudah dihapus dari perangkat. */
    fun forgetPackage(packageName: String) = update { UsageRanking.forgetPackage(it, packageName) }

    /** Hapus seluruh riwayat. */
    fun clear() {
        scope.launch { try { context.usageDataStore.edit { it.remove(key) } } catch (e: IOException) { /* gagal tulis: riwayat tetap seperti semula */ } }
    }

    // DataStore.edit berjalan serial (transaksional), jadi baca-ubah-tulis di bawah ini tidak saling menimpa.
    private fun update(transform: (UsageData) -> UsageData) {
        scope.launch {
            try {
                context.usageDataStore.edit { prefs ->
                    prefs[key] = json.encodeToString(UsageData.serializer(), transform(decode(prefs[key])))
                }
            } catch (e: IOException) {
                // Riwayat hanyalah kenyamanan; gagal menulis tidak boleh mengganggu peluncuran aplikasi
            }
        }
    }

    companion object {
        // Yang disimpan adalah applicationContext (hidup sepanjang proses), bukan Activity: tidak bocor
        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: UsageStore? = null
        fun get(context: Context): UsageStore =
            instance ?: synchronized(this) {
                instance ?: UsageStore(context.applicationContext).also { instance = it }
            }
    }
}
