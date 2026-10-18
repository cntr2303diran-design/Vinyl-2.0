package com.vinyl.launcher.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True bila pengguna mematikan animasi di sistem (Opsi pengembang / Aksesibilitas → "Hapus animasi", skala animasi 0×).
 * Animasi dekoratif (transisi warna sinematik, gulir lirik berpegas, parallax) wajib dilewati saat ini true.
 * Nilai dibaca sekali per komposisi; perubahan berlaku saat layar digambar ulang berikutnya.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
}
