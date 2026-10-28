package com.vinyl.launcher.ui.home

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vinyl.launcher.data.AppInfo
import com.vinyl.launcher.data.AppSettings
import com.vinyl.launcher.data.AppShortcut
import com.vinyl.launcher.data.AppsRepository
import com.vinyl.launcher.data.BadgeRepository
import com.vinyl.launcher.data.ContactHit
import com.vinyl.launcher.data.ContactsRepository
import com.vinyl.launcher.data.FilmCassette
import com.vinyl.launcher.data.FilmResume
import com.vinyl.launcher.data.FilmResumeData
import com.vinyl.launcher.data.FilmResumeStore
import com.vinyl.launcher.data.FilmRules
import com.vinyl.launcher.data.FilmStart
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
import com.vinyl.launcher.ui.film.FilmProbe
import com.vinyl.launcher.ui.film.FilmResumeSnapshot
import com.vinyl.launcher.ui.film.FilmSample
import com.vinyl.launcher.ui.film.MusicEffect
import com.vinyl.launcher.ui.film.Tape
import com.vinyl.launcher.ui.film.TapeFlow
import com.vinyl.launcher.ui.film.TapeState
import com.vinyl.launcher.ui.film.TapeStep
import com.vinyl.launcher.ui.film.VideoStatus
import com.vinyl.launcher.ui.game.Dir
import com.vinyl.launcher.ui.game.Games
import com.vinyl.launcher.ui.game.Snake
import com.vinyl.launcher.ui.game.SnakeEvent
import com.vinyl.launcher.ui.game.SnakeHud
import com.vinyl.launcher.ui.game.SnakePhase
import com.vinyl.launcher.ui.game.SnakeState
import com.vinyl.launcher.ui.game.hud
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ViewModel home screen.
 * Mode edit memakai "draft": perubahan langsung terlihat (pratinjau) tapi baru
 * disimpan ke DataStore saat pengguna menekan Simpan.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository.get(app)
    private val appsRepo = AppsRepository.get(app)
    private val usageStore = UsageStore.get(app)
    private val resumeStore = FilmResumeStore.get(app)

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
        _trayOpen.value = false
        _page.value = target
    }

    /** Rak kaset (dulu "rak piringan") kini halaman kiri; nama lama dipertahankan untuk aksi gestur CRATE. */
    val crateOpen: StateFlow<Boolean> = _page.map { it == RoomPage.LIBRARY }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    fun openCrate() = showPage(RoomPage.LIBRARY)
    fun closeCrate() { if (_page.value == RoomPage.LIBRARY) _page.value = RoomPage.ROOM }

    private val _lyricsOpen = MutableStateFlow(false)
    /** Lirik layar penuh. */
    val lyricsOpen: StateFlow<Boolean> = _lyricsOpen.asStateFlow()
    fun openLyrics() { if (_draft.value == null) { _drawerOpen.value = false; _trayOpen.value = false; _lyricsOpen.value = true } }
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
    fun openDrawer() { if (_draft.value == null) { _lyricsOpen.value = false; _trayOpen.value = false; _theatre.value = false; _drawerOpen.value = true } }
    fun closeDrawer() { _drawerOpen.value = false }

    /** Tombol Home ditekan saat launcher sudah di depan: tutup drawer/editor. */
    fun onHomePressed() {
        _drawerOpen.value = false
        _lyricsOpen.value = false
        _trayOpen.value = false
        _theatre.value = false
        _page.value = RoomPage.ROOM
        if (_draft.value != null) cancelEdit()
    }

    // ---------- Mode edit ----------
    fun startEdit() {
        _drawerOpen.value = false
        _lyricsOpen.value = false
        _trayOpen.value = false
        _theatre.value = false
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
    /** Tombol putar/jeda di ruang TV: bunyi tombol deck kaset (sesuai Pengaturan → Suara & getar), lalu perintahnya. */
    fun togglePlay() {
        wakeTv()
        if (MediaRepository.state.value.isPlaying) SoundFx.stopKey(getApplication(), settings.value.effects)
        else SoundFx.playKey(getApplication(), settings.value.effects)
        MediaRepository.togglePlayPause("Ruang TV")
    }
    fun next() { wakeTv(); SoundFx.flip(getApplication(), settings.value.effects); MediaRepository.next() }
    fun previous() { wakeTv(); SoundFx.flip(getApplication(), settings.value.effects); MediaRepository.previous() }
    fun seek(ms: Long) = MediaRepository.seekTo(ms)
    fun openMusic() = MediaRepository.openMusicApp(getApplication())

    // ---------- Efek jarum ----------
    fun tick() = SoundFx.tick(getApplication(), settings.value.effects)

    // ---------- Laci kaset di meja ----------
    private val _tape = MutableStateFlow(TapeState())
    /** Kaset di deck: kaset musik (bawaan), kaset film dari laci, atau kaset game. */
    val tape: StateFlow<TapeState> = _tape.asStateFlow()

    private val _trayOpen = MutableStateFlow(false)
    /** Laci di meja terbuka (baki kaset tampil). */
    val trayOpen: StateFlow<Boolean> = _trayOpen.asStateFlow()

    private val _theatre = MutableStateFlow(false)
    /** Mode Teater: film memenuhi layar. Hanya bermakna selama kaset film ada di deck. */
    val theatre: StateFlow<Boolean> = _theatre.asStateFlow()

    private val _film = MutableStateFlow<FilmSample?>(null)

    // ---------- Game Ular di kaset game ----------
    private val snakeRandom = kotlin.random.Random.Default

    private val _snake = MutableStateFlow<SnakeState?>(null)

    /** Papan Ular selama kaset game di deck; null selain itu. Berubah tiap langkah: hanya layar game yang membacanya. */
    val snake: StateFlow<SnakeState?> = _snake.asStateFlow()

    /** Ringkasan papan (fase, skor, rekor) untuk deck, VFD, dan OSD; berubah jarang, aman dibaca beranda. */
    val snakeHud: StateFlow<SnakeHud?> = _snake.map { it?.hud() }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _tvPower = MutableStateFlow(true)

    /** Tombol daya TV: false = TV dimatikan dengan tombolnya. Tidak disimpan; beranda yang baru dimulai selalu menyala. */
    val tvPower: StateFlow<Boolean> = _tvPower.asStateFlow()

    /**
     * Apa yang sedang "diputar" di ruang TV (layar, deck, OSD, proyeksi): lagu dari aplikasi musik saat kaset musik di
     * deck; nama kaset + keadaan video saat kaset film di deck; nama game + keadaan permainan saat kaset game di deck.
     * Perpustakaan, lirik, dan drawer tetap memakai [nowPlaying].
     */
    val roomNowPlaying: StateFlow<NowPlaying> = combine(MediaRepository.state, _tape, _film, settings, snakeHud) { music, t, sample, s, hud ->
        when (val tape = t.tape) {
            is Tape.Film -> filmNowPlaying(s.film.cassettes.firstOrNull { it.id == tape.id }, sample)
            is Tape.Game -> gameNowPlaying(tape.id, hud)
            else -> music
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MediaRepository.state.value)

    /** Titik lanjut tiap kaset film (halaman + menit terakhir; hanya di perangkat ini), untuk tanda "lanjut" di baki. */
    val filmResume: StateFlow<FilmResumeData> = resumeStore.points

    init {
        // Titik lanjut milik kaset yang sudah tidak ada dibuang. Memakai pengaturan yang benar-benar tersimpan
        // (bukan nilai awal sebelum pengaturan selesai dibaca, yang akan menghapus titik kaset buatan pengguna)
        viewModelScope.launch { resumeStore.prune(settingsRepo.current().film.cassettes) }
        // Kaset film yang dihapus dari laci (atau kaset game yang tidak dikenal) saat sedang di deck → kaset musik kembali
        // (musik tidak dilanjutkan otomatis)
        viewModelScope.launch {
            settings.collect { s ->
                _tape.update { TapeFlow.reconcile(it, filmExists = { id -> s.film.cassettes.any { c -> c.id == id } }) }
                if (_tape.value.tape !is Tape.Film) _theatre.value = false
                if (_tape.value.tape !is Tape.Game) _snake.value = null
            }
        }
        // Rekor disetel ulang dari Pengaturan: papan yang sedang terbuka ikut memakai rekor yang lebih rendah itu
        // (rekor hanya pernah dinaikkan oleh game ini, jadi nilai yang lebih rendah selalu berarti penyetelan ulang)
        viewModelScope.launch {
            settings.map { it.film.snakeBest }.distinctUntilChanged().collect { best ->
                _snake.update { s -> if (s != null && best < s.best) s.copy(best = best) else s }
            }
        }
    }

    fun toggleTray() {
        if (_draft.value != null) return
        setTray(!_trayOpen.value)
    }
    fun closeTray() { setTray(false) }

    // Laci di meja berbunyi saat ditarik atau didorong (penutupan senyap oleh halaman/mode lain tidak berbunyi)
    private fun setTray(open: Boolean) {
        if (_trayOpen.value == open) return
        _trayOpen.value = open
        SoundFx.drawer(getApplication(), settings.value.effects, open)
    }

    // ---------- Tombol daya TV ----------
    /** Tombol daya TV ditekan: layar menyusut jadi titik (atau menyala lagi). Game yang berjalan dijeda saat TV dimatikan. */
    fun toggleTvPower() {
        val on = !_tvPower.value
        _tvPower.value = on
        if (on) SoundFx.crtOn(settings.value.effects)
        else {
            SoundFx.crtOff(settings.value.effects)
            snakePause()
        }
    }

    /**
     * Nyalakan TV bila sedang dimatikan dengan tombolnya. True bila tadi mati: sentuhan yang membangunkan TV tidak ikut
     * menjalankan perintahnya (pemanggil berhenti di situ untuk film dan game, yang tadinya dijeda).
     */
    fun wakeTv(): Boolean {
        if (_tvPower.value) return false
        _tvPower.value = true
        SoundFx.crtOn(settings.value.effects)
        return true
    }

    // ---------- Kendali game Ular ----------
    /** Belok ke [d] (usapan, panah deck, aksi aksesibilitas). Gerakan pertama juga memulai permainan. */
    fun snakeTurn(d: Dir) {
        val s = _snake.value ?: return
        if (wakeTv()) return
        val next = Snake.turn(s, d)
        if (next == s) return
        _snake.value = next
        SoundFx.tick(getApplication(), settings.value.effects)
    }

    /** Ketuk: mulai, jeda, lanjut, atau (setelah selesai) main lagi dengan rekor yang dibawa. */
    fun snakeTap() {
        val s = _snake.value ?: return
        if (wakeTv()) return
        val next = Snake.tap(s, snakeRandom)
        _snake.value = next
        if (next.phase == SnakePhase.RUNNING) SoundFx.playKey(getApplication(), settings.value.effects)
        else if (next.phase == SnakePhase.PAUSED) {
            SoundFx.stopKey(getApplication(), settings.value.effects)
            persistSnakeBest(next)
        }
    }

    /** Jeda bila sedang berjalan (ruang TV tidak tampil, TV dimatikan, laci dibuka, aplikasi ke latar); rekor ikut disimpan. */
    fun snakePause() {
        val s = _snake.value ?: return
        val next = Snake.pause(s)
        if (next == s) return
        _snake.value = next
        persistSnakeBest(next)
    }

    /** Jeda antar langkah untuk skor sekarang (makin cepat setiap makan). */
    fun snakeTickMs(): Long = Snake.tickMs(_snake.value?.score ?: 0)

    /** Satu langkah permainan; bunyi dan getar menurut kejadiannya. */
    fun snakeStep() {
        val s = _snake.value ?: return
        val step = Snake.step(s, snakeRandom)
        if (step.state == s) return
        _snake.value = step.state
        val fx = settings.value.effects
        when (step.event) {
            SnakeEvent.ATE -> SoundFx.gameEat(getApplication(), fx)
            SnakeEvent.DIED -> { SoundFx.gameOver(getApplication(), fx); persistSnakeBest(step.state) }
            SnakeEvent.WON -> { SoundFx.gameWin(getApplication(), fx); persistSnakeBest(step.state) }
            SnakeEvent.NONE -> Unit
        }
    }

    /** Rekor tersimpan lokal bersama pengaturan; hanya dinaikkan, tidak pernah diturunkan oleh permainan. */
    private fun persistSnakeBest(s: SnakeState) {
        val best = s.shownBest
        if (best > settings.value.film.snakeBest) {
            updateSettings { cur -> cur.copy(film = FilmRules.withSnakeScore(cur.film, best)) }
        }
    }

    private fun gameNowPlaying(id: String, hud: SnakeHud?): NowPlaying {
        val color = Games.color(id)
        return NowPlaying(
            hasSession = true,
            title = Games.title(id),
            artist = "Game",
            isPlaying = hud?.phase == SnakePhase.RUNNING,
            dominantColor = color,
            accentColor = color,
            swatches = listOf(color),
            // Tidak ada garis waktu: posisi diam, supaya penghitung posisi tidak menyusun ulang OSD tiap detak
            speed = 0f,
        )
    }

    /** Kaset di laci ditekan: masuk ke deck, atau (bila sudah di deck) keluar dan kaset musik kembali. Laci menutup sebentar kemudian. */
    fun pressTape(pressed: Tape) {
        applyTape(TapeFlow.press(_tape.value, pressed, MediaRepository.state.value.isPlaying, settings.value.film.resumeMusic))
        closeTraySoon()
    }

    private fun applyTape(step: TapeStep) {
        val before = _tape.value.tape
        val after = step.state.tape
        // Kaset game keluar dari deck: rekor disimpan; kaset game masuk: papan baru dengan rekor tersimpan
        if (before is Tape.Game) _snake.value?.let { persistSnakeBest(it) }
        _snake.value = if (after is Tape.Game) Snake.newGame(settings.value.film.snakeBest, snakeRandom) else null
        wakeTv()
        _tape.value = step.state
        _film.value = null
        if (step.state.tape !is Tape.Film) _theatre.value = false
        when (step.music) {
            MusicEffect.PAUSE -> MediaRepository.pause("Laci kaset")
            MusicEffect.RESUME -> MediaRepository.play("Laci kaset")
            MusicEffect.NONE -> Unit
        }
        SoundFx.flip(getApplication(), settings.value.effects)
        SoundFx.channel(settings.value.effects)
    }

    // Laci menutup setelah kaset berpindah supaya pergantian kaset di meja terlihat
    private var closeTrayJob: kotlinx.coroutines.Job? = null
    private fun closeTraySoon() {
        closeTrayJob?.cancel()
        closeTrayJob = viewModelScope.launch {
            delay(TRAY_CLOSE_DELAY_MS)
            setTray(false)
        }
    }

    fun openTheatre() {
        if (_tape.value.tape is Tape.Film) { _trayOpen.value = false; _theatre.value = true }
    }
    fun closeTheatre() { _theatre.value = false }

    /**
     * Dari mana kaset film [c] dimulai: halaman terakhir yang tersimpan + menitnya, atau alamat awal kaset. Penyimpanan
     * yang tidak menjawab dalam 2 detik dilewati (kaset tetap terbuka dari alamat awalnya).
     */
    suspend fun filmStart(c: FilmCassette): FilmStart {
        val saved = withTimeoutOrNull(RESUME_READ_TIMEOUT_MS) { resumeStore.current() } ?: FilmResumeData()
        return FilmResume.start(saved, c, System.currentTimeMillis())
    }

    /** Catat titik lanjut dari keadaan pemegang film ([snapshot] null = tidak ada yang dicatat). Tidak memblokir. */
    fun saveFilmPoint(snapshot: FilmResumeSnapshot?) {
        val s = snapshot ?: return
        val c = settings.value.film.cassettes.firstOrNull { it.id == s.cassetteId } ?: return
        resumeStore.record(c, s.pageUrl, s.positionMs, s.durationMs, s.ended)
    }

    /** Lupakan titik lanjut kaset [id] (tombol di Pengaturan, atau kaset dihapus). */
    fun forgetFilmPoint(id: String) = resumeStore.forget(id)

    /** Hasil membaca <video> di halaman film (null = tidak ada video yang terbaca). */
    fun onFilmVideo(status: VideoStatus?) {
        _film.update { FilmProbe.update(it, status, SystemClock.elapsedRealtime()) }
    }

    /** Kaset film baru ([id] null) atau perubahan kaset [id]; alamat yang tidak sah ditolak (editor sudah memeriksanya). */
    fun saveFilmCassette(id: String?, name: String, url: String, color: Int) {
        updateSettings { s ->
            val next = if (id == null) FilmRules.add(s.film, name, url, color) else FilmRules.update(s.film, id, name, url, color)
            if (next == null) s else s.copy(film = next)
        }
    }
    fun deleteFilmCassette(id: String) {
        updateSettings { s -> s.copy(film = FilmRules.remove(s.film, id)) }
        resumeStore.forget(id)
    }
    fun restoreFilmDefaults() {
        updateSettings { s -> s.copy(film = s.film.copy(cassettes = FilmRules.defaults())) }
        resumeStore.prune(FilmRules.defaults())
    }

    private fun filmNowPlaying(c: FilmCassette?, sample: FilmSample?): NowPlaying {
        if (c == null) return NowPlaying()
        val st = sample?.status
        return NowPlaying(
            hasSession = true,
            title = c.name,
            artist = FilmRules.hostOf(c.url),
            durationMs = st?.durationMs ?: 0L,
            isPlaying = st != null && st.playing && !st.ended,
            dominantColor = c.color,
            accentColor = c.color,
            swatches = listOf(c.color),
            positionMs = st?.positionMs ?: 0L,
            positionUpdateTime = sample?.atMs ?: 0L,
            canSeek = st != null && st.durationMs > 0L,
        )
    }

    private companion object {
        const val TRAY_CLOSE_DELAY_MS = 420L
        const val RESUME_READ_TIMEOUT_MS = 2_000L
    }
}
