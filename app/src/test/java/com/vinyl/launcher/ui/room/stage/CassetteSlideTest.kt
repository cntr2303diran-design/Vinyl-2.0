package com.vinyl.launcher.ui.room.stage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CassetteSlideTest {
    private val screens = listOf(
        320f to 640f, 360f to 780f, 411f to 891f, 800f to 1280f,
        640f to 360f, 915f to 412f, 1024f to 768f, 1312f to 816f, 2000f to 900f,
    )

    @Test
    fun theSlideStartsAtRestAndEndsInsideTheDeckGap() {
        assertEquals(0f, CassetteSlide.ease(0f), 0f)
        assertEquals(1f, CassetteSlide.ease(1f), 0f)
        assertEquals(1f, CassetteSlide.scale(0f), 0f)
        assertEquals(CassetteSlide.MIN_SCALE, CassetteSlide.scale(1f), 0.0001f)
        assertEquals(1f, CassetteSlide.alpha(0f), 0f)
        assertEquals(0f, CassetteSlide.alpha(1f), 0.0001f)
    }

    @Test
    fun theCurvesNeverGoBackwardsOrLeaveTheirRange() {
        var prevEase = -1f; var prevScale = 2f; var prevAlpha = 2f
        for (i in 0..100) {
            val p = i / 100f
            val e = CassetteSlide.ease(p)
            assertTrue(e in 0f..1f && e >= prevEase); prevEase = e
            val s = CassetteSlide.scale(e)
            assertTrue(s in CassetteSlide.MIN_SCALE..1f && s <= prevScale); prevScale = s
            val a = CassetteSlide.alpha(e)
            assertTrue(a in 0f..1f && a <= prevAlpha); prevAlpha = a
        }
    }

    @Test
    fun outOfRangeAndNotANumberAreHarmless() {
        assertEquals(0f, CassetteSlide.ease(Float.NaN), 0f)
        assertEquals(1f, CassetteSlide.ease(5f), 0f)
        assertEquals(0f, CassetteSlide.ease(-5f), 0f)
        assertEquals(1f, CassetteSlide.scale(-3f), 0f)
        assertEquals(CassetteSlide.MIN_SCALE, CassetteSlide.scale(9f), 0.0001f)
    }

    @Test
    fun theTargetIsTheDeckOnEveryScreen() {
        for ((w, h) in screens) {
            val f = StageLayout.compute(w, h)
            val t = CassetteSlide.target(f)
            assertEquals("w=$w h=$h", f.deck.centerX, f.cassetteBounds.centerX + t.dx, 0.001f)
            assertEquals("w=$w h=$h", f.deck.centerY, f.cassetteBounds.centerY + t.dy, 0.001f)
            // Deck ada di bawah kaset (rak meja di bawah papan atas tempat kaset berdiri)
            assertTrue("w=$w h=$h dy=${t.dy}", t.dy > 0f)
        }
    }
}
