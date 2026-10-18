package com.vinyl.launcher.ui.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneLayoutTest {
    private val ref = SceneLayout.compute(SceneLayout.REF_ASPECT, 0.85f)

    /** Angka diukur dari gambar referensi 1312 x 816. */
    @Test
    fun theReferenceCompositionIsReproducedOnATablet() {
        val tol = 0.006f
        assertEquals(413f / 1312, ref.tv.left, tol)
        assertEquals(218f / 816, ref.tv.top, 0.05f)          // atap TV (referensi menyertakan tepi atas bodi)
        assertEquals(547f / 1312, ref.tv.width, tol)
        assertEquals(625f / 816, ref.tv.bottom, tol)
        assertEquals(513f / 1312, ref.cassette.left, tol)
        assertEquals(284f / 1312, ref.cassette.width, tol)
        assertEquals(638f / 816, ref.cassette.top, tol)
        assertEquals(705f / 816, ref.controlsY, tol)
        assertEquals(401f / 1312, ref.controlsX[0], tol)
        assertEquals(848f / 1312, ref.controlsX[2], tol)
        assertEquals(909f / 1312, ref.controlsX[3], tol)
        assertEquals(778f / 816, ref.progress.top, tol)
        assertEquals(ref.cassette.left, ref.progress.left, 1e-6f)
        assertTrue(ref.landscape)
    }

    private val aspects = listOf(0.42f, 0.46f, 0.56f, 0.75f, 1.0f, 1.33f, 1.6f, 1.78f, 2.0f, 2.4f)
    private val sizes = listOf(0.3f, 0.6f, 0.85f, 1.0f, 1.2f, 9f, -2f)

    @Test
    fun everythingStaysOnScreenAndNothingOverlaps() {
        for (a in aspects) for (s in sizes) {
            val sc = SceneLayout.compute(a, s)
            val tag = "a=$a s=$s $sc"
            assertTrue(tag, sc.tv.inside() && sc.cassette.inside())
            assertTrue(tag, !sc.tv.overlaps(sc.cassette))
            assertTrue(tag, sc.cassette.top >= sc.tv.bottom)
            assertTrue(tag, sc.progress.top in 0f..1f && sc.volume.top in 0f..1f)
            assertTrue(tag, sc.controlsY in 0f..1f)
            sc.controlsX.forEach { assertTrue(tag, it in 0f..1f) }
            // Tombol tidak menimpa kaset di landscape (mengapit), di portrait berada di bawahnya
            if (sc.landscape) {
                assertTrue(tag, sc.controlsX[1] < sc.cassette.left && sc.controlsX[2] > sc.cassette.right)
            } else {
                assertTrue(tag, sc.controlsY > sc.cassette.bottom)
            }
            // TV menjaga proporsinya
            assertEquals(tag, TvGeometry.ASPECT, sc.tv.width * a / sc.tv.height, 0.02f)
        }
    }

    @Test
    fun controlsAreOrderedLeftToRight() {
        for (a in aspects) {
            val x = SceneLayout.compute(a).controlsX
            assertTrue(x.zipWithNext().all { (p, q) -> p < q })
        }
    }
}
