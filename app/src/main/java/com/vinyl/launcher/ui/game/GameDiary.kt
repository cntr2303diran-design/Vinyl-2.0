package com.vinyl.launcher.ui.game

/**
 * Aturan catatan game di buku harian (murni, teruji). Satu ronde dicatat dengan nama game dan skornya, tanpa data lain:
 *  - saat ronde berakhir (Ular menabrak atau menang, nyawa Bata habis), dan
 *  - saat kaset game dikeluarkan sementara ronde masih berjalan atau dijeda (ronde yang ditinggalkan).
 * Papan yang belum pernah dimulai ([GamePhase.READY]) dan ronde yang sudah dicatat saat berakhir tidak dicatat lagi.
 */
object GameDiary {
    /** Keterangan baris kedua: "Skor 23", atau "Skor 23 · rekor baru" bila ronde ini memecahkan rekor tersimpan. */
    fun detail(score: Int, newBest: Boolean): String {
        val s = score.coerceAtLeast(0)
        return if (newBest && s > 0) "Skor $s · rekor baru" else "Skor $s"
    }

    /** Ronde yang ditinggalkan (kaset dikeluarkan) layak dicatat hanya bila sedang berjalan atau dijeda. */
    fun worthRecordingOnEject(phase: GamePhase): Boolean = phase == GamePhase.RUNNING || phase == GamePhase.PAUSED
}
