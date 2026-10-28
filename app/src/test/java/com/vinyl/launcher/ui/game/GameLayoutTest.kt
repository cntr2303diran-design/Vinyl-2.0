package com.vinyl.launcher.ui.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameLayoutTest {
    private fun assertInside(w: Float, h: Float, l: BoardLayout) {
        assertTrue("kiri ${l.left}", l.left >= -0.001f)
        assertTrue("kanan ${l.left + l.boardWidth} > $w", l.left + l.boardWidth <= w + 0.001f)
        assertTrue("atas ${l.top} di bawah pita ${l.hudHeight}", l.top >= l.hudHeight - 0.001f)
        assertTrue("bawah ${l.top + l.boardHeight} > $h", l.top + l.boardHeight <= h + 0.001f)
    }

    @Test
    fun theBoardFitsInsideScreensOfEveryShapeBelowTheScoreBand() {
        val screens = listOf(
            200f to 168f,   // layar TV ponsel kecil
            266f to 223f,   // layar TV ponsel biasa (rasio ≈ 1,19)
            780f to 655f,   // layar TV tablet
            400f to 120f,   // sangat lebar
            120f to 400f,   // sangat tinggi
        )
        for ((w, h) in screens) {
            val l = GameLayout.compute(w, h, Snake.COLS, Snake.ROWS)
            assertInside(w, h, l)
            assertTrue("sel > 0 untuk $w×$h", l.cell > 0f)
            assertEquals(l.cell * Snake.COLS, l.boardWidth, 0.001f)
            assertEquals(l.cell * Snake.ROWS, l.boardHeight, 0.001f)
        }
    }

    @Test
    fun theBoardIsCenteredBelowTheBand() {
        val l = GameLayout.compute(266f, 223f, Snake.COLS, Snake.ROWS)
        assertEquals((266f - l.boardWidth) / 2f, l.left, 0.001f)
        val free = 223f - l.hudHeight
        assertEquals(l.hudHeight + (free - l.boardHeight) / 2f, l.top, 0.001f)
        assertEquals(223f * GameLayout.HUD_FRACTION, l.hudHeight, 0.001f)
    }

    @Test
    fun aZeroSizedScreenGivesAnEmptyBoardNotNaN() {
        val l = GameLayout.compute(0f, 0f, Snake.COLS, Snake.ROWS)
        assertEquals(0f, l.cell, 0f)
        assertEquals(0f, l.boardWidth, 0f)
    }

    @Test
    fun theHudSummaryOnlyChangesWhenPhaseScoreOrBestChange() {
        val s = Snake.newGame(best = 9, random = Random(1))
        val a = s.hud()
        assertEquals(GameHud(GamePhase.READY, 0, 9), a)
        // geser satu langkah: tubuh berubah, ringkasan tidak
        val moving = Snake.turn(s, Dir.RIGHT)
        val stepped = Snake.step(moving, Random(1)).state
        assertEquals(GamePhase.RUNNING, stepped.phase)
        assertEquals(GameHud(GamePhase.RUNNING, 0, 9), stepped.hud())
        assertEquals(stepped.hud(), Snake.step(stepped, Random(1)).state.hud())
    }

    @Test
    fun theHudShowsTheBestAsSoonAsTheScorePassesIt() {
        val s = Snake.newGame(best = 0, random = Random(1)).copy(score = 3, phase = GamePhase.RUNNING)
        assertEquals(3, s.hud().best)
    }
}
