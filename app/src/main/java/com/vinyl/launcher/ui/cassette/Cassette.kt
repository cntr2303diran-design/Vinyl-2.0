package com.vinyl.launcher.ui.cassette

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.vinyl.launcher.data.CassetteShell
import com.vinyl.launcher.data.ReelSpeed

/**
 * Isi label satu kaset (disimpan agar kaset LAMA tetap berlabel lagu lama saat keluar). [shell] & [tint] bila diisi
 * menggantikan pilihan cangkang pengguna untuk kaset ini saja (kaset film berwarna sendiri); null = ikut pengaturan.
 */
data class CassetteLabel(
    val key: String?,
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val shell: CassetteShell? = null,
    val tint: Color? = null,
)

/**
 * Gambar kaset diam (gulungan tidak berputar) dilihat dari depan, untuk rak dan detail perpustakaan. Bentuk, label
 * spidol, dan cangkangnya sama dengan kaset 3D di beranda.
 */
@Composable
fun CassetteArt(
    label: CassetteLabel,
    shell: CassetteShell,
    accent: Color,
    modifier: Modifier = Modifier,
    progress: Float = 0.35f,
    timeText: String = "",
) {
    val (rl, rr) = Reels.radii(progress)
    val text = rememberLabelText(label.title.ifBlank { "—" }, label.artist, timeText)
    val look = shellLook(shell, accent)
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            val k = minOf(size.width / CassetteFaceUnits.W, size.height / CassetteFaceUnits.H)
            val dx = (size.width - CassetteFaceUnits.W * k) / 2f
            val dy = (size.height - CassetteFaceUnits.H * k) / 2f
            withTransform({
                translate(dx, dy)
                scale(k, k, pivot = Offset.Zero)
            }) {
                drawCassetteFace(look, text, accent, rl, rr, 0f, 0f, label.art, shell == CassetteShell.PICTURE, light = 0f, glow = Color.White)
            }
        }
    }
}

/** Sudut hub kiri & kanan (derajat) yang berputar hanya saat musik berjalan dan animasi diizinkan. */
class ReelAngles {
    var left by mutableFloatStateOf(0f)
    var right by mutableFloatStateOf(0f)
}

/**
 * Putaran gulungan: kecepatan pita tetap (gulungan kecil berputar lebih cepat), mengikuti [progress] terbaru. Berhenti
 * total saat jeda, saat "Hapus animasi", atau saat hemat daya ([running] false).
 */
@Composable
fun rememberReelAngles(running: Boolean, speed: ReelSpeed, progress: () -> Float): ReelAngles {
    val angles = remember { ReelAngles() }
    val currentProgress by rememberUpdatedState(progress)
    LaunchedEffect(running, speed) {
        if (!running) return@LaunchedEffect
        var last = withFrameNanos { it }
        val rpm = Reels.rpm(speed)
        while (true) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1e9f
            last = now
            val (l, r) = Reels.radii(currentProgress())
            angles.left = (angles.left + Reels.turnsPerSecond(l, rpm) * dt * 360f) % 360f
            angles.right = (angles.right + Reels.turnsPerSecond(r, rpm) * dt * 360f) % 360f
        }
    }
    return angles
}

/**
 * Animasi ganti kaset ([CassetteSwap]) sebagai State: label yang tampil dan tahap keluar/masuk. Perintah lagu sudah
 * dikirim pemanggil; ini hanya menggambar.
 */
@Composable
fun rememberCassetteSwap(label: CassetteLabel, animate: Boolean): State<Pair<CassetteLabel, CassetteSwap.Frame>> {
    val swap = remember { CassetteSwap(label.key) }
    var labels by remember { mutableStateOf(mapOf(label.key to label)) }
    val state = remember { mutableStateOf(label to swap.frame(0L)) }
    LaunchedEffect(label) {
        labels = (labels + (label.key to label)).entries.toList().takeLast(4).associate { it.key to it.value }
        val f = state.value.second
        state.value = (labels[f.shownKey] ?: label) to f
    }
    LaunchedEffect(label.key, animate) {
        val start = withFrameNanos { it / 1_000_000L }
        swap.onTrack(label.key, start, animate)
        var f = swap.frame(start)
        state.value = (labels[f.shownKey] ?: label) to f
        while (swap.running) {
            val now = withFrameNanos { it / 1_000_000L }
            f = swap.frame(now)
            state.value = (labels[f.shownKey] ?: label) to f
        }
        f = swap.frame(Long.MAX_VALUE / 2)
        state.value = (labels[f.shownKey] ?: label) to f
    }
    return state
}
