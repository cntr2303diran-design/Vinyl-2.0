package com.vinyl.launcher.data

/** Tingkat piala; urutan = nilai. */
enum class TrophyTier { BRONZE, SILVER, GOLD }

/**
 * Satu piala di rak. [progress] dan [goal] dalam satuan [unit] (mis. 12 dari 25 poin); [earned] bila [progress] ≥ [goal].
 */
data class Trophy(
    val id: String,
    val title: String,
    val hint: String,
    val tier: TrophyTier,
    val progress: Long,
    val goal: Long,
    val unit: String,
) {
    val earned: Boolean get() = progress >= goal
    /** Kemajuan 0..1 untuk batang progres. */
    val fraction: Float get() = if (goal <= 0L) 1f else (progress.toFloat() / goal).coerceIn(0f, 1f)
}

/** Angka yang dihitung dari data lokal yang sudah ada; tidak ada data baru yang dikumpulkan untuk piala. */
data class AchievementInput(
    val snakeBest: Int = 0,
    val breakoutBest: Int = 0,
    val listenedMs: Long = 0L,
    val songs: Int = 0,
)

/**
 * Rak piala (murni, teruji). Semua piala diturunkan dari rekor game, total waktu dengar, dan jumlah lagu di koleksi, jadi
 * menghapus riwayat atau menyetel ulang rekor otomatis menarik pialanya kembali. Piala tidak disimpan terpisah.
 */
object Achievements {
    const val HOUR_MS = 3_600_000L

    private class Rule(val id: String, val title: String, val hint: String, val tier: TrophyTier, val goal: Long, val unit: String, val value: (AchievementInput) -> Long)

    private val rules: List<Rule> = listOf(
        Rule("snake_1", "Ular Pemula", "Raih 10 poin di game Ular", TrophyTier.BRONZE, 10, "poin") { it.snakeBest.toLong() },
        Rule("snake_2", "Ular Lincah", "Raih 25 poin di game Ular", TrophyTier.SILVER, 25, "poin") { it.snakeBest.toLong() },
        Rule("snake_3", "Ular Legenda", "Raih 50 poin di game Ular", TrophyTier.GOLD, 50, "poin") { it.snakeBest.toLong() },
        Rule("breakout_1", "Pemecah Bata", "Raih 50 poin di game Bata", TrophyTier.BRONZE, 50, "poin") { it.breakoutBest.toLong() },
        Rule("breakout_2", "Penghancur Dinding", "Raih 150 poin di game Bata", TrophyTier.SILVER, 150, "poin") { it.breakoutBest.toLong() },
        Rule("breakout_3", "Raja Bata", "Raih 400 poin di game Bata", TrophyTier.GOLD, 400, "poin") { it.breakoutBest.toLong() },
        Rule("listen_1", "Telinga Baru", "Dengarkan musik total 1 jam", TrophyTier.BRONZE, 1, "jam") { it.listenedMs / HOUR_MS },
        Rule("listen_2", "Pendengar Setia", "Dengarkan musik total 10 jam", TrophyTier.SILVER, 10, "jam") { it.listenedMs / HOUR_MS },
        Rule("listen_3", "Maraton Kaset", "Dengarkan musik total 50 jam", TrophyTier.GOLD, 50, "jam") { it.listenedMs / HOUR_MS },
        Rule("songs_1", "Rak Mulai Terisi", "Putar 10 lagu berbeda", TrophyTier.BRONZE, 10, "lagu") { it.songs.toLong() },
        Rule("songs_2", "Kolektor Kaset", "Putar 50 lagu berbeda", TrophyTier.SILVER, 50, "lagu") { it.songs.toLong() },
        Rule("songs_3", "Perpustakaan Pita", "Putar 200 lagu berbeda", TrophyTier.GOLD, 200, "lagu") { it.songs.toLong() },
    )

    val count: Int get() = rules.size

    /** Semua piala (diperoleh maupun belum) menurut urutan rak; nilai negatif dianggap nol. */
    fun compute(input: AchievementInput): List<Trophy> = rules.map { r ->
        val v = r.value(input).coerceAtLeast(0L)
        Trophy(r.id, r.title, r.hint, r.tier, progress = minOf(v, r.goal), goal = r.goal, unit = r.unit)
    }

    fun earned(trophies: List<Trophy>): List<Trophy> = trophies.filter { it.earned }

    /** Piala yang dipajang di rak dinding: yang diperoleh, tertinggi dulu, paling banyak [slots]. */
    fun display(trophies: List<Trophy>, slots: Int): List<Trophy> =
        earned(trophies).sortedWith(compareByDescending<Trophy> { it.tier.ordinal }.thenBy { it.id }).take(slots.coerceAtLeast(0))

    /** Piala berikutnya yang paling dekat diraih (kemajuan terbesar di antara yang belum), atau null bila semua sudah. */
    fun next(trophies: List<Trophy>): Trophy? = trophies.filterNot { it.earned }.maxByOrNull { it.fraction }
}
