package com.vinyl.launcher.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GamesTest {
    @Test
    fun theRegistryKnowsSnakeAndNothingElse() {
        assertTrue(Games.exists(Games.SNAKE))
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
    fun idsAreUnique() {
        assertEquals(Games.all.size, Games.all.toSet().size)
    }
}
