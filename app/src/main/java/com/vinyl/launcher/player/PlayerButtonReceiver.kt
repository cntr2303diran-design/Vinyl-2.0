package com.vinyl.launcher.player

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver

/**
 * Penerima tombol media (putar di headset, Bluetooth, atau layar kunci) saat proses aplikasi sudah dimatikan sistem:
 * menyalakan [PlayerService] supaya antrean terakhir bisa dilanjutkan ([PlayerService]'s onPlaybackResumption).
 *
 * Penerima ini diekspor agar sistem bisa mengirim tombol media kepadanya. Supaya siaran dari aplikasi lain tidak bisa
 * menyalakan layanan tanpa alasan, layanan HANYA dinyalakan bila ada antrean tersimpan untuk dilanjutkan; tanpa itu
 * siarannya diabaikan.
 */
@OptIn(UnstableApi::class)
class PlayerButtonReceiver : MediaButtonReceiver() {
    override fun shouldStartForegroundService(context: Context, intent: Intent): Boolean = QueueStore.hasSaved(context)
}
