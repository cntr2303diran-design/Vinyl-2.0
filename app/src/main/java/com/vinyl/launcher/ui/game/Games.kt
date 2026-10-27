package com.vinyl.launcher.ui.game

/** Game bawaan yang tersedia sebagai kaset di laci. Murni agar bisa diuji; tampilannya ada di layar game masing-masing. */
object Games {
    const val SNAKE = "snake"

    /** Urutan kaset game di baki. */
    val all: List<String> = listOf(SNAKE)

    fun exists(id: String): Boolean = id in all

    /** Nama di label kaset. */
    fun title(id: String): String = when (id) {
        SNAKE -> "Ular"
        else -> ""
    }

    /** Warna cangkang kaset game (ARGB). */
    fun color(id: String): Int = when (id) {
        SNAKE -> 0xFF2F7D5B.toInt()
        else -> 0xFF8A857C.toInt()
    }
}
