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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val editing = draft != null

    // Efek visual (Tahap 4): satu keputusan (FxPlan) dari pengaturan + lingkungan; tertutup overlay = semua efek berhenti
    val fx = rememberFxState()
    val fxPlan = rememberFxPlan(settings.visual, np.isPlaying, screenVisible = (page == RoomPage.ROOM || page == RoomPage.CONSOLE) && !drawerOpen && !lyricsOpen, fx = fx)
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
    LaunchedEffect(np.isPlaying) { if (np.isPlaying) SleepTimer.clearSlept() }
    val tvOff = slept && !np.isPlaying

    // Bunyi TV: "thunk" saat layar menyala dari siaga, statis singkat saat pindah halaman ("ganti channel")
    var hadTrack by remember { mutableStateOf(np.hasTrack) }
    LaunchedEffect(np.hasTrack) {
        if (np.hasTrack && !hadTrack) SoundFx.crtOn(settings.effects)
        hadTrack = np.hasTrack
    }
    var lastPage by remember { mutableStateOf(page) }
    LaunchedEffect(page) {
        if (page != lastPage) SoundFx.channel(settings.effects)
        lastPage = page
    }

    // Warna dari cover album (Palette); netral saat tidak ada musik
    val dominant = if (np.hasTrack) np.dominantColor?.toComposeColor() ?: NeutralBg else NeutralBg
    val accent = if (np.hasTrack) np.accentColor?.toComposeColor() ?: NeutralLabel else NeutralLabel
    val bgColor = dominant.darken(0.12f)

    // SELALU tangani tombol/gestur Back di launcher. Tanpa ini, Android memutar animasi
    // "kembali" (predictive back) ke Home… yang adalah launcher itu sendiri → layar berkedip/glitch.
    BackHandler(enabled = true) {
        when {
            lyricsOpen -> vm.closeLyrics()
            drawerOpen -> vm.closeDrawer()
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
        reapplyKey = resumed,
    )

    // ----- Video milik pengguna di layar TV (folder SAF). Pemutar hanya ada selama ruang TV benar-benar tampil -----
    val video = settings.video
    val videoLib by VideoLibrary.state.collectAsStateWithLifecycle()
    LaunchedEffect(video.enabled, video.folderUri, resumed) {
        if (video.enabled && resumed) VideoLibrary.scan(context, video.folderUri)
    }
    val videoMatch = remember(video, videoLib, np.artist, np.album, np.title, np.hasTrack) {
        if (!video.enabled || !np.hasTrack || video.folderUri == null || videoLib.folderUri != video.folderUri) null
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
    val cover = np.art.takeIf { np.hasTrack }
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
        !np.hasTrack || projector.source == ProjectorSource.OFF -> null
        projector.source == ProjectorSource.VISUALIZER && showBars -> ProjectionContent.Visualizer {
            SpectrumBars(fx, accent, Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp).clearAndSetSemantics { })
        }
        cover != null -> ProjectionContent.Picture(cover)
        else -> ProjectionContent.Tint(accent)
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
                np = np,
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
                lyricsVisible = home.lyricsVisible,
                tvOff = tvOff,
                seek = seekState,
                projection = projection,
                screenVideo = screenVideo,
                dock = settings.dock,
                onWidgetChange = { updated -> vm.updateDraft { h -> h.copy(widgets = h.widgets.map { if (it.id == updated.id) updated else it }) } },
                vm = vm,
            )
        }

        // Rekaman VHS (butir, tracking) lalu OSD di atasnya; ikut bergeser bersama halaman
        if (settings.retro.osd.film) RoomFilm(base = motionAllowed && resumed && page == RoomPage.ROOM, pager = pager, Modifier.fillMaxSize())
        if (settings.retro.osd.visible) {
            // Detak posisi hidup di dalam OsdLayer: hanya OSD yang dikomposisi ulang, bukan seluruh beranda
            OsdLayer(
                np = np, seek = seekState, osd = settings.retro.osd, clock = home.clock, landscape = landscape,
                onSeek = vm::seek,
                modifier = Modifier.padding(dockPadding(settings.dock, editing)),
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
    }
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
    val info = StageInfo(
        tvVfd = VfdTicker.playback(np.hasTrack, np.isPlaying),
        deck = deckDisplay(transport, tapeMode(np, context.packageName, hifi), audio, hifi.takeIf { np.packageName == context.packageName && it.active }),
        tapeTime = tapeTime(np),
    )
    val actions = remember(vm) {
        StageActions(
            onPrevious = vm::previous,
            onRewind = { vm.seek((vm.nowPlaying.value.positionAt() - 10_000L).coerceAtLeast(0L)) },
            onPlayPause = { if (vm.nowPlaying.value.hasTrack) vm.togglePlay() else vm.openMusic() },
            onNext = vm::next,
            onSeek = vm::seek,
            onTick = vm::tick,
            onOpenLyrics = vm::openLyrics,
        )
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
) {
    val position = rememberPositionMs(np)
    RoomOsd(np = np, positionMs = position.longValue, seek = seek, osd = osd, clock = clock, landscape = landscape, onSeek = onSeek, modifier = modifier)
}

/** Butir VHS berhenti menggambar ulang selama halaman bergerak (dibaca di sini agar hanya lapisan ini yang disusun ulang). */
@Composable
private fun RoomFilm(base: Boolean, pager: PagePager, modifier: Modifier) {
    VhsFilm(animate = base && !pager.moving, modifier)
}
