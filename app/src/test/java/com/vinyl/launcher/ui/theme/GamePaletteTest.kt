package com.vinyl.launcher.ui.theme

import com.vinyl.launcher.ui.theme.Tokens.Game
import com.vinyl.launcher.ui.theme.Tokens.Retro
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tulisan di papan game (Ular, Bata) harus >= 4,5:1 dan bentuk penting (ular, makanan, bata, palet, bola) >= 3:1 terhadap papan. */
class GamePaletteTest {
    private val boards = listOf(Game.BOARD_TOP, Game.BOARD_BOTTOM)
    private val white = 0xFFFFFFFFL

    private fun assertText(name: String, fg: Long, bg: Long) {
        val c = ColorMath.contrast(fg, bg)
        assertTrue("$name: %.2f:1 < 4,5:1".format(c), c >= Tokens.A11y.MIN_CONTRAST)
    }

    private fun assertShape(name: String, fg: Long, bg: Long) {
        val c = ColorMath.contrast(fg, bg)
        assertTrue("$name: %.2f:1 < 3:1".format(c), c >= 3.0)
    }

    @Test
    fun theScoreBandIsReadableOnTheBoard() {
        for (b in boards) {
            assertText("SKOR/%08X".format(b), white, b)
            assertText("REKOR/%08X".format(b), Retro.AMBER, b)
        }
    }

    /** Panel tengah menutupi ular dan makanan: kasus terburuk adalah bagian papan yang paling terang di belakangnya. */
    @Test
    fun thePanelTextIsReadableOverTheBrightestThingsOnTheBoard() {
        val behind = listOf(Game.SNAKE_HEAD, Game.SNAKE_TAIL, Game.SNAKE_DEAD, Retro.AMBER, Game.BOARD_TOP, 0xFFFFFFFFL)
        for (x in behind) {
            val panel = BackdropDim.darken(x, Game.PANEL_ALPHA)
            assertText("judul/%08X".format(x), white, panel)
            assertText("keterangan/%08X".format(x), Retro.CREAM_TEXT, panel)
            assertText("REKOR BARU/%08X".format(x), Retro.AMBER, panel)
        }
    }

    @Test
    fun theSnakeAndTheFoodStandOutFromTheBoard() {
        for (b in boards) {
            assertShape("kepala", Game.SNAKE_HEAD, b)
            assertShape("ekor", Game.SNAKE_TAIL, b)
            assertShape("ular mati", Game.SNAKE_DEAD, b)
            assertShape("makanan", Retro.AMBER, b)
        }
    }

    @Test
    fun theDeadSnakeIsNotTheSameColorAsTheLiveOne() {
        assertTrue(Game.SNAKE_DEAD != Game.SNAKE_HEAD && Game.SNAKE_DEAD != Game.SNAKE_TAIL)
        // merah vs hijau: bedanya juga terlihat tanpa warna, lewat mata silang & papan yang bergetar (bukan warna saja)
        assertTrue(ColorMath.luminance(Game.SNAKE_HEAD) > ColorMath.luminance(Game.SNAKE_TAIL))
    }

    @Test
    fun everyBrickRowThePaddleAndTheBallStandOutFromTheBoard() {
        for (b in boards) {
            Game.BRICK_ROWS.forEachIndexed { row, color -> assertShape("bata baris $row/%08X".format(b), color, b) }
            assertShape("palet/%08X".format(b), Game.PADDLE, b)
            assertShape("bola/%08X".format(b), Game.BALL, b)
        }
    }

    /** Panel tengah (JEDA, GAME OVER) menutupi dinding bata: tulisannya harus terbaca di atas warna bata mana pun. */
    @Test
    fun thePanelTextIsReadableOverEveryBrickColor() {
        for (x in Game.BRICK_ROWS + Game.PADDLE + Game.BALL) {
            val panel = BackdropDim.darken(x, Game.PANEL_ALPHA)
            assertText("judul/%08X".format(x), white, panel)
            assertText("keterangan/%08X".format(x), Retro.CREAM_TEXT, panel)
            assertText("REKOR BARU/%08X".format(x), Retro.AMBER, panel)
        }
    }

    @Test
    fun theBrickRowsHaveSevenDistinctColorsAndAllAreOpaque() {
        assertTrue(Game.BRICK_ROWS.size >= 7)
        assertTrue(Game.BRICK_ROWS.toSet().size == Game.BRICK_ROWS.size)
        assertTrue(Game.BRICK_ROWS.all { it ushr 24 == 0xFFL })
        // Palet dan bola berbeda dari bata supaya tidak hilang di depan dinding
        assertTrue(Game.PADDLE !in Game.BRICK_ROWS && Game.BALL !in Game.BRICK_ROWS && Game.PADDLE != Game.BALL)
    }
}
