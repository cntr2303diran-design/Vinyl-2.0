package com.vinyl.launcher.ui.film

import com.vinyl.launcher.data.FilmResume
import com.vinyl.launcher.data.FilmResumePoint
import com.vinyl.launcher.data.FilmStart
import kotlin.math.abs

/*
 * Aturan "lanjut dari posisi terakhir" untuk pemegang WebView film: kapan melompat ke posisi tersimpan, dan apa yang
 * dicatat sebagai titik lanjut. Murni (tanpa Android) agar diuji. Tidak ada teks dari halaman yang masuk ke skrip: yang
 * dikirim ke halaman hanyalah satu bilangan bulat (lihat FilmProbe.seekToJs).
 */

/** Lompatan ke posisi terakhir yang menunggu videonya siap. Dibuat saat kaset dibuka, dibuang begitu selesai atau gugur. */
class ResumeSeek(
    /** Halaman yang dibuka; lompatan hanya berlaku di halaman ini. */
    val page: String,
    val targetMs: Long,
    /** Durasi video saat posisi dicatat (0 = tidak diketahui): video lain (iklan, tayangan berikutnya) tidak dilompati. */
    val expectedDurationMs: Long,
    /** Batas waktu (jam monotonik, ms): lewat dari ini lompatan batal. */
    val deadlineMs: Long,
)

sealed interface SeekStep {
    /** Belum saatnya (halaman belum terpasang / videonya belum terbaca); coba lagi pada pembacaan berikutnya. */
    data object Wait : SeekStep

    /** Batal: waktunya habis, atau pengguna sudah berpindah halaman. */
    data object Drop : SeekStep

    /** Lompat ke [ms] sekarang. */
    data class Go(val ms: Long) : SeekStep
}

object ResumeSeekRules {
    /** Lama menunggu video siap setelah kaset dibuka (ms); sambungan lambat tetap sempat. */
    const val WAIT_MS = 45_000L

    /** Selisih durasi yang masih dianggap video yang sama (pembulatan, metadata yang sedikit berbeda). */
    const val DURATION_TOLERANCE_MS = 2_000L

    /** Rencana lompatan untuk [start] pada jam monotonik [nowMs]; null bila tidak ada posisi untuk dilompati. */
    fun plan(start: FilmStart, nowMs: Long): ResumeSeek? {
        val target = start.seekMs ?: return null
        return ResumeSeek(start.url, target, start.durationMs, nowMs + WAIT_MS)
    }

    /**
     * Satu langkah keputusan pada tiap pembacaan video. [shownPage] = halaman yang kini tampil menurut WebView (null =
     * halaman kaset ini belum terpasang), [status] = bacaan video terbaru (null = tidak ada video yang terbaca).
     */
    fun step(seek: ResumeSeek, shownPage: String?, status: VideoStatus?, nowMs: Long): SeekStep {
        if (nowMs > seek.deadlineMs) return SeekStep.Drop
        val page = shownPage ?: return SeekStep.Wait
        if (!FilmResume.samePage(seek.page, page)) return SeekStep.Drop
        val st = status ?: return SeekStep.Wait
        if (st.ended) return SeekStep.Wait
        if (st.durationMs <= 0L) {
            // Metadata belum dimuat (video belum diputar). Posisi mulai bawaan masih bisa diatur, tetapi hanya bila video
            // di halaman ini pernah terbaca punya durasi (bukan siaran langsung)
            return if (seek.expectedDurationMs > 0L) SeekStep.Go(seek.targetMs) else SeekStep.Wait
        }
        if (seek.expectedDurationMs > 0L && abs(st.durationMs - seek.expectedDurationMs) > DURATION_TOLERANCE_MS) return SeekStep.Wait
        return if (FilmResume.canSeekNow(st.durationMs, seek.targetMs)) SeekStep.Go(seek.targetMs) else SeekStep.Wait
    }
}

/** Yang dicatat ke titik lanjut: kaset yang terbuka, halaman yang tampil, dan bacaan video terakhir di halaman itu. */
data class FilmResumeSnapshot(
    val cassetteId: String,
    val pageUrl: String,
    /** null = tidak ada video yang terbaca di halaman ini. */
    val positionMs: Long?,
    val durationMs: Long?,
    val ended: Boolean,
)

object FilmSnapshots {
    /** Bacaan yang lebih tua dari ini tidak diperkirakan majunya (pembacaan berhenti dan video dijeda saat ruang TV ditinggalkan). */
    const val FRESH_MS = 5_000L

    /**
     * Rangkum keadaan sekarang. [sample] hanya dipakai bila diambil di halaman yang sama dengan [shownPage]
     * ([samplePage]); bacaan dari halaman sebelumnya tidak pernah dicatat untuk halaman yang baru.
     */
    fun make(cassetteId: String?, shownPage: String?, sample: FilmSample?, samplePage: String?, nowMs: Long): FilmResumeSnapshot? {
        val id = cassetteId ?: return null
        val page = shownPage ?: return null
        val s = sample?.takeIf { samplePage != null && FilmResume.samePage(samplePage, page) }
        val position = s?.let { if (nowMs - it.atMs <= FRESH_MS) it.positionAt(nowMs) else it.status.positionMs }
        return FilmResumeSnapshot(id, page, position, s?.status?.durationMs, s?.status?.ended == true)
    }
}

object FilmRetry {
    /**
     * Dari mana mencoba lagi setelah sinyal hilang: halaman dan menit terakhir yang diketahui [snapshot]. Bila halaman
     * pertama kaset belum sempat terbaca videonya, rencana awal ([initial]) diulang; tanpa keduanya, dari alamat awal
     * kaset ([home]).
     */
    fun from(initial: FilmStart?, home: String, snapshot: FilmResumeSnapshot?): FilmStart {
        val base = initial ?: FilmStart(home)
        val s = snapshot ?: return base
        val position = s.positionMs
        return when {
            position != null -> {
                val duration = s.durationMs ?: 0L
                FilmStart(s.pageUrl, FilmResume.seekTarget(FilmResumePoint(s.pageUrl, position, duration, 0L)), duration)
            }
            FilmResume.samePage(base.url, s.pageUrl) -> base
            else -> FilmStart(s.pageUrl)
        }
    }
}
