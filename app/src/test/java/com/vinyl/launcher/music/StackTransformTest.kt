package com.vinyl.launcher.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class StackTransformTest {

    private val eps = 1e-3f

    @Test fun focusedCardIsIdentity() {
        val c = StackTransform.of(0f)
        assertEquals(0f, c.slotY, 0f)
        assertEquals(1f, c.scale, 0f)
        assertEquals(0f, c.rotationXDeg, 0f)
        assertEquals(1f, c.alpha, 0f)
    }

    @Test fun everyPropertyIsContinuousThroughTheFocus() {
        val a = StackTransform.of(-1e-4f)
        val b = StackTransform.of(1e-4f)
        assertTrue(abs(a.slotY - b.slotY) < eps)
        assertTrue(abs(a.scale - b.scale) < eps)
        assertTrue(abs(a.rotationXDeg - b.rotationXDeg) < 0.01f)
        assertTrue(abs(a.alpha - b.alpha) < eps)
        assertTrue("z harus tetap di sekitar fokus", abs(a.z - b.z) < 1.1f)
    }

    @Test fun upcomingCardsRecedeBehindAndPeekAbove() {
        var prev = StackTransform.of(0f)
        for (o in listOf(0.5f, 1f, 2f, 3f)) {
            val c = StackTransform.of(o)
            assertTrue("o=$o harus di atas fokus", c.slotY < prev.slotY)
            assertTrue("o=$o harus lebih kecil", c.scale < prev.scale)
            assertTrue("o=$o harus lebih belakang", c.z < prev.z)
            assertTrue("o=$o harus lebih miring", c.rotationXDeg > prev.rotationXDeg)
            assertTrue("o=$o tidak boleh lebih pekat", c.alpha <= prev.alpha)
            prev = c
        }
    }

    @Test fun passedCardsFlyUpInFrontAndFade() {
        val focus = StackTransform.of(0f)
        var prev = focus
        for (o in listOf(-0.25f, -0.5f, -0.75f, -1f)) {
            val c = StackTransform.of(o)
            assertTrue("o=$o makin ke atas", c.slotY < prev.slotY)
            assertTrue("o=$o makin transparan", c.alpha < prev.alpha)
            assertTrue("o=$o di depan fokus", c.z > focus.z)
            prev = c
        }
        assertEquals(0f, StackTransform.of(-1f).alpha, 1e-6f)
        assertEquals(0f, StackTransform.of(-5f).alpha, 0f)
    }

    @Test fun alphaAndScaleAlwaysStayValid() {
        var o = -10f
        while (o <= 12f) {
            val c = StackTransform.of(o)
            assertTrue("alpha o=$o = ${c.alpha}", c.alpha in 0f..1f)
            assertTrue("skala o=$o = ${c.scale}", c.scale > 0f)
            assertFalse(c.slotY.isNaN() || c.rotationXDeg.isNaN() || c.z.isNaN())
            o += 0.05f
        }
    }

    @Test fun farthestLayerFadesInWithoutPopping() {
        val edge = StackTransform.VISIBLE_BEHIND.toFloat()
        val before = StackTransform.of(edge - 1e-3f).alpha
        val after = StackTransform.of(edge + 1e-3f).alpha
        assertTrue("lompatan alpha ${before - after}", abs(before - after) < 0.02f)
        assertEquals(0f, StackTransform.of(edge + 1f).alpha, 1e-6f)
        assertEquals(0f, StackTransform.of(edge + 5f).alpha, 0f)
    }

    @Test fun alphaNeverIncreasesGoingDeeper() {
        var prev = 2f
        var o = 0f
        while (o <= 6f) {
            val a = StackTransform.of(o).alpha
            assertTrue("o=$o", a <= prev + 1e-6f)
            prev = a; o += 0.1f
        }
    }

    @Test fun hiddenCardsAreSkippedButVisibleOnesAreNot() {
        assertFalse(StackTransform.isHidden(0f))
        assertFalse(StackTransform.isHidden(1f))
        assertFalse(StackTransform.isHidden(2.5f))
        assertFalse(StackTransform.isHidden(-0.5f))
        assertTrue(StackTransform.isHidden(StackTransform.VISIBLE_BEHIND + 1.5f))
        assertTrue(StackTransform.isHidden(-1.5f))
        assertTrue(StackTransform.isHidden(-100f))
    }
}
