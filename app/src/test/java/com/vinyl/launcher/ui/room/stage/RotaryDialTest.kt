package com.vinyl.launcher.ui.room.stage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RotaryDialTest {
    @Test
    fun normWrapsAndSurvivesNonFiniteValues() {
        assertEquals(10f, RotaryDial.norm(370f), 0.001f)
        assertEquals(350f, RotaryDial.norm(-10f), 0.001f)
        assertEquals(0f, RotaryDial.norm(Float.NaN), 0f)
        assertEquals(0f, RotaryDial.norm(Float.POSITIVE_INFINITY), 0f)
    }

    @Test
    fun signedDeltaTakesTheShortestWay() {
        assertEquals(20f, RotaryDial.signedDelta(350f, 10f), 0.001f)
        assertEquals(-20f, RotaryDial.signedDelta(10f, 350f), 0.001f)
    }

    @Test
    fun angleOfPointsClockwiseFromTop() {
        assertEquals(0f, RotaryDial.angleOf(0f, 0f, 0f, -5f), 0.001f)
        assertEquals(90f, RotaryDial.angleOf(0f, 0f, 5f, 0f), 0.001f)
        assertEquals(180f, RotaryDial.angleOf(0f, 0f, 0f, 5f), 0.001f)
        assertEquals(270f, RotaryDial.angleOf(0f, 0f, -5f, 0f), 0.001f)
    }

    @Test
    fun oneTravelsLeastAndZeroTravelsMost() {
        val travels = (1..9).map { RotaryDial.travel(it) } + RotaryDial.travel(0)
        assertEquals(travels.sorted(), travels)
        assertEquals(RotaryDial.FIRST_TRAVEL, RotaryDial.travel(1), 0.001f)
        assertEquals(RotaryDial.FIRST_TRAVEL + 9 * RotaryDial.SPACING, RotaryDial.travel(0), 0.001f)
    }

    @Test
    fun holesNeverOverlapAndEachIsFoundAtItsOwnCentre() {
        assertTrue(RotaryDial.HOLE_TOL * 2 < RotaryDial.SPACING)
        for (d in 0..9) assertEquals(d, RotaryDial.digitAt(RotaryDial.holeAngle(d)))
    }

    @Test
    fun betweenHolesAndAtTheFingerStopNothingIsPicked() {
        val between = RotaryDial.holeAngle(3) + RotaryDial.SPACING / 2f
        assertNull(RotaryDial.digitAt(between))
        assertNull(RotaryDial.digitAt(RotaryDial.STOP_DEG))
        assertNotNull(RotaryDial.digitAt(RotaryDial.holeAngle(5) + 3f))
    }

    @Test
    fun advanceIsClampedBetweenZeroAndTheDigitTravel() {
        val max = RotaryDial.travel(4)
        assertEquals(0f, RotaryDial.advance(0f, 100f, 80f, 4), 0f)
        assertEquals(max, RotaryDial.advance(max - 1f, 100f, 160f, 4), 0.001f)
        assertEquals(15f, RotaryDial.advance(5f, 100f, 110f, 4), 0.001f)
        // melintasi 0/360 tetap benar
        assertEquals(20f, RotaryDial.advance(0f, 350f, 10f, 4), 0.001f)
    }

    @Test
    fun releasingEarlyDoesNotCountButReachingTheStopDoes() {
        for (d in 0..9) {
            assertFalse(RotaryDial.completes(RotaryDial.travel(d) - RotaryDial.DONE_MARGIN - 1f, d))
            assertTrue(RotaryDial.completes(RotaryDial.travel(d), d))
            assertTrue(RotaryDial.completes(RotaryDial.travel(d) - RotaryDial.DONE_MARGIN, d))
        }
    }
}
