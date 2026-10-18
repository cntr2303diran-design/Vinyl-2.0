package com.vinyl.launcher.ui.console

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.vinyl.launcher.advanced.AdvancedAccess

/** Pintasan halaman sistem untuk langkah Akses lanjutan tanpa komputer. Halaman yang tidak ada di perangkat diabaikan. */
internal object SystemSettingsIntents {
    fun open(context: Context, action: String) {
        try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            // Mis. Opsi pengembang belum diaktifkan: buka Pengaturan utama sebagai gantinya
            runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        } catch (e: SecurityException) {
            runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    /** Buka Shizuku bila terpasang; selain itu halaman Shizuku di toko aplikasi (Play Store, lalu web). */
    fun openShizuku(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(AdvancedAccess.SHIZUKU_PACKAGE)
        val candidates = listOfNotNull(
            launch,
            Intent(Intent.ACTION_VIEW, "market://details?id=${AdvancedAccess.SHIZUKU_PACKAGE}".toUri()),
            Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=${AdvancedAccess.SHIZUKU_PACKAGE}".toUri()),
        )
        for (i in candidates) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                // coba berikutnya
            }
        }
    }
}
