package com.vinyl.launcher.data

import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Titik lanjut satu kaset film: halaman terakhir yang dibuka (alamat https di situs kaset itu) dan, bila videonya terbaca,
 * posisi putar. Disimpan di perangkat ini saja, terpisah dari pengaturan (TIDAK masuk berkas cadangan), dan bisa dihapus
 * dari Pengaturan → Laci kaset. Hanya alamat halaman, angka posisi, dan cap waktu; tidak ada isi halaman.
 */
@Serializable
data class FilmResumePoint(
    val url: String,
    /** Posisi putar (ms) di halaman itu; 0 = awal / tidak layak dilanjutkan. */
    val positionMs: Long = 0L,
    /** Durasi (ms) saat posisi dicatat; 0 = tidak diketahui (siaran langsung / belum dimuat). */
    val durationMs: Long = 0L,
    /** Cap waktu (ms epoch) pencatatan; dipakai membuang titik yang sudah lama. */
    val savedAtMs: Long = 0L,
)

/**
 * Dari mana kaset film dimulai: halaman [url] dan, bila ada posisi yang layak dilanjutkan, [seekMs] yang dilompati begitu
 * videonya terbaca. [durationMs] = durasi video saat posisi dicatat (0 = tidak diketahui); dipakai memastikan video yang
 * dilompati memang video yang sama.
 */
data class FilmStart(val url: String, val seekMs: Long? = null, val durationMs: Long = 0L)

/** Semua titik lanjut, kunci = id kaset film. */
@Serializable
data class FilmResumeData(val points: Map<String, FilmResumePoint> = emptyMap())

/**
 * Aturan titik lanjut kaset film, murni (tanpa Android) agar bisa diuji. Prinsip keamanan: yang dicatat dan dibuka lagi
 * hanyalah alamat https di situs yang sama dengan alamat kaset (halaman yang tadinya dibuka orang itu sendiri, bukan
 * alamat sembarang), diperiksa ulang lewat [FilmRules] setiap kali dipakai; alamat yang terlalu panjang tidak dicatat.
 */
object FilmResume {
    const val MAX_URL = 2_000
    /** Posisi di bawah ini tidak layak dilanjutkan (mulai dari awal saja). */
    const val MIN_RESUME_MS = 15_000L
    /** Sisa durasi di bawah ini dianggap sudah selesai ditonton. */
    const val END_MARGIN_MS = 30_000L
    /** Titik yang lebih tua dari ini dibuang (180 hari). */
    const val MAX_AGE_MS = 180L * 24L * 60L * 60L * 1000L
    /** Batas wajar posisi/durasi (100 jam); angka di luar itu dianggap rusak. */
    const val MAX_MS = 360_000_000L
    /** Titik yang isinya tidak berubah baru ditulis ulang setelah selang ini (10 menit), supaya penyimpanan tidak ditulis terus. */
    const val REFRESH_MS = 10L * 60L * 1000L

    /** Nama situs tanpa "www." dari alamat yang sudah dinormalkan, atau null bila alamat tidak sah. */
    private fun siteOf(url: String): String? =
        (FilmRules.parseUrl(url, FilmRules.MAX_NAV_URL) as? UrlResult.Ok)?.url?.let { FilmRules.hostOf(it) }

    /** [pageUrl] berada di situs yang sama dengan [cassetteUrl] (situs itu sendiri atau anak situsnya, bukan nama yang mirip). */
    fun sameSite(cassetteUrl: String, pageUrl: String): Boolean {
        val c = siteOf(cassetteUrl) ?: return false
        val p = siteOf(pageUrl) ?: return false
        return p == c || p.endsWith(".$c")
    }

    /**
     * Alamat halaman yang boleh dicatat untuk kaset beralamat [cassetteUrl]: https (http dinaikkan), situs yang sama,
     * panjang wajar. Null bila tidak boleh.
     */
    fun recordablePage(cassetteUrl: String, pageUrl: String?): String? {
        if (pageUrl == null) return null
        val ok = FilmRules.parseUrl(pageUrl, FilmRules.MAX_NAV_URL) as? UrlResult.Ok ?: return null
        if (ok.url.length > MAX_URL) return null
        return ok.url.takeIf { sameSite(cassetteUrl, it) }
    }

    /** Dua alamat halaman sama (skema, nama situs, jalur); parameter dan fragmen diabaikan karena situs sering menambahkannya sendiri. */
    fun samePage(a: String, b: String): Boolean {
        fun key(u: String): String? {
            val ok = FilmRules.parseUrl(u, FilmRules.MAX_NAV_URL) as? UrlResult.Ok ?: return null
            return ok.url.substringBefore('#').substringBefore('?').trimEnd('/')
        }
        val ka = key(a) ?: return false
        return ka == key(b)
    }

    /**
     * Catat keadaan kaset [cassette] saat ini. [pageUrl] halaman yang tampil; [positionMs]/[durationMs] hasil membaca video
     * (null = tidak ada video yang terbaca); [ended] video sudah selesai. Tidak mengubah apa pun (data yang sama persis
     * dikembalikan) bila halamannya tidak boleh dicatat atau tidak ada yang berubah. Posisi yang terlalu awal disimpan
     * sebagai 0 (halamannya tetap diingat). Video yang sudah selesai ditonton MEMBUANG titik kaset itu: kaset berikutnya
     * dibuka dari alamat awalnya lagi, tempat memilih film lain.
     */
    fun record(
        data: FilmResumeData,
        cassette: FilmCassette,
        pageUrl: String?,
        positionMs: Long?,
        durationMs: Long?,
        ended: Boolean,
        nowMs: Long,
    ): FilmResumeData {
        val page = recordablePage(cassette.url, pageUrl) ?: return data
        val old = data.points[cassette.id]
        val pos: Long
        val dur: Long
        if (positionMs == null) {
            // Video belum terbaca (mis. kaset baru dimasukkan lalu dikeluarkan): halaman yang sama mempertahankan posisi lama
            val keep = old != null && samePage(old.url, page)
            pos = if (keep) old!!.positionMs else 0L
            dur = if (keep) old!!.durationMs else 0L
        } else {
            val d = (durationMs ?: 0L).coerceIn(0L, MAX_MS)
            val p = positionMs.coerceIn(0L, MAX_MS)
            val finished = ended || (d > 0L && p >= d - END_MARGIN_MS)
            if (finished) return forget(data, cassette.id)
            pos = if (p < MIN_RESUME_MS) 0L else p
            dur = d
        }
        // Tidak ada yang berubah (mis. film dijeda): kembalikan data yang sama persis, pemanggil tidak perlu menulis
        if (old != null && old.url == page && old.positionMs == pos && old.durationMs == dur && nowMs - old.savedAtMs in 0 until REFRESH_MS) return data
        val next = FilmResumePoint(page, pos, dur, nowMs)
        return limit(FilmResumeData(data.points + (cassette.id to next)))
    }

    /** Titik lanjut [cassette] yang masih berlaku: situsnya masih sama dengan alamat kaset sekarang dan belum kedaluwarsa. */
    fun point(data: FilmResumeData, cassette: FilmCassette, nowMs: Long): FilmResumePoint? {
        val p = data.points[cassette.id] ?: return null
        if (nowMs - p.savedAtMs > MAX_AGE_MS) return null
        return p.takeIf { sameSite(cassette.url, it.url) }
    }

    /**
     * Dari mana [cassette] dimulai sekarang: halaman terakhir yang masih berlaku (diperiksa ulang: https, situs yang sama
     * dengan alamat kaset, belum kedaluwarsa) beserta posisinya, atau alamat awal kaset bila tidak ada.
     */
    fun start(data: FilmResumeData, cassette: FilmCassette, nowMs: Long): FilmStart {
        val p = point(data, cassette, nowMs) ?: return FilmStart(cassette.url)
        val page = recordablePage(cassette.url, p.url) ?: return FilmStart(cassette.url)
        return FilmStart(page, seekTarget(p), p.durationMs.coerceIn(0L, MAX_MS))
    }

    /** Posisi yang layak dipulihkan dari [point], atau null (mulai dari awal). */
    fun seekTarget(point: FilmResumePoint?): Long? {
        val p = point?.positionMs ?: return null
        if (p < MIN_RESUME_MS || p > MAX_MS) return null
        if (point.durationMs > 0L && p > point.durationMs - END_MARGIN_MS) return null
        return p
    }

    /** Video yang baru terbaca (durasi [durationMs]) sudah cukup siap untuk dilompati ke [targetMs]. */
    fun canSeekNow(durationMs: Long, targetMs: Long): Boolean = durationMs > 0L && targetMs <= durationMs - END_MARGIN_MS

    fun forget(data: FilmResumeData, id: String): FilmResumeData =
        if (id in data.points) FilmResumeData(data.points - id) else data

    /**
     * Bersihkan data yang dibaca dari penyimpanan: buang titik tanpa kaset, yang kedaluwarsa, yang alamatnya tidak lagi
     * sah/sesitus dengan kaset, dan angka di luar batas; jumlahnya dibatasi.
     */
    fun sanitize(data: FilmResumeData, cassettes: List<FilmCassette>, nowMs: Long): FilmResumeData {
        val byId = cassettes.associateBy { it.id }
        val clean = LinkedHashMap<String, FilmResumePoint>()
        for ((id, p) in data.points) {
            val c = byId[id] ?: continue
            if (nowMs - p.savedAtMs > MAX_AGE_MS) continue
            val page = recordablePage(c.url, p.url) ?: continue
            val dur = p.durationMs.coerceIn(0L, MAX_MS)
            val pos = p.positionMs.coerceIn(0L, MAX_MS).let { if (dur > 0L && it > dur) 0L else it }
            clean[id] = FilmResumePoint(page, pos, dur, p.savedAtMs.coerceAtMost(nowMs))
        }
        return limit(FilmResumeData(clean))
    }

    /** Paling banyak satu titik per kaset yang bisa ada; yang tertua dibuang bila lebih. */
    private fun limit(data: FilmResumeData): FilmResumeData {
        if (data.points.size <= FilmRules.MAX_CASSETTES) return data
        val keep = data.points.entries.sortedByDescending { it.value.savedAtMs }.take(FilmRules.MAX_CASSETTES)
        return FilmResumeData(keep.associate { it.key to it.value })
    }

    /** "12:34" atau "1:02:03" (detik dibulatkan ke bawah). */
    fun label(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) / 1000L)
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        // Locale.ROOT: angka tetap Latin (font OSD tidak punya angka Arab-Indic) di semua bahasa
        return if (h > 0L) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    /** Untuk pembaca layar: "12 menit 34 detik", "1 jam 2 menit 3 detik". */
    fun spoken(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) / 1000L)
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return buildList {
            if (h > 0L) add("$h jam")
            if (m > 0L) add("$m menit")
            if (s > 0L || isEmpty()) add("$s detik")
        }.joinToString(" ")
    }
}
