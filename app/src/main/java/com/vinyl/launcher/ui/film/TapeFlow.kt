package com.vinyl.launcher.ui.film

import com.vinyl.launcher.ui.game.Games

/*
 * Alur kaset di deck: kaset musik (bawaan, menampilkan lagu yang diputar aplikasi musik) bisa diganti kaset dari laci —
 * kaset film (situs di layar TV) atau kaset game. Murni (tanpa Android) agar aturan jeda/lanjut musiknya bisa diuji.
 */

/** Apa yang ada di deck sekarang. */
sealed interface Tape {
    /** Kaset musik: TV menampilkan lagu yang sedang diputar aplikasi musik. */
    data object Music : Tape

    /** Kaset film di laci: layar TV membuka situs film kaset itu. */
    data class Film(val id: String) : Tape

    /** Kaset game bawaan (id dari [Games]). */
    data class Game(val id: String) : Tape
}

/** Perintah ke aplikasi musik yang menyertai pergantian kaset. */
enum class MusicEffect { NONE, PAUSE, RESUME }

/**
 * @param tape kaset yang sedang di deck
 * @param musicWasPlaying musik sedang main saat kaset musik dikeluarkan dari deck (dilanjutkan saat kaset musik kembali,
 *   bila pengguna mengizinkan)
 */
data class TapeState(val tape: Tape = Tape.Music, val musicWasPlaying: Boolean = false)

data class TapeStep(val state: TapeState, val music: MusicEffect)

object TapeFlow {

    /**
     * Kaset [pressed] di laci ditekan. Menekan kaset yang SUDAH di deck mengeluarkannya (kaset musik kembali); menekan
     * kaset lain menggantinya langsung. Musik dijeda hanya saat kaset musik meninggalkan deck dalam keadaan main, dan
     * keadaan itu diingat sampai kaset musik kembali.
     */
    fun press(s: TapeState, pressed: Tape, musicPlaying: Boolean, resumeMusic: Boolean): TapeStep {
        if (pressed == Tape.Music || pressed == s.tape) return eject(s, resumeMusic)
        return if (s.tape == Tape.Music) {
            TapeStep(TapeState(pressed, musicWasPlaying = musicPlaying), if (musicPlaying) MusicEffect.PAUSE else MusicEffect.NONE)
        } else {
            // Film → film lain / game: musik sudah dijeda sejak kaset musik keluar; ingatan lama dipertahankan
            TapeStep(s.copy(tape = pressed), MusicEffect.NONE)
        }
    }

    /** Keluarkan kaset di deck: kaset musik kembali; musik dilanjutkan bila tadi main dan [resumeMusic] menyala. */
    fun eject(s: TapeState, resumeMusic: Boolean): TapeStep =
        if (s.tape == Tape.Music) TapeStep(s, MusicEffect.NONE)
        else TapeStep(TapeState(Tape.Music, musicWasPlaying = false), if (s.musicWasPlaying && resumeMusic) MusicEffect.RESUME else MusicEffect.NONE)

    /**
     * Kaset film di deck sudah tidak ada di laci (dihapus dari pengaturan), atau kaset game tidak dikenal: kembali ke kaset
     * musik. Musik TIDAK dilanjutkan otomatis di sini — penghapusan bukan perintah "kembalikan musik".
     */
    fun reconcile(s: TapeState, filmExists: (String) -> Boolean, gameExists: (String) -> Boolean = Games::exists): TapeState {
        val t = s.tape
        val gone = (t is Tape.Film && !filmExists(t.id)) || (t is Tape.Game && !gameExists(t.id))
        return if (gone) TapeState(Tape.Music, musicWasPlaying = false) else s
    }

    /** Teks VFD deck/TV untuk kaset [tape] dan keadaannya, mis. "FILM ▶". */
    fun modeWord(tape: Tape): String = when (tape) {
        Tape.Music -> "MUSIK"
        is Tape.Film -> "FILM"
        is Tape.Game -> "GAME"
    }
}
