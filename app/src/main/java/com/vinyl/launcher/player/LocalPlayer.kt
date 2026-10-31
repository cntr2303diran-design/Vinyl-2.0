package com.vinyl.launcher.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.vinyl.launcher.data.RadioRules
import com.vinyl.launcher.data.RadioStation

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
        withController(context) { c ->
            c.setMediaItems(ids.map { MediaItem.Builder().setMediaId(it).build() }, start.coerceIn(0, ids.size - 1), 0L)
            c.prepare()
            c.play()
        }
    }

    /**
     * Putar siaran [station] (aliran internet milik pengguna). Layanan pemutar mencocokkan id-nya dengan daftar stasiun
     * tersimpan, jadi alamat yang tidak ada di daftar tidak pernah dimainkan.
     */
    fun playStation(context: Context, station: RadioStation) {
        HiFi.updatePath { it.copy(skipped = "") }
        withController(context) { c ->
            val item = MediaItem.Builder()
                .setMediaId(RadioRules.idOf(station))
                .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(station.url.toUri()).build())
                .setMediaMetadata(MediaMetadata.Builder().setTitle(station.name).setArtist("Radio").setIsPlayable(true).setIsBrowsable(false).build())
                .build()
            c.setMediaItems(listOf(item), 0, 0L)
            c.prepare()
            c.play()
        }
    }

    private fun withController(context: Context, block: (MediaController) -> Unit) {
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
                block(c)
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
