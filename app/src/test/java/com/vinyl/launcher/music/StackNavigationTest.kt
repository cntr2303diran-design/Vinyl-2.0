package com.vinyl.launcher.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class StackNavigationTest {
    private val cover = 400f

    // ---- dragTo ----

    @Test fun swipingUpAdvancesTheStack() {
        assertTrue(StackNavigation.dragTo(2f, -120f, cover, 10) > 2f)
    }

    @Test fun swipingDownGoesBack() {
        assertTrue(StackNavigation.dragTo(2f, 120f, cover, 10) < 2f)
    }

    @Test fun movingTheFingerSixTenthsOfACoverMovesExactlyOneCover() {
        assertEquals(3f, StackNavigation.dragTo(2f, -cover * StackNavigation.DRAG_PER_COVER, cover, 10), 1e-4f)
    }

    @Test fun dragIsClampedToTheFirstAndLastCover() {
        assertEquals(0f, StackNavigation.dragTo(0.2f, 5000f, cover, 10), 0f)
        assertEquals(9f, StackNavigation.dragTo(8.9f, -5000f, cover, 10), 0f)
    }

    @Test fun dragWithBadInputKeepsThePosition() {
        assertEquals(2f, StackNavigation.dragTo(2f, Float.NaN, cover, 10), 0f)
        assertEquals(2f, StackNavigation.dragTo(2f, -50f, 0f, 10), 0f)
        assertEquals(2f, StackNavigation.dragTo(2f, -50f, -3f, 10), 0f)
        assertEquals(2f, StackNavigation.dragTo(2f, -50f, Float.NaN, 10), 0f)
    }

    @Test fun positionOutsideRangeIsPulledBackInOnBadInput() {
        assertEquals(9f, StackNavigation.dragTo(40f, Float.NaN, cover, 10), 0f)
        assertEquals(0f, StackNavigation.dragTo(-3f, Float.NaN, cover, 10), 0f)
    }

    // ---- settleTarget ----

    @Test fun slowReleaseLandsOnTheNearestCover() {
        assertEquals(3, StackNavigation.settleTarget(2.6f, 0f, cover, 10))
        assertEquals(2, StackNavigation.settleTarget(2.4f, 0f, cover, 10))
    }

    @Test fun flickUpGoesForwardAndFlickDownGoesBack() {
        assertTrue(StackNavigation.settleTarget(2f, -3000f, cover, 10) > 2)
        assertTrue(StackNavigation.settleTarget(2f, 3000f, cover, 10) < 2)
    }

    @Test fun aSingleFlickIsLimited() {
        val t = StackNavigation.settleTarget(10f, -1_000_000f, cover, 100)
        assertEquals(10 + StackNavigation.MAX_FLICK_COVERS.toInt(), t)
        val b = StackNavigation.settleTarget(50f, 1_000_000f, cover, 100)
        assertEquals(50 - StackNavigation.MAX_FLICK_COVERS.toInt(), b)
    }

    @Test fun settleNeverLeavesTheList() {
        assertEquals(0, StackNavigation.settleTarget(0f, 9000f, cover, 5))
        assertEquals(4, StackNavigation.settleTarget(4f, -9000f, cover, 5))
        assertEquals(0, StackNavigation.settleTarget(3f, -9000f, cover, 1))
    }

    @Test fun settleWithBadInputFallsBackToNearest() {
        assertEquals(3, StackNavigation.settleTarget(2.7f, Float.NaN, cover, 10))
        assertEquals(3, StackNavigation.settleTarget(2.7f, -900f, 0f, 10))
    }

    @Test fun emptyOrSingleListAlwaysTargetsZero() {
        assertEquals(0, StackNavigation.settleTarget(0f, -5000f, cover, 0))
        assertEquals(0, StackNavigation.settleTarget(0f, -5000f, cover, 1))
        assertEquals(0f, StackNavigation.dragTo(0f, -200f, cover, 0), 0f)
        assertEquals(0, StackNavigation.focusOf(5.5f, 0))
    }

    @Test fun randomGesturesAlwaysStayInRange() {
        val rnd = Random(77)
        repeat(5000) {
            val count = rnd.nextInt(300)
            val pos = rnd.nextFloat() * (count.coerceAtLeast(1)) - 2f
            val v = (rnd.nextFloat() - 0.5f) * 40_000f
            val d = (rnd.nextFloat() - 0.5f) * 2_000f
            val t = StackNavigation.settleTarget(pos, v, cover, count)
            assertTrue("t=$t count=$count", t in 0..(count - 1).coerceAtLeast(0))
            val p = StackNavigation.dragTo(pos, d, cover, count)
            assertTrue("p=$p count=$count", p >= 0f && p <= (count - 1).coerceAtLeast(0).toFloat())
        }
    }

    // ---- focusOf ----

    @Test fun focusRoundsToTheNearestCover() {
        assertEquals(2, StackNavigation.focusOf(2.49f, 10))
        assertEquals(3, StackNavigation.focusOf(2.5f, 10))
        assertEquals(9, StackNavigation.focusOf(99f, 10))
        assertEquals(0, StackNavigation.focusOf(-4f, 10))
    }

    // ---- QueuePlan ----

    @Test fun onlyRealQueueIdsArePlayable() {
        assertTrue(QueuePlan.isPlayable(0))
        assertTrue(QueuePlan.isPlayable(42))
        assertEquals(false, QueuePlan.isPlayable(QueuePlan.UNKNOWN_ID))
        assertEquals(false, QueuePlan.isPlayable(-7))
    }

    @Test fun queueStartsOneTrackBeforeTheCurrentOne() {
        assertEquals(4, QueuePlan.scrollTarget(5, 20))
        assertEquals(0, QueuePlan.scrollTarget(0, 20))
        assertEquals(0, QueuePlan.scrollTarget(1, 20))
    }

    @Test fun noCurrentTrackMeansNoAutoScroll() {
        assertNull(QueuePlan.scrollTarget(-1, 20))
        assertNull(QueuePlan.scrollTarget(20, 20))
        assertNull(QueuePlan.scrollTarget(0, 0))
    }
}
