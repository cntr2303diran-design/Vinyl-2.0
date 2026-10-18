package com.vinyl.launcher.data

import com.vinyl.launcher.music.Lrclib
import com.vinyl.launcher.music.LyricsCache
import com.vinyl.launcher.music.LyricsOutcome
import com.vinyl.launcher.music.TitleCleaner
import com.vinyl.launcher.util.Http
import com.vinyl.launcher.util.HttpResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * Inti pencarian lirik, terpisah dari jaringan agar perilakunya bisa diuji dengan pengambil data palsu.
 * Aturan menafsirkan jawaban ada di [Lrclib]; kelas ini mengurus urutan (exact → search), cache, dan penggabungan
 * permintaan kembar.
 *
 * @param fetch mengambil satu URL; harus aman dipanggil dari thread latar belakang dan tidak melempar.
 */
class LyricsService(
    private val fetch: (url: String) -> HttpResult,
    private val cache: LyricsCache,
    private val scope: CoroutineScope,
) {
    /** Permintaan yang sedang berjalan per lagu: beberapa layar (beranda, lirik penuh, layar kunci) berbagi satu permintaan. */
    private val inFlight = HashMap<String, Deferred<LyricsOutcome>>()

    /**
     * @param allowNetwork false = pengguna mematikan pencarian online; hasilnya [LyricsOutcome.Disabled] dan tidak ada
     * permintaan jaringan yang dibuat.
     */
    suspend fun get(title: String, artist: String, album: String, durationMs: Long, allowNetwork: Boolean): LyricsOutcome {
        if (!allowNetwork) return LyricsOutcome.Disabled
        if (title.isBlank()) return LyricsOutcome.NotFound
        val key = LyricsCache.key(title, artist)
        cache.get(key)?.let { return it }

        val job = synchronized(inFlight) {
            inFlight.getOrPut(key) {
                // LAZY: baru jalan saat di-await, sesudah entri ini terdaftar. Berjalan di scope layanan, jadi bila
                // yang menunggu dibatalkan (ganti lagu), hasilnya tetap masuk cache untuk dipakai nanti.
                scope.async(start = CoroutineStart.LAZY) {
                    try {
                        search(title, artist, album, durationMs).also { cache.put(key, it) }
                    } finally {
                        synchronized(inFlight) { inFlight.remove(key) }
                    }
                }
            }
        }
        return job.await()
    }

    /** Lupakan hasil tersimpan sebuah lagu agar [get] berikutnya bertanya lagi ke server (tombol "Coba lagi"). */
    fun forget(title: String, artist: String) = cache.invalidate(LyricsCache.key(title, artist))

    private fun search(title: String, artist: String, album: String, durationMs: Long): LyricsOutcome {
        val exact = request(Lrclib.getUrl(title.trim(), artist.trim(), album.trim(), durationMs), Lrclib::parseExact)
        return Lrclib.combine(exact) {
            request(Lrclib.searchUrl(TitleCleaner.clean(title), TitleCleaner.clean(artist))) { body -> Lrclib.parseSearch(body, durationMs) }
        }
    }

    private fun request(url: String, parse: (String) -> LyricsOutcome): LyricsOutcome =
        when (val r = fetch(url)) {
            is HttpResult.Ok -> parse(r.bytes.toString(Charsets.UTF_8))
            is HttpResult.Status -> Lrclib.outcomeForStatus(r.code)
            HttpResult.Failed -> LyricsOutcome.Failed
        }
}

/**
 * Lirik dari LRCLIB (lrclib.net) lewat [LyricsService]. Yang dikirim ke lrclib.net hanya judul, artis, album, dan
 * durasi lagu — dan hanya bila `allowNetwork = true` (Pengaturan → Musik → Cari lirik online).
 */
object LyricsRepository {
    private const val UA = "VinylLauncher/1.0 (https://github.com/cntr2303diran-design/Vinyl-2.0)"

    private val service = LyricsService(
        fetch = { url -> Http.fetch(url, Lrclib.MAX_RESPONSE, UA) },
        cache = LyricsCache(now = { System.nanoTime() / 1_000_000L }),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    suspend fun get(title: String, artist: String, album: String, durationMs: Long, allowNetwork: Boolean): LyricsOutcome =
        service.get(title, artist, album, durationMs, allowNetwork)

    fun forget(title: String, artist: String) = service.forget(title, artist)
}
