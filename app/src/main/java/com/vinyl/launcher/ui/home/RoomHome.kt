package com.vinyl.launcher.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.media.AudioManager
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.vinyl.launcher.audio.AudioMonitor
import com.vinyl.launcher.audio.Source
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.ui.room.VfdTicker
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.vinyl.launcher.data.BackgroundSettings
import com.vinyl.launcher.data.BackgroundType
import com.vinyl.launcher.data.RoomTone
import com.vinyl.launcher.ui.room.RoomInk
import com.vinyl.launcher.ui.room.rememberWorstCoverPixel

/*
 * Penghubung pengaturan latar lama (ALBUM / SOLID / GRADIENT / IMAGE) dengan ruang TV:
 *  - ALBUM   → dinding ruang menerima proyeksi cover
 *  - SOLID   → dinding diwarnai warna pilihan
 *  - GRADIENT→ dinding bergradasi
 *  - IMAGE   → gambar pilihan diproyeksikan ke dinding
 * Sekaligus memutuskan warna teks ruang dan "pelat" yang menjaga kontrasnya.
 */

/** Tampilan teks di ruang TV untuk keadaan saat ini. */
@Immutable
data class RoomLook(
    val tone: RoomTone,
    val primary: Color,
    val secondary: Color,
    /** Alpha pelat di belakang teks (putih untuk ruang terang, hitam untuk ruang redup); 0 = tidak terlihat. */
    val plateAlpha: Float,
) {
    val plateColor: Color get() = if (tone == RoomTone.LIGHT) Color.White else Color.Black
}

@Composable
fun rememberRoomLook(room: RoomTone, bg: BackgroundSettings, projected: ImageBitmap?): RoomLook {
    // Dinding berwarna pilihan pengguna menentukan nada teks; gradien dinilai dari warna tengahnya
    val wall: Long? = when (bg.type) {
        BackgroundType.SOLID -> bg.solidColor
        BackgroundType.GRADIENT -> mid(bg.gradientStart, bg.gradientEnd)
        else -> null
    }
    val tone = RoomInk.textTone(room, wall)
    val worstProjected = rememberWorstCoverPixel(projected, tone)
    return remember(tone, wall, worstProjected, bg.type, bg.gradientStart, bg.gradientEnd) {
        val alpha = when (bg.type) {
            BackgroundType.SOLID -> RoomInk.plateAlpha(bg.solidColor, tone, projectionAlpha = 1.0)
            BackgroundType.GRADIENT -> maxOf(
                RoomInk.plateAlpha(bg.gradientStart, tone, projectionAlpha = 1.0),
                RoomInk.plateAlpha(bg.gradientEnd, tone, projectionAlpha = 1.0),
            )
            else -> RoomInk.plateAlpha(worstProjected, tone)
        }
        RoomLook(tone, Color(RoomInk.primary(tone)), Color(RoomInk.secondary(tone)), alpha.toFloat())
    }
}

private fun mid(a: Long, b: Long): Long {
    fun ch(s: Int) = (((a shr s) and 0xFF) + ((b shr s) and 0xFF)) / 2
    return (0xFFL shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/** Nada ruang efektif untuk partikel/debu (gelap di ruang terang). */
fun dustColor(tone: RoomTone): Color = if (tone == RoomTone.LIGHT) Color(0xFF6E6254) else Color.White

/** Teks TIME seperti referensi: "10:09 AM" (12 jam) atau "22:09" (24 jam). */
fun clockText(now: LocalDateTime, use24h: Boolean): String =
    now.format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a", Locale.US))

/** Teks Output: nama keluaran aktif; "≈" bila hanya perkiraan; jujur bila tidak diketahui. */
fun outputText(s: AudioMonitor.Snapshot): String = when (s.active.reading.source) {
    Source.REPORTED, Source.MEASURED -> s.active.reading.display
    Source.ESTIMATED -> "≈ " + s.active.reading.display
    Source.UNAVAILABLE -> "Tidak diketahui"
}

/**
 * Tiga baris VFD: status putar, sample rate keluaran BAWAAN sistem (diberi awalan OUT, bukan format lagu), dan nama
 * keluaran. Nilai yang tidak diketahui ditulis "--", tidak ditebak.
 */
fun vfdLines(np: NowPlaying, s: AudioMonitor.Snapshot): List<String> = listOf(
    VfdTicker.playback(np.hasTrack, np.isPlaying),
    "OUT " + (s.nativeRate.text ?: "--"),
    (s.active.device?.name ?: "--").uppercase().take(VfdTicker.MAX_CHARS),
)

/**
 * Volume media perangkat (0..1) dan pengaturnya, lewat API standar AudioManager (tanpa izin). Dibaca ulang tiap detik
 * hanya selama layar aktif, karena tombol volume fisik bisa mengubahnya kapan saja. Null bila sistem menolak.
 */
@Composable
fun rememberMediaVolume(): Pair<Float?, (Float) -> Unit> {
    val context = LocalContext.current
    val am = remember(context) { context.applicationContext.getSystemService(AudioManager::class.java) }
    var value by remember { mutableStateOf<Float?>(null) }
    fun read(): Float? = am?.let {
        runCatching {
            val max = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (max > 0) it.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max else null
        }.getOrNull()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(am, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { value = read(); delay(1000) }
        }
    }
    val set: (Float) -> Unit = { f ->
        am?.let {
            runCatching {
                val max = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                it.setStreamVolume(AudioManager.STREAM_MUSIC, (f.coerceIn(0f, 1f) * max).roundToInt(), 0)
            }
        }
        value = read()
    }
    return value to set
}
