package com.vinyl.launcher.ui.room.stage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockMathTest {
    @Test
    fun hourHandMovesSmoothlyWithTheMinutes() {
        assertEquals(0f, ClockMath.hourAngle(12, 0), 0.001f)
        assertEquals(0f, ClockMath.hourAngle(0, 0), 0.001f)
        assertEquals(90f, ClockMath.hourAngle(3, 0), 0.001f)
        assertEquals(105f, ClockMath.hourAngle(15, 30), 0.001f)
        assertEquals(359.5f, ClockMath.hourAngle(11, 59), 0.5f)
    }

    @Test
    fun minuteHandIsSixDegreesPerMinuteAndClamped() {
        assertEquals(30f, ClockMath.minuteAngle(5), 0.001f)
        assertEquals(354f, ClockMath.minuteAngle(59), 0.001f)
        assertEquals(354f, ClockMath.minuteAngle(99), 0.001f)
        assertEquals(0f, ClockMath.minuteAngle(-3), 0.001f)
    }

    @Test
    fun spokenTimeUsesTwoDigits() {
        assertEquals("Pukul 07.05", ClockMath.spoken(7, 5))
        assertEquals("Pukul 23.59", ClockMath.spoken(23, 59))
        assertEquals("Pukul 00.00", ClockMath.spoken(-1, -1))
    }

    @Test
    fun nextMinuteWaitIsAlwaysWithinOneMinute() {
        for (t in listOf(0L, 1L, 59_999L, 60_000L, 1_700_000_012_345L, -5L)) {
            val ms = ClockMath.msToNextMinute(t)
            assertTrue("t=$t ms=$ms", ms in 1L..60_000L)
            assertEquals(0L, Math.floorMod(t + ms, 60_000L))
        }
    }
}
