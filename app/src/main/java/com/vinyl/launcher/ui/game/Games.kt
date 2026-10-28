package com.vinyl.launcher.ui.game

import com.vinyl.launcher.data.FilmRules
import com.vinyl.launcher.data.FilmSettings

/** Game bawaan yang tersedia sebagai kaset di laci. Murni agar bisa diuji; tampilannya ada di layar game masing-masing. */
object Games {
    const val SNAKE = "snake"
    const val BREAKOUT = "breakout"

    /** Urutan kaset game di baki. */
    val all: List<String> = listOf(SNAKE, BREAKOUT)

    fun exists(id: String): Boolean = id in all

    /** Nama di label kaset. */
    fun title(id: String): String = when (id) {
        SNAKE -> "Ular"
        BREAKOUT -> "Bata"
        else -> ""
    }

    /** Rekor tersimpan game [id] (0 = belum ada). */
    fun best(film: FilmSettings, id: String): Int = when (id) {
        SNAKE -> film.snakeBest
        BREAKOUT -> film.breakoutBest
        else -> 0
    }

    /** Pengaturan dengan rekor game [id] sebesar [score] bila itu lebih baik dari yang tersimpan (tidak pernah menurunkan). */
    fun withScore(film: FilmSettings, id: String, score: Int): FilmSettings = when (id) {
        SNAKE -> FilmRules.withSnakeScore(film, score)
        BREAKOUT -> FilmRules.withBreakoutScore(film, score)
        else -> film
    }

    /** Pengaturan dengan rekor game [id] dikembalikan ke nol (tombol "Setel ulang rekor" di Pengaturan → Laci kaset). */
    fun reset(film: FilmSettings, id: String): FilmSettings = when (id) {
        SNAKE -> film.copy(snakeBest = 0)
        BREAKOUT -> film.copy(breakoutBest = 0)
        else -> film
    }

    /** Teks kecil di label kaset dan baki: "Game", atau "Game · rekor N" bila sudah ada rekor. */
    fun subtitle(film: FilmSettings, id: String): String {
        val best = best(film, id)
        return if (best > 0) "Game · rekor $best" else "Game"
    }

    /** Warna cangkang kaset game (ARGB). */
    fun color(id: String): Int = when (id) {
        SNAKE -> 0xFF2F7D5B.toInt()
        BREAKOUT -> 0xFFB5532E.toInt()
        else -> 0xFF8A857C.toInt()
    }
}
