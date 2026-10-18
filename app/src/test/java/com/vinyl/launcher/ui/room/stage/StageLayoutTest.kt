package com.vinyl.launcher.ui.room.stage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class StageLayoutTest {

    /** Ukuran layar nyata (dp): ponsel kecil/besar tegak & mendatar, lipat, tablet. */
    private val screens = listOf(
        320f to 640f, 360f to 780f, 393f to 852f, 411f to 891f, 480f to 1040f, 673f to 841f, 800f to 1280f,
        640f to 360f, 780f to 360f, 915f to 412f, 852f to 393f, 1024f to 768f, 1280f to 800f, 1312f to 816f, 1366f to 1024f,
        2000f to 900f, 600f to 960f,
    )
    private val sizes = listOf(0.5f, 0.7f, 0.85f, 1f, Float.NaN, -3f, 9f)

    @Test
    fun everyInteractivePartStaysOnScreenBetweenTheOsdBands() {
        for ((w, h) in screens) for (s in sizes) {
            val f = StageLayout.compute(w, h, s)
            val tag = "w=$w h=$h s=$s $f"
            assertTrue(tag, f.tvFront.inside(w, h))
            assertTrue(tag, f.deck.inside(w, h))
            assertTrue(tag, f.cassetteBounds.inside(w, h, eps = 2f))
            // OSD (jam/baterai di atas, tanggal/penghitung di bawah) tidak menutupi TV, deck, atau kaset
            assertTrue(tag, f.tvFront.top >= f.osdTop - 0.5f)
            assertTrue(tag, f.deck.bottom <= f.osdBottom + 0.5f)
            assertTrue(tag, f.cassetteBounds.bottom <= f.osdBottom + 2f)
        }
    }

    @Test
    fun onShortLandscapeScreensTheTvMayRiseOnlyBetweenTheOsdCornerColumns() {
        var relaxedSeen = false
        for ((w, h) in screens) for (s in sizes) {
            val f = StageLayout.compute(w, h, s)
            if (f.tvFront.top < StageLayout.OSD_TOP - 0.5f) {
                relaxedSeen = true
                val tag = "w=$w h=$h s=$s"
                assertTrue(tag, f.landscape && h < 560f)
                val tvLeft = minOf(f.tvFront.left, SRect.around(f.tv.corners().map(f.camera::project)).left)
                assertTrue(tag, tvLeft >= StageLayout.OSD_CORNER_W)
                assertTrue(tag, f.cassetteBounds.right <= w - StageLayout.OSD_CORNER_W)
            }
        }
        assertTrue("ponsel mendatar seharusnya memakai ruang pita atas", relaxedSeen)
    }

    @Test
    fun deckIsBigEnoughForFourTouchTargetsAndSitsInsideTheStand() {
        for ((w, h) in screens) {
            val f = StageLayout.compute(w, h)
            val tag = "w=$w h=$h"
            assertTrue(tag, f.deck.height >= StageLayout.DECK_MIN_H - 0.5f)
            assertTrue(tag, f.deck.width >= minOf(StageLayout.DECK_MIN_W, w - 2 * StageLayout.SIDE) - 0.5f)
            assertTrue(tag, f.shelf.contains(f.deck))
            assertTrue(tag, f.standFront.contains(f.shelf))
            assertTrue(tag, f.deck.top > f.tvFront.bottom)
        }
    }

    @Test
    fun partsDoNotOverlapEachOther() {
        for ((w, h) in screens) for (s in sizes) {
            val f = StageLayout.compute(w, h, s)
            val tag = "w=$w h=$h s=$s"
            assertFalse(tag, f.cassetteBounds.overlaps(f.deck))
            // Kaset berdiri di samping TV, bukan di depan layar
            assertTrue(tag, f.cassetteBounds.left >= f.tvFront.right - 1f)
            assertTrue(tag, f.cassetteBounds.width >= 48f - 0.01f && f.cassetteBounds.height >= 48f - 0.01f)
        }
    }

    @Test
    fun tvKeepsItsProportionsAndIsTheLargestObject() {
        for ((w, h) in screens) {
            val f = StageLayout.compute(w, h)
            assertEquals(StageLayout.TV_W / StageLayout.TV_H, f.tvFront.width / f.tvFront.height, 0.01f)
            assertTrue(f.tvFront.width > f.cassetteBounds.width * 2f)
            // Ukuran wajar: TV mengisi bagian berarti dari layar
            assertTrue("w=$w h=$h tv=${f.tvFront}", f.tvFront.width >= minOf(w, h) * 0.3f)
        }
    }

    @Test
    fun projectionHangsBehindAndAboveTheTvWithItsTopEdgeOnScreen() {
        for ((w, h) in screens) for (p in listOf(0.6f, 1f, 1.2f, Float.NaN)) {
            val f = StageLayout.compute(w, h, projectorSize = p)
            val tag = "w=$w h=$h p=$p proj=${f.projection} tv=${f.tvFront}"
            assertTrue(tag, f.projection.top < f.tvFront.top)
            assertTrue(tag, abs(f.projection.centerX - f.tvFront.centerX) < f.tvFront.width * 0.2f)
            // Bingkai cahaya terbaca utuh di atas: tepi atas & samping di dalam layar
            assertTrue(tag, f.projection.top >= 0f && f.projection.left >= 0f && f.projection.right <= w)
            // Lebih lebar dari TV sehingga cahayanya terlihat di kedua sisi
            assertTrue(tag, f.projection.width > f.tvFront.width)
            assertTrue(tag, f.floorLine > f.tvFront.bottom - f.tvFront.height)
        }
    }

    @Test
    fun projectorSizeSettingGrowsAndShrinksThePicture() {
        val small = StageLayout.compute(411f, 891f, projectorSize = 0.6f).projection
        val big = StageLayout.compute(411f, 891f, projectorSize = 1.2f).projection
        assertTrue(small.width < big.width)
    }

    @Test
    fun consoleHasASlabAnOpenShelfAndSpeakerGrillesThatDoNotOverlap() {
        for ((w, h) in screens) {
            val f = StageLayout.compute(w, h)
            val tag = "w=$w h=$h"
            assertTrue(tag, f.slabEdge.bottom <= f.shelf.top + 0.5f)
            assertTrue(tag, f.shelf.bottom <= f.plinth.top + 0.5f)
            f.grilleLeft?.let { assertTrue(tag, it.right <= f.shelf.left && it.top >= f.slabEdge.bottom) }
            f.grilleRight?.let { assertTrue(tag, it.left >= f.shelf.right && it.top >= f.slabEdge.bottom) }
            assertTrue(tag, f.baseboardTop < f.floorLine)
        }
        // Di tablet mendatar kedua kisi speaker muat
        val t = StageLayout.compute(1280f, 800f)
        assertTrue(t.grilleLeft != null && t.grilleRight != null)
    }

    @Test
    fun theTvCannotBeMovedOnlyResized() {
        val big = StageLayout.compute(1280f, 800f, 1f)
        val small = StageLayout.compute(1280f, 800f, 0.5f)
        assertTrue(small.tvFront.width < big.tvFront.width)
        // Ukuran di atas rancangan tidak memperbesar lagi (tidak pernah keluar layar)
        assertEquals(StageLayout.compute(1280f, 800f, 0.85f).tvFront, StageLayout.compute(1280f, 800f, 9f).tvFront)
        // Pusat susunan tetap di tengah area, berapa pun ukurannya
        assertEquals(big.tvFront.centerX, small.tvFront.centerX, 40f)
    }

    @Test
    fun sideFacesAreVisibleSoTheSceneReadsAs3d() {
        val f = StageLayout.compute(1280f, 800f)
        val e = f.camera.eye
        // Kamera di kiri atas: atap meja, sisi kiri TV, dan muka depan kaset terlihat
        assertTrue(f.stand.visible(Face.TOP, e))
        assertTrue(f.tv.visible(Face.LEFT, e))
        assertTrue(f.tv.visible(Face.FRONT, e))
        assertTrue(f.cassette.visible(Face.FRONT, e))
        assertFalse(f.cassette.visible(Face.BACK, e))
    }

    @Test
    fun frontFacesProjectToAxisAlignedRectangles() {
        val f = StageLayout.compute(411f, 891f)
        val q = f.tv.quad(Face.FRONT).map(f.camera::project)
        assertEquals(q[0].y, q[1].y, 0.01f)
        assertEquals(q[1].x, q[2].x, 0.01f)
        assertEquals(q[2].y, q[3].y, 0.01f)
        assertEquals(q[3].x, q[0].x, 0.01f)
    }

    @Test
    fun systemBarsAndDockPushTheStageAwayFromTheEdges() {
        val plain = StageLayout.compute(411f, 891f)
        val inset = StageLayout.compute(411f, 891f, topInset = 30f, bottomInset = 48f)
        assertEquals(plain.osdTop + 30f, inset.osdTop, 0.01f)
        assertEquals(plain.osdBottom - 48f, inset.osdBottom, 0.01f)
        assertTrue(inset.deck.bottom <= inset.osdBottom + 0.5f)
        assertTrue(inset.tvFront.top >= inset.osdTop - 0.5f)
    }

    @Test
    fun unprojectIsTheInverseOfProjectOnAPlane() {
        val f = StageLayout.compute(1280f, 800f)
        val p = V3(12.5f, 33f, StageLayout.STAND_FRONT_Z)
        val s = f.camera.project(p)
        assertEquals(p.x, f.camera.unprojectX(s.x, p.z), 0.001f)
        assertEquals(p.y, f.camera.unprojectY(s.y, p.z), 0.001f)
    }

    /** Bukan pengujian: cetak geometri untuk diperiksa visual saat merancang (dijalankan bersama tes lain, murah). */
    @Test
    fun dumpForDesignReview() {
        for ((w, h) in listOf(1312f to 816f, 411f to 891f, 360f to 780f, 915f to 412f, 673f to 841f, 640f to 360f, 852f to 393f)) {
            val f = StageLayout.compute(w, h)
            val c = f.cassette.corners().map(f.camera::project)
            println("STAGE w=$w h=$h tv=${f.tvFront} deck=${f.deck} shelf=${f.shelf} stand=${f.standFront} slab=${f.slabEdge} gl=${f.grilleLeft} gr=${f.grilleRight} cas=${f.cassetteBounds} proj=${f.projection} floor=${f.floorLine} f=${f.camera.focal} cx=${f.camera.cx} cy=${f.camera.cy}")
            println("CAS " + c.joinToString(" ") { "%.1f,%.1f".format(it.x, it.y) })
        }
    }
}
