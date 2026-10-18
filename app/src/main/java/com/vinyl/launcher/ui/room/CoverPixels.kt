package com.vinyl.launcher.ui.room

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.graphics.scale
import com.vinyl.launcher.data.RoomTone
import com.vinyl.launcher.ui.theme.BackdropDim
import com.vinyl.launcher.ui.theme.BackdropLift
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
