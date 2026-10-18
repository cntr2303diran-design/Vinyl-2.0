package com.vinyl.launcher.ui.home

import java.util.Locale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import com.vinyl.launcher.audio.AudioSnapshot
import com.vinyl.launcher.audio.Source
import com.vinyl.launcher.media.NowPlaying
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.vinyl.launcher.data.BackgroundSettings
import com.vinyl.launcher.data.BackgroundType
import com.vinyl.launcher.data.RoomTone
import com.vinyl.launcher.ui.room.RoomInk
import com.vinyl.launcher.ui.room.rememberWorstCoverPixel
import com.vinyl.launcher.ui.room.stage.DeckDisplay
import com.vinyl.launcher.system.StatusText

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

/** Durasi lagu untuk label kaset, mis. "4:18"; kosong bila tidak diketahui. */
fun tapeTime(np: NowPlaying): String {
    if (np.durationMs <= 0L) return ""
    val s = np.durationMs / 1000L
    return if (s >= 3600) "%d:%02d:%02d".format(Locale.ROOT, s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(Locale.ROOT, s / 60, s % 60)
}

/**
 * Isi VFD deck: mode pita (+ "HI-FI"/"SP") dan keluaran audio — nama perangkat dan sample rate keluaran BAWAAN sistem
 * (diberi awalan OUT, bukan format lagu). Nilai yang tidak diketahui ditulis "--", tidak ditebak.
 */
fun deckDisplay(transport: StatusText.Transport, tapeMode: String?, s: AudioSnapshot): DeckDisplay {
    val device = when (s.active.reading.source) {
        Source.REPORTED, Source.MEASURED -> s.active.device?.name ?: s.active.reading.display
        Source.ESTIMATED -> "≈ " + (s.active.device?.name ?: s.active.reading.display)
        Source.UNAVAILABLE -> "--"
    }
    // Ringkas untuk jendela VFD kecil: laju keluaran dulu ("48K"), lalu nama perangkat (yang terpotong bila sempit)
    val rate = s.nativeRate.text?.replace(" kHz", "K")
    val output = listOfNotNull(rate, device.uppercase()).joinToString(" ")
    return DeckDisplay(StatusText.modeLine(transport) + (tapeMode?.let { " $it" } ?: ""), output)
}
