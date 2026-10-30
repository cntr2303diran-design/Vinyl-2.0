package com.vinyl.launcher.ui.room.stage

import com.vinyl.launcher.data.LightLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectorLightTest {
    private val screens = listOf(
        320f to 640f, 360f to 780f, 411f to 891f, 800f to 1280f,
        640f to 360f, 915f to 412f, 1024f to 768f, 1312f to 816f, 2000f to 900f,
    )
    private val phases = listOf(0f, 0.13f, 0.5f, 0.99f, 1f, 2.7f, -0.4f, Float.NaN, Float.POSITIVE_INFINITY)

    private fun cross(a: P2, b: P2, p: P2) = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)

    private fun inside(a: P2, b: P2, c: P2, p: P2, eps: Float = 0.01f): Boolean {
        val d1 = cross(a, b, p); val d2 = cross(b, c, p); val d3 = cross(c, a, p)
        val neg = d1 < -eps || d2 < -eps || d3 < -eps
        val pos = d1 > eps || d2 > eps || d3 > eps
        return !(neg && pos)
    }

    @Test
    fun theBeamStartsBelowTheScreenAndEndsAtTheTopEdgeOfTheProjection() {
        for ((w, h) in screens) {
            val f = StageLayout.compute(w, h)
            val b = ProjectorLight.beam(f)
            val tag = "w=$w h=$h"
            assertTrue(tag, b.apex.y > f.height)
            assertEquals(tag, f.projection.top, b.topLeft.y, 0.001f)
            assertEquals(tag, f.projection.left, b.topLeft.x, 0.001f)
            assertEquals(tag, f.projection.right, b.topRight.x, 0.001f)
            assertTrue(tag, b.topLeft.y < b.apex.y)
        }
    }

    @Test
    fun everyDustMoteStaysInsideTheBeamAtAnyPhase() {
        for ((w, h) in screens) {
            val f = StageLayout.compute(w, h)
            val b = ProjectorLight.beam(f)
            for (m in ProjectorLight.motes(ProjectorLight.MOTES_NORMAL)) for (ph in phases) {
                val p = ProjectorLight.position(b, m, ph)
                assertTrue("w=$w h=$h ph=$ph $p", p.x.isFinite() && p.y.isFinite())
                assertTrue("w=$w h=$h ph=$ph $p", inside(b.apex, b.topLeft, b.topRight, p))
                val tw = ProjectorLight.twinkle(m, ph)
                assertTrue("kedipan $tw", tw in 0.34f..1.01f)
            }
        }
    }

    @Test
    fun theMotesAreTheSameOnEveryLaunch() {
        val a = ProjectorLight.motes(ProjectorLight.MOTES_NORMAL)
        val b = ProjectorLight.motes(ProjectorLight.MOTES_NORMAL)
        assertEquals(a.map { it.t }, b.map { it.t })
        assertEquals(a.map { it.u }, b.map { it.u })
        assertEquals(ProjectorLight.MOTES_NORMAL, a.size)
    }

    @Test
    fun theLevelDecidesHowMuchLightAndDustThereIs() {
        assertEquals(0f, ProjectorLight.strength(LightLevel.OFF), 0f)
        assertTrue(ProjectorLight.strength(LightLevel.SOFT) in 0.1f..0.9f)
        assertEquals(1f, ProjectorLight.strength(LightLevel.NORMAL), 0f)
        assertEquals(0, ProjectorLight.moteCount(LightLevel.OFF))
        assertTrue(ProjectorLight.moteCount(LightLevel.SOFT) < ProjectorLight.moteCount(LightLevel.NORMAL))
        assertTrue(ProjectorLight.motes(1000).size <= ProjectorLight.MOTES_NORMAL)
        assertEquals(0, ProjectorLight.motes(-5).size)
    }
}
