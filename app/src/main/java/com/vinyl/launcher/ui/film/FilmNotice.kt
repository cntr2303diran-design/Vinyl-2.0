package com.vinyl.launcher.ui.film

import com.vinyl.launcher.data.FilmResume

/**
 * Pesan singkat di layar TV film, bergaya OSD kamera: judul besar dan keterangan kecil opsional. Tampil beberapa detik lalu
 * hilang sendiri; dibacakan pembaca layar. Murni (tanpa Android) agar teksnya diuji.
 */
data class FilmNotice(val title: String, val detail: String? = null) {
    /** Teks utuh untuk pembaca layar. */
    val spoken: String get() = if (detail.isNullOrBlank()) title else "$title. $detail"

    companion object {
        /** Lama pesan tampil (ms). */
        const val SHOW_MS = 4_000L

        /** Tombol deck ditekan tetapi halaman tidak punya video yang bisa dikendalikan (belum dibuka, atau pemutar terlindungi). */
        val NO_VIDEO = FilmNotice(
            "TIDAK ADA VIDEO",
            "Mulai dulu filmnya di layar. Pemutar yang terlindungi (DRM) atau tertanam tidak bisa dikendalikan dari deck.",
        )

        /** Film dilanjutkan dari posisi terakhir [positionMs]. */
        fun resumed(positionMs: Long) = FilmNotice("LANJUT ${FilmResume.label(positionMs)}")
    }
}
