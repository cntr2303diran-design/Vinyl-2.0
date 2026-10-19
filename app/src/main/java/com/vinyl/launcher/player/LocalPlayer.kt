package com.vinyl.launcher.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/**
 * Pengendali pemutar bawaan dari UI launcher (perpustakaan kaset). Tersambung saat dibutuhkan dan dilepas saat beranda
 * tidak terlihat, sehingga layanan pemutar bisa berhenti sendiri bila tidak memutar apa-apa. Kendali lain (putar/jeda
 * di deck, layar kunci, headset) lewat sesi media platform seperti aplikasi musik lain.
 */
object LocalPlayer {
    private var future: ListenableFuture<MediaController>? = null

    /** Putar [ids] (urutan daftar yang tampil) mulai dari [start]. */
    fun play(context: Context, ids: List<String>, start: Int) {
        if (ids.isEmpty()) return
        // Antrean baru: keterangan lagu yang dilewati di antrean sebelumnya tidak relevan lagi
        HiFi.updatePath { it.copy(skipped = "") }
        val app = context.applicationContext
        val f = future ?: MediaController.Builder(app, SessionToken(app, ComponentName(app, PlayerService::class.java)))
            .buildAsync()
            .also { future = it }
        f.addListener(
            {
                val c = runCatching { f.get() }.getOrNull()
                if (c == null) {
                    if (future === f) future = null
                    return@addListener
                }
                c.setMediaItems(ids.map { MediaItem.Builder().setMediaId(it).build() }, start.coerceIn(0, ids.size - 1), 0L)
                c.prepare()
                c.play()
            },
            ContextCompat.getMainExecutor(app),
        )
    }

    /** Lepaskan sambungan (dipanggil saat beranda berhenti tampil); pemutaran tetap berjalan bila sedang memutar. */
    fun release() {
        future?.let { MediaController.releaseFuture(it) }
        future = null
    }
}
