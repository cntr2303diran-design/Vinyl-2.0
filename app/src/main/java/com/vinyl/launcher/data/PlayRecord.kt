package com.vinyl.launcher.data

import kotlinx.serialization.Serializable

/** Satu lagu di koleksi (riwayat putar). */
@Serializable
data class PlayRecord(
    val key: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val pkg: String? = null,
    val firstPlayed: Long,
    val lastPlayed: Long,
    val plays: Int = 1,
    /** Perkiraan total waktu didengarkan (ms). */
    val listenedMs: Long = 0,
    /** Path thumbnail cover (JPEG kecil di penyimpanan internal). */
    val artFile: String? = null,
    /** Riwayat waktu putar terakhir (maks 50) untuk statistik mingguan. */
    val playTimes: List<Long> = emptyList(),
)
