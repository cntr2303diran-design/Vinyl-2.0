package com.vinyl.launcher.ui.cover

import android.Manifest
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinyl.launcher.data.Anchor
import com.vinyl.launcher.data.CoverSettings
import com.vinyl.launcher.media.MediaRepository
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.ui.components.BackgroundLayer
import com.vinyl.launcher.ui.components.BatteryIndicator
import com.vinyl.launcher.ui.components.ClockView
import com.vinyl.launcher.ui.components.DateText
import com.vinyl.launcher.ui.components.NowPlayingCard
import com.vinyl.launcher.ui.components.LyricsLines
import com.vinyl.launcher.ui.components.rememberLyrics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.vinyl.launcher.ui.components.ShimmerText
import com.vinyl.launcher.ui.components.TwoMonthCalendar
import com.vinyl.launcher.data.RetroSettings
import com.vinyl.launcher.data.RoomTone
import com.vinyl.launcher.fx.rememberPowerSave
import com.vinyl.launcher.ui.room.CrtTv
import com.vinyl.launcher.ui.room.TvGeometry
import com.vinyl.launcher.ui.room.TvScreen
import com.vinyl.launcher.ui.room.VfdTicker
import com.vinyl.launcher.ui.room.rememberWorstCoverPixel
import com.vinyl.launcher.ui.theme.BackdropDim
import com.vinyl.launcher.ui.theme.rememberReduceMotion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.vinyl.launcher.ui.components.rememberNow
import com.vinyl.launcher.ui.components.sheet
import com.vinyl.launcher.ui.components.timeParts
import com.vinyl.launcher.ui.settings.CoverStyleControls
import com.vinyl.launcher.ui.theme.Accent
import com.vinyl.launcher.ui.theme.horizontal
import com.vinyl.launcher.ui.theme.toAlignment
import com.vinyl.launcher.ui.theme.toColor
import com.vinyl.launcher.ui.theme.toComposeColor
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.min

private val EDITOR_WIDTH = 420.dp
private val EDITOR_HEIGHT = 440.dp

/** Elemen info yang ditempatkan di area kanan (landscape) / bawah (portrait). Urutan = urutan tumpukan. */
private enum class CoverItem { DATE, CLOCK, CALENDAR, SONG, BATTERY }

@Composable
fun CoverScreen(
    vm: CoverViewModel,
    onUnlock: (onCancelled: () -> Unit) -> Unit,
    onRequestEdit: () -> Unit,
) {
    val cover by vm.cover.collectAsStateWithLifecycle()
    val music by vm.music.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val retro = vm.settings.collectAsStateWithLifecycle().value.retro
    val np by vm.nowPlaying.collectAsStateWithLifecycle()
    val editing = draft != null
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val now by rememberNow()

    val albumColor = np.dominantColor?.takeIf { np.hasTrack }?.toComposeColor() ?: Color(0xFF26262C)
    val accent = np.accentColor?.takeIf { np.hasTrack }?.toComposeColor() ?: Accent

    // ---- Geser untuk membuka: SELURUH layar bisa digeser, vinyl ikut bergerak ----
    val offsetX = remember { Animatable(0f) }
    var widthPx by remember { mutableIntStateOf(1) }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
    ) {
        BackgroundLayer(
            cover.background, albumColor, art = np.art, swatches = np.swatches, animate = np.isPlaying, dim = 0.42f,
            cinematic = music.cinematicColors && !editing,
        )

        Box(
            Modifier
                .fillMaxSize()
                .padding(end = if (editing && landscape) EDITOR_WIDTH else 0.dp, bottom = if (editing && !landscape) EDITOR_HEIGHT else 0.dp)
                // Cegah gestur "kembali" sistem di tepi layar mencuri geseran
                .systemGestureExclusion()
                .pointerInput(editing) {
                    if (editing) return@pointerInput
                    detectTapGestures(onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRequestEdit()
                    })
                }
                .pointerInput(editing) {
                    if (editing) return@pointerInput
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onDragEnd = {
                            val v = tracker.calculateVelocity().x
                            scope.launch {
                                val pass = offsetX.value > widthPx * 0.28f || (v > 1800f && offsetX.value > widthPx * 0.06f)
                                if (pass) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    offsetX.animateTo(widthPx.toFloat(), tween(280, easing = FastOutSlowInEasing))
                                    onUnlock { scope.launch { offsetX.animateTo(0f, spring(0.75f, 260f)) } }
                                } else {
                                    offsetX.animateTo(0f, spring(0.7f, 320f))
                                }
                            }
                        },
                        onDragCancel = { scope.launch { offsetX.animateTo(0f, spring(0.7f, 320f)) } },
                    ) { change, dx ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        scope.launch { offsetX.snapTo((offsetX.value + dx).coerceAtLeast(0f)) }
                    }
                }
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 40.dp, vertical = 28.dp)
        ) {
            val progress = (offsetX.value / widthPx).coerceIn(0f, 1f)
            // Konten info memudar & sedikit bergeser saat layar digeser
            val infoAlpha = 1f - (progress * 2.2f).coerceIn(0f, 1f)
            if (landscape) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    BoxWithConstraints(Modifier.weight(0.47f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        CoverTv(np, cover, retro, accent, min(maxWidth.value * 0.95f, maxHeight.value * 0.86f * TvGeometry.ASPECT).dp, now, offsetX.value)
                    }
                    InfoArea(
                        cover, np, now, accent, music.lyricsOnline,
                        Modifier.weight(0.53f).fillMaxHeight().padding(start = 40.dp)
                            .graphicsLayer { alpha = infoAlpha; translationX = offsetX.value * 0.35f },
                    )
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    BoxWithConstraints(Modifier.fillMaxWidth().weight(0.46f), contentAlignment = Alignment.Center) {
                        CoverTv(np, cover, retro, accent, min(maxWidth.value * 0.9f, maxHeight.value * 0.95f * TvGeometry.ASPECT).dp, now, offsetX.value)
                    }
                    InfoArea(
                        cover, np, now, accent, music.lyricsOnline,
                        Modifier.fillMaxWidth().weight(0.54f).padding(top = 28.dp)
                            .graphicsLayer { alpha = infoAlpha; translationX = offsetX.value * 0.35f },
                    )
                }
            }

            if (!editing) {
                ShimmerText(
                    "Geser ke kanan untuk membuka",
                    modifier = Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = infoAlpha },
                )
            }
        }

        AnimatedVisibility(
            visible = editing,
            modifier = Modifier.align(if (landscape) Alignment.CenterEnd else Alignment.BottomCenter),
            enter = (if (landscape) slideInHorizontally { it } else slideInVertically { it }) + fadeIn(),
            exit = (if (landscape) slideOutHorizontally { it } else slideOutVertically { it }) + fadeOut(),
        ) {
            CoverEditorPanel(vm, landscape)
        }
    }
}

/**
 * TV tabung berisi cover album (pengganti piringan vinyl di layar kunci). Ikut bergeser & memudar saat layar digeser ke
 * kanan untuk membuka. VFD menampilkan status putar, jam, dan artis.
 */
@Composable
private fun CoverTv(np: NowPlaying, cover: CoverSettings, retro: RetroSettings, accent: Color, width: Dp, now: LocalDateTime, dragOffset: Float) {
    val motionAllowed = !rememberReduceMotion() && !rememberPowerSave()
    val brightest = rememberWorstCoverPixel(np.art.takeIf { np.hasTrack }, RoomTone.DIM)
    val scrim = remember(brightest) {
        // Judul putih di atas cover: peredam secukupnya agar >= 4,5:1 di piksel paling terang
        brightest?.let { BackdropDim.minOverlayAlpha(it, listOf(0xFFFFFFFFL), effects = false).toFloat() } ?: 0.25f
    }
    val time = timeParts(now, cover.clock.use24h).let { (hm, ampm) -> hm + (ampm?.let { " $it" } ?: "") }
    CrtTv(
        mode = TvScreen.mode(np.hasTrack, np.isPlaying, retro.albumMotion, motionAllowed),
        art = np.art.takeIf { np.hasTrack },
        title = if (np.hasTrack) np.title else "",
        artist = if (np.hasTrack) np.artist else "",
        accent = accent,
        body = retro.tvBody,
        knob = retro.knobStyle,
        vfdColor = retro.vfdColor,
        vfdLines = listOf(
            VfdTicker.playback(np.hasTrack, np.isPlaying),
            time.uppercase().take(VfdTicker.MAX_CHARS),
            (np.artist.ifBlank { "--" }).uppercase().take(VfdTicker.MAX_CHARS),
        ),
        crtStrength = retro.crtStrength,
        motion = retro.albumMotion,
        reduceMotion = !motionAllowed,
        titleScrimAlpha = scrim,
        modifier = Modifier
            .size(width, width / TvGeometry.ASPECT)
            .graphicsLayer {
                translationX = dragOffset
                alpha = 1f - (dragOffset / (width.toPx() * 2.2f)).coerceIn(0f, 0.8f)
            }
            .semantics(mergeDescendants = true) {
                contentDescription = if (np.hasTrack) "TV menampilkan ${np.title}" + (if (np.artist.isNotBlank()) " oleh ${np.artist}" else "")
                else "TV, siaga"
            },
    )
}

@Composable
private fun InfoArea(cover: CoverSettings, np: NowPlaying, now: LocalDateTime, accent: Color, lyricsOnline: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val items = buildList {
        if (cover.date.visible) add(CoverItem.DATE to cover.date.anchor)
        if (cover.clock.visible) add(CoverItem.CLOCK to cover.clock.anchor)
        if (cover.calendar.visible) add(CoverItem.CALENDAR to cover.calendar.anchor)
        if (cover.songInfo.visible) add(CoverItem.SONG to cover.songInfo.anchor)
        if (cover.battery.visible) add(CoverItem.BATTERY to cover.battery.anchor)
    }.groupBy({ it.second }, { it.first })

    val textColor = cover.clock.color.toColor()
    BoxWithConstraints(modifier) {
        val cell = min(32f, maxWidth.value / 17f).dp
        val clockBase = min(110f, maxWidth.value / 4.2f).dp
        items.forEach { (anchor: Anchor, list) ->
            Column(
                Modifier.align(anchor.toAlignment()),
                horizontalAlignment = anchor.horizontal(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                list.forEachIndexed { i, item ->
                    if (i > 0 && item != CoverItem.CLOCK) Spacer(Modifier.height(14.dp))
                    when (item) {
                        CoverItem.DATE -> DateText(now, cover.clock, baseSize = clockBase, modifier = Modifier.padding(start = 4.dp))
                        CoverItem.CLOCK -> ClockView(
                            settings = cover.clock.copy(showDate = false),
                            now = now,
                            baseSize = clockBase,
                            horizontalAlignment = anchor.horizontal(),
                        )
                        CoverItem.CALENDAR -> TwoMonthCalendar(
                            today = LocalDate.from(now),
                            showEvents = cover.calendarEvents,
                            color = textColor,
                            accent = accent,
                            cell = cell,
                            card = true,
                        )
                        CoverItem.SONG -> Column(horizontalAlignment = anchor.horizontal()) {
                            NowPlayingCard(
                                np = np,
                                onTogglePlay = { MediaRepository.togglePlayPause("Kartu layar kunci") },
                                onNext = { MediaRepository.next() },
                                onPrevious = { MediaRepository.previous() },
                                onOpen = { MediaRepository.openMusicApp(context) },
                            )
                            if (cover.lyricsVisible && np.hasTrack) {
                                val lyrics = rememberLyrics(np, true, lyricsOnline)
                                LyricsLines(
                                    np, lyrics,
                                    modifier = Modifier.padding(top = 16.dp, start = 6.dp).width(440.dp),
                                    fontSize = 22.sp,
                                    align = when (anchor.horizontal()) {
                                        Alignment.End -> TextAlign.End
                                        Alignment.CenterHorizontally -> TextAlign.Center
                                        else -> TextAlign.Start
                                    },
                                )
                            }
                        }
                        CoverItem.BATTERY -> BatteryIndicator(textColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverEditorPanel(vm: CoverViewModel, landscape: Boolean) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val retro = vm.settings.collectAsStateWithLifecycle().value.retro
    val d = draft ?: return
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.updateDraft(d.copy(calendarEvents = false))
    }
    val shape = if (landscape) RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp) else RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    Column(
        (if (landscape) Modifier.width(EDITOR_WIDTH).fillMaxHeight() else Modifier.fillMaxWidth().height(EDITOR_HEIGHT))
            .sheet(shape)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(if (landscape) WindowInsetsSides.Vertical + WindowInsetsSides.End else WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            )
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 14.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Layar kunci", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::cancelEdit) { Text("Batal") }
            Button(onClick = vm::saveEdit) { Text("Simpan") }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            CoverStyleControls(
                cover = d,
                onChange = vm::updateDraft,
                onRequestCalendarPermission = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) },
            )
            Box(Modifier.height(28.dp))
        }
    }
}
