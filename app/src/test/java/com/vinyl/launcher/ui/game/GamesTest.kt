package com.vinyl.launcher.ui.game

import com.vinyl.launcher.data.FilmSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GamesTest {
    @Test
    fun theRegistryKnowsSnakeAndBreakoutAndNothingElse() {
        assertEquals(listOf(Games.SNAKE, Games.BREAKOUT), Games.all)
        assertTrue(Games.exists(Games.SNAKE))
        assertTrue(Games.exists(Games.BREAKOUT))
        assertFalse(Games.exists(""))
        assertFalse(Games.exists("SNAKE"))
        assertFalse(Games.exists("tetris"))
    }

    @Test
    fun everyGameHasATitleThatFitsOnACassetteLabelAndAColor() {
        for (id in Games.all) {
            val t = Games.title(id)
            assertTrue("judul $id", t.isNotBlank() && t.length <= 8)
            assertTrue("warna $id tidak transparan", Games.color(id) ushr 24 == 0xFF)
        }
    }

    @Test
    fun titlesAndColorsTellTheCassettesApart() {
        assertEquals(Games.all.size, Games.all.map { Games.title(it) }.toSet().size)
        assertEquals(Games.all.size, Games.all.map { Games.color(it) }.toSet().size)
        assertEquals("", Games.title("tetris"))
    }

    @Test
    fun idsAreUnique() {
        assertEquals(Games.all.size, Games.all.toSet().size)
    }

    @Test
    fun eachGameHasItsOwnRecordAndTheCassetteLabelShowsIt() {
        val f = FilmSettings(snakeBest = 12, breakoutBest = 340)
        assertEquals(12, Games.best(f, Games.SNAKE))
        assertEquals(340, Games.best(f, Games.BREAKOUT))
        assertEquals(0, Games.best(f, "tetris"))
        assertEquals("Game · rekor 12", Games.subtitle(f, Games.SNAKE))
        assertEquals("Game · rekor 340", Games.subtitle(f, Games.BREAKOUT))
        // Belum ada rekor: tidak menulis "rekor 0"
        assertEquals("Game", Games.subtitle(FilmSettings(), Games.BREAKOUT))
        assertEquals("Game", Games.subtitle(FilmSettings(snakeBest = 5), Games.BREAKOUT))
    }

    @Test
    fun aScoreOnlyRaisesItsOwnGamesRecord() {
        val f = FilmSettings(snakeBest = 12, breakoutBest = 340)
        val raised = Games.withScore(f, Games.BREAKOUT, 500)
        assertEquals(500, raised.breakoutBest)
        assertEquals(12, raised.snakeBest)
        // Skor lebih rendah atau sama tidak menurunkan rekor
        assertEquals(f, Games.withScore(f, Games.BREAKOUT, 100))
        assertEquals(f, Games.withScore(f, Games.BREAKOUT, 340))
        // Game tak dikenal tidak mengubah apa pun
        assertEquals(f, Games.withScore(f, "tetris", 999_999))
        val snake = Games.withScore(f, Games.SNAKE, 40)
        assertEquals(40, snake.snakeBest)
        assertEquals(340, snake.breakoutBest)
    }

    @Test
    fun resettingClearsOnlyTheNamedGame() {
        val f = FilmSettings(snakeBest = 12, breakoutBest = 340)
        val noSnake = Games.reset(f, Games.SNAKE)
        assertEquals(0, noSnake.snakeBest)
        assertEquals(340, noSnake.breakoutBest)
        val noBreakout = Games.reset(f, Games.BREAKOUT)
        assertEquals(12, noBreakout.snakeBest)
        assertEquals(0, noBreakout.breakoutBest)
        assertEquals(f, Games.reset(f, "tetris"))
        assertNotEquals(f, noSnake)
    }
}
