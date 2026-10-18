package com.vinyl.launcher.ui.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.scale
import com.vinyl.launcher.data.RetroSettings
import com.vinyl.launcher.data.RoomTone
import com.vinyl.launcher.data.TurntablePosition
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.ui.theme.BackdropDim
import com.vinyl.launcher.ui.theme.BackdropLift
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Panggung ruang TV: TV tabung di lantai (dan, mulai Tahap 4, kaset di sampingnya). Melaporkan posisi lantai dan
 * area proyeksi ke [onFloor] (koordinat akar) agar latar menggambar lantai & proyeksi sejajar dengan TV.
 *
 * @param cassette isi area kaset (Tahap 4); diberi ukuran area dalam dp.
 */
@Composable
fun RoomStage(
    np: NowPlaying,
    retro: RetroSettings,
    tvSize: Float,
    position: TurntablePosition,
    landscape: Boolean,
    accent: Color,
    motionAllowed: Boolean,
    vfdLines: List<VfdTicker.Line>,
    onScreenTap: () -> Unit,
    onFloor: (RoomFloor) -> Unit,
    modifier: Modifier = Modifier,
    knobTurns: Float = 0f,
    knobHighlight: Boolean = false,
    knob: @Composable (tvLeft: Dp, tvTop: Dp, tvWidth: Dp, tvHeight: Dp) -> Unit = { _, _, _, _ -> },
    cassette: @Composable (width: Dp, height: Dp) -> Unit = { _, _ -> },
) {
    val mode = TvScreen.mode(np.hasTrack, np.isPlaying, retro.albumMotion, motionAllowed)
    val report by rememberUpdatedState(onFloor)

    // Jam VFD: diperbarui tiap detik hanya selama panggung tampil
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L - now % 1000L)
        }
    }
    val vfd = VfdTicker.current(vfdLines, now)

    BoxWithConstraints(modifier) {
        val wDp = maxWidth
        val hDp = maxHeight
        val aspect = if (hDp.value > 0f) wDp.value / hDp.value else 1f
        val align = when (position) {
            TurntablePosition.LEFT -> -1
            TurntablePosition.CENTER -> 0
            TurntablePosition.RIGHT -> 1
        }
        val stage = RoomLayout.stage(aspect, landscape, tvSize, align)
        val tv = stage.tv
        val cas = stage.cassette

        Box(
            Modifier
                .offset(wDp * tv.left, hDp * tv.top)
                .size(wDp * tv.width, hDp * tv.height)
                .onGloballyPositioned { c ->
                    val p = c.positionInRoot()
                    val tvW = c.size.width.toFloat()
                    val tvH = c.size.height.toFloat()
                    // Proyeksi: lebih lebar dari TV, menempel di dinding tepat di belakangnya (bagian bawahnya tertutup TV)
                    val projW = tvW * 1.45f
                    val projH = projW * 0.58f
                    val projBottom = p.y + tvH * 0.42f
                    val projTop = (projBottom - projH).coerceAtLeast(0f)
                    report(RoomFloor(p.y + tvH, p.x + tvW / 2f - projW / 2f, projTop, projW, projBottom - projTop))
                }
                .semantics(mergeDescendants = true) {
                    contentDescription = when (mode) {
                        TvScreenMode.STANDBY -> "TV, siaga. Tidak ada lagu"
                        else -> "TV menampilkan sampul ${np.title}" + if (np.artist.isNotBlank()) " oleh ${np.artist}" else ""
                    }
                    stateDescription = if (!np.hasTrack) "Siaga" else if (np.isPlaying) "Diputar" else "Dijeda"
                }
                .clickable(
                    onClickLabel = if (np.hasTrack) (if (np.isPlaying) "Jeda" else "Putar") else "Buka aplikasi musik",
                    role = Role.Button,
                    onClick = onScreenTap,
                ),
        ) {
            CrtTv(
                mode = mode,
                art = np.art,
                title = np.title,
                accent = accent,
                body = retro.tvBody,
                knob = retro.knobStyle,
                vfdColor = retro.vfdColor,
                vfdText = vfd?.text.orEmpty(),
                crtStrength = retro.crtStrength,
                motion = retro.albumMotion,
                reduceMotion = !motionAllowed,
                knobTurns = knobTurns,
                knobHighlight = knobHighlight,
            )
        }
        knob(wDp * tv.left, hDp * tv.top, wDp * tv.width, hDp * tv.height)
        Box(Modifier.offset(wDp * cas.left, hDp * cas.top).size(wDp * cas.width, hDp * cas.height)) {
            cassette(wDp * cas.width, hDp * cas.height)
        }
    }
}

/**
 * Piksel cover yang paling merugikan teks ruang: TERGELAP untuk ruang terang (teks gelap), TERTERANG untuk ruang redup.
 * Dihitung di luar thread utama dari cover yang diperkecil; null bila tidak ada cover.
 */
@Composable
fun rememberWorstCoverPixel(art: ImageBitmap?, tone: RoomTone): Long? {
    val v by produceState<Long?>(null, art, tone) {
        value = if (art == null) null else withContext(Dispatchers.Default) {
            runCatching {
                val small = art.asAndroidBitmap().scale(24, 24, filter = true)
                val px = IntArray(24 * 24)
                small.getPixels(px, 0, 24, 0, 0, 24, 24)
                if (tone == RoomTone.LIGHT) BackdropLift.darkest(px) else BackdropDim.brightest(px)
            }.getOrNull()
                // Gagal membaca piksel (mis. bitmap hardware): anggap kasus terburuk agar kontras tetap terjamin
                ?: if (tone == RoomTone.LIGHT) 0xFF000000L else 0xFFFFFFFFL
        }
    }
    return v
}
