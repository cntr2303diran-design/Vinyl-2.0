package com.vinyl.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.vinyl.launcher.data.BackgroundSettings
import com.vinyl.launcher.data.BackgroundType
import com.vinyl.launcher.data.RoomTone
import com.vinyl.launcher.ui.room.RoomInk
import com.vinyl.launcher.ui.room.WallSource
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

/** Pelat lembut di belakang teks ruang; tak terlihat bila tidak dibutuhkan. */
fun Modifier.roomPlate(look: RoomLook): Modifier =
    if (look.plateAlpha <= 0f) this else background(look.plateColor.copy(alpha = look.plateAlpha), RoundedCornerShape(16.dp))

/** Sumber gambar dinding untuk [bg]. */
fun wallSource(bg: BackgroundSettings, cover: ImageBitmap?, picture: ImageBitmap?): WallSource = when (bg.type) {
    BackgroundType.ALBUM -> cover?.let { WallSource.Cover(it) } ?: WallSource.Blank
    BackgroundType.IMAGE -> picture?.let { WallSource.Picture(it) } ?: WallSource.Blank
    BackgroundType.SOLID, BackgroundType.GRADIENT -> WallSource.Blank
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
