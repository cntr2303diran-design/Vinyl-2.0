package com.vinyl.launcher.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerPlanTest {

    private val min = 60_000L

    @Test fun durationIsClampedToTheSupportedRange() {
        assertEquals(1 * min, SleepTimerPlan.durationMs(0))
        assertEquals(1 * min, SleepTimerPlan.durationMs(-50))
        assertEquals(240 * min, SleepTimerPlan.durationMs(9999))
        assertEquals(30 * min, SleepTimerPlan.durationMs(30))
    }

    @Test fun presetsAreSortedAndInsideTheRange() {
        val p = SleepTimerPlan.PRESET_MINUTES
        assertEquals(p.sorted(), p)
        assertTrue(p.all { it in SleepTimerPlan.MIN_MINUTES..SleepTimerPlan.MAX_MINUTES })
        assertEquals(p.size, p.toSet().size)
    }

    @Test fun fadeIsThirtySecondsAtMostAndAThirdOfShortTimers() {
        assertEquals(30_000L, SleepTimerPlan.fadeMsFor(10 * min))
        assertEquals(20_000L, SleepTimerPlan.fadeMsFor(1 * min))
        assertEquals(1_000L, SleepTimerPlan.fadeMsFor(3_000))
        assertEquals(0L, SleepTimerPlan.fadeMsFor(0))
        assertEquals(0L, SleepTimerPlan.fadeMsFor(-5))
    }

    @Test fun phaseIsCountingBeforeTheFade() {
        val p = SleepTimerPlan.phaseAt(startMs = 1_000, durationMs = 10 * min, nowMs = 1_000 + 5 * min) as SleepPhase.Counting
        assertEquals(5 * min, p.remainingMs)
    }

    @Test fun fadeStartsExactlyThirtySecondsBeforeTheEndAtFullVolume() {
        val d = 10 * min
        val p = SleepTimerPlan.phaseAt(0, d, d - 30_000) as SleepPhase.Fading
        assertEquals(1f, p.volumeFraction, 1e-6f)
        assertEquals(30_000L, p.remainingMs)
        val justBefore = SleepTimerPlan.phaseAt(0, d, d - 30_001)
        assertTrue(justBefore is SleepPhase.Counting)
    }

    @Test fun fadeFractionIsLinearInTimeThenCurved() {
        val d = 10 * min
        val mid = SleepTimerPlan.phaseAt(0, d, d - 15_000) as SleepPhase.Fading
        assertEquals(0.5f, mid.volumeFraction, 1e-6f)
        assertEquals(0.25f, SleepTimerPlan.volumeFactor(mid.volumeFraction), 1e-6f)
    }

    @Test fun finishesAtTheEndAndStaysFinished() {
        val d = 5 * min
        assertTrue(SleepTimerPlan.phaseAt(0, d, d) is SleepPhase.Finished)
        assertTrue(SleepTimerPlan.phaseAt(0, d, d + 1) is SleepPhase.Finished)
        assertTrue(SleepTimerPlan.phaseAt(0, d, d * 100) is SleepPhase.Finished)
        assertTrue(SleepTimerPlan.phaseAt(0, 0, 0) is SleepPhase.Finished)
        assertTrue(SleepTimerPlan.phaseAt(0, -10, 0) is SleepPhase.Finished)
    }

    @Test fun clockGoingBackwardsNeverExtendsOrCorruptsTheTimer() {
        val p = SleepTimerPlan.phaseAt(startMs = 10_000, durationMs = 5 * min, nowMs = 2_000) as SleepPhase.Counting
        assertEquals(5 * min, p.remainingMs)
    }

    @Test fun tinyDurationsWithNoFadeNeverDivideByZero() {
        for (d in 1L..5L) for (now in 0L..6L) SleepTimerPlan.phaseAt(0, d, now)   // tidak boleh melempar
        assertTrue(SleepTimerPlan.phaseAt(0, 2, 1) is SleepPhase.Counting)
    }

    @Test fun volumeFactorEndpointsAndClamping() {
        assertEquals(1f, SleepTimerPlan.volumeFactor(1f), 0f)
        assertEquals(0f, SleepTimerPlan.volumeFactor(0f), 0f)
        assertEquals(1f, SleepTimerPlan.volumeFactor(7f), 0f)
        assertEquals(0f, SleepTimerPlan.volumeFactor(-3f), 0f)
    }

    @Test fun volumeIndexStaysInsideZeroToOriginalAndIsMonotone() {
        for (original in 0..30) {
            assertEquals(original, SleepTimerPlan.volumeIndex(original, 1f))
            assertEquals(0, SleepTimerPlan.volumeIndex(original, 0f))
            var prev = Int.MAX_VALUE
            for (step in 100 downTo 0) {
                val v = SleepTimerPlan.volumeIndex(original, step / 100f)
                assertTrue("original=$original f=${step / 100f} v=$v", v in 0..original)
                assertTrue("harus tidak naik saat memudar", v <= prev)
                prev = v
            }
        }
        assertEquals(0, SleepTimerPlan.volumeIndex(-4, 0.5f))
    }

    @Test fun fullTimelineIsCountingThenFadingThenFinishedWithFallingVolume() {
        val d = 3 * min
        val original = 12
        val kinds = StringBuilder()
        var lastFraction = 2f
        var lastIndex = Int.MAX_VALUE
        var t = 0L
        while (t <= d + 2_000) {
            when (val p = SleepTimerPlan.phaseAt(0, d, t)) {
                is SleepPhase.Counting -> kinds.append('C')
                is SleepPhase.Fading -> {
                    kinds.append('F')
                    assertTrue(p.volumeFraction < lastFraction || p.volumeFraction == lastFraction)
                    lastFraction = p.volumeFraction
                    val idx = SleepTimerPlan.volumeIndex(original, p.volumeFraction)
                    assertTrue(idx <= lastIndex)
                    lastIndex = idx
                }
                SleepPhase.Finished -> kinds.append('X')
                SleepPhase.Idle -> throw AssertionError("Idle tidak mungkin dari phaseAt")
            }
            t += SleepTimerPlan.TICK_MS
        }
        val s = kinds.toString()
        assertTrue(s, Regex("^C+F+X+$").matches(s))
        assertTrue("indeks terakhir selama pemudaran harus mendekati 0", lastIndex <= 1)
    }

    // ---- sampai lagu selesai ----

    @Test fun endOfTrackSubtractsPositionAndGuard() {
        assertEquals(200_000L - 50_000L - SleepTimerPlan.END_GUARD_MS, SleepTimerPlan.endOfTrackMs(200_000, 50_000))
        assertEquals(200_000L - SleepTimerPlan.END_GUARD_MS, SleepTimerPlan.endOfTrackMs(200_000, -10))
    }

    @Test fun endOfTrackIsNullWhenUnknownOrTooShort() {
        assertNull(SleepTimerPlan.endOfTrackMs(0, 0))
        assertNull(SleepTimerPlan.endOfTrackMs(-1, 0))
        assertNull(SleepTimerPlan.endOfTrackMs(200_000, 199_000))
        assertNull(SleepTimerPlan.endOfTrackMs(200_000, 500_000))     // posisi di luar durasi
        val edge = SleepTimerPlan.MIN_END_OF_TRACK_MS + SleepTimerPlan.END_GUARD_MS
        assertEquals(SleepTimerPlan.MIN_END_OF_TRACK_MS, SleepTimerPlan.endOfTrackMs(edge, 0))
        assertNull(SleepTimerPlan.endOfTrackMs(edge - 1, 0))
    }

    // ---- format ----

    @Test fun formatRoundsUpSoARunningTimerNeverShowsZero() {
        assertEquals("0:00", SleepTimerPlan.format(0))
        assertEquals("0:00", SleepTimerPlan.format(-500))
        assertEquals("0:01", SleepTimerPlan.format(1))
        assertEquals("0:01", SleepTimerPlan.format(1000))
        assertEquals("0:02", SleepTimerPlan.format(1001))
        assertEquals("1:00", SleepTimerPlan.format(59_001))
        assertEquals("1:00", SleepTimerPlan.format(60_000))
        assertEquals("59:59", SleepTimerPlan.format(3_599_000))
        assertEquals("1:00:00", SleepTimerPlan.format(3_600_000))
        assertEquals("4:00:00", SleepTimerPlan.format(240 * min))
    }

    // ---- pemulihan volume ----

    @Test fun restoresOnlyWhenTheVolumeIsStillWhereWeLeftIt() {
        assertEquals(10, VolumeGuardPlan.restoreTarget(original = 10, lastSet = 3, current = 3))
        assertNull("pengguna menyentuh volume → jangan timpa", VolumeGuardPlan.restoreTarget(10, 3, 6))
        assertNull("sudah sama/lebih keras dari asli → tidak ada yang dipulihkan", VolumeGuardPlan.restoreTarget(10, 10, 10))
        assertNull(VolumeGuardPlan.restoreTarget(5, 8, 8))
    }

    @Test fun detectsUserVolumeChanges() {
        assertFalse(VolumeGuardPlan.userOverrode(4, 4))
        assertTrue(VolumeGuardPlan.userOverrode(4, 5))
        assertTrue(VolumeGuardPlan.userOverrode(4, 3))
    }

    // ---- pergantian lembut ----

    @Test fun softSkipDownGoesFromOriginalToZero() {
        val o = 12
        assertEquals(o, SoftSkipPlan.downIndex(o, 0))
        assertEquals(0, SoftSkipPlan.downIndex(o, SoftSkipPlan.DOWN_MS))
        assertEquals(0, SoftSkipPlan.downIndex(o, SoftSkipPlan.DOWN_MS * 10))
        assertEquals(o, SoftSkipPlan.downIndex(o, -50))
        var prev = Int.MAX_VALUE
        var t = 0L
        while (t <= SoftSkipPlan.DOWN_MS) {
            val v = SoftSkipPlan.downIndex(o, t)
            assertTrue(v in 0..o && v <= prev)
            prev = v; t += SoftSkipPlan.STEP_MS
        }
    }

    @Test fun softSkipUpGoesFromZeroBackToExactlyTheOriginal() {
        for (o in 3..30) {
            assertEquals(0, SoftSkipPlan.upIndex(o, 0))
            assertEquals("harus kembali TEPAT ke volume pengguna", o, SoftSkipPlan.upIndex(o, SoftSkipPlan.UP_MS))
            assertEquals(o, SoftSkipPlan.upIndex(o, SoftSkipPlan.UP_MS * 3))
            var prev = -1
            var t = 0L
            while (t <= SoftSkipPlan.UP_MS) {
                val v = SoftSkipPlan.upIndex(o, t)
                assertTrue(v in 0..o && v >= prev)
                prev = v; t += SoftSkipPlan.STEP_MS
            }
        }
    }

    @Test fun softSkipNeedsEnoughVolumeSteps() {
        assertFalse(SoftSkipPlan.canFade(0))
        assertFalse(SoftSkipPlan.canFade(SoftSkipPlan.MIN_STEPS - 1))
        assertTrue(SoftSkipPlan.canFade(SoftSkipPlan.MIN_STEPS))
    }
}
