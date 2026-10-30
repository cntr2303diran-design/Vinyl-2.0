package com.vinyl.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsTest {
    private fun all(i: AchievementInput) = Achievements.compute(i)

    @Test
    fun nothingIsEarnedOnAFreshInstall() {
        val t = all(AchievementInput())
        assertEquals(Achievements.count, t.size)
        assertTrue(Achievements.earned(t).isEmpty())
        assertTrue(t.all { it.progress == 0L && it.fraction == 0f })
    }

    @Test
    fun idsAreUniqueAndEachGroupHasThreeTiers() {
        val t = all(AchievementInput())
        assertEquals(t.size, t.map { it.id }.toSet().size)
        for (g in listOf("snake", "breakout", "listen", "songs")) {
            val tiers = t.filter { it.id.startsWith("${g}_") }.map { it.tier }
            assertEquals(TrophyTier.entries, tiers)
        }
    }

    @Test
    fun thresholdsAreInclusiveAndIncreasing() {
        assertTrue(all(AchievementInput(snakeBest = 10)).first { it.id == "snake_1" }.earned)
        assertFalse(all(AchievementInput(snakeBest = 9)).first { it.id == "snake_1" }.earned)
        assertTrue(all(AchievementInput(breakoutBest = 400)).first { it.id == "breakout_3" }.earned)
        for (g in listOf("snake", "breakout", "listen", "songs")) {
            val goals = all(AchievementInput()).filter { it.id.startsWith("${g}_") }.map { it.goal }
            assertEquals(goals.sorted(), goals)
            assertEquals(goals.distinct(), goals)
        }
    }

    @Test
    fun listeningIsCountedInWholeHours() {
        val almost = all(AchievementInput(listenedMs = Achievements.HOUR_MS - 1)).first { it.id == "listen_1" }
        assertFalse(almost.earned)
        val one = all(AchievementInput(listenedMs = Achievements.HOUR_MS)).first { it.id == "listen_1" }
        assertTrue(one.earned)
        assertEquals(10L, all(AchievementInput(listenedMs = 10 * Achievements.HOUR_MS)).first { it.id == "listen_2" }.progress)
    }

    @Test
    fun progressIsCappedAtTheGoalAndNegativesAreZero() {
        val t = all(AchievementInput(snakeBest = 9999, breakoutBest = -5, listenedMs = -1, songs = -3))
        val snake = t.first { it.id == "snake_1" }
        assertEquals(snake.goal, snake.progress)
        assertEquals(1f, snake.fraction, 0f)
        assertEquals(0L, t.first { it.id == "breakout_1" }.progress)
        assertEquals(0L, t.first { it.id == "songs_1" }.progress)
    }

    @Test
    fun resettingARecordTakesTheTrophyBack() {
        assertTrue(all(AchievementInput(snakeBest = 30)).first { it.id == "snake_2" }.earned)
        assertFalse(all(AchievementInput(snakeBest = 0)).first { it.id == "snake_2" }.earned)
    }

    @Test
    fun displayShowsTheBestEarnedFirstWithinTheSlots() {
        val t = all(AchievementInput(snakeBest = 60, breakoutBest = 60, listenedMs = 2 * Achievements.HOUR_MS, songs = 12))
        val shown = Achievements.display(t, 3)
        assertEquals(3, shown.size)
        assertEquals(TrophyTier.GOLD, shown.first().tier)
        assertTrue(shown.all { it.earned })
        assertEquals(shown.map { it.tier.ordinal }.sortedDescending(), shown.map { it.tier.ordinal })
        assertTrue(Achievements.display(t, 0).isEmpty())
        assertTrue(Achievements.display(t, -2).isEmpty())
    }

    @Test
    fun nextIsTheClosestUnearnedTrophy() {
        val t = all(AchievementInput(snakeBest = 20))
        assertEquals("snake_2", Achievements.next(t)!!.id)
        val everything = all(AchievementInput(snakeBest = 99, breakoutBest = 999, listenedMs = 99 * Achievements.HOUR_MS, songs = 999))
        assertNull(Achievements.next(everything))
    }
}
