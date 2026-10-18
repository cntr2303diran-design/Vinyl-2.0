package com.vinyl.launcher.util

import java.net.HttpURLConnection
import java.net.URL

/**
 * Satu-satunya pintu keluar ke internet. Aturannya sederhana dan sama untuk semua pemanggil:
 * - hanya HTTPS (tidak ada http, file, atau skema lain);
 * - ada batas waktu sambung & baca;
 * - ukuran respons dibatasi ([maxBytes]); lebih besar dari itu dianggap gagal;
 * - hanya status 200 yang dianggap berhasil.
 * Tidak ada cookie, tidak ada header identitas selain User-Agent bila diminta pemanggil.
 */
object Http {
    // URL(String) sengaja dipakai (bukan URI.create): URL dari aplikasi musik sering tidak ter-encode rapi
    // (spasi, tanda |, dll) dan URI menolaknya. Konstruktor ini baru deprecated di JDK 20+; di android.jar
    // dan JDK 17 (CI) tidak, jadi peringatan itu hanya muncul bila dikompilasi dengan JDK terbaru.
    @Suppress("DEPRECATION")
    private fun toUrl(url: String): URL = URL(url)

    /** True bila [url] berskema https dan punya host. */
    fun isAllowed(url: String): Boolean = runCatching {
        val u = toUrl(url)
        u.protocol.equals("https", ignoreCase = true) && !u.host.isNullOrBlank()
    }.getOrDefault(false)

    /**
     * Unduh [url] dan bedakan penyebab gagalnya: server MENJAWAB dengan status lain ([HttpResult.Status], mis. 404
     * "tidak ada") atau permintaannya sendiri gagal ([HttpResult.Failed]: URL ditolak, terlalu besar, jaringan putus).
     * Pemanggil yang perlu membedakan "tidak ada" dari "tidak tahu" (mis. lirik) memakai ini. Tidak melempar.
     * Panggil dari thread latar belakang.
     */
    fun fetch(
        url: String,
        maxBytes: Int,
        userAgent: String? = null,
        connectMs: Int = 7_000,
        readMs: Int = 9_000,
    ): HttpResult {
        if (!isAllowed(url)) return HttpResult.Failed
        var conn: HttpURLConnection? = null
        return try {
            conn = (toUrl(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = connectMs
                readTimeout = readMs
                useCaches = false
                if (userAgent != null) setRequestProperty("User-Agent", userAgent)
            }
            val code = conn.responseCode
            if (code != 200) return HttpResult.Status(code)
            if (conn.contentLengthLong > maxBytes) return HttpResult.Failed
            val bytes = conn.inputStream.use { it.readAtMost(maxBytes) }
            if (bytes == null) HttpResult.Failed else HttpResult.Ok(bytes)
        } catch (e: java.io.IOException) {
            HttpResult.Failed
        } catch (e: RuntimeException) {
            // URL cacat, SecurityException, dll — semuanya berarti "tidak tersedia"
            HttpResult.Failed
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Unduh [url] sebagai byte. Mengembalikan null bila URL ditolak, status bukan 200,
     * ukuran melebihi [maxBytes], atau terjadi kesalahan jaringan (tidak melempar).
     * Panggil dari thread latar belakang.
     */
    fun get(
        url: String,
        maxBytes: Int,
        userAgent: String? = null,
        connectMs: Int = 7_000,
        readMs: Int = 9_000,
    ): ByteArray? = (fetch(url, maxBytes, userAgent, connectMs, readMs) as? HttpResult.Ok)?.bytes

    fun getText(url: String, maxBytes: Int, userAgent: String? = null): String? =
        get(url, maxBytes, userAgent)?.toString(Charsets.UTF_8)
}

/** Hasil [Http.fetch]. */
sealed interface HttpResult {
    /** Status 200 dengan isi yang muat dalam batas ukuran. */
    class Ok(val bytes: ByteArray) : HttpResult
    /** Server menjawab dengan status selain 200. */
    class Status(val code: Int) : HttpResult
    /** Tidak ada jawaban yang bisa dipakai: URL ditolak, respons terlalu besar, atau kesalahan jaringan. */
    object Failed : HttpResult
}
