package com.vinyl.launcher.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vinyl.launcher.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Menyalakan kembali layanan layar kunci vinyl setelah perangkat dinyalakan ulang. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Hanya dua siaran sistem yang didaftarkan di manifest; apa pun selain itu diabaikan
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (SettingsRepository.get(context).current().coverEnabled) CoverService.start(context)
            } finally {
                pending.finish()
            }
        }
    }
}
