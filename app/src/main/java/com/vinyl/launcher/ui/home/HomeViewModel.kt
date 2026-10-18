package com.vinyl.launcher.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vinyl.launcher.data.AppInfo
import com.vinyl.launcher.data.AppSettings
import com.vinyl.launcher.data.AppShortcut
import com.vinyl.launcher.data.AppsRepository
import com.vinyl.launcher.data.BadgeRepository
import com.vinyl.launcher.data.ContactHit
import com.vinyl.launcher.data.ContactsRepository
import com.vinyl.launcher.data.HistoryRepository
import com.vinyl.launcher.data.HomeSettings
import com.vinyl.launcher.data.PlayRecord
import com.vinyl.launcher.data.SettingsMigration
import com.vinyl.launcher.util.SoundFx
import com.vinyl.launcher.data.MAX_DOCK_APPS
import com.vinyl.launcher.data.SettingsRepository
import com.vinyl.launcher.data.SystemSettingsLauncher
import com.vinyl.launcher.data.ThemePreset
import com.vinyl.launcher.data.UsageStore
import com.vinyl.launcher.drawer.AppCategory
import com.vinyl.launcher.drawer.SystemSetting
import com.vinyl.launcher.drawer.UsageData
import com.vinyl.launcher.media.MediaRepository
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.media.playFromSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel home screen.
 * Mode edit memakai "draft": perubahan langsung terlihat (pratinjau) tapi baru
 * disimpan ke DataStore saat pengguna menekan Simpan.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository.get(app)
    private val appsRepo = AppsRepository.get(app)
    private val usageStore = UsageStore.get(app)

    val settings: StateFlow<AppSettings> = settingsRepo.settings
    val nowPlaying: StateFlow<NowPlaying> = MediaRepository.state

    /** Aplikasi yang terlihat di drawer (tanpa yang disembunyikan). */
    val visibleApps: StateFlow<List<AppInfo>> = combine(appsRepo.apps, settings) { apps, s ->
        apps.filter { it.key !in s.hiddenApps }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allApps: StateFlow<List<AppInfo>> = appsRepo.apps

    /** Riwayat peluncuran lokal untuk "Sering dipakai". */
    val usage: StateFlow<UsageData> = usageStore.usage
    /** Jumlah notifikasi per paket (kosong bila akses notifikasi tidak diberikan atau lencana dimatikan). */
    val badges: StateFlow<Map<String, Int>> = BadgeRepository.counts

    /** Aplikasi di dock vinyl, sesuai urutan yang disimpan. */
    val dockApps: StateFlow<List<AppInfo>> = combine(appsRepo.apps, settings) { apps, s ->
        s.dock.apps.mapNotNull { key -> apps.firstOrNull { it.key == key } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val queue: StateFlow<List<MediaRepository.QueueEntry>> = MediaRepository.queue
    val history: StateFlow<List<PlayRecord>> = HistoryRepository.records

    /*
     * Tiga halaman beranda (lihat PageNav). Halaman aktif hidup di ViewModel sehingga bertahan saat rotasi/ubah ukuran.
     * Setelah proses dimatikan sistem, launcher sengaja kembali ke ruang TV: HomeActivity memakai stateNotNeeded (perilaku
     * standar launcher), jadi sistem tidak menyimpan state-nya, dan "Home" memang berarti ruang TV.
     */
    private val _page = MutableStateFlow(RoomPage.ROOM)
    val page: StateFlow<RoomPage> = _page.asStateFlow()

    /** Pindah ke [target]; drawer & lirik ditutup. Diabaikan selama mode edit. */
    fun showPage(target: RoomPage) {
        if (_draft.value != null) return
        _drawerOpen.value = false
        _lyricsOpen.value = false
        _page.value = target
    }

    /** Rak kaset (dulu "rak piringan") kini halaman kiri; nama lama dipertahankan untuk aksi gestur CRATE. */
    val crateOpen: StateFlow<Boolean> = _page.map { it == RoomPage.LIBRARY }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    fun openCrate() = showPage(RoomPage.LIBRARY)
    fun closeCrate() { if (_page.value == RoomPage.LIBRARY) _page.value = RoomPage.ROOM }

    private val _lyricsOpen = MutableStateFlow(false)
    /** Lirik layar penuh. */
    val lyricsOpen: StateFlow<Boolean> = _lyricsOpen.asStateFlow()
    fun openLyrics() { if (_draft.value == null) { _drawerOpen.value = false; _lyricsOpen.value = true } }
    fun closeLyrics() { _lyricsOpen.value = false }
    fun clearHistory() = HistoryRepository.clear()

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()

    private val _draft = MutableStateFlow<HomeSettings?>(null)
    /** Non-null saat mode edit aktif. */
    val draft: StateFlow<HomeSettings?> = _draft.asStateFlow()

    /** Gaya yang sedang ditampilkan (draft saat edit, tersimpan saat normal). */
    val home: StateFlow<HomeSettings> = combine(settings, _draft) { s, d -> d ?: s.home }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeSettings())

    // ---------- Drawer ----------
    fun openDrawer() { if (_draft.value == null) { _lyricsOpen.value = false; _drawerOpen.value = true } }
    fun closeDrawer() { _drawerOpen.value = false }

    /** Tombol Home ditekan saat launcher sudah di depan: tutup drawer/editor. */
    fun onHomePressed() {
        _drawerOpen.value = false
        _lyricsOpen.value = false
        _page.value = RoomPage.ROOM
        if (_draft.value != null) cancelEdit()
    }

    // ---------- Mode edit ----------
    fun startEdit() {
        _drawerOpen.value = false
        _lyricsOpen.value = false
        _page.value = RoomPage.ROOM
        _draft.value = settings.value.home
    }
    fun updateDraft(transform: (HomeSettings) -> HomeSettings) = _draft.update { it?.let(transform) }
    fun cancelEdit() { _draft.value = null }
    fun saveEdit() {
        val d = _draft.value ?: return
        viewModelScope.launch { settingsRepo.update { it.copy(home = d) } }
        _draft.value = null
    }

    /** Pengaturan non-gaya (mis. gestur) disimpan langsung tanpa draft. */
    fun updateSettings(transform: (AppSettings) -> AppSettings) = viewModelScope.launch { settingsRepo.update(transform) }

    // ---------- Preset tema ----------
    fun savePreset(name: String) = viewModelScope.launch {
        val home = _draft.value ?: settings.value.home
        settingsRepo.update {
            it.copy(presets = it.presets + ThemePreset(System.currentTimeMillis(), name.ifBlank { "Preset ${it.presets.size + 1}" }, home, it.cover, it.retro))
        }
    }
    fun applyPreset(preset: ThemePreset) {
        if (_draft.value != null) _draft.value = preset.home
        else viewModelScope.launch { settingsRepo.update { it.copy(home = preset.home, cover = preset.cover, retro = SettingsMigration.retroFor(preset)) } }
    }
    fun deletePreset(id: Long) = viewModelScope.launch {
        settingsRepo.update { s -> s.copy(presets = s.presets.filterNot { it.id == id }) }
    }

    // ---------- Aplikasi ----------
    fun launch(app: AppInfo) { appsRepo.launch(app); closeDrawer() }

    // ---------- Drawer: pintasan, kategori, pencarian ----------
    suspend fun shortcutsFor(app: AppInfo): List<AppShortcut> = appsRepo.shortcuts(app)
    fun startShortcut(shortcut: AppShortcut) { appsRepo.startShortcut(shortcut); closeDrawer() }

    /** [category] null = kembali ke kategori otomatis. */
    fun setCategory(app: AppInfo, category: AppCategory?) = viewModelScope.launch {
        settingsRepo.update { s ->
            val map = s.drawer.categoryOverrides.toMutableMap()
            if (category == null) map.remove(app.key) else map[app.key] = category.name
            s.copy(drawer = s.drawer.copy(categoryOverrides = map))
        }
    }

    fun openSystemSetting(setting: SystemSetting) { SystemSettingsLauncher.open(getApplication(), setting); closeDrawer() }
    fun playSong(record: PlayRecord) { playFromSearch(getApplication(), record); closeDrawer() }
    suspend fun searchContacts(query: String): List<ContactHit> = ContactsRepository.search(getApplication(), query)
    fun openContact(hit: ContactHit) { ContactsRepository.open(getApplication(), hit); closeDrawer() }
    fun clearUsage() = usageStore.clear()
    fun appInfo(app: AppInfo) = appsRepo.openAppInfo(app)
    fun uninstall(app: AppInfo) = appsRepo.uninstall(app)
    fun hideApp(app: AppInfo) = viewModelScope.launch {
        settingsRepo.update { it.copy(hiddenApps = it.hiddenApps + app.key) }
    }

    fun addToDock(app: AppInfo) = viewModelScope.launch {
        settingsRepo.update { s ->
            if (app.key in s.dock.apps || s.dock.apps.size >= MAX_DOCK_APPS) s
            else s.copy(dock = s.dock.copy(apps = s.dock.apps + app.key))
        }
    }
    fun replaceDock(index: Int, app: AppInfo) = viewModelScope.launch {
        settingsRepo.update { s ->
            val list = s.dock.apps.toMutableList()
            if (index in list.indices) list[index] = app.key
            s.copy(dock = s.dock.copy(apps = list.distinct()))
        }
    }
    fun removeDock(index: Int) = viewModelScope.launch {
        settingsRepo.update { s ->
            s.copy(dock = s.dock.copy(apps = s.dock.apps.filterIndexed { i, _ -> i != index }))
        }
    }

    // ---------- Musik ----------
    fun togglePlay() = MediaRepository.togglePlayPause("Turntable")
    fun next() { SoundFx.flip(getApplication(), settings.value.effects); MediaRepository.next() }
    fun previous() { SoundFx.flip(getApplication(), settings.value.effects); MediaRepository.previous() }
    fun seek(ms: Long) = MediaRepository.seekTo(ms)
    fun openMusic() = MediaRepository.openMusicApp(getApplication())

    // ---------- Efek jarum ----------
    fun needleDown() = SoundFx.needleDown(getApplication(), settings.value.effects)
    fun needleUp() = SoundFx.needleUp(getApplication(), settings.value.effects)
    fun tick() = SoundFx.tick(getApplication(), settings.value.effects)
}
