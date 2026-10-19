package com.vinyl.launcher

import android.app.Application
import com.vinyl.launcher.advanced.AdvancedAccess
import com.vinyl.launcher.audio.AudioConsoleRuntime
import com.vinyl.launcher.audio.BluetoothBattery
import com.vinyl.launcher.data.AppsRepository
import com.vinyl.launcher.data.HistoryRepository
import com.vinyl.launcher.data.SettingsRepository
import com.vinyl.launcher.media.MediaRepository
import com.vinyl.launcher.media.SleepTimer
import com.vinyl.launcher.service.CoverService
import com.vinyl.launcher.service.NavButtonsService
import com.vinyl.launcher.service.TaskbarService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Inisialisasi repository bersama dan sinkronisasi pengaturan → layanan. */
class VinylApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        HistoryRepository.init(this)
        MediaRepository.init(this)
        MediaRepository.connect()
        AppsRepository.get(this) // muat daftar aplikasi lebih awal
        SleepTimer.init(this)    // siapkan pengatur volume dan pulihkan volume yang tertinggal bila proses sempat mati

        val settings = SettingsRepository.get(this).settings
        // Audio Console: sesi efek dari aplikasi musik, EQ, dan aturan otomasi (tanpa layanan latar tambahan)
        AdvancedAccess.start(this)
        // Level terakhir yang diingat (perkiraan sampai laporan baru datang); dibaca di thread latar
        appScope.launch { BluetoothBattery.load(this@VinylApp) }
        BluetoothBattery.refresh(this) // hanya mendaftar bila izinnya sudah diberikan
        AudioConsoleRuntime.start(this, appScope)
        appScope.launch {
            settings.map { it.defaultMusicPackage }.distinctUntilChanged()
                .collect { MediaRepository.defaultMusicPackage = it }
        }
        appScope.launch {
            settings.map { it.music.softSkip }.distinctUntilChanged()
                .collect { MediaRepository.softSkipEnabled = it }
        }
        appScope.launch {
            // Nyalakan/matikan layanan layar kunci sesuai pengaturan
            SettingsRepository.get(this@VinylApp).flow.map { it.coverEnabled }.distinctUntilChanged().collect { enabled ->
                if (enabled) CoverService.start(this@VinylApp) else CoverService.stop(this@VinylApp)
            }
        }
        appScope.launch {
            // Tombol navigasi Vinyl menyala/mati sesuai pengaturan
            SettingsRepository.get(this@VinylApp).flow.map { it.nav.enabled }.distinctUntilChanged().collect { enabled ->
                if (enabled) NavButtonsService.start(this@VinylApp) else NavButtonsService.stop(this@VinylApp)
            }
        }
        appScope.launch {
            // Taskbar Vinyl menyala/mati sesuai pengaturan
            SettingsRepository.get(this@VinylApp).flow.map { it.dock.enabled }.distinctUntilChanged().collect { enabled ->
                if (enabled) TaskbarService.start(this@VinylApp) else TaskbarService.stop(this@VinylApp)
            }
        }
    }
}
