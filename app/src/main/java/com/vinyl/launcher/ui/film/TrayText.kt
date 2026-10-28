package com.vinyl.launcher.ui.film

import com.vinyl.launcher.data.FilmResume

/** Teks baki kaset yang bergantung pada keadaan, murni (tanpa Android) agar diuji. */
object TrayText {
    /** Kalimat petunjuk di bawah judul baki. [scrollable]: ada kaset di luar layar (daftar bisa digeser). */
    fun hint(current: Tape, scrollable: Boolean): String {
        val base = if (current == Tape.Music) "Ketuk kaset untuk memasukkannya ke TV · tahan untuk mengubah"
        else "Ketuk kaset yang bertanda DI TV untuk mengembalikan kaset musik"
        return if (scrollable) "$base · geser untuk kaset lain" else base
    }

    /** Tanda di kartu kaset film yang punya titik lanjut, mis. "LANJUT 12:34"; null bila tidak ada. */
    fun resumeBadge(resumeMs: Long?): String? = resumeMs?.let { "LANJUT ${FilmResume.label(it)}" }

    /** Deskripsi kartu kaset untuk pembaca layar. */
    fun cardDescription(title: String, subtitle: String, resumeMs: Long?): String =
        "Kaset $title, $subtitle" + (resumeMs?.let { ". Lanjut dari ${FilmResume.spoken(it)}" } ?: "")
}
