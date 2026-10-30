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
 * menghapus riwayat atau menyetel ulang rekor otomatis menarik pialanya kembali. Piala tidak disimpan terpisah. Ambangnya
 * ([TrophyGoals]) diatur sendiri oleh pengguna; tanpa pengaturan, ambang bawaan dipakai.
 */
object Achievements {
    const val HOUR_MS = 3_600_000L

    /** Jumlah piala: empat jalur × tiga tingkat. */
    val count: Int = TrophyLine.entries.size * TrophyTier.entries.size

    /** Semua piala (diperoleh maupun belum) menurut urutan rak; nilai negatif dianggap nol. */
    fun compute(input: AchievementInput, goals: TrophyGoals = TrophyGoals()): List<Trophy> {
        val out = ArrayList<Trophy>(count)
        fun line(
            id: String,
            titles: List<String>,
            goal: TrophyGoal,
            unit: String,
            value: Long,
            hint: (Int) -> String,
        ) {
            val v = value.coerceAtLeast(0L)
            for ((i, tier) in TrophyTier.entries.withIndex()) {
                val g = goal.forTier(tier)
                out += Trophy("${id}_${i + 1}", titles[i], hint(g), tier, progress = minOf(v, g.toLong()), goal = g.toLong(), unit = unit)
            }
        }
        line("snake", listOf("Ular Pemula", "Ular Lincah", "Ular Legenda"), goals.snake, "poin", input.snakeBest.toLong()) { "Raih $it poin di game Ular" }
        line("breakout", listOf("Pemecah Bata", "Penghancur Dinding", "Raja Bata"), goals.breakout, "poin", input.breakoutBest.toLong()) { "Raih $it poin di game Bata" }
        line("listen", listOf("Telinga Baru", "Pendengar Setia", "Maraton Kaset"), goals.listen, "jam", input.listenedMs / HOUR_MS) { "Dengarkan musik total $it jam" }
        line("songs", listOf("Rak Mulai Terisi", "Kolektor Kaset", "Perpustakaan Pita"), goals.songs, "lagu", input.songs.toLong()) { "Putar $it lagu berbeda" }
        return out
    }

    fun earned(trophies: List<Trophy>): List<Trophy> = trophies.filter { it.earned }

    /** Piala yang dipajang di rak dinding: yang diperoleh, tertinggi dulu, paling banyak [slots]. */
    fun display(trophies: List<Trophy>, slots: Int): List<Trophy> =
        earned(trophies).sortedWith(compareByDescending<Trophy> { it.tier.ordinal }.thenBy { it.id }).take(slots.coerceAtLeast(0))

    /** Piala berikutnya yang paling dekat diraih (kemajuan terbesar di antara yang belum), atau null bila semua sudah. */
    fun next(trophies: List<Trophy>): Trophy? = trophies.filterNot { it.earned }.maxByOrNull { it.fraction }
}
