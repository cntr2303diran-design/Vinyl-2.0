package com.vinyl.launcher.ui.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Jalur tanpa animasi pada [PagePager]: mengikuti jari, dijepit di ujung, keputusan saat dilepas, dan perpindahan lewat kode. */
class PagePagerTest {
    private fun pager(initial: Int = 1, width: Int = 1000) = PagePager(initial, CoroutineScope(Job())).also { it.widthPx = width }

    @Test
    fun theFingerMovesThePageAndTheEndsClamp() {
        val p = pager()
        p.startDrag()
        assertTrue(p.dragging)
        p.dragByPx(250f) // jari ke kanan → halaman kiri masuk → posisi mengecil
        assertEquals(0.75f, p.position, 0.0001f)
        p.dragByPx(5000f)
        assertEquals(0f, p.position, 0f)
        p.dragByPx(-9000f)
        assertEquals(2f, p.position, 0f)
    }

    @Test
    fun releasingPastTheThresholdSettlesOnTheNextPage() {
        val p = pager()
        p.startDrag()
        p.dragByPx(-300f) // posisi 1,3
        assertEquals(2, p.finishDrag(0f, animated = false))
        assertEquals(2f, p.position, 0f)
        assertEquals(2, p.targetIndex)
        assertFalse(p.moving)
    }

    @Test
    fun aShortDragSnapsBackToWhereItStarted() {
        val p = pager()
        p.startDrag()
        p.dragByPx(-100f)
        assertEquals(1, p.finishDrag(0f, animated = false))
        assertEquals(1f, p.position, 0f)
    }

    @Test
    fun aFastFlickCarriesAShortDrag() {
        val p = pager()
        p.startDrag()
        p.dragByPx(-60f)
        // Jari melaju ke kiri 2500 px/dtk = 2,5 halaman/dtk → proyeksi 60 + 250 px
        assertEquals(2, p.finishDrag(-2500f, animated = false))
        val q = pager()
        q.startDrag()
        q.dragByPx(60f)
        assertEquals(0, q.finishDrag(2500f, animated = false))
    }

    @Test
    fun codeDrivenMovesJumpWhenNotAnimatedAndAreIgnoredWhileDragging() {
        val p = pager()
        p.goTo(2, animated = false)
        assertEquals(2f, p.position, 0f)
        assertEquals(2, p.targetIndex)
        p.startDrag()
        p.dragByPx(100f)
        p.goTo(0, animated = false) // sedang diseret: jari yang menentukan
        assertEquals(1.9f, p.position, 0.0001f)
        assertEquals(2, p.targetIndex)
    }

    @Test
    fun withoutAKnownWidthNothingMoves() {
        val p = pager(width = 0)
        p.startDrag()
        p.dragByPx(300f)
        assertEquals(1f, p.position, 0f)
        assertEquals(1, p.finishDrag(0f, animated = false))
    }

    @Test
    fun badNumbersAreIgnored() {
        val p = pager()
        p.startDrag()
        p.dragByPx(Float.NaN)
        assertEquals(1f, p.position, 0f)
        assertEquals(1, p.finishDrag(Float.NaN, animated = false))
    }

    @Test
    fun grabbingStopsNothingWhenIdleAndStartsFromTheTargetPage() {
        val p = pager(initial = 0)
        p.startDrag()
        assertEquals(0, p.targetIndex)
        p.dragByPx(-400f)
        assertEquals(1, p.finishDrag(0f, animated = false))
    }
}
