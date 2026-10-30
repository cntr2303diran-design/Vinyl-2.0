package com.vinyl.launcher.media

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioPickTest {
    @Test
    fun emptyLibraryGivesEmptyOrder() {
        assertEquals(emptyList<String>(), RadioPick.order(emptyList(), null, Random(1)))
    }

    @Test
    fun orderKeepsEverySongOnceEvenWithDuplicates() {
        val out = RadioPick.order(listOf("a", "b", "c", "a"), null, Random(7))
        assertEquals(setOf("a", "b", "c"), out.toSet())
        assertEquals(3, out.size)
    }

    @Test
    fun theLastPlayedSongIsNeverFirstWhenThereIsAChoice() {
        for (seed in 0..200) {
            val out = RadioPick.order(listOf("a", "b", "c", "d"), "a", Random(seed))
            assertNotEquals("seed=$seed", "a", out.first())
            assertEquals(4, out.size)
        }
    }

    @Test
    fun aSingleSongIsStillPlayed() {
        assertEquals(listOf("a"), RadioPick.order(listOf("a"), "a", Random(3)))
    }

    @Test
    fun sameSeedGivesSameOrderAndDifferentSeedsVary() {
        val ids = (1..20).map { "s$it" }
        assertEquals(RadioPick.order(ids, null, Random(5)), RadioPick.order(ids, null, Random(5)))
        assertTrue((0..10).map { RadioPick.order(ids, null, Random(it)) }.toSet().size > 1)
    }
}
