package com.vinyl.launcher.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import com.vinyl.launcher.media.NowPlaying
import kotlinx.coroutines.delay

/** Selang pembaruan posisi lagu untuk tampilan (setengah detik: cukup untuk penghitung pita dan gulungan kaset). */
private const val POSITION_TICK_MS = 500L

/**
 * Posisi lagu yang diperbarui tiap [POSITION_TICK_MS] saat memutar. Dikembalikan sebagai State yang BELUM dibaca:
 * pemanggil membacanya di fase gambar atau di composable kecil (lambda `{ state.longValue }`), jadi detaknya tidak
 * mengomposisi ulang layar yang memegangnya. (Dulu penghitung di tubuh layar membuat seluruh beranda dikomposisi ulang
 * dua kali per detik selama musik berjalan.)
 */
@Composable
fun rememberPositionMs(np: NowPlaying): LongState {
    val state = remember { mutableLongStateOf(np.positionAt()) }
    LaunchedEffect(np) {
        state.longValue = np.positionAt()
        while (np.isPlaying) {
            delay(POSITION_TICK_MS)
            state.longValue = np.positionAt()
        }
    }
    return state
}
