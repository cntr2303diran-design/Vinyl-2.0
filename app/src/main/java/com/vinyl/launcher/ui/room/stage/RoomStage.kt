package com.vinyl.launcher.ui.room.stage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.vinyl.launcher.data.RetroSettings
import com.vinyl.launcher.data.RoomTone
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.ui.cassette.CassetteLabel
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
)

class StageActions(
    val onPrevious: () -> Unit,
    val onRewind: () -> Unit,
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onTick: () -> Unit,
    val onOpenLyrics: () -> Unit,
)

/**
 * Beranda bergaya rekaman VHS: ruangan malam, proyeksi di dinding, meja kayu dengan deck kaset di raknya, TV tabung
 * yang DIAM (tidak membesar, tidak bisa dipindah), dan kaset 3D di sampingnya. Susunan dari [StageLayout].
 *
 * Interaksi: ketuk layar TV = lirik layar penuh; putar knob SEEK = geser posisi (waktu tampil di layar TV); ketuk kaset =
 * putar/jeda; tombol deck = sebelumnya / mundur 10 detik / putar-jeda / berikutnya.
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
    positionMs: Long,
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
    /** Lapisan di depan dinding & proyeksi, di belakang meja dan TV (mis. debu melayang). */
    atmosphere: @Composable () -> Unit = {},
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

    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth.value
        val h = maxHeight.value
        val f = remember(w, h, tvSize, topInset, bottomInset) { StageLayout.compute(w, h, tvSize, topInset, bottomInset) }

        // ---------- Dinding & lantai
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            drawStageBackdrop(f, pal, projectionGlow, screenGlow, retro.glowIntensity * if (screenOn) 1f else 0.2f, wallGradient)
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
                    onDrawBehind { drawStageFurniture(f, pal, material, screenGlow, retro.glowIntensity, screenOn, grain) }
                },
        )

        // ---------- TV (muka depan): ketuk layar = lirik
        val tv = f.tvFront
        Box(
            Modifier
                .offset(tv.left.dp, tv.top.dp)
                .size(tv.width.dp, tv.height.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = if (mode == TvScreenMode.STANDBY) "TV, siaga. Tidak ada lagu"
                    else "TV menampilkan ${np.title}" + if (np.artist.isNotBlank()) " oleh ${np.artist}" else ""
                    stateDescription = if (!np.hasTrack) "Siaga" else if (np.isPlaying) "Diputar" else "Dijeda"
                    customActions = listOf(CustomAccessibilityAction("Buka lirik layar penuh") { actions.onOpenLyrics(); true })
                }
                .clickable(enabled = !editing && np.hasTrack, onClickLabel = "Buka lirik", role = Role.Button, onClick = actions.onOpenLyrics),
        ) {
            CrtTv(
                mode = mode, art = np.art.takeIf { showCover }, title = if (showTitle) np.title else "",
                artist = if (showTitle) np.artist else "", accent = accent,
                body = retro.tvBody, knob = retro.knobStyle, vfdColor = retro.vfdColor, vfdLines = listOf(info.tvVfd),
                crtStrength = retro.crtStrength, motion = retro.albumMotion, reduceMotion = !motionAllowed,
                titleScrimAlpha = titleScrimAlpha, knobTurns = seek.turns, knobHighlight = seek.touching,
                modifier = Modifier.fillMaxSize(), poweredOff = tvOff,
                screenOverlay = {
                    screenExtra()
                    SeekOsd(seek, np.durationMs, tv.width.dp)
                },
            )
        }
        // Knob SEEK: area sentuh di atas gambar knob (tidak ikut klik TV)
        SeekKnob(
            seek, np,
            center = Offset(tv.left + tv.width * TvGeometry.KNOB_CX, tv.top + tv.height * TvGeometry.KNOB_CY),
            radius = tv.width * TvGeometry.KNOB_R,
            enabled = !editing && np.hasTrack,
            onSeek = actions.onSeek, onTick = actions.onTick,
        )

        // ---------- Kaset 3D
        val progress = if (np.durationMs > 0) positionMs.toFloat() / np.durationMs else 0f
        val label = remember(np.hasTrack, np.title, np.artist, np.art) {
            CassetteLabel(if (np.hasTrack) "${np.title}\u0000${np.artist}" else null, np.title, np.artist, np.art)
        }
        StageCassette(
            f, label, hasSession = np.hasSession, playing = np.isPlaying, progress = progress, timeText = info.tapeTime,
            shell = retro.cassetteShell, speed = retro.reelSpeed, accent = accent, screenGlow = screenGlow,
            glow = retro.glowIntensity * if (screenOn) 1f else 0.2f, motionAllowed = motionAllowed && !editing,
            enabled = !editing, onPlayPause = actions.onPlayPause,
        )

        // ---------- Deck kaset di rak meja
        TapeDeck(
            f.deck, info.deck, retro.vfdColor, hasSession = np.hasSession, playing = np.isPlaying, canSeek = np.canSeek,
            enabled = !editing, onPrevious = actions.onPrevious, onRewind = actions.onRewind,
            onPlayPause = actions.onPlayPause, onNext = actions.onNext,
        )
    }
}
