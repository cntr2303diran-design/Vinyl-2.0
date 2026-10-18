package com.vinyl.launcher.media

import com.vinyl.launcher.data.HistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Mengamati status pemutaran dan mencatat lagu ke koleksi setelah benar-benar
 * didengarkan ≥30 detik (atau setengah durasi untuk lagu pendek) — mirip aturan "scrobble".
 */
object HistoryRecorder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var currentKey: String? = null
    private var playingSince: Long? = null
    private var listenedMs = 0L
    private var counted = false
    private var timer: Job? = null
    private var last: NowPlaying = NowPlaying()

    fun onState(np: NowPlaying) {
        val key = if (np.hasTrack) "${np.title.trim().lowercase()}|${np.artist.trim().lowercase()}" else null
        val now = System.currentTimeMillis()

        if (key != currentKey) {
            flush(now)
            currentKey = key
            listenedMs = 0; counted = false; playingSince = null
        }
        last = np
        if (key == null) return

        if (np.isPlaying && playingSince == null) {
            playingSince = now
            scheduleCount(np)
        } else if (!np.isPlaying && playingSince != null) {
            listenedMs += now - (playingSince ?: now)
            playingSince = null
            timer?.cancel()
        }
    }

    private fun threshold(np: NowPlaying): Long =
        if (np.durationMs in 1..59_999) np.durationMs / 2 else 30_000L

    private fun scheduleCount(np: NowPlaying) {
        if (counted) return
        timer?.cancel()
        val remaining = (threshold(np) - listenedMs).coerceAtLeast(0)
        val key = currentKey ?: return
        timer = scope.launch {
            delay(remaining)
            if (currentKey == key && !counted) {
                counted = true
                val s = last
                HistoryRepository.recordPlay(key, s.title, s.artist, s.album, s.packageName, s.art)
            }
        }
    }

    /** Tutup sesi lagu sebelumnya: tambahkan total waktu dengar. */
    private fun flush(now: Long) {
        val key = currentKey ?: return
        timer?.cancel()
        val total = listenedMs + (playingSince?.let { now - it } ?: 0L)
        if (counted) HistoryRepository.addListening(key, total)
    }
}
