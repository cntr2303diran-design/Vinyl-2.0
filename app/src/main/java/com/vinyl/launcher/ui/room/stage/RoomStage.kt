package com.vinyl.launcher.ui.room.stage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.vinyl.launcher.data.RetroSettings
import com.vinyl.launcher.data.RoomTone
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.ui.cassette.CassetteLabel
import com.vinyl.launcher.ui.room.ChannelStatic
import com.vinyl.launcher.ui.room.CrtTv
import com.vinyl.launcher.ui.room.TvGeometry
import com.vinyl.launcher.ui.room.TvScreen
import com.vinyl.launcher.ui.room.TvScreenMode
import com.vinyl.launcher.ui.room.tvMaterial

/** Teks dan warna yang sudah diputuskan pemanggil untuk panggung. */
@Immutable
data class StageInfo(
    /** Baris VFD kecil di panel TV (mis. "PLAY"). */
    val tvVfd: String,
    val deck: DeckDisplay,
    /** Durasi lagu di label kaset (mis. "4:18"). */
    val tapeTime: String,
    /** Keadaan layar TV untuk pembaca layar bila bukan kaset musik (mis. "Skor 12, rekor 30. Berjalan"); null = tidak ada. */
    val tvState: String? = null,
)

class StageActions(
    val onPrevious: () -> Unit,
    val onRewind: () -> Unit,
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onTick: () -> Unit,
    val onOpenLyrics: () -> Unit,
    /** Ketuk laci kaset di meja: tarik/dorong. */
    val onToggleDrawer: () -> Unit = {},
    /** Jendela VFD deck menjadi tombol (mis. mode Teater); null = hanya tampilan. */
    val onVfdClick: (() -> Unit)? = null,
    val vfdClickLabel: String = "",
    /** Ketuk kaset di meja; null = sama dengan [onPlayPause] (kaset game memakainya untuk mulai/jeda, bukan tombol "turun"). */
    val onCassette: (() -> Unit)? = null,
    /** Tombol daya TV di panel kontrol; null = tombol tidak dipasang. */
    val onPower: (() -> Unit)? = null,
    /** Aksi aksesibilitas pada layar TV bagi pembaca layar (mis. belokan game): nama dan aksinya. */
    val tvActions: List<Pair<String, () -> Unit>> = emptyList(),
)

/** Keadaan laci kaset di meja: terbuka atau tidak, warna kaset yang mengintip, dan jumlah kasetnya. */
@Immutable
class StageDrawerInfo(val open: Boolean, val peek: List<Color>, val count: Int)

/**
 * Beranda bergaya rekaman VHS: ruangan malam, proyeksi di dinding, meja kayu dengan deck kaset di raknya, TV tabung
 * yang DIAM (tidak membesar, tidak bisa dipindah), dan kaset 3D di sampingnya. Susunan dari [StageLayout].
 *
 * Interaksi: ketuk layar TV = lirik layar penuh; putar knob SEEK = geser posisi (waktu tampil di layar TV); ketuk kaset =
 * putar/jeda; tombol deck = sebelumnya / mundur 10 detik / putar-jeda / berikutnya; ketuk lampu dinding = ruang terang/gelap.
 *
 * @param topInset ruang di atas yang dipakai sistem/taskbar (dp); [bottomInset] di bawah
 */
@Composable
fun RoomStage(
    np: NowPlaying,
    retro: RetroSettings,
    tone: RoomTone,
    tvSize: Float,
    accent: Color,
    motionAllowed: Boolean,
    editing: Boolean,
    /** Posisi lagu; dibaca hanya di fase gambar (tidak mengomposisi ulang panggung tiap detak). */
    position: () -> Long,
    titleScrimAlpha: Float,
    seek: SeekState,
    info: StageInfo,
    actions: StageActions,
    projection: ProjectionContent?,
    modifier: Modifier = Modifier,
    topInset: Float = 0f,
    bottomInset: Float = 0f,
    wallColor: Color? = null,
    wallGradient: Pair<Color, Color>? = null,
    wallPicture: ImageBitmap? = null,
    showCover: Boolean = true,
    showTitle: Boolean = true,
    tvOff: Boolean = false,
    /** Video milik pengguna di layar TV (menggantikan cover setelah frame pertamanya tampil). */
    screenVideo: (@Composable () -> Unit)? = null,
    /** Lapisan di depan dinding & proyeksi, di belakang meja dan TV (mis. debu melayang). */
    atmosphere: @Composable () -> Unit = {},
    /** Kaset di deck: menentukan tombol deck dan apa yang bisa diketuk di TV. */
    tape: DeckTape = DeckTape.MUSIC,
    /** Label kaset di meja bila bukan kaset musik (kaset film/game); null = kaset musik. */
    tapeLabel: CassetteLabel? = null,
    /** Isi layar TV yang hidup (halaman film, game); null = cover musik. */
    tvScreen: (@Composable () -> Unit)? = null,
    /** Laci kaset di meja; null = tanpa laci. */
    drawer: StageDrawerInfo? = null,
    /**
     * Lampu dinding: ketuk = ruang terang/gelap. Null = tanpa lampu (mis. dinding berwarna pilihan sendiri, yang tidak
     * mengikuti nada ruang, sehingga ketukannya tidak akan terlihat).
     */
    onToggleLamp: (() -> Unit)? = null,
    /** Isi tambahan di layar TV (subtitel lirik). Lambda terakhir. */
    screenExtra: @Composable () -> Unit = {},
) {
    val mode = TvScreen.mode(np.hasTrack, np.isPlaying, retro.albumMotion, motionAllowed)
    val screenOn = mode != TvScreenMode.STANDBY && !tvOff
    val screenGlow = if (np.hasTrack) accent else Color(0xFFBFD4FF)
    val material = tvMaterial(retro.tvBody)
    val pal = stagePalette(tone, wallColor)
    val projectionGlow = if (projection != null) {
        val c = (projection as? ProjectionContent.Tint)?.color ?: accent
        c.copy(alpha = 0.30f * retro.projector.brightness.coerceIn(0.1f, 1f))
    } else Color.Transparent

    val weave = remember { weaveBrush() }
    val carpet = remember { carpetBrush() }
    // Panggung adalah benda fisik: tidak dicerminkan pada bahasa kanan-ke-kiri (tombol pita & knob juga tidak, seperti
    // pedoman kontrol media); teks di dalamnya tetap mengikuti arah tulisannya sendiri
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth.value
        val h = maxHeight.value
        val projectorSize = retro.projector.size
        val f = remember(w, h, tvSize, topInset, bottomInset, projectorSize) {
            StageLayout.compute(w, h, tvSize, topInset, bottomInset, projectorSize)
        }

        // ---------- Dinding & lantai
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            drawStageBackdrop(
                f, pal, projectionGlow, screenGlow, retro.glowIntensity * if (screenOn) 1f else 0.2f, wallGradient,
                paneling = tone == RoomTone.DIM, carpet = carpet,
            )
        }
        // Gambar dinding pilihan pengguna (samar, di belakang proyeksi)
        if (wallPicture != null) {
            Image(
                wallPicture, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.22f }.clearAndSetSemantics { },
            )
        }
        // ---------- Proyeksi di dinding
        if (projection != null) {
            WallProjection(f.projection, projection, retro.projector, animate = motionAllowed && np.isPlaying)
        }

        atmosphere()

        // ---------- Meja, rak, atap & sisi kabinet TV
        Box(
            Modifier
                .fillMaxSize()
                .clearAndSetSemantics { }
                .drawWithCache {
                    val grain = standGrain(f, density)
                    onDrawBehind { drawStageFurniture(f, pal, material, screenGlow, retro.glowIntensity, screenOn, grain, weave) }
                },
        )

        // ---------- TV (muka depan): ketuk layar = lirik
        val tv = f.tvFront
        Box(
            Modifier
                .offset(tv.left.dp, tv.top.dp)
                .size(tv.width.dp, tv.height.dp)
                .semantics(mergeDescendants = tvScreen == null) {
                    contentDescription = when {
                        tvOff -> "TV mati"
                        tape == DeckTape.FILM -> "TV menampilkan film ${np.title}"
                        tape.isGame -> "TV menampilkan game ${np.title}"
                        mode == TvScreenMode.STANDBY -> "TV, siaga. Tidak ada lagu"
                        else -> "TV menampilkan ${np.title}" + if (np.artist.isNotBlank()) " oleh ${np.artist}" else ""
                    }
                    if (tape == DeckTape.MUSIC) {
                        stateDescription = if (!np.hasTrack) "Siaga" else if (np.isPlaying) "Diputar" else "Dijeda"
                        customActions = listOf(CustomAccessibilityAction("Buka lirik layar penuh") { actions.onOpenLyrics(); true })
                    } else {
                        info.tvState?.let { stateDescription = it }
                        if (!tvOff && actions.tvActions.isNotEmpty()) {
                            customActions = actions.tvActions.map { (label, perform) -> CustomAccessibilityAction(label) { perform(); true } }
                        }
                    }
                }
                // Layar hidup (WebView/game) menerima sentuhan sendiri; ketuk-untuk-lirik hanya untuk kaset musik
                .clickable(enabled = !editing && !tvOff && np.hasTrack && tape == DeckTape.MUSIC, onClickLabel = "Buka lirik", role = Role.Button, onClick = actions.onOpenLyrics),
        ) {
            CrtTv(
                mode = mode, art = np.art.takeIf { showCover }, title = if (showTitle) np.title else "",
                artist = if (showTitle) np.artist else "", accent = accent,
                body = retro.tvBody, knob = retro.knobStyle, vfdColor = retro.vfdColor, vfdLines = listOf(info.tvVfd),
                crtStrength = retro.crtStrength, motion = retro.albumMotion, reduceMotion = !motionAllowed,
                titleScrimAlpha = titleScrimAlpha, knobTurns = seek.turns, knobHighlight = seek.touching,
                modifier = Modifier.fillMaxSize(), poweredOff = tvOff, screenVideo = screenVideo, screenLive = tvScreen,
                screenOverlay = {
                    screenExtra()
                    SeekOsd(seek, np.durationMs, tv.width.dp)
                    // Kaset berganti = "ganti channel": hujan statis singkat (kaset musik: satu identitas, ganti lagu tidak memicunya)
                    ChannelStatic(trigger = if (tape == DeckTape.MUSIC) tape else tapeLabel?.key, enabled = motionAllowed && !editing)
                },
            )
        }
        // Knob SEEK: area sentuh di atas gambar knob (tidak ikut klik TV)
        SeekKnob(
            seek, np,
            center = Offset(tv.left + tv.width * TvGeometry.KNOB_CX, tv.top + tv.height * TvGeometry.KNOB_CY),
            radius = tv.width * TvGeometry.KNOB_R,
            enabled = !editing && np.hasTrack && !tape.isGame,
            onSeek = actions.onSeek, onTick = actions.onTick,
        )
        // Tombol daya TV: area sentuh >= 48 dp di atas gambar tombol; digeser ke bawah bila akan menimpa area knob SEEK
        actions.onPower?.let { onPower ->
            val target = 48f
            val knobHalf = maxOf(48f, tv.width * TvGeometry.KNOB_R * 2.8f) / 2f
            val knobCy = tv.top + tv.height * TvGeometry.KNOB_CY
            val cx = (tv.left + tv.width * TvGeometry.POWER_CX).coerceIn(tv.left + target / 2f, maxOf(tv.left + target / 2f, tv.left + tv.width - target / 2f))
            val cy = maxOf(tv.top + tv.height * TvGeometry.POWER_CY, knobCy + knobHalf + target / 2f)
            Box(
                Modifier
                    .offset((cx - target / 2f).dp, (cy - target / 2f).dp)
                    .size(target.dp)
                    .clip(CircleShape)
                    .semantics {
                        contentDescription = "Tombol daya TV"
                        stateDescription = if (tvOff) "Mati" else "Menyala"
                    }
                    .clickable(enabled = !editing, onClickLabel = if (tvOff) "Nyalakan TV" else "Matikan TV", role = Role.Button, onClick = onPower),
            )
        }

        // ---------- Kaset 3D
        val duration = np.durationMs
        val progress: () -> Float = { if (duration > 0) position().toFloat() / duration else 0f }
        val musicLabel = remember(np.hasTrack, np.title, np.artist, np.art) {
            CassetteLabel(if (np.hasTrack) "${np.title}\u0000${np.artist}" else null, np.title, np.artist, np.art)
        }
        val label = tapeLabel ?: musicLabel
        StageCassette(
            f, label, hasSession = np.hasSession, playing = np.isPlaying, progress = progress, timeText = info.tapeTime,
            shell = retro.cassetteShell, speed = retro.reelSpeed, accent = accent, screenGlow = screenGlow,
            glow = retro.glowIntensity * if (screenOn) 1f else 0.2f, motionAllowed = motionAllowed && !editing,
            enabled = !editing, onPlayPause = actions.onCassette ?: actions.onPlayPause,
        )

        // ---------- Deck kaset di rak meja
        TapeDeck(
            f.deck, info.deck, retro.vfdColor, hasSession = np.hasSession, playing = np.isPlaying, canSeek = np.canSeek,
            enabled = !editing, onPrevious = actions.onPrevious, onRewind = actions.onRewind,
            onPlayPause = actions.onPlayPause, onNext = actions.onNext,
            tape = tape, onVfdClick = actions.onVfdClick, vfdClickLabel = actions.vfdClickLabel,
        )

        // ---------- Laci kaset di muka meja
        if (drawer != null) {
            StageDrawer(
                f, pal, open = drawer.open, peek = drawer.peek, tapeOut = tape != DeckTape.MUSIC,
                motionAllowed = motionAllowed && !editing, enabled = !editing, count = drawer.count, onToggle = actions.onToggleDrawer,
            )
        }

        // ---------- Lampu dinding: ketuk = ruang terang/gelap (hanya bila ada tempat bersih dan aksinya dipasang)
        val lampRect = f.lamp
        if (onToggleLamp != null && lampRect != null) {
            StageLamp(lampRect, on = tone == RoomTone.LIGHT, motionAllowed = motionAllowed && !editing, enabled = !editing, onToggle = onToggleLamp)
            // Pergantian terang/gelap yang seketika ditutup kilatan singkat; paling atas di panggung, tidak menangkap sentuhan
            ToneFlash(tone, motionAllowed = motionAllowed && !editing)
        }
    }
    }
}
