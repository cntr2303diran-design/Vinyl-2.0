package com.vinyl.launcher.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ambang piala yang diatur pengguna: bawaan, validasi ketikan, perbaikan pengaturan rusak, cadangan, dan preset. */
class TrophyGoalsTest {
    private val custom = TrophyGoals(
        snake = TrophyGoal(5, 12, 30),
        breakout = TrophyGoal(20, 60, 120),
        listen = TrophyGoal(2, 5, 20),
        songs = TrophyGoal(3, 8, 15),
    )

    private fun ok(line: TrophyLine, b: String, s: String, g: String) = (TrophyGoalRules.parse(line, b, s, g) as GoalParse.Ok).goal
    private fun bad(line: TrophyLine, b: String, s: String, g: String) = (TrophyGoalRules.parse(line, b, s, g) as GoalParse.Bad).message

    // ---------------------------------------------------------------- bawaan

    @Test
    fun theDefaultsAreTheThresholdsTheShelfAlwaysHad() {
        val d = TrophyGoals()
        assertEquals(TrophyGoal(10, 25, 50), d.snake)
        assertEquals(TrophyGoal(50, 150, 400), d.breakout)
        assertEquals(TrophyGoal(1, 10, 50), d.listen)
        assertEquals(TrophyGoal(10, 50, 200), d.songs)
        for (line in TrophyLine.entries) {
            assertTrue(line.name, TrophyGoalRules.isValid(line, line.default))
            assertEquals(line.default, d[line])
        }
    }

    @Test
    fun noGoalsGivenMeansTheDefaultsAreUsed() {
        val input = AchievementInput(snakeBest = 30, breakoutBest = 160, listenedMs = 12 * Achievements.HOUR_MS, songs = 60)
        assertEquals(Achievements.compute(input, TrophyGoals()), Achievements.compute(input))
    }

    @Test
    fun getAndReplaceTouchOnlyTheirOwnLine() {
        for (line in TrophyLine.entries) {
            val changed = TrophyGoals().replace(line, TrophyGoal(2, 3, 4))
            assertEquals(TrophyGoal(2, 3, 4), changed[line])
            for (other in TrophyLine.entries) if (other != line) assertEquals(other.default, changed[other])
        }
    }

    // ---------------------------------------------------------------- validasi ketikan

    @Test
    fun threeAscendingWholeNumbersAreAccepted() {
        assertEquals(TrophyGoal(3, 7, 9), ok(TrophyLine.SNAKE, "3", "7", "9"))
        assertEquals(TrophyGoal(1, 2, 3), ok(TrophyLine.LISTEN, "1", "2", "3"))
        assertEquals("spasi di tepi diabaikan", TrophyGoal(4, 5, 6), ok(TrophyLine.SONGS, " 4 ", "5\n", "\t6"))
        assertEquals(TrophyGoal(1, 2, TrophyLine.SONGS.max), ok(TrophyLine.SONGS, "1", "2", "${TrophyLine.SONGS.max}"))
    }

    @Test
    fun anythingThatIsNotThreeWholeNumbersIsRefusedWithAReason() {
        for ((b, s, g) in listOf(
            Triple("", "5", "9"),
            Triple("3", "", "9"),
            Triple("3", "5", ""),
            Triple("tiga", "5", "9"),
            Triple("3.5", "5", "9"),
            Triple("3", "5", "9 poin"),
            Triple("99999999999", "5", "9"),
        )) {
            assertEquals("$b|$s|$g", "Isi ketiga kolom dengan bilangan bulat.", bad(TrophyLine.SNAKE, b, s, g))
        }
    }

    @Test
    fun theBronzeNumberMustBeAtLeastOne() {
        assertEquals("Angka perunggu minimal 1.", bad(TrophyLine.SNAKE, "0", "5", "9"))
        assertEquals("Angka perunggu minimal 1.", bad(TrophyLine.SNAKE, "-3", "5", "9"))
    }

    @Test
    fun theNumbersMustStrictlyGrowFromBronzeToGold() {
        val message = "Angka harus makin besar: perunggu < perak < emas."
        assertEquals(message, bad(TrophyLine.BREAKOUT, "5", "5", "9"))
        assertEquals(message, bad(TrophyLine.BREAKOUT, "5", "9", "9"))
        assertEquals(message, bad(TrophyLine.BREAKOUT, "9", "5", "7"))
        assertEquals(message, bad(TrophyLine.BREAKOUT, "5", "9", "7"))
    }

    @Test
    fun goldCannotExceedTheLineLimit() {
        val max = TrophyLine.LISTEN.max
        assertEquals(TrophyGoal(1, 2, max), ok(TrophyLine.LISTEN, "1", "2", "$max"))
        assertEquals("Angka emas paling besar $max.", bad(TrophyLine.LISTEN, "1", "2", "${max + 1}"))
    }

    // ---------------------------------------------------------------- sah atau tidak

    @Test
    fun validityMatchesWhatTheEditorAccepts() {
        val line = TrophyLine.SNAKE
        assertTrue(TrophyGoalRules.isValid(line, TrophyGoal(1, 2, 3)))
        assertTrue(TrophyGoalRules.isValid(line, TrophyGoal(1, 2, line.max)))
        assertFalse(TrophyGoalRules.isValid(line, TrophyGoal(0, 2, 3)))
        assertFalse(TrophyGoalRules.isValid(line, TrophyGoal(-1, 2, 3)))
        assertFalse(TrophyGoalRules.isValid(line, TrophyGoal(2, 2, 3)))
        assertFalse(TrophyGoalRules.isValid(line, TrophyGoal(3, 2, 1)))
        assertFalse(TrophyGoalRules.isValid(line, TrophyGoal(1, 2, line.max + 1)))
        assertFalse(TrophyGoalRules.isValid(line, TrophyGoal(1, 2, Int.MAX_VALUE)))
    }

    @Test
    fun sanitizingResetsOnlyTheBrokenLines() {
        val messy = TrophyGoals(
            snake = TrophyGoal(9, 5, 1),
            breakout = custom.breakout,
            listen = TrophyGoal(0, 1, 2),
            songs = TrophyGoal(1, 2, Int.MAX_VALUE),
        )
        val clean = TrophyGoalRules.sanitize(messy)
        assertEquals(TrophyLine.SNAKE.default, clean.snake)
        assertEquals(custom.breakout, clean.breakout)
        assertEquals(TrophyLine.LISTEN.default, clean.listen)
        assertEquals(TrophyLine.SONGS.default, clean.songs)
        assertEquals("idempoten", clean, TrophyGoalRules.sanitize(clean))
        assertEquals("yang sudah sah tidak disentuh", custom, TrophyGoalRules.sanitize(custom))
    }

    // ---------------------------------------------------------------- piala memakai ambang pengguna

    @Test
    fun customGoalsMoveTheThresholdsAndTheHints() {
        val t = Achievements.compute(AchievementInput(snakeBest = 12), custom)
        val bronze = t.first { it.id == "snake_1" }
        val silver = t.first { it.id == "snake_2" }
        val gold = t.first { it.id == "snake_3" }
        assertEquals(listOf(5L, 12L, 30L), listOf(bronze.goal, silver.goal, gold.goal))
        assertTrue(bronze.earned)
        assertTrue("12 tepat di ambang perak", silver.earned)
        assertFalse(gold.earned)
        assertEquals(12L, gold.progress)
        assertEquals("Raih 30 poin di game Ular", gold.hint)
        // Dengan ambang bawaan skor 12 hanya perunggu
        assertEquals(listOf("snake_1"), Achievements.earned(Achievements.compute(AchievementInput(snakeBest = 12))).map { it.id })
    }

    @Test
    fun everyLineFollowsItsOwnGoals() {
        val input = AchievementInput(snakeBest = 5, breakoutBest = 20, listenedMs = 2 * Achievements.HOUR_MS, songs = 3)
        val earned = Achievements.earned(Achievements.compute(input, custom)).map { it.id }.toSet()
        assertEquals(setOf("snake_1", "breakout_1", "listen_1", "songs_1"), earned)
        assertEquals("listen_1 pada 2 jam bila ambangnya 2", 2L, Achievements.compute(input, custom).first { it.id == "listen_1" }.goal)
    }

    @Test
    fun loweringAGoalEarnsTheTrophyAndRaisingItTakesItBack() {
        val input = AchievementInput(snakeBest = 20)
        fun earnedWith(goals: TrophyGoals) = Achievements.earned(Achievements.compute(input, goals)).map { it.id }
        assertEquals(listOf("snake_1"), earnedWith(TrophyGoals()))
        assertEquals(listOf("snake_1", "snake_2", "snake_3"), earnedWith(TrophyGoals(snake = TrophyGoal(1, 2, 3))))
        assertEquals(emptyList<String>(), earnedWith(TrophyGoals(snake = TrophyGoal(30, 40, 50))))
    }

    @Test
    fun theTrophyCountAndIdsStayTheSameWhateverTheGoals() {
        val a = Achievements.compute(AchievementInput(), TrophyGoals())
        val b = Achievements.compute(AchievementInput(), custom)
        assertEquals(Achievements.count, b.size)
        assertEquals(a.map { it.id }, b.map { it.id })
        assertEquals(a.map { it.title }, b.map { it.title })
        assertNotEquals(a.map { it.goal }, b.map { it.goal })
    }

    // ---------------------------------------------------------------- tersimpan bersama pengaturan

    @Test
    fun oldSettingsDocumentsReadWithTheDefaultGoals() {
        val withGoals = AppSettings(retro = RetroSettings(trophyGoals = custom))
        val root = SettingsMigration.storeJson.encodeToJsonElement(AppSettings.serializer(), withGoals).jsonObject
        val retro = root[SettingsMigration.RETRO_KEY]!!.jsonObject
        assertTrue(retro.containsKey("trophyGoals"))
        val old = JsonObject(root + (SettingsMigration.RETRO_KEY to JsonObject(retro - "trophyGoals"))).toString()
        assertEquals(TrophyGoals(), SettingsMigration.decode(SettingsMigration.storeJson, old).retro.trophyGoals)
        // Dan yang tersimpan kembali persis
        val raw = SettingsMigration.storeJson.encodeToString(AppSettings.serializer(), withGoals)
        assertEquals(custom, SettingsMigration.decode(SettingsMigration.storeJson, raw).retro.trophyGoals)
    }

    @Test
    fun aDamagedGoalLineInStoredSettingsResetsOnlyThatLine() {
        val good = SettingsMigration.storeJson.encodeToString(AppSettings.serializer(), AppSettings(retro = RetroSettings(trophyGoals = custom)))
        val needle = "\"bronze\":5,\"silver\":12,\"gold\":30"
        assertTrue("dokumen uji harus memuat ambang Ular", good.contains(needle))
        val raw = good.replace(needle, "\"bronze\":30,\"silver\":12,\"gold\":5")
        val back = SettingsSanitizer.sanitize(SettingsMigration.decode(SettingsMigration.storeJson, raw))
        assertEquals(TrophyLine.SNAKE.default, back.retro.trophyGoals.snake)
        assertEquals(custom.breakout, back.retro.trophyGoals.breakout)
        assertEquals(custom.listen, back.retro.trophyGoals.listen)
        assertEquals(custom.songs, back.retro.trophyGoals.songs)
    }

    @Test
    fun theSanitizerAlsoRepairsGoalsInsideSavedPresets() {
        val broken = RetroSettings(trophyGoals = TrophyGoals(snake = TrophyGoal(0, 0, 0)))
        val s = SettingsSanitizer.sanitize(AppSettings(presets = listOf(ThemePreset(1, "P", HomeSettings(), CoverSettings(), broken))))
        assertEquals(TrophyLine.SNAKE.default, s.presets[0].retro!!.trophyGoals.snake)
    }

    @Test
    fun goalsTravelInTheSettingsBackupAndComeBackSanitized() {
        val mine = AppSettings(retro = RetroSettings(trophyGoals = custom))
        val text = SettingsBackup.export(mine)
        val back = SettingsBackup.parse(text, AppSettings(), { true }) as SettingsBackup.ImportResult.Ok
        assertEquals(custom, back.settings.retro.trophyGoals)

        // Berkas yang diedit tangan atau berasal dari sumber asing: ekspor tidak membersihkan, impor yang membersihkan
        val tampered = SettingsBackup.export(AppSettings(retro = RetroSettings(trophyGoals = custom.copy(snake = TrophyGoal(0, 0, 0)))))
        val fixed = SettingsBackup.parse(tampered, AppSettings(), { true }) as SettingsBackup.ImportResult.Ok
        assertEquals(TrophyLine.SNAKE.default, fixed.settings.retro.trophyGoals.snake)
        assertEquals(custom.breakout, fixed.settings.retro.trophyGoals.breakout)
    }

    @Test
    fun applyingAPresetKeepsYourOwnGoals() {
        val preset = ThemePreset(1, "P", HomeSettings(), CoverSettings(), RetroSettings(tvBody = TvBody.WALNUT, trophyGoals = TrophyGoals(snake = TrophyGoal(1, 2, 3))))
        val applied = SettingsMigration.retroToApply(preset, RetroSettings(trophyGoals = custom))
        assertEquals(custom, applied.trophyGoals)
        assertEquals(TvBody.WALNUT, applied.tvBody)
    }
}
