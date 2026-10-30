package com.vinyl.launcher.ui.home

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import com.vinyl.launcher.ui.room.LocalRoomInsets
import com.vinyl.launcher.ui.room.RoomInk
import com.vinyl.launcher.ui.room.roomInsets
import com.vinyl.launcher.ui.room.stableRoomInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinyl.launcher.data.ClockSettings
import com.vinyl.launcher.data.HomeSettings
import com.vinyl.launcher.data.OsdSettings
import com.vinyl.launcher.data.WeatherSettings
import com.vinyl.launcher.data.WidgetConfig
import com.vinyl.launcher.data.WidgetType
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.ui.components.LyricsLines
import com.vinyl.launcher.ui.components.rememberLyrics
import com.vinyl.launcher.ui.crate.CrateSheet
import com.vinyl.launcher.ui.lyrics.LyricsActions
import com.vinyl.launcher.ui.lyrics.LyricsScreen
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.vinyl.launcher.ui.drawer.AppDrawer
import com.vinyl.launcher.ui.drawer.DrawerActions
import com.vinyl.launcher.ui.drawer.DrawerData
import com.vinyl.launcher.ui.theme.Ease
import com.vinyl.launcher.ui.theme.Tokens
import com.vinyl.launcher.ui.theme.RetroColors
import com.vinyl.launcher.ui.theme.darken
import com.vinyl.launcher.ui.theme.horizontal
import com.vinyl.launcher.ui.theme.toColor
import com.vinyl.launcher.ui.theme.toComposeColor
import com.vinyl.launcher.util.SystemActions
import com.vinyl.launcher.util.SoundFx
import com.vinyl.launcher.data.DockPosition
import com.vinyl.launcher.data.VisualSettings
import com.vinyl.launcher.data.VisualizerMode
import com.vinyl.launcher.fx.FxPlan
import com.vinyl.launcher.fx.FxRunner
import com.vinyl.launcher.fx.FxState
import com.vinyl.launcher.fx.SpectrumBars
import com.vinyl.launcher.fx.VisualizerPlan
import com.vinyl.launcher.fx.VisualizerStatus
import com.vinyl.launcher.fx.rememberFxPlan
import com.vinyl.launcher.fx.rememberFxState
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.sign
import com.vinyl.launcher.util.GestureRecognizer
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.vinyl.launcher.ui.console.ConsoleRoute
import com.vinyl.launcher.data.VideoRules
import com.vinyl.launcher.media.VideoLibrary
import com.vinyl.launcher.media.SleepTimer
import com.vinyl.launcher.media.VideoMatch
import com.vinyl.launcher.ui.room.ScreenVideo
import com.vinyl.launcher.ui.room.TearCalendarWidget
import com.vinyl.launcher.ui.room.VfdWeatherWidget
import com.vinyl.launcher.ui.room.PolaroidWidget
import com.vinyl.launcher.ui.room.NeedleBatteryWidget
import com.vinyl.launcher.audio.AudioConsoleRuntime
import com.vinyl.launcher.ui.theme.rememberReduceMotion
import com.vinyl.launcher.data.BackgroundType
import com.vinyl.launcher.data.RetroSettings
import com.vinyl.launcher.fx.FxOverlay
import com.vinyl.launcher.fx.rememberPowerSave
import com.vinyl.launcher.ui.components.rememberFileImage
import com.vinyl.launcher.ui.room.rememberWorstCoverPixel
import com.vinyl.launcher.audio.AudioMonitor
import com.vinyl.launcher.data.RoomTone
import com.vinyl.launcher.ui.theme.BackdropDim
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.sp
import com.vinyl.launcher.ui.library.LibraryCatalog
import com.vinyl.launcher.ui.library.rememberLibraryState
import com.vinyl.launcher.data.PhoneBook
import com.vinyl.launcher.data.AchievementInput
import com.vinyl.launcher.data.Achievements
import com.vinyl.launcher.data.DiaryStore
import com.vinyl.launcher.data.HistoryRepository
import com.vinyl.launcher.ui.settings.DiaryDialog
import com.vinyl.launcher.ui.settings.TrophyDialog
import com.vinyl.launcher.ui.settings.rememberImagePicker
import com.vinyl.launcher.ui.room.stage.PropActions
import com.vinyl.launcher.ui.room.stage.RoomStage
import com.vinyl.launcher.player.HiFi
import com.vinyl.launcher.ui.room.stage.SeekState
import com.vinyl.launcher.ui.room.stage.StageActions
import com.vinyl.launcher.ui.room.stage.StageInfo
import com.vinyl.launcher.ui.room.stage.ProjectionContent
import com.vinyl.launcher.ui.room.vhs.VhsFilm
import com.vinyl.launcher.ui.room.VfdTicker
import com.vinyl.launcher.system.DeviceStatusMonitor
import com.vinyl.launcher.system.StatusText
import com.vinyl.launcher.data.ProjectorSource
import com.vinyl.launcher.data.DockSettings
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import com.vinyl.launcher.data.CassetteShell
import com.vinyl.launcher.data.EffectSettings
import com.vinyl.launcher.data.FilmCassette
import com.vinyl.launcher.data.FilmResume
import com.vinyl.launcher.data.FilmRules
import com.vinyl.launcher.data.FilmStart
import com.vinyl.launcher.system.StatusText.Transport
import com.vinyl.launcher.ui.cassette.CassetteLabel
import com.vinyl.launcher.ui.film.FilmCassetteEditor
import com.vinyl.launcher.ui.film.FilmFullscreen
import com.vinyl.launcher.ui.film.FilmTray
import com.vinyl.launcher.ui.film.FilmTvScreen
import com.vinyl.launcher.ui.film.FilmWebHolder
import com.vinyl.launcher.ui.film.Tape
import com.vinyl.launcher.ui.film.TapeFlow
import com.vinyl.launcher.ui.film.TrayItem
import com.vinyl.launcher.ui.game.BreakoutTvScreen
import com.vinyl.launcher.ui.game.Dir
import com.vinyl.launcher.ui.game.GameHud
import com.vinyl.launcher.ui.game.GamePhase
import com.vinyl.launcher.ui.game.Games
import com.vinyl.launcher.ui.game.SnakeTvScreen
import com.vinyl.launcher.ui.room.stage.DeckDisplay
import com.vinyl.launcher.ui.room.stage.DeckTape
import com.vinyl.launcher.ui.room.stage.StageDrawerInfo

/** Warna netral saat tidak ada musik. */
private val NeutralBg = Color(0xFF1C1B19) // charcoal hangat bila tidak ada cover
private val NeutralLabel = Color(0xFF8A8780)

@Composable
fun HomeScreen(vm: HomeViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val np by vm.nowPlaying.collectAsStateWithLifecycle()
    val drawerOpen by vm.drawerOpen.collectAsStateWithLifecycle()
    val visibleApps by vm.visibleApps.collectAsStateWithLifecycle()
    val allApps by vm.allApps.collectAsStateWithLifecycle()
    val lyricsOpen by vm.lyricsOpen.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val page by vm.page.collectAsStateWithLifecycle()
    val tapeState by vm.tape.collectAsStateWithLifecycle()
    val trayOpen by vm.trayOpen.collectAsStateWithLifecycle()
    val theatre by vm.theatre.collectAsStateWithLifecycle()
    val tvPower by vm.tvPower.collectAsStateWithLifecycle()
    val gameHud by vm.gameHud.collectAsStateWithLifecycle()
    val filmResume by vm.filmResume.collectAsStateWithLifecycle()
    // Yang tampil di ruang TV: lagu (kaset musik) atau film dari laci. np tetap lagu aplikasi musik (perpustakaan, lirik, drawer).
    val shown by vm.roomNowPlaying.collectAsStateWithLifecycle()
    val tape = tapeState.tape
    val tapeIsMusic = tape is Tape.Music
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val editing = draft != null

    // Efek visual (Tahap 4): satu keputusan (FxPlan) dari pengaturan + lingkungan; tertutup overlay = semua efek berhenti
    val fx = rememberFxState()
    val fxPlan = rememberFxPlan(settings.visual, shown.isPlaying, screenVisible = (page == RoomPage.ROOM || page == RoomPage.CONSOLE) && !drawerOpen && !lyricsOpen, fx = fx)
    FxRunner(fx, fxPlan)
    val visualizerState by VisualizerStatus.state.collectAsStateWithLifecycle()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Buffer suara disiapkan di thread latar sekali, bukan saat pertama kali dibunyikan
    LaunchedEffect(Unit) { SoundFx.warmUp() }

    // Kresek vinyl (opsional): hanya saat layar utama terlihat & lagu diputar
    val crackleOn = settings.effects.crackle && np.isPlaying
    LifecycleResumeEffect(crackleOn, settings.effects.volume) {
        if (crackleOn) SoundFx.startCrackle(settings.effects.volume)
        onPauseOrDispose { SoundFx.stopCrackle() }
    }

    // Timer tidur selesai → TV "dimatikan" sampai musik diputar lagi
    val slept by SleepTimer.slept.collectAsStateWithLifecycle()
    // Lagu mulai diputar: TV yang dimatikan (timer tidur atau tombol dayanya) menyala lagi
    LaunchedEffect(np.isPlaying) {
        if (np.isPlaying) {
            SleepTimer.clearSlept()
            vm.wakeTv()
        }
    }
    val tvOff = (slept && !np.isPlaying && tapeIsMusic) || !tvPower

    // Bunyi TV: "thunk" saat layar menyala dari siaga, statis singkat saat pindah halaman ("ganti channel")
    var hadTrack by remember { mutableStateOf(shown.hasTrack) }
    LaunchedEffect(shown.hasTrack) {
        if (shown.hasTrack && !hadTrack) SoundFx.crtOn(settings.effects)
        hadTrack = shown.hasTrack
    }
    var lastPage by remember { mutableStateOf(page) }
    LaunchedEffect(page) {
        if (page != lastPage) SoundFx.channel(settings.effects)
        lastPage = page
    }

    // Warna dari cover album (Palette); netral saat tidak ada musik
    val dominant = if (shown.hasTrack) shown.dominantColor?.toComposeColor() ?: NeutralBg else NeutralBg
    val accent = if (shown.hasTrack) shown.accentColor?.toComposeColor() ?: NeutralLabel else NeutralLabel
    val bgColor = dominant.darken(0.12f)

    // ----- Laci kaset: SATU WebView film seumur beranda (dipasang bergantian di layar TV dan di Teater) -----
    val filmHolder = remember { FilmWebHolder(context).also { it.onVideo = vm::onFilmVideo } }
    // Titik lanjut (halaman + menit terakhir) dicatat di perangkat ini, kecuali dimatikan di Pengaturan → Laci kaset
    val saveResume: () -> Unit = remember(vm, filmHolder) {
        { if (vm.settings.value.film.rememberPlace) vm.saveFilmPoint(filmHolder.resumeSnapshot()) }
    }
    DisposableEffect(filmHolder) { onDispose { saveResume(); filmHolder.close() } }
    val filmCassette = (tape as? Tape.Film)?.let { t -> settings.film.cassettes.firstOrNull { it.id == t.id } }
    var editorTarget by remember { mutableStateOf<EditorTarget?>(null) }
    // Layar penuh: Teater, atau tampilan layar penuh yang diminta pemutar di halaman film
    val fullscreen = (theatre && tape is Tape.Film) || filmHolder.customView != null

    // SELALU tangani tombol/gestur Back di launcher. Tanpa ini, Android memutar animasi
    // "kembali" (predictive back) ke Home… yang adalah launcher itu sendiri → layar berkedip/glitch.
    BackHandler(enabled = true) {
        when {
            lyricsOpen -> vm.closeLyrics()
            drawerOpen -> vm.closeDrawer()
            trayOpen -> vm.closeTray()
            page == RoomPage.ROOM && tape is Tape.Film && filmHolder.canGoBack -> filmHolder.backOrHome()
            page != RoomPage.ROOM -> vm.showPage(RoomPage.ROOM)
            editing -> vm.cancelEdit()
            else -> Unit // sudah di Home: abaikan
        }
    }

    val gestures by rememberUpdatedState(settings.gestures)
    val customGestures by rememberUpdatedState(settings.customGestures)

    // ----- Tiga halaman: perpustakaan kaset · ruang TV · konsol audio (aturan di PageNav, gerak di PagePager) -----
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val swapPages = settings.retro.swapPages
    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    val pageIndex = PageNav.indexOf(page, swapPages, rtl)
    val pager = remember { PagePager(pageIndex, scope) }
    // Perpindahan lewat kode (Back, aksesibilitas, otomasi, gestur gambar) → pegas; seretan jari mengatur dirinya sendiri
    LaunchedEffect(pageIndex, reduceMotion) { pager.goTo(pageIndex, animated = !reduceMotion) }
    val currentPage by rememberUpdatedState(page)
    val pageLayout by rememberUpdatedState(swapPages to rtl)
    val animatedSwipe by rememberUpdatedState(!reduceMotion)
    // Keadaan perpustakaan kaset hidup di sini agar posisi rak bertahan saat halaman ditinggalkan
    val libraryState = rememberLibraryState()
    // Jari dilepas di halaman tujuan [index]: pasang ke ViewModel; bila ia menolak (mis. mode edit) pegas kembali ke halaman nyata
    val commitPage: (Int) -> Unit = { index ->
        vm.showPage(PageNav.pageAt(index, pageLayout.first, pageLayout.second))
        val actual = PageNav.indexOf(vm.page.value, pageLayout.first, pageLayout.second)
        if (actual != index) pager.settleTo(actual, 0f, animatedSwipe)
    }
    fun indexOf(p: RoomPage) = PageNav.indexOf(p, swapPages, rtl)
    // Halaman samping baru disusun saat mulai terlihat; dibaca lewat derivedStateOf agar beranda tidak
    // disusun ulang tiap frame geser (hanya saat nilainya berbalik)
    val libraryVisible by remember(swapPages, rtl) {
        derivedStateOf { pageVisible(PageNav.indexOf(RoomPage.LIBRARY, swapPages, rtl), pager.position) }
    }
    val consoleVisible by remember(swapPages, rtl) {
        derivedStateOf { pageVisible(PageNav.indexOf(RoomPage.CONSOLE, swapPages, rtl), pager.position) }
    }

    // Pemantau perangkat audio (indikator Output & VFD) hanya aktif saat beranda tampil
    var resumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        resumed = true
        AudioMonitor.acquire(context)
        DeviceStatusMonitor.acquire(context)
        onPauseOrDispose {
            resumed = false
            AudioMonitor.release()
            DeviceStatusMonitor.release()
        }
    }
    // Status bar Samsung disembunyikan selama ruang TV tampil; infonya ada di OSD
    SystemStatusBar(
        hidden = settings.retro.osd.hideStatusBar && settings.retro.osd.visible && page == RoomPage.ROOM && !drawerOpen && !lyricsOpen && !editing,
        reapplyKey = resumed to fullscreen,
    )

    // ----- Film di layar TV: dibuka/ditutup mengikuti kaset di deck, dijeda bila ruang TV tidak tampil atau TV dimatikan, dibaca tiap detik -----
    val roomVisible = resumed && page == RoomPage.ROOM && !drawerOpen && !lyricsOpen
    val filmVisible = roomVisible && !tvOff
    LaunchedEffect(filmCassette?.id, filmCassette?.url) {
        val c = filmCassette
        if (c == null) {
            filmHolder.close()
            return@LaunchedEffect
        }
        // Mulai dari halaman terakhir yang ditonton (dan menitnya) bila diizinkan; selain itu dari alamat awal kaset
        val from = if (settings.film.rememberPlace) vm.filmStart(c) else FilmStart(c.url)
        filmHolder.open(c.id, c.url, from)
    }
    SideEffect { filmHolder.setActive(filmVisible) }
    LaunchedEffect(filmCassette?.id, filmVisible) {
        if (filmCassette == null || !filmVisible) return@LaunchedEffect
        while (true) {
            filmHolder.poll()
            delay(FILM_POLL_MS)
        }
    }
    // Titik lanjut: dicatat tiap beberapa detik selama film tampil, sekali lagi begitu ruang TV ditinggalkan (halaman lain,
    // aplikasi ke latar), dan sebelum kaset diganti (lihat onPress laci). Yang tak berubah tidak ditulis ulang.
    LaunchedEffect(filmCassette?.id, filmVisible) {
        if (filmCassette == null || !filmVisible) return@LaunchedEffect
        while (true) {
            delay(RESUME_SAVE_MS)
            saveResume()
        }
    }
    var filmWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(filmVisible) {
        if (filmWasVisible && !filmVisible) saveResume()
        filmWasVisible = filmVisible
    }
    // ----- Game di kaset game: berjalan hanya selama ruang TV tampil, TV menyala, laci tertutup, dan bukan mode edit -----
    val gameId = (tape as? Tape.Game)?.id
    val gameActive = roomVisible && !tvOff && !trayOpen && !editing
    val gameRunning = gameId != null && gameHud?.phase == GamePhase.RUNNING
    LaunchedEffect(gameRunning, gameActive, gameId) {
        if (!gameRunning) return@LaunchedEffect
        if (!gameActive) {
            vm.gamePause()
            return@LaunchedEffect
        }
        if (gameId == Games.BREAKOUT) {
            // Bola bergerak mengikuti bingkai layar (selisih waktu nyata, bukan hitungan bingkai)
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                vm.breakoutAdvance((now - last) / 1_000_000_000f)
                last = now
            }
        } else {
            // Ular melangkah per petak dengan jeda yang makin pendek
            while (true) {
                delay(vm.snakeTickMs())
                vm.snakeStep()
            }
        }
    }
    // Layar tetap menyala selama film atau game berjalan dan terlihat
    KeepScreenOn((filmVisible && tape is Tape.Film && shown.isPlaying) || (gameActive && gameRunning))

    // ----- Video milik pengguna di layar TV (folder SAF). Pemutar hanya ada selama ruang TV benar-benar tampil -----
    val video = settings.video
    val videoLib by VideoLibrary.state.collectAsStateWithLifecycle()
    LaunchedEffect(video.enabled, video.folderUri, resumed) {
        if (video.enabled && resumed) VideoLibrary.scan(context, video.folderUri)
    }
    val videoMatch = remember(video, videoLib, np.artist, np.album, np.title, np.hasTrack, tapeIsMusic) {
        if (!tapeIsMusic || !video.enabled || !np.hasTrack || video.folderUri == null || videoLib.folderUri != video.folderUri) null
        else VideoMatch.find(videoLib.files, VideoRules.linkMap(video), np.artist, np.album, np.title)
    }
    // Berkas yang gagal diputar tidak dicoba terus-menerus; cover tetap tampil
    var failedVideo by remember { mutableStateOf<String?>(null) }
    // Video tidak diputar saat "Hapus animasi" atau hemat daya aktif (cover diam yang tampil)
    val videoMotionOk = !reduceMotion && !rememberPowerSave()
    val videoUri = videoMatch?.takeIf {
        resumed && page == RoomPage.ROOM && !drawerOpen && !lyricsOpen && videoMotionOk && it.file.documentId != failedVideo
    }?.let { VideoLibrary.uriOf(video.folderUri!!, it.file.documentId) }
    // Aturan otomasi "USB DAC tersambung → buka console": dijalankan saat beranda tampil (tidak menarik launcher ke depan)
    val consoleRequested by AudioConsoleRuntime.consoleRequested.collectAsStateWithLifecycle()
    LaunchedEffect(consoleRequested) {
        if (consoleRequested) {
            vm.showPage(RoomPage.CONSOLE)
            AudioConsoleRuntime.consumeConsoleRequest()
        }
    }

    // ----- Ruang TV: dinding, proyeksi, nada teks -----
    val motionAllowed = !reduceMotion && !rememberPowerSave()
    val picture = if (home.background.type == BackgroundType.IMAGE) rememberFileImage(home.background.imagePath) else null
    val cover = shown.art.takeIf { shown.hasTrack }
    val projected = if (home.background.type == BackgroundType.IMAGE) picture else if (home.background.type == BackgroundType.ALBUM) cover else null
    val look = rememberRoomLook(settings.retro.room, home.background, projected)
    // Knob SEEK: dibagi panggung (jarum & layar TV) dan OSD (mode FF/REW, penghitung pita)
    val seekState = remember { SeekState() }
    // Proyektor: cover atau visualizer; mati = dinding gelap. Video milik pengguna diputar di layar TV.
    val projector = settings.retro.projector
    val showBars = (fxPlan.visualizer == VisualizerPlan.REAL && visualizerState != VisualizerStatus.State.UNAVAILABLE) ||
        fxPlan.visualizer == VisualizerPlan.DECORATIVE
    val screenVideo: (@Composable () -> Unit)? = videoUri?.let { uri ->
        { ScreenVideo(uri, np.isPlaying, onError = { failedVideo = videoMatch?.file?.documentId }, Modifier.fillMaxSize()) }
    }
    val projection: ProjectionContent? = when {
        !shown.hasTrack || projector.source == ProjectorSource.OFF -> null
        projector.source == ProjectorSource.VISUALIZER && showBars -> ProjectionContent.Visualizer {
            SpectrumBars(fx, accent, Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp).clearAndSetSemantics { })
        }
        cover != null -> ProjectionContent.Picture(cover)
        else -> ProjectionContent.Tint(accent)
    }

    // Kaset film di meja (label dan warnanya milik kaset itu) dan isi laci
    // Label kaset game memuat rekornya ("Game · rekor 42") dan ikut berubah saat rekor naik, tanpa animasi ganti kaset
    val gameSubtitle = gameId?.let { Games.subtitle(settings.film, it) }
    val tapeLabel = remember(filmCassette, gameId, gameSubtitle) {
        when {
            filmCassette != null -> CassetteLabel("film\u0000${filmCassette.id}", filmCassette.name, FilmRules.hostOf(filmCassette.url), null, CassetteShell.COLORED, Color(filmCassette.color))
            gameId != null -> CassetteLabel("game\u0000$gameId", Games.title(gameId).uppercase(), gameSubtitle.orEmpty(), null, CassetteShell.COLORED, Color(Games.color(gameId)))
            else -> null
        }
    }
    val trayItems = remember(settings.film, filmResume) {
        val now = System.currentTimeMillis()
        settings.film.cassettes.map { c ->
            TrayItem(Tape.Film(c.id), c.name, FilmRules.hostOf(c.url), Color(c.color), c, resumeMs = FilmResume.seekTarget(FilmResume.point(filmResume, c, now)))
        } +
            Games.all.map { id ->
                TrayItem(Tape.Game(id), Games.title(id), Games.subtitle(settings.film, id), Color(Games.color(id)), null)
            }
    }
    val drawerInfo = remember(trayOpen, trayItems) {
        StageDrawerInfo(open = trayOpen, peek = trayItems.map { it.color }, count = trayItems.size)
    }

    // Tata letak ruang TV tidak ikut animasi bilah status (lihat RoomInsets): bilah itu naik-turun tiap pindah halaman
    val stableInsets = stableRoomInsets(settings.retro.osd.hideStatusBar && settings.retro.osd.visible)
    CompositionLocalProvider(LocalRoomInsets provides stableInsets) {
    Box(Modifier.fillMaxSize().onSizeChanged { pager.widthPx = it.width }) {
    Box(
        Modifier
            .fillMaxSize()
            .roomPage(indexOf(RoomPage.ROOM), pager)
            // Hanya MENGAMATI sentuhan untuk riak distorsi kaca; tidak pernah mengonsumsi event
            .pointerInput(fxPlan.distortion) {
                if (!fxPlan.distortion) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fx.pokeRipple(down.position)
                }
            }
    ) {
        // Dasar ruangan (juga di bawah bilah sistem); dinding, lantai, dan cahaya digambar panggung
        Box(Modifier.fillMaxSize().background(roomBase(look.tone)))

        // Area konten; saat edit, menyusut agar pratinjau tidak tertutup panel editor
        Box(
            Modifier
                .fillMaxSize()
                .padding(
                    end = if (editing && landscape) EDITOR_WIDTH else 0.dp,
                    bottom = if (editing && !landscape) EDITOR_HEIGHT_PORTRAIT else 0.dp,
                )
                // Geser atas/kiri dan tekan-lama hanya gestur; pembaca layar/switch access mendapat aksi setara
                .semantics {
                    contentDescription = "Layar utama"
                    if (!editing) customActions = listOf(
                        CustomAccessibilityAction("Buka semua aplikasi") { vm.openDrawer(); true },
                        CustomAccessibilityAction("Buka perpustakaan kaset") { vm.showPage(RoomPage.LIBRARY); true },
                        CustomAccessibilityAction("Buka konsol audio") { vm.showPage(RoomPage.CONSOLE); true },
                        CustomAccessibilityAction("Ubah tampilan layar utama") { vm.startEdit(); true },
                    )
                }
                .pointerInput(editing) {
                    if (editing) return@pointerInput
                    detectTapGestures(
                        onDoubleTap = { SystemActions.perform(context, gestures.doubleTap, vm::openCrate, vm::startEdit) { vm.openDrawer() } },
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.startEdit()
                        },
                    )
                }
                .pointerInput(editing) {
                    if (editing) return@pointerInput
                    // Satu detektor goresan untuk semua gestur: gestur gambar buatan sendiri diperiksa
                    // lebih dulu; bila tidak ada yang cocok → swipe atas/bawah/kiri bawaan.
                    awaitEachGesture {
                        // Ketukan pada widget tidak membatalkan swipe; yang membatalkan hanya elemen yang ikut
                        // memakai gerakan jari (mis. knob SEEK atau penghitung pita saat diseret)
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Tanpa gestur gambar buatan sendiri, gerak horizontal langsung MENYERET halaman (mengikuti jari).
                        // Dengan gestur gambar, goresan harus selesai dulu agar bisa dicocokkan → pindah halaman diputuskan
                        // setelah jari diangkat
                        val live = customGestures.isEmpty()
                        val slop = viewConfiguration.touchSlop
                        val path = mutableListOf(down.position)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var moved = false
                        var dragging = false
                        var takenByChild = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (ch.isConsumed) { takenByChild = true; break }
                            if (!ch.pressed) break
                            path.add(ch.position)
                            tracker.addPosition(ch.uptimeMillis, ch.position)
                            val total = ch.position - down.position
                            if (!moved && total.getDistance() > slop) {
                                moved = true
                                if (live && abs(total.x) > abs(total.y)) {
                                    dragging = true
                                    pager.startDrag()
                                    pager.dragByPx(total.x - sign(total.x) * slop)
                                }
                            } else if (dragging) {
                                pager.dragByPx(ch.positionChange().x)
                            }
                            if (moved) ch.consume()   // batalkan ketuk-lama (mode edit) selama menggambar/menyeret
                        }
                        if (dragging) {
                            commitPage(pager.finishDrag(tracker.calculateVelocity().x, animatedSwipe))
                            return@awaitEachGesture
                        }
                        if (takenByChild || !moved) return@awaitEachGesture
                        val custom = GestureRecognizer.match(path, customGestures)
                        if (custom != null) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            SystemActions.perform(context, custom.binding, vm::openCrate, vm::startEdit) { vm.openDrawer() }
                            return@awaitEachGesture
                        }
                        val dx = path.last().x - path.first().x
                        val dy = path.last().y - path.first().y
                        when {
                            abs(dy) > abs(dx) && dy < -120f ->
                                SystemActions.perform(context, gestures.swipeUp, vm::openCrate, vm::startEdit) { vm.openDrawer() }
                            abs(dy) > abs(dx) && dy > 120f ->
                                SystemActions.perform(context, gestures.swipeDown, vm::openCrate, vm::startEdit) { vm.openDrawer() }
                            abs(dx) >= abs(dy) && abs(dx) >= PageNav.SWIPE_PX ->
                                vm.showPage(PageNav.afterSwipe(currentPage, dx, pageLayout.first, pageLayout.second))
                        }
                    }
                }
        ) {
            HomeContent(
                home = home,
                np = shown,
                accent = accent,
                weather = settings.weather,
                lyricsOnline = settings.music.lyricsOnline,
                editing = editing,
                fxPlan = fxPlan,
                fx = fx,
                visual = settings.visual,
                retro = settings.retro,
                look = look,
                motionAllowed = motionAllowed,
                wallPicture = picture,
                lyricsVisible = home.lyricsVisible && tapeIsMusic,
                tvOff = tvOff,
                seek = seekState,
                projection = projection,
                screenVideo = screenVideo,
                dock = settings.dock,
                onWidgetChange = { updated -> vm.updateDraft { h -> h.copy(widgets = h.widgets.map { if (it.id == updated.id) updated else it }) } },
                tape = tape,
                tapeLabel = tapeLabel,
                filmHolder = filmHolder,
                theatre = theatre,
                drawerInfo = drawerInfo,
                effects = settings.effects,
                gameHud = gameHud,
                gameActive = gameActive,
                vm = vm,
            )
        }

        // Rekaman VHS (butir, tracking) lalu OSD di atasnya; ikut bergeser bersama halaman
        if (settings.retro.osd.film) RoomFilm(base = motionAllowed && resumed && page == RoomPage.ROOM, pager = pager, Modifier.fillMaxSize())
        if (settings.retro.osd.visible) {
            // Detak posisi hidup di dalam OsdLayer: hanya OSD yang dikomposisi ulang, bukan seluruh beranda
            val onOsdSeek = remember(vm, filmHolder, tape is Tape.Film) {
                if (tape is Tape.Film) filmHolder::seekTo else vm::seek
            }
            OsdLayer(
                np = shown, seek = seekState, osd = settings.retro.osd, clock = home.clock, landscape = landscape,
                onSeek = onOsdSeek,
                modifier = Modifier.padding(dockPadding(settings.dock, editing)),
                modeWord = if (tapeIsMusic) null else TapeFlow.modeWord(tape),
                // Game punya skornya sendiri di layar TV; garis waktu pita hanya untuk lagu dan film
                showCounter = tape !is Tape.Game,
            )
        }

        // Baki kaset (isi laci di meja): kaset berwarna; ketuk memasukkan ke TV atau mengeluarkannya
        AnimatedVisibility(
            visible = trayOpen && !editing,
            enter = slideInVertically(tween(Tokens.Motion.MEDIUM_MS, easing = Ease.decelerate)) { it / 2 } + fadeIn(tween(Tokens.Motion.MEDIUM_MS)),
            exit = slideOutVertically(tween(Tokens.Motion.MEDIUM_MS, easing = Ease.accelerate)) { it / 2 } + fadeOut(tween(Tokens.Motion.FAST_MS)),
        ) {
            FilmTray(
                items = trayItems,
                current = tape,
                canAdd = settings.film.cassettes.size < FilmRules.MAX_CASSETTES,
                // Posisi film yang sedang di deck dicatat sebelum kaset diganti atau dikeluarkan
                onPress = { pressed -> saveResume(); vm.pressTape(pressed) },
                onAdd = { editorTarget = EditorTarget(null) },
                onEdit = { editorTarget = EditorTarget(it) },
                onRestoreDefaults = vm::restoreFilmDefaults,
                onClose = vm::closeTray,
                sheetModifier = Modifier.padding(dockPadding(settings.dock, editing)).windowInsetsPadding(roomInsets()),
            )
        }
    }

        // Halaman perpustakaan kaset (dulu "rak piringan" yang muncul sebagai lapisan)
        Box(
            Modifier
                .fillMaxSize()
                .roomPage(indexOf(RoomPage.LIBRARY), pager)
                .sidePageDrag(pager, { animatedSwipe }, commitPage)
                .semantics {
                    customActions = listOf(CustomAccessibilityAction("Kembali ke ruang TV") { vm.showPage(RoomPage.ROOM); true })
                },
        ) {
            if (libraryVisible) {
                CrateSheet(
                    np = np, queue = queue, history = history,
                    music = settings.music,
                    onMusicChange = { m -> vm.updateSettings { it.copy(music = m) } },
                    onClose = { vm.showPage(RoomPage.ROOM) }, onClearHistory = vm::clearHistory,
                    state = libraryState,
                    onToggleFavorite = { key ->
                        vm.updateSettings { it.copy(music = it.music.copy(favorites = LibraryCatalog.toggleFavorite(it.music.favorites, key))) }
                    },
                    player = settings.player,
                    videoFolder = settings.video.folderUri,
                    onPlayer = { f -> vm.updateSettings { it.copy(player = f(it.player)) } },
                )
            }
        }

        // Halaman konsol audio
        Box(
            Modifier
                .fillMaxSize()
                .roomPage(indexOf(RoomPage.CONSOLE), pager)
                .sidePageDrag(pager, { animatedSwipe }, commitPage),
        ) {
            if (consoleVisible) {
                ConsoleRoute(
                    audio = settings.audio,
                    player = settings.player,
                    // Hanya bagian suara pemutar yang disimpan dari konsol (folder & urutan diatur di Perpustakaan)
                    onSavePlayer = { p ->
                        vm.updateSettings {
                            it.copy(
                                player = it.player.copy(
                                    replayGain = p.replayGain, eqEnabled = p.eqEnabled, eq = p.eq, savedEqs = p.savedEqs,
                                    autoHeadroom = p.autoHeadroom, crossfeed = p.crossfeed, hiResOutput = p.hiResOutput,
                                    usbRateMatch = p.usbRateMatch,
                                ),
                            )
                        }
                    },
                    musicPackage = np.packageName,
                    onSave = { a -> vm.updateSettings { it.copy(audio = a) } },
                    onBack = { vm.showPage(RoomPage.ROOM) },
                ) {
                    // VU meter: visualizer yang sama dengan layar TV (keterangannya ikut tampil bila tidak tersedia)
                    VisualizerStrip(fx, fxPlan.visualizer, settings.visual.visualizer, visualizerState == VisualizerStatus.State.UNAVAILABLE,
                        RetroColors.Amber, RetroColors.CreamTextSecondary)
                }
            }
        }

        // App drawer
        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically(tween(Tokens.Motion.MEDIUM_MS, easing = Ease.decelerate)) { it / 3 } + fadeIn(tween(Tokens.Motion.MEDIUM_MS)),
            exit = slideOutVertically(tween(Tokens.Motion.MEDIUM_MS, easing = Ease.accelerate)) { it / 3 } + fadeOut(tween(Tokens.Motion.FAST_MS)),
        ) {
            // Riwayat & lencana dikumpulkan di sini (bukan di tingkat HomeScreen) agar notifikasi baru tidak
            // merekomposisi layar utama saat drawer tertutup
            val usage by vm.usage.collectAsStateWithLifecycle()
            val badges by vm.badges.collectAsStateWithLifecycle()
            val drawerData = remember(visibleApps, settings.drawer, usage, badges, history, settings.dock.enabled, settings.dock.apps) {
                DrawerData(
                    apps = visibleApps,
                    settings = settings.drawer,
                    usage = usage,
                    badges = badges,
                    songs = history,
                    dockEnabled = settings.dock.enabled,
                    dockKeys = settings.dock.apps,
                )
            }
            val drawerActions = remember(vm) {
                DrawerActions(
                    launch = { vm.launch(it) },
                    info = { vm.appInfo(it) },
                    uninstall = { vm.uninstall(it) },
                    addToDock = { vm.addToDock(it) },
                    hide = { vm.hideApp(it) },
                    setCategory = { app, category -> vm.setCategory(app, category) },
                    shortcuts = { app -> vm.shortcutsFor(app) },
                    startShortcut = { vm.startShortcut(it) },
                    playSong = { vm.playSong(it) },
                    openSetting = { vm.openSystemSetting(it) },
                    searchContacts = { query -> vm.searchContacts(query) },
                    openContact = { vm.openContact(it) },
                    close = { vm.closeDrawer() },
                )
            }
            AppDrawer(data = drawerData, tint = bgColor, np = np, actions = drawerActions)
        }

        // Lirik layar penuh
        AnimatedVisibility(
            visible = lyricsOpen,
            enter = slideInVertically(tween(Tokens.Motion.MEDIUM_MS, easing = Ease.decelerate)) { it / 4 } + fadeIn(tween(Tokens.Motion.MEDIUM_MS)),
            exit = slideOutVertically(tween(Tokens.Motion.MEDIUM_MS, easing = Ease.accelerate)) { it / 4 } + fadeOut(tween(Tokens.Motion.FAST_MS)),
        ) {
            val lyricsActions = remember(vm) {
                LyricsActions(
                    close = { vm.closeLyrics() },
                    seek = { vm.seek(it) },
                    togglePlay = { vm.togglePlay() },
                    previous = { vm.previous() },
                    next = { vm.next() },
                    enableOnline = { vm.updateSettings { it.copy(music = it.music.copy(lyricsOnline = true)) } },
                    rememberSleepMinutes = { minutes -> vm.updateSettings { it.copy(music = it.music.copy(sleepMinutes = minutes)) } },
                )
            }
            LyricsScreen(np = np, fallback = bgColor, music = settings.music, actions = lyricsActions)
        }

        // Panel editor (kanan di landscape, bawah di portrait)
        AnimatedVisibility(
            visible = editing,
            modifier = Modifier.align(if (landscape) Alignment.CenterEnd else Alignment.BottomCenter),
            enter = if (landscape) slideInHorizontally { it } else slideInVertically { it },
            exit = if (landscape) slideOutHorizontally { it } else slideOutVertically { it },
        ) {
            HomeEditorPanel(vm = vm, landscape = landscape, apps = allApps)
        }

        // Teater / layar penuh dari pemutar film: menutupi seluruh beranda
        if (fullscreen) {
            FilmFullscreen(
                holder = filmHolder,
                theatre = theatre && tape is Tape.Film,
                landscape = settings.film.theaterLandscape,
                onExitTheatre = vm::closeTheatre,
            )
        }
    }
    }

    // Tambah / ubah kaset film
    editorTarget?.let { target ->
        FilmCassetteEditor(
            initial = target.film,
            onSave = { name, url, color ->
                vm.saveFilmCassette(target.film?.id, name, url, color)
                editorTarget = null
            },
            onDelete = target.film?.let { f -> { vm.deleteFilmCassette(f.id); editorTarget = null } },
            onDismiss = { editorTarget = null },
        )
    }
}

/** Kaset film yang sedang dibuka di editor; [film] null = kaset baru. */
private class EditorTarget(val film: FilmCassette?)

/** Selang pembacaan keadaan video di halaman film (ms). */
private const val FILM_POLL_MS = 1_000L

/** Selang pencatatan titik lanjut selama film tampil (ms). */
private const val RESUME_SAVE_MS = 15_000L

/** Selang lompat maju/mundur tombol deck saat kaset film (ms). */
private const val FILM_STEP_MS = 10_000L

/** Layar tidak padam selama [on] (film sedang diputar dan terlihat). */
@Composable
private fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}

/** Panggung TV & widget. Dipisah agar mudah dibaca. */
@Composable
private fun HomeContent(
    home: HomeSettings,
    np: NowPlaying,
    accent: Color,
    weather: WeatherSettings,
    lyricsOnline: Boolean,
    editing: Boolean,
    fxPlan: FxPlan,
    fx: FxState,
    visual: VisualSettings,
    retro: RetroSettings,
    look: RoomLook,
    motionAllowed: Boolean,
    wallPicture: ImageBitmap?,
    lyricsVisible: Boolean,
    tvOff: Boolean,
    seek: SeekState,
    projection: ProjectionContent?,
    screenVideo: (@Composable () -> Unit)?,
    dock: DockSettings,
    onWidgetChange: (WidgetConfig) -> Unit,
    tape: Tape,
    tapeLabel: CassetteLabel?,
    filmHolder: FilmWebHolder,
    theatre: Boolean,
    drawerInfo: StageDrawerInfo,
    effects: EffectSettings,
    gameHud: GameHud?,
    gameActive: Boolean,
    vm: HomeViewModel,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safe = roomInsets()
    val dockPad = dockPadding(dock, editing)
    // Panggung digambar penuh (juga di bawah bilah sistem); ruang yang dipakai sistem & taskbar diteruskan sebagai inset
    val topInset = with(density) { safe.getTop(density).toDp() } + dockPad.calculateTopPadding()
    val bottomInset = with(density) { safe.getBottom(density).toDp() } + dockPad.calculateBottomPadding()
    val startPad = with(density) { (if (layoutDirection == LayoutDirection.Ltr) safe.getLeft(density, layoutDirection) else safe.getRight(density, layoutDirection)).toDp() } +
        dockPad.calculateStartPadding(layoutDirection)
    val endPad = with(density) { (if (layoutDirection == LayoutDirection.Ltr) safe.getRight(density, layoutDirection) else safe.getLeft(density, layoutDirection)).toDp() } +
        dockPad.calculateEndPadding(layoutDirection)

    val position = rememberPositionMs(np)
    val audio by AudioMonitor.state.collectAsStateWithLifecycle()
    val hifi by HiFi.path.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val brightest = rememberWorstCoverPixel(np.art, RoomTone.DIM)
    val scrim = remember(brightest) {
        // Cover sedikit diredam seperti tabung; judul memakai kotak teks hitam sendiri
        brightest?.let { BackdropDim.minOverlayAlpha(it, listOf(0xFFFFFFFFL), effects = false).toFloat() } ?: 0.25f
    }
    val lyrics = rememberLyrics(np, lyricsVisible, lyricsOnline)
    val transport = StatusText.transport(np.hasTrack, np.isPlaying, seek.deltaMs?.takeIf { seek.touching })
    val isFilm = tape is Tape.Film
    val gameId = (tape as? Tape.Game)?.id
    val isGame = gameId != null
    val info = StageInfo(
        tvVfd = if (isGame) gameVfd(gameHud) else VfdTicker.playback(np.hasTrack, np.isPlaying),
        deck = when {
            isFilm -> filmDeck(transport)
            isGame -> gameDeck(gameHud)
            else -> deckDisplay(transport, tapeMode(np, context.packageName, hifi), audio, hifi.takeIf { np.packageName == context.packageName && it.active })
        },
        tapeTime = tapeTime(np),
        tvState = if (isGame) gameSpoken(gameHud) else null,
    )
    val gamePhase = gameHud?.phase
    val actions = remember(vm, isFilm, gameId, gamePhase, filmHolder, effects) {
        when {
            isFilm -> StageActions(
                onPrevious = { if (!vm.wakeTv()) filmHolder.backOrHome() },
                onRewind = { if (!vm.wakeTv()) filmHolder.seekBy(-FILM_STEP_MS) },
                onPlayPause = {
                    if (!vm.wakeTv()) {
                        if (vm.roomNowPlaying.value.isPlaying) SoundFx.stopKey(context, effects) else SoundFx.playKey(context, effects)
                        filmHolder.toggle()
                    }
                },
                onNext = { if (!vm.wakeTv()) filmHolder.seekBy(FILM_STEP_MS) },
                onSeek = filmHolder::seekTo,
                onTick = vm::tick,
                onOpenLyrics = {},
                onToggleDrawer = vm::toggleTray,
                onVfdClick = { if (!vm.wakeTv()) vm.openTheatre() },
                vfdClickLabel = "Buka mode Teater, film layar penuh",
                onPower = vm::toggleTvPower,
            )
            gameId == Games.BREAKOUT -> StageActions(
                // Tiga tombol deck: palet ke kiri · mulai/jeda · palet ke kanan (tombol "mundur" tidak dipakai Bata)
                onPrevious = { vm.breakoutNudge(-1) },
                onRewind = {},
                onPlayPause = vm::breakoutTap,
                onNext = { vm.breakoutNudge(1) },
                onSeek = {},
                onTick = vm::tick,
                onOpenLyrics = {},
                onToggleDrawer = vm::toggleTray,
                onVfdClick = vm::breakoutTap,
                vfdClickLabel = gameTapLabel(gamePhase),
                onCassette = vm::breakoutTap,
                onPower = vm::toggleTvPower,
                tvActions = listOf<Pair<String, () -> Unit>>(
                    gameTapLabel(gamePhase) to vm::breakoutTap,
                    "Palet ke kiri" to { vm.breakoutNudge(-1) },
                    "Palet ke kanan" to { vm.breakoutNudge(1) },
                ),
            )
            isGame -> StageActions(
                onPrevious = { vm.snakeTurn(Dir.LEFT) },
                onRewind = { vm.snakeTurn(Dir.UP) },
                onPlayPause = { vm.snakeTurn(Dir.DOWN) },
                onNext = { vm.snakeTurn(Dir.RIGHT) },
                onSeek = {},
                onTick = vm::tick,
                onOpenLyrics = {},
                onToggleDrawer = vm::toggleTray,
                onVfdClick = vm::snakeTap,
                vfdClickLabel = gameTapLabel(gamePhase),
                onCassette = vm::snakeTap,
                onPower = vm::toggleTvPower,
                tvActions = listOf<Pair<String, () -> Unit>>(
                    gameTapLabel(gamePhase) to vm::snakeTap,
                    "Belok kiri" to { vm.snakeTurn(Dir.LEFT) },
                    "Belok kanan" to { vm.snakeTurn(Dir.RIGHT) },
                    "Naik" to { vm.snakeTurn(Dir.UP) },
                    "Turun" to { vm.snakeTurn(Dir.DOWN) },
                ),
            )
            else -> StageActions(
                onPrevious = vm::previous,
                onRewind = { vm.seek((vm.nowPlaying.value.positionAt() - 10_000L).coerceAtLeast(0L)) },
                onPlayPause = { if (vm.nowPlaying.value.hasTrack) vm.togglePlay() else vm.openMusic() },
                onNext = vm::next,
                onSeek = vm::seek,
                onTick = vm::tick,
                onOpenLyrics = vm::openLyrics,
                onToggleDrawer = vm::toggleTray,
                onPower = vm::toggleTvPower,
            )
        }
    }
    // Benda tambahan di ruangan: jam dinding, radio (lagu acak / timer tidur), telepon putar (pintasan kontak lokal)
    val sleepState by SleepTimer.state.collectAsStateWithLifecycle()
    val phoneBook = remember(context) { PhoneBook.get(context) }
    val phoneFavorites by phoneBook.favorites.collectAsStateWithLifecycle()
    val sleepRunning = sleepState is SleepTimer.State.Running
    val pickPoster = rememberImagePicker("poster") { vm.setPoster(it) }
    // Rak piala dan buku harian: dihitung dari data lokal yang sudah ada (rekor game, riwayat putar) dan buku harian opsional
    val playRecords by HistoryRepository.records.collectAsStateWithLifecycle()
    val diaryStore = remember(context) { DiaryStore.get(context) }
    val diaryEntries by diaryStore.entries.collectAsStateWithLifecycle()
    val diaryRecording by diaryStore.recording.collectAsStateWithLifecycle()
    var showTrophies by rememberSaveable { mutableStateOf(false) }
    var showDiary by rememberSaveable { mutableStateOf(false) }
    val filmSettings = vm.settings.collectAsStateWithLifecycle().value.film
    val trophies = remember(filmSettings.snakeBest, filmSettings.breakoutBest, playRecords) {
        Achievements.compute(
            AchievementInput(
                snakeBest = Games.best(filmSettings, Games.SNAKE), breakoutBest = Games.best(filmSettings, Games.BREAKOUT),
                listenedMs = playRecords.sumOf { it.listenedMs }, songs = playRecords.size,
            ),
        )
    }
    val propActions = remember(vm, sleepRunning, phoneFavorites, trophies, diaryRecording, diaryEntries.size) {
        PropActions(
            onClockAlarms = vm::openAlarms, onClockTimers = vm::openTimers,
            onRadioPlay = vm::radioPlay, onRadioSleep = vm::radioSleep, radioSleepActive = sleepRunning,
            onDial = vm::dial, onOpenPhone = vm::openPhone,
            phoneFavorites = phoneFavorites.mapValues { it.value.name },
            onFramePick = pickPoster, onFrameClear = { vm.clearPoster() },
            trophies = trophies, onTrophies = { showTrophies = true },
            diaryRecording = diaryRecording, diaryCount = diaryEntries.size, onDiary = { showDiary = true },
        )
    }
    if (showTrophies) TrophyDialog(trophies, onDismiss = { showTrophies = false })
    if (showDiary) DiaryDialog(diaryStore, onDismiss = { showDiary = false })
    val tvScreen: (@Composable () -> Unit)? = when {
        isFilm -> {
            val content: @Composable () -> Unit = { FilmTvScreen(filmHolder, theatre, motionAllowed) }
            content
        }
        gameId == Games.BREAKOUT -> {
            val content: @Composable () -> Unit = {
                BreakoutTvScreen(vm.breakout, enabled = !editing && gameActive, motionAllowed = motionAllowed, onMove = vm::breakoutMove, onTap = vm::breakoutTap)
            }
            content
        }
        isGame -> {
            val content: @Composable () -> Unit = {
                SnakeTvScreen(vm.snake, enabled = !editing && gameActive, motionAllowed = motionAllowed, onTurn = vm::snakeTurn, onTap = vm::snakeTap)
            }
            content
        }
        else -> null
    }
    Box(Modifier.fillMaxSize()) {
        RoomStage(
            np = np, retro = retro, tone = look.tone, tvSize = home.turntableSize, accent = accent,
            motionAllowed = motionAllowed && !editing, editing = editing, position = { position.longValue }, titleScrimAlpha = scrim,
            seek = seek, info = info, actions = actions, projection = projection,
            modifier = Modifier.padding(start = startPad, end = endPad),
            topInset = topInset.value, bottomInset = bottomInset.value,
            wallColor = if (home.background.type == BackgroundType.SOLID) home.background.solidColor.toColor() else null,
            wallGradient = if (home.background.type == BackgroundType.GRADIENT)
                home.background.gradientStart.toColor() to home.background.gradientEnd.toColor() else null,
            wallPicture = wallPicture,
            showCover = home.coverVisible,
            // Saat subtitel lirik tampil, judul tidak ditulis di atasnya
            showTitle = home.trackInfoVisible && lyrics == null,
            tvOff = tvOff,
            screenVideo = screenVideo,
            tape = when {
                isFilm -> DeckTape.FILM
                gameId == Games.BREAKOUT -> DeckTape.PADDLE
                isGame -> DeckTape.GAME
                else -> DeckTape.MUSIC
            },
            tapeLabel = tapeLabel,
            tvScreen = tvScreen,
            drawer = drawerInfo,
            // Lampu dinding hanya bila nada ruang yang menentukan dinding (bukan warna/gradasi pilihan sendiri): ketukannya terlihat
            onToggleLamp = if (RoomInk.toneDecidesWall(home.background.type)) vm::toggleRoomLamp else null,
            onCassetteSlot = vm::cassetteSlot,
            propActions = propActions,
            lightOk = !rememberPowerSave(),
            atmosphere = {
                // Debu halus melayang di cahaya proyeksi (di depan dinding, di belakang TV)
                if (fxPlan.particles) {
                    FxOverlay(fx, fxPlan.copy(glow = false), accent, visual.parallax, visual.parallaxStrength, particleColor = dustColor(look.tone))
                }
            },
        ) {
            // Subtitel lirik di bagian bawah layar TV
            if (lyrics != null) {
                Box(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.background(Color.Black.copy(alpha = 0.72f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        LyricsLines(np, lyrics, primary = Color.White, secondary = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
                    }
                }
            }
        }

        // ----- Widget tambahan (di area aman, tidak di bawah bilah sistem/taskbar)
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(safe).padding(dockPad)) {
            val wPx = constraints.maxWidth
            val hPx = constraints.maxHeight
            home.widgets.forEach { w ->
                HomeWidget(w, wPx, hPx, editing, weather, onWidgetChange)
            }
        }
    }
}

/** VFD deck untuk kaset film: kata mode + tanda transport, dan petunjuk bahwa jendela ini membuka Teater. */
private fun filmDeck(t: Transport): DeckDisplay {
    val symbol = when (t) {
        Transport.PLAY -> "▶"
        Transport.PAUSE -> "❚❚"
        Transport.FORWARD -> "▶▶"
        Transport.REWIND -> "◀◀"
        Transport.STOP -> "■"
    }
    return DeckDisplay("FILM $symbol", "TEATER")
}

/** VFD deck untuk kaset game: kata mode + tanda transport, dan skor (atau rekor bila belum mulai). */
private fun gameDeck(h: GameHud?): DeckDisplay {
    val symbol = when (h?.phase) {
        GamePhase.RUNNING -> "▶"
        GamePhase.PAUSED -> "❚❚"
        else -> "■"
    }
    val line = when {
        h == null -> ""
        h.phase == GamePhase.READY -> if (h.best > 0) "REKOR ${h.best}" else "SIAP MAIN"
        else -> "SKOR ${h.score}"
    }
    return DeckDisplay("GAME $symbol", line)
}

/** Baris VFD kecil di panel TV (± tujuh huruf): keadaan permainan. */
private fun gameVfd(h: GameHud?): String = when (h?.phase) {
    GamePhase.RUNNING -> "PLAY"
    GamePhase.PAUSED -> "PAUSE"
    GamePhase.OVER -> "OVER"
    GamePhase.WON -> "WIN"
    else -> "READY"
}

/** Nama aksi ketuk pada layar/kaset/VFD game menurut keadaannya (juga untuk pembaca layar). */
private fun gameTapLabel(phase: GamePhase?): String = when (phase) {
    GamePhase.RUNNING -> "Jeda game"
    GamePhase.PAUSED -> "Lanjutkan game"
    GamePhase.OVER, GamePhase.WON -> "Main lagi"
    else -> "Mulai game"
}

/** Keadaan game untuk pembaca layar. */
private fun gameSpoken(h: GameHud?): String? {
    h ?: return null
    val state = when (h.phase) {
        GamePhase.READY -> "Siap main"
        GamePhase.RUNNING -> "Berjalan"
        GamePhase.PAUSED -> "Dijeda"
        GamePhase.OVER -> "Game over"
        GamePhase.WON -> "Menang, papan penuh"
    }
    // Bata punya tingkat dan nyawa (level > 0); Ular tidak
    val extra = if (h.level > 0) ". Tingkat ${h.level}, nyawa ${h.lives}" else ""
    return "$state. Skor ${h.score}, rekor ${h.best}$extra"
}

/** Ruang yang dipakai Taskbar TV di sisi tempat ia menempel (tidak ada saat mengedit). */
private fun dockPadding(dock: DockSettings, editing: Boolean): PaddingValues {
    if (!dock.enabled || editing) return PaddingValues(0.dp)
    val t = (76 + dock.margin).dp
    return when (dock.position) {
        DockPosition.LEFT -> PaddingValues(start = t)
        DockPosition.TOP -> PaddingValues(top = t)
        DockPosition.RIGHT -> PaddingValues(end = t)
        DockPosition.BOTTOM -> PaddingValues(bottom = t)
    }
}

/** Warna dasar ruangan di bawah panggung (terlihat sesaat saat halaman bergeser). */
private fun roomBase(tone: RoomTone): Color = if (tone == RoomTone.LIGHT) Color(0xFFBDB7AC) else Color(Tokens.Vhs.WALL_DARK)

/**
 * Widget yang bisa diseret & diubah ukurannya di mode edit.
 * Posisi disimpan sebagai fraksi layar agar tetap pas setelah rotasi.
 */
@Composable
private fun HomeWidget(
    config: WidgetConfig,
    areaW: Int,
    areaH: Int,
    editing: Boolean,
    weather: WeatherSettings,
    onChange: (WidgetConfig) -> Unit,
) {
    val cfg by rememberUpdatedState(config)
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val x = (config.x * areaW).roundToInt().coerceIn(0, (areaW - size.width).coerceAtLeast(0))
    val y = (config.y * areaH).roundToInt().coerceIn(0, (areaH - size.height).coerceAtLeast(0))

    Box(
        Modifier
            .offset { IntOffset(x, y) }
            .onSizeChanged { size = it }
            .then(
                if (editing) Modifier
                    .border(1.5.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                    .pointerInput(areaW, areaH) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            onChange(
                                cfg.copy(
                                    x = (cfg.x + drag.x / areaW).coerceIn(0f, 0.98f),
                                    y = (cfg.y + drag.y / areaH).coerceIn(0f, 0.98f),
                                )
                            )
                        }
                    }
                else Modifier
            )
            .padding(12.dp)
    ) {
        when (config.type) {
            // Widget bertema ruang TV: masing-masing membawa permukaannya sendiri (kontras tidak bergantung dinding)
            WidgetType.CALENDAR -> TearCalendarWidget(java.time.LocalDate.now(), scale = config.scale)
            WidgetType.WEATHER -> VfdWeatherWidget(weather, scale = config.scale)
            WidgetType.PHOTO -> PolaroidWidget(config.images, scale = config.scale)
            WidgetType.BATTERY -> NeedleBatteryWidget(scale = config.scale)
        }
        if (editing) {
            // Pegangan ubah ukuran di sudut kanan bawah
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(14.dp, 14.dp)
                    .size(22.dp)
                    .background(Color.White, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag: Offset ->
                            change.consume()
                            val delta = (drag.x + drag.y) / with(density) { 300.dp.toPx() }
                            onChange(cfg.copy(scale = (cfg.scale + delta).coerceIn(0.6f, 2.2f)))
                        }
                    }
            )
        }
    }
}

/** Lebar panel editor di landscape & tinggi di portrait. */
val EDITOR_WIDTH = 400.dp
val EDITOR_HEIGHT_PORTRAIT = 440.dp

/**
 * Batang spektrum di bawah progress lagu, atau keterangan jujur mengapa tidak ada. Mode "Audio asli" yang gagal TIDAK
 * pernah diganti animasi tiruan; animasi dekoratif selalu diberi label.
 */
@Composable
private fun VisualizerStrip(
    fx: FxState,
    plan: VisualizerPlan,
    mode: VisualizerMode,
    unavailable: Boolean,
    barColor: Color,
    noteColor: Color,
) {
    val note: String? = when {
        plan == VisualizerPlan.NEEDS_PERMISSION -> "Visualizer audio asli butuh izin mikrofon. Atur di Pengaturan → Gambar & efek."
        mode == VisualizerMode.REAL && plan == VisualizerPlan.REAL && unavailable -> "Visualizer audio asli tidak tersedia di perangkat ini."
        plan == VisualizerPlan.DECORATIVE -> "Animasi dekoratif, tidak mengikuti suara"
        else -> null
    }
    val showBars = (plan == VisualizerPlan.REAL && !unavailable) || plan == VisualizerPlan.DECORATIVE
    if (!showBars && note == null) return
    Column(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (showBars) {
            SpectrumBars(
                fx, barColor,
                Modifier.fillMaxWidth().height(28.dp)
                    // Gambar dekoratif: pembaca layar cukup mendengar keterangannya, bukan batang yang berubah tiap frame
                    .clearAndSetSemantics { },
            )
        }
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                color = noteColor,
                modifier = Modifier.padding(top = if (showBars) 6.dp else 0.dp),
            )
        }
    }
}

/** OSD beranda dengan detak posisi sendiri (lihat [rememberPositionMs]). */
@Composable
private fun OsdLayer(
    np: NowPlaying,
    seek: SeekState,
    osd: OsdSettings,
    clock: ClockSettings,
    landscape: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    modeWord: String? = null,
    showCounter: Boolean = true,
) {
    val position = rememberPositionMs(np)
    RoomOsd(
        np = np, positionMs = position.longValue, seek = seek, osd = osd, clock = clock, landscape = landscape, onSeek = onSeek,
        modifier = modifier, modeWord = modeWord, showCounter = showCounter,
    )
}

/** Butir VHS berhenti menggambar ulang selama halaman bergerak (dibaca di sini agar hanya lapisan ini yang disusun ulang). */
@Composable
private fun RoomFilm(base: Boolean, pager: PagePager, modifier: Modifier) {
    VhsFilm(animate = base && !pager.moving, modifier)
}
