package com.vinyl.launcher.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameDiaryTest {
    @Test
    fun theDetailIsTheScoreAndMarksANewRecord() {
        assertEquals("Skor 23", GameDiary.detail(23, newBest = false))
        assertEquals("Skor 23 · rekor baru", GameDiary.detail(23, newBest = true))
    }

    @Test
    fun aZeroScoreIsNeverCalledARecord() {
        assertEquals("Skor 0", GameDiary.detail(0, newBest = true))
        assertEquals("Skor 0", GameDiary.detail(0, newBest = false))
    }

    @Test
    fun negativeScoresAreShownAsZero() {
        assertEquals("Skor 0", GameDiary.detail(-4, newBest = true))
    }

    @Test
    fun onlyARoundInProgressIsWorthRecordingWhenTheCassetteIsEjected() {
        assertTrue(GameDiary.worthRecordingOnEject(GamePhase.RUNNING))
        assertTrue(GameDiary.worthRecordingOnEject(GamePhase.PAUSED))
        assertFalse("papan yang belum dimulai bukan ronde", GameDiary.worthRecordingOnEject(GamePhase.READY))
        // Ronde yang sudah berakhir sudah dicatat saat berakhir: tidak dicatat dua kali
        for (p in GamePhase.entries) {
            if (p != GamePhase.RUNNING && p != GamePhase.PAUSED) assertFalse(p.name, GameDiary.worthRecordingOnEject(p))
        }
    }
}
