package com.vinyl.launcher.ui.home

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.vinyl.launcher.ui.components.formatMs
import com.vinyl.launcher.ui.components.glass
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinyl.launcher.data.HomeSettings
import com.vinyl.launcher.data.WeatherSettings
import com.vinyl.launcher.data.WidgetConfig
import com.vinyl.launcher.data.WidgetType
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.ui.components.BatteryIndicator
import com.vinyl.launcher.ui.components.ClockView
import com.vinyl.launcher.ui.components.TwoMonthCalendar
import com.vinyl.launcher.ui.components.LyricsLines
import com.vinyl.launcher.ui.components.PhotoWidget
import com.vinyl.launcher.ui.components.rememberLyrics
import com.vinyl.launcher.ui.crate.CrateSheet
import com.vinyl.launcher.ui.lyrics.LyricsActions
import com.vinyl.launcher.ui.lyrics.LyricsScreen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import com.vinyl.launcher.ui.components.WeatherWidget
import com.vinyl.launcher.ui.components.rememberNow
import com.vinyl.launcher.ui.drawer.AppDrawer
import com.vinyl.launcher.ui.drawer.DrawerActions
import com.vinyl.launcher.ui.drawer.DrawerData
import com.vinyl.launcher.ui.theme.Ease
import com.vinyl.launcher.ui.theme.Tokens
import com.vinyl.launcher.ui.settings.AppPickerDialog
import com.vinyl.launcher.ui.theme.darken
import com.vinyl.launcher.ui.theme.horizontal
import com.vinyl.launcher.ui.theme.toAlignment
import com.vinyl.launcher.ui.theme.toColor
import com.vinyl.launcher.ui.theme.toComposeColor
import com.vinyl.launcher.ui.theme.Corner
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
import com.vinyl.launcher.fx.fxForeground
import com.vinyl.launcher.fx.rememberFxPlan
import com.vinyl.launcher.fx.rememberFxState
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.math.roundToInt
import kotlin.math.abs
import com.vinyl.launcher.util.GestureRecognizer
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.vinyl.launcher.ui.console.ConsoleScreen
import com.vinyl.launcher.ui.theme.rememberReduceMotion
import com.vinyl.launcher.data.BackgroundType
import com.vinyl.launcher.data.RetroSettings
import com.vinyl.launcher.fx.FxOverlay
import com.vinyl.launcher.fx.fxBackdrop
import com.vinyl.launcher.fx.rememberPowerSave
import com.vinyl.launcher.ui.components.rememberFileImage
import com.vinyl.launcher.ui.room.RoomBackdrop
import com.vinyl.launcher.ui.room.RoomFloor
import com.vinyl.launcher.ui.room.RoomStage
import com.vinyl.launcher.ui.room.VfdTicker
import com.vinyl.launcher.ui.room.RoomInk
import com.vinyl.launcher.ui.room.KNOB_ZOOM
import com.vinyl.launcher.ui.room.KnobLayer
import com.vinyl.launcher.ui.room.KnobState
import com.vinyl.launcher.ui.cassette.Cassette
import com.vinyl.launcher.ui.cassette.CassetteLabel
import com.vinyl.launcher.ui.cassette.KnobSeek
import com.vinyl.launcher.ui.library.LibraryCatalog
import com.vinyl.launcher.ui.library.rememberLibraryState

/** Warna netral saat tidak ada musik. */
private val NeutralBg = Color(0xFF1B1B1F)
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
    val fxPlan = rememberFxPlan(settings.visual, np.isPlaying, screenVisible = page == RoomPage.ROOM && !drawerOpen && !lyricsOpen, fx = fx)
    FxRunner(fx, fxPlan)
    val visualizerState by VisualizerStatus.state.collectAsStateWithLifecycle()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Kresek vinyl (opsional): hanya saat layar utama terlihat & lagu diputar
    val crackleOn = settings.effects.crackle && np.isPlaying
    LifecycleResumeEffect(crackleOn, settings.effects.volume) {
        if (crackleOn) SoundFx.startCrackle(settings.effects.volume)
        onPauseOrDispose { SoundFx.stopCrackle() }
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

    // ----- Tiga halaman: perpustakaan kaset · ruang TV · konsol audio (aturan di PageNav) -----
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val swapPages = settings.retro.swapPages
    val reduceMotion = rememberReduceMotion()
    val pagePosition by animateFloatAsState(
        PageNav.indexOf(page, swapPages, rtl).toFloat(),
        tween(if (reduceMotion) 0 else Tokens.Motion.PAGE_SLIDE_MS, easing = Ease.standard),
        label = "page",
    )
    val currentPage by rememberUpdatedState(page)
    val pageLayout by rememberUpdatedState(swapPages to rtl)
    var screenWidthPx by remember { mutableIntStateOf(0) }
    // Keadaan perpustakaan kaset hidup di sini agar posisi rak bertahan saat halaman ditinggalkan
    val libraryState = rememberLibraryState()
    val swipeFromSide: (Float) -> Unit = { dx ->
        vm.showPage(PageNav.afterSwipe(currentPage, dx, pageLayout.first, pageLayout.second))
    }
    fun indexOf(p: RoomPage) = PageNav.indexOf(p, swapPages, rtl)

    // ----- Ruang TV: dinding, proyeksi, nada teks -----
    val motionAllowed = !reduceMotion && !rememberPowerSave()
    var roomFloor by remember { mutableStateOf(RoomFloor.UNKNOWN) }
    val picture = if (home.background.type == BackgroundType.IMAGE) rememberFileImage(home.background.imagePath) else null
    val cover = np.art.takeIf { np.hasTrack }
    val projected = if (home.background.type == BackgroundType.IMAGE) picture else if (home.background.type == BackgroundType.ALBUM) cover else null
    val look = rememberRoomLook(settings.retro.room, home.background, projected)

    Box(Modifier.fillMaxSize().onSizeChanged { screenWidthPx = it.width }) {
    Box(
        Modifier
            .fillMaxSize()
            .roomPage(indexOf(RoomPage.ROOM), pagePosition, screenWidthPx)
            // Hanya MENGAMATI sentuhan untuk riak distorsi kaca; tidak pernah mengonsumsi event
            .pointerInput(fxPlan.distortion) {
                if (!fxPlan.distortion) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fx.pokeRipple(down.position)
                }
            }
    ) {
        RoomBackdrop(
            tone = settings.retro.room,
            floor = roomFloor,
            source = wallSource(home.background, cover, picture),
            animate = np.isPlaying && motionAllowed && !editing,
            // Warna sinematik mati = cahaya layar netral (putih hangat), bukan warna cover
            glow = if (settings.music.cinematicColors) accent else Color(0xFFFFF1DC),
            glowAlpha = if (np.hasTrack) settings.retro.glowIntensity * 0.18f else 0f,
            wallColor = if (home.background.type == BackgroundType.SOLID) home.background.solidColor.toColor() else null,
            wallGradient = if (home.background.type == BackgroundType.GRADIENT)
                home.background.gradientStart.toColor() to home.background.gradientEnd.toColor() else null,
            projectionModifier = Modifier.fxBackdrop(fx, fxPlan, settings.visual.parallax, settings.visual.parallaxStrength),
        )
        // Debu halus (partikel) di atas dinding, di bawah konten; cahaya reaktif sudah digambar ruang (bukan lapisan putih)
        if (fxPlan.particles) {
            FxOverlay(fx, fxPlan.copy(glow = false), accent, settings.visual.parallax, settings.visual.parallaxStrength, particleColor = dustColor(look.tone))
        }

        // Area konten; saat edit, menyusut agar pratinjau tidak tertutup panel editor
        Box(
            Modifier
                .fillMaxSize()
                .padding(
                    end = if (editing && landscape) EDITOR_WIDTH else 0.dp,
                    bottom = if (editing && !landscape) EDITOR_HEIGHT_PORTRAIT else 0.dp,
                )
                .windowInsetsPadding(WindowInsets.safeDrawing) // ruang aman untuk taskbar & status bar
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
                        // memakai gerakan jari (mis. turntable saat vinyl/tonearm diseret)
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val path = mutableListOf(down.position)
                        var moved = false
                        var takenByChild = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (ch.isConsumed) { takenByChild = true; break }
                            if (!ch.pressed) break
                            path.add(ch.position)
                            if (!moved && (ch.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                            if (moved) ch.consume()   // batalkan ketuk-lama (mode edit) selama menggambar
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
                // Sisakan ruang untuk Taskbar Vinyl di sisi tempat ia menempel
                .padding(
                    start = if (settings.dock.enabled && !editing && settings.dock.position == DockPosition.LEFT) (76 + settings.dock.margin).dp else 0.dp,
                    top = if (settings.dock.enabled && !editing && settings.dock.position == DockPosition.TOP) (76 + settings.dock.margin).dp else 0.dp,
                    end = if (settings.dock.enabled && !editing && settings.dock.position == DockPosition.RIGHT) (76 + settings.dock.margin).dp else 0.dp,
                    bottom = if (settings.dock.enabled && !editing && settings.dock.position == DockPosition.BOTTOM) (76 + settings.dock.margin).dp else 0.dp,
                )
        ) {
            HomeContent(
                home = home,
                np = np,
                accent = accent,
                weather = settings.weather,
                lyricsOnline = settings.music.lyricsOnline,
                editing = editing,
                fx = fx,
                fxPlan = fxPlan,
                visual = settings.visual,
                visualizerUnavailable = visualizerState == VisualizerStatus.State.UNAVAILABLE,
                retro = settings.retro,
                look = look,
                landscape = landscape,
                motionAllowed = motionAllowed,
                onFloor = { roomFloor = it },
                onWidgetChange = { updated -> vm.updateDraft { h -> h.copy(widgets = h.widgets.map { if (it.id == updated.id) updated else it }) } },
                vm = vm,
            )

        }
    }

        // Halaman perpustakaan kaset (dulu "rak piringan" yang muncul sebagai lapisan)
        Box(
            Modifier
                .fillMaxSize()
                .roomPage(indexOf(RoomPage.LIBRARY), pagePosition, screenWidthPx)
                .sidePageSwipe(swipeFromSide)
                .semantics {
                    customActions = listOf(CustomAccessibilityAction("Kembali ke ruang TV") { vm.showPage(RoomPage.ROOM); true })
                },
        ) {
            if (pageVisible(indexOf(RoomPage.LIBRARY), pagePosition)) {
                CrateSheet(
                    np = np, queue = queue, history = history,
                    music = settings.music,
                    onMusicChange = { m -> vm.updateSettings { it.copy(music = m) } },
                    onClose = { vm.showPage(RoomPage.ROOM) }, onClearHistory = vm::clearHistory,
                    state = libraryState,
                    onToggleFavorite = { key ->
                        vm.updateSettings { it.copy(music = it.music.copy(favorites = LibraryCatalog.toggleFavorite(it.music.favorites, key))) }
                    },
                )
            }
        }

        // Halaman konsol audio
        Box(
            Modifier
                .fillMaxSize()
                .roomPage(indexOf(RoomPage.CONSOLE), pagePosition, screenWidthPx)
                .sidePageSwipe(swipeFromSide),
        ) {
            if (pageVisible(indexOf(RoomPage.CONSOLE), pagePosition)) ConsoleScreen(onBack = { vm.showPage(RoomPage.ROOM) })
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

/** Jam, turntable & widget. Dipisah agar mudah dibaca. */
@Composable
private fun HomeContent(
    home: HomeSettings,
    np: NowPlaying,
    accent: Color,
    weather: WeatherSettings,
    lyricsOnline: Boolean,
    editing: Boolean,
    fx: FxState,
    fxPlan: FxPlan,
    visual: VisualSettings,
    visualizerUnavailable: Boolean,
    retro: RetroSettings,
    look: RoomLook,
    landscape: Boolean,
    motionAllowed: Boolean,
    onFloor: (RoomFloor) -> Unit,
    onWidgetChange: (WidgetConfig) -> Unit,
    vm: HomeViewModel,
) {
    val now by rememberNow()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxW = maxWidth

        // ----- Ruang TV: TV tabung (+ kaset, Tahap 4) di lantai, info lagu di bawahnya -----
        val showInfo = home.trackInfoVisible
        val clockText = "%02d:%02d".format(now.hour, now.minute)
        val vfdLines = remember(clockText, np.hasTrack, np.isPlaying) {
            listOf(
                VfdTicker.line("clock", clockText),
                VfdTicker.line("state", VfdTicker.playback(np.hasTrack, np.isPlaying)),
            )
        }
        // Knob TV (seek) dan kaset (kontrol putar)
        val knobState = remember { KnobState() }
        LaunchedEffect(editing, np.hasTrack) { if (editing || !np.hasTrack) knobState.exit() }
        val zoom by animateFloatAsState(
            if (knobState.active) KNOB_ZOOM else 1f,
            tween(
                if (!motionAllowed) 0 else if (knobState.active) Tokens.Motion.KNOB_ZOOM_IN_MS else Tokens.Motion.KNOB_ZOOM_OUT_MS,
                easing = Ease.standard,
            ),
            label = "knobZoom",
        )
        var clockTick by remember { mutableIntStateOf(0) }
        LaunchedEffect(np) {
            while (np.isPlaying) { delay(500); clockTick++ }
        }
        val posMs = remember(np, clockTick) { np.positionAt() }
        val cassetteLabel = remember(np.hasTrack, np.title, np.artist, np.art) {
            CassetteLabel(if (np.hasTrack) "${np.title}\u0000${np.artist}" else null, np.title, np.artist, np.art)
        }
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RoomStage(
                np = np,
                retro = retro,
                tvSize = home.turntableSize,
                position = home.turntablePosition,
                landscape = landscape,
                accent = accent,
                motionAllowed = motionAllowed && !editing,
                vfdLines = vfdLines,
                onScreenTap = { if (editing) Unit else if (np.hasTrack) vm.togglePlay() else vm.openMusic() },
                onFloor = onFloor,
                zoom = zoom,
                knobTurns = knobState.turns,
                knobHighlight = knobState.active,
                knob = { l, t, w, h ->
                    KnobLayer(
                        knobState, np, l, t, w, h, zoom = zoom, enabled = !editing,
                        onSeek = vm::seek, onTick = vm::tick,
                    )
                },
                cassette = { _, _ ->
                    Cassette(
                        label = cassetteLabel,
                        hasSession = np.hasSession,
                        playing = np.isPlaying,
                        progress = if (np.durationMs > 0) posMs.toFloat() / np.durationMs else 0f,
                        timeText = if (np.durationMs > 0) KnobSeek.bubble(posMs, np.durationMs) else KnobSeek.clock(posMs),
                        shell = retro.cassetteShell,
                        speed = retro.reelSpeed,
                        accent = accent,
                        motionAllowed = motionAllowed && !editing,
                        onPrevious = { if (!editing) vm.previous() },
                        onPlayPause = { if (!editing) { if (np.hasTrack) vm.togglePlay() else vm.openMusic() } },
                        onNext = { if (!editing) vm.next() },
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .fxForeground(fx, fxPlan, visual.parallax, visual.parallaxStrength),
            )
            if (showInfo) {
                TrackInfo(
                    np, home.lyricsVisible, lyricsOnline, onOpenCrate = vm::openCrate, onOpenLyrics = vm::openLyrics,
                    fx = fx, visualizer = fxPlan.visualizer, visualizerMode = visual.visualizer,
                    visualizerUnavailable = visualizerUnavailable, barColor = accent, look = look,
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 12.dp)
                        .width(minOf(maxW * 0.86f, 560.dp))
                        .roomPlate(look)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        // ----- Jam & tanggal (kecil, default pojok kiri atas) -----
        if (home.clock.visible) {
            ClockView(
                // Warna jam tersimpan dipakai bila terbaca di dinding; krem bawaan tema vinyl diganti tinta ruang
                settings = home.clock.copy(color = RoomInk.readable(home.clock.color, look.tone)),
                now = now,
                horizontalAlignment = home.clock.anchor.horizontal(),
                modifier = Modifier.align(home.clock.anchor.toAlignment()).padding(20.dp).roomPlate(look).padding(8.dp),
            )
        }

        // ----- Widget tambahan -----
        val wPx = constraints.maxWidth
        val hPx = constraints.maxHeight
        home.widgets.forEach { w ->
            HomeWidget(w, wPx, hPx, editing, accent, weather, Color(RoomInk.readable(home.clock.color, look.tone)), onWidgetChange)
        }
    }
}

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
    accent: Color,
    weather: WeatherSettings,
    textColor: Color,
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
            WidgetType.CALENDAR -> TwoMonthCalendar(
                today = java.time.LocalDate.now(),
                showEvents = false,
                color = textColor,
                accent = accent,
                card = true,
                cell = (22 * config.scale).dp,
            )
            WidgetType.WEATHER -> WeatherWidget(
                weather, textColor, scale = config.scale,
                modifier = Modifier.glass(Corner.l).padding(horizontal = 20.dp, vertical = 14.dp),
            )
            WidgetType.PHOTO -> PhotoWidget(config.images, scale = config.scale)
            WidgetType.BATTERY -> BatteryIndicator(
                textColor, scale = config.scale,
                modifier = Modifier.glass(RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 10.dp),
            )
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
 * Info lagu di bawah turntable: judul tebal, artis redup, progress tipis + waktu
 * (hierarki Now Playing Apple Music). Kontrol ada di turntable; dua kapsul di bawahnya membuka lirik layar penuh
 * dan rak piringan.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrackInfo(
    np: NowPlaying,
    showLyrics: Boolean,
    lyricsOnline: Boolean,
    onOpenCrate: () -> Unit,
    onOpenLyrics: () -> Unit,
    fx: FxState,
    visualizer: VisualizerPlan,
    visualizerMode: VisualizerMode,
    visualizerUnavailable: Boolean,
    barColor: Color,
    look: RoomLook,
    modifier: Modifier = Modifier,
) {
    val lyrics = rememberLyrics(np, showLyrics, lyricsOnline)
    var progress by remember { mutableFloatStateOf(np.progress) }
    var posText by remember { mutableStateOf(formatMs(np.positionAt())) }
    LaunchedEffect(np) {
        while (true) {
            progress = np.progress
            posText = formatMs(np.positionAt())
            if (!np.isPlaying) break
            delay(500)
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(
            targetState = np.title to np.artist,
            transitionSpec = { (fadeIn(tween(400)) + slideInVertically { it / 3 }) togetherWith fadeOut(tween(250)) },
            label = "track",
        ) { (title, artist) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (np.hasTrack) title else "Tidak ada yang diputar",
                    style = MaterialTheme.typography.headlineSmall,
                    color = look.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (np.hasTrack) artist else "Ketuk TV untuk membuka aplikasi musik",
                    style = MaterialTheme.typography.bodyLarge,
                    color = look.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (np.hasTrack && np.durationMs > 0) {
            Box(
                Modifier.padding(top = 18.dp).fillMaxWidth().height(4.dp)
                    .clip(RoundedCornerShape(50)).background(look.primary.copy(alpha = 0.16f))
            ) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(look.primary.copy(alpha = 0.78f)))
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(posText, style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = look.secondary)
                Spacer(Modifier.weight(1f))
                Text(formatMs(np.durationMs), style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = look.secondary)
            }
        }
        if (np.hasTrack) VisualizerStrip(fx, visualizer, visualizerMode, visualizerUnavailable, barColor, look.secondary)
        if (np.hasTrack && showLyrics) {
            LyricsLines(
                np, lyrics,
                primary = look.primary,
                secondary = look.secondary,
                modifier = Modifier.padding(top = 20.dp).fillMaxWidth()
                    .clip(Corner.s)
                    .clickable(onClickLabel = "Buka lirik layar penuh", role = Role.Button, onClick = onOpenLyrics),
            )
        }
        if (np.hasTrack) {
            FlowRow(
                Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                for ((label, clickLabel, action) in listOf(
                    Triple("Lirik", "Buka lirik layar penuh", onOpenLyrics),
                    Triple("Kaset  ›", "Buka perpustakaan kaset", onOpenCrate),
                )) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = look.primary,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, look.secondary.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = action)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

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
        plan == VisualizerPlan.NEEDS_PERMISSION -> "Visualizer audio asli butuh izin mikrofon. Atur di Pengaturan → Efek visual."
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
