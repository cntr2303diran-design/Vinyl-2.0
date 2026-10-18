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
import com.vinyl.launcher.ui.theme.TextPrimary
import com.vinyl.launcher.ui.theme.TextSecondary
import com.vinyl.launcher.ui.theme.TextTertiary
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
import com.vinyl.launcher.data.TurntablePosition
import com.vinyl.launcher.data.WeatherSettings
import com.vinyl.launcher.data.WidgetConfig
import com.vinyl.launcher.data.WidgetType
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.ui.components.BackgroundLayer
import com.vinyl.launcher.ui.components.BatteryIndicator
import com.vinyl.launcher.ui.components.ClockView
import com.vinyl.launcher.ui.components.TURNTABLE_ASPECT
import com.vinyl.launcher.ui.components.Turntable
import com.vinyl.launcher.ui.components.TurntableStyle
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
    val crateOpen by vm.crateOpen.collectAsStateWithLifecycle()
    val lyricsOpen by vm.lyricsOpen.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val editing = draft != null

    // Efek visual (Tahap 4): satu keputusan (FxPlan) dari pengaturan + lingkungan; tertutup overlay = semua efek berhenti
    val fx = rememberFxState()
    val fxPlan = rememberFxPlan(settings.visual, np.isPlaying, screenVisible = !drawerOpen && !crateOpen && !lyricsOpen, fx = fx)
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
            crateOpen -> vm.closeCrate()
            drawerOpen -> vm.closeDrawer()
            editing -> vm.cancelEdit()
            else -> Unit // sudah di Home: abaikan
        }
    }

    val gestures by rememberUpdatedState(settings.gestures)
    val customGestures by rememberUpdatedState(settings.customGestures)

    Box(
        Modifier
            .fillMaxSize()
            // Hanya MENGAMATI sentuhan untuk riak distorsi kaca; tidak pernah mengonsumsi event
            .pointerInput(fxPlan.distortion) {
                if (!fxPlan.distortion) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fx.pokeRipple(down.position)
                }
            }
    ) {
        BackgroundLayer(
            home.background, bgColor, art = np.art.takeIf { np.hasTrack }, swatches = np.swatches, animate = np.isPlaying,
            cinematic = settings.music.cinematicColors && !editing,
            fx = fx, fxPlan = fxPlan, visual = settings.visual, fxTint = accent,
        )

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
                        CustomAccessibilityAction("Buka rak piringan") { vm.openCrate(); true },
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
                            abs(dx) >= abs(dy) && dx < -160f -> vm.openCrate()
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
                dominant = dominant,
                weather = settings.weather,
                lyricsOnline = settings.music.lyricsOnline,
                editing = editing,
                fx = fx,
                fxPlan = fxPlan,
                visual = settings.visual,
                visualizerUnavailable = visualizerState == VisualizerStatus.State.UNAVAILABLE,
                onWidgetChange = { updated -> vm.updateDraft { h -> h.copy(widgets = h.widgets.map { if (it.id == updated.id) updated else it }) } },
                vm = vm,
            )

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

        // Rak piringan
        AnimatedVisibility(
            visible = crateOpen,
            enter = slideInHorizontally(tween(340)) { it / 3 } + fadeIn(tween(260)),
            exit = slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(200)),
        ) {
            CrateSheet(
                np = np, queue = queue, history = history, fallback = bgColor,
                music = settings.music,
                onMusicChange = { m -> vm.updateSettings { it.copy(music = m) } },
                onClose = vm::closeCrate, onClearHistory = vm::clearHistory,
            )
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
    dominant: Color,
    weather: WeatherSettings,
    lyricsOnline: Boolean,
    editing: Boolean,
    fx: FxState,
    fxPlan: FxPlan,
    visual: VisualSettings,
    visualizerUnavailable: Boolean,
    onWidgetChange: (WidgetConfig) -> Unit,
    vm: HomeViewModel,
) {
    val now by rememberNow()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxW = maxWidth
        val maxH = maxHeight

        // ----- Turntable (+ info lagu di bawahnya, ala Apple Music) -----
        val showInfo = home.trackInfoVisible
        val heightShare = if (showInfo) (if (home.lyricsVisible) 0.58f else 0.66f) else 0.80f
        val baseWidth = minOf(maxW * 0.94f, maxH * heightShare * TURNTABLE_ASPECT)
        val ttWidth = baseWidth * home.turntableSize
        val ttHeight = ttWidth / TURNTABLE_ASPECT
        val align = when (home.turntablePosition) {
            TurntablePosition.LEFT -> Alignment.CenterStart
            TurntablePosition.CENTER -> Alignment.Center
            TurntablePosition.RIGHT -> Alignment.CenterEnd
        }
        Column(
            Modifier.align(align).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Turntable(
                nowPlaying = np,
                style = TurntableStyle(
                    vinylStyle = home.vinylStyle,
                    tonearmStyle = home.tonearmStyle,
                    coverVisible = home.coverVisible,
                    coverRounded = home.coverRounded,
                    coverShadow = home.coverShadow,
                    rpm = home.rpm,
                ),
                labelColor = accent,
                discTint = dominant,
                interactive = !editing,
                onTogglePlay = vm::togglePlay,
                onNext = vm::next,
                onPrevious = vm::previous,
                onSeek = vm::seek,
                onOpenMusic = vm::openMusic,
                onNeedleDown = vm::needleDown,
                onNeedleUp = vm::needleUp,
                onTick = vm::tick,
                modifier = Modifier.size(ttWidth, ttHeight).fxForeground(fx, fxPlan, visual.parallax, visual.parallaxStrength),
            )
            if (showInfo) {
                TrackInfo(
                    np, home.lyricsVisible, lyricsOnline, onOpenCrate = vm::openCrate, onOpenLyrics = vm::openLyrics,
                    fx = fx, visualizer = fxPlan.visualizer, visualizerMode = visual.visualizer,
                    visualizerUnavailable = visualizerUnavailable, barColor = accent,
                    modifier = Modifier.padding(top = 28.dp).width(minOf(ttWidth * 0.62f, 560.dp)),
                )
            }
        }

        // ----- Jam & tanggal (kecil, default pojok kiri atas) -----
        if (home.clock.visible) {
            ClockView(
                settings = home.clock,
                now = now,
                horizontalAlignment = home.clock.anchor.horizontal(),
                modifier = Modifier.align(home.clock.anchor.toAlignment()).padding(28.dp),
            )
        }

        // ----- Widget tambahan -----
        val wPx = constraints.maxWidth
        val hPx = constraints.maxHeight
        home.widgets.forEach { w ->
            HomeWidget(w, wPx, hPx, editing, accent, weather, home.clock.color.toColor(), onWidgetChange)
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
                modifier = Modifier.glass(RoundedCornerShape(26.dp)).padding(horizontal = 20.dp, vertical = 14.dp),
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
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (np.hasTrack) artist else "Ketuk sampul untuk membuka aplikasi musik",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (np.hasTrack && np.durationMs > 0) {
            Box(
                Modifier.padding(top = 18.dp).fillMaxWidth().height(4.dp)
                    .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f))
            ) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Color.White.copy(alpha = 0.78f)))
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(posText, style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = TextTertiary)
                Spacer(Modifier.weight(1f))
                Text(formatMs(np.durationMs), style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = TextTertiary)
            }
        }
        if (np.hasTrack) VisualizerStrip(fx, visualizer, visualizerMode, visualizerUnavailable, barColor)
        if (np.hasTrack && showLyrics) {
            LyricsLines(
                np, lyrics,
                Modifier.padding(top = 20.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
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
                    Triple("Berikutnya  ›", "Buka rak piringan", onOpenCrate),
                )) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(50))
                            .glass(RoundedCornerShape(50), fillAlpha = 0.06f)
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
                color = TextSecondary,
                modifier = Modifier.padding(top = if (showBars) 6.dp else 0.dp),
            )
        }
    }
}
