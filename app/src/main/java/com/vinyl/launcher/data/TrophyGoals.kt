package com.vinyl.launcher.data

import kotlinx.serialization.Serializable

/** Tiga ambang satu jalur piala, dalam satuan jalurnya (poin, jam, atau lagu): perunggu < perak < emas. */
@Serializable
data class TrophyGoal(val bronze: Int, val silver: Int, val gold: Int) {
    fun forTier(tier: TrophyTier): Int = when (tier) {
        TrophyTier.BRONZE -> bronze
        TrophyTier.SILVER -> silver
        TrophyTier.GOLD -> gold
    }
}

/** Jalur piala yang ambangnya bisa diatur pengguna; [default] adalah ambang bawaan dan [max] angka terbesar yang diterima. */
enum class TrophyLine(val label: String, val unit: String, val default: TrophyGoal, val max: Int) {
    SNAKE("Game Ular", "poin", TrophyGoal(10, 25, 50), FilmRules.MAX_SNAKE),
    BREAKOUT("Game Bata", "poin", TrophyGoal(50, 150, 400), FilmRules.MAX_BREAKOUT),
    LISTEN("Waktu dengar", "jam", TrophyGoal(1, 10, 50), TrophyGoalRules.MAX_OTHER),
    SONGS("Lagu berbeda", "lagu", TrophyGoal(10, 50, 200), TrophyGoalRules.MAX_OTHER),
}

/**
 * Ambang semua jalur piala, diatur sendiri oleh pengguna (Pengaturan → Benda ruangan → Rak piala). Disimpan lokal bersama
 * pengaturan gaya TV dan ikut berkas cadangan pengaturan (bukan data pribadi), tetapi tidak berubah saat menerapkan preset.
 * Dokumen lama tanpa kunci ini terbaca dengan ambang bawaan.
 */
@Serializable
data class TrophyGoals(
    val snake: TrophyGoal = TrophyLine.SNAKE.default,
    val breakout: TrophyGoal = TrophyLine.BREAKOUT.default,
    val listen: TrophyGoal = TrophyLine.LISTEN.default,
    val songs: TrophyGoal = TrophyLine.SONGS.default,
) {
    operator fun get(line: TrophyLine): TrophyGoal = when (line) {
        TrophyLine.SNAKE -> snake
        TrophyLine.BREAKOUT -> breakout
        TrophyLine.LISTEN -> listen
        TrophyLine.SONGS -> songs
    }

    fun replace(line: TrophyLine, goal: TrophyGoal): TrophyGoals = when (line) {
        TrophyLine.SNAKE -> copy(snake = goal)
        TrophyLine.BREAKOUT -> copy(breakout = goal)
        TrophyLine.LISTEN -> copy(listen = goal)
        TrophyLine.SONGS -> copy(songs = goal)
    }
}

/** Hasil membaca tiga angka ketikan pengguna. [Bad.message] ditampilkan apa adanya di editor. */
sealed interface GoalParse {
    data class Ok(val goal: TrophyGoal) : GoalParse
    data class Bad(val message: String) : GoalParse
}

/** Aturan ambang piala (murni, teruji). */
object TrophyGoalRules {
    /** Batas atas jalur jam dengar dan jumlah lagu. */
    const val MAX_OTHER = 99_999

    /** Ambang sah: bilangan bulat ≥ 1, naik tegas (perunggu < perak < emas), dan emas tidak melebihi batas jalurnya. */
    fun isValid(line: TrophyLine, g: TrophyGoal): Boolean =
        g.bronze >= 1 && g.bronze < g.silver && g.silver < g.gold && g.gold <= line.max

    /** Jalur yang ambangnya tidak sah dikembalikan ke bawaan; jalur lain tidak diubah (pengaturan rusak/hasil cadangan asing). */
    fun sanitize(goals: TrophyGoals): TrophyGoals {
        fun fix(line: TrophyLine) = if (isValid(line, goals[line])) goals[line] else line.default
        return TrophyGoals(
            snake = fix(TrophyLine.SNAKE),
            breakout = fix(TrophyLine.BREAKOUT),
            listen = fix(TrophyLine.LISTEN),
            songs = fix(TrophyLine.SONGS),
        )
    }

    /** Tiga isian teks → ambang, atau alasan penolakannya dalam bahasa sehari-hari. */
    fun parse(line: TrophyLine, bronze: String, silver: String, gold: String): GoalParse {
        val b = bronze.trim().toIntOrNull()
        val s = silver.trim().toIntOrNull()
        val g = gold.trim().toIntOrNull()
        if (b == null || s == null || g == null) return GoalParse.Bad("Isi ketiga kolom dengan bilangan bulat.")
        if (b < 1) return GoalParse.Bad("Angka perunggu minimal 1.")
        if (!(b < s && s < g)) return GoalParse.Bad("Angka harus makin besar: perunggu < perak < emas.")
        if (g > line.max) return GoalParse.Bad("Angka emas paling besar ${line.max}.")
        return GoalParse.Ok(TrophyGoal(b, s, g))
    }
}
