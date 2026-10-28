package com.vinyl.launcher.ui.game

/*
 * Bagian yang dipakai bersama semua game di kaset game (Ular, Bata): fase permainan dan ringkasan untuk deck/VFD/OSD.
 * Murni (tanpa Android) agar bisa diuji.
 */

enum class GamePhase {
    /** Papan siap; permainan menunggu gerakan atau ketukan pertama. */
    READY,
    RUNNING,
    PAUSED,

    /** Kalah: ular menabrak tubuhnya sendiri, atau nyawa Bata habis. */
    OVER,

    /** Menang: papan Ular penuh. (Bata tidak pernah menang; levelnya terus naik.) */
    WON,
}

/**
 * Ringkasan papan untuk deck, VFD, dan OSD: hanya berubah saat fase, skor, rekor, nyawa, atau tingkat berubah (tidak tiap
 * langkah atau bingkai), jadi membacanya tidak menyusun ulang beranda sepanjang permainan.
 *
 * @param best rekor yang ditampilkan (bertambah seketika saat skor melampauinya)
 * @param lives nyawa tersisa; 0 bila game tidak punya nyawa (Ular)
 * @param level tingkat sekarang; 0 bila game tidak punya tingkat (Ular)
 */
data class GameHud(val phase: GamePhase, val score: Int, val best: Int, val lives: Int = 0, val level: Int = 0)
