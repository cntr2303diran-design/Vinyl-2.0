package com.vinyl.launcher.ui.room.stage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StagePropsTest {
    private val screens = listOf(
        320f to 640f, 360f to 780f, 411f to 891f, 800f to 1280f,
        640f to 360f, 915f to 412f, 1024f to 768f, 1312f to 816f, 2000f to 900f,
    )
    private val tablets = listOf(1024f to 768f, 1312f to 816f, 2000f to 900f, 915f to 412f)

    @Test
    fun everyPlacedPropIsTouchableAndInsideTheScreen() {
        for ((w, h) in screens) {
            val f = StageLayout.compute(w, h)
            for ((prop, spot) in f.props) {
                val tag = "$prop w=$w h=$h"
                assertTrue("$tag lebar", spot.hit.width >= StageProps.MIN_TOUCH - 0.01f)
                assertTrue("$tag tinggi", spot.hit.height >= StageProps.MIN_TOUCH - 0.01f)
                assertTrue("$tag kiri", spot.hit.left >= StageLayout.SIDE - 0.01f)
                assertTrue("$tag kanan", spot.hit.right <= w - StageLayout.SIDE + 0.01f)
                assertTrue("$tag atas", spot.hit.top >= StageLayout.OSD_TOP_LEFT_BOTTOM - 0.01f)
                assertTrue("$tag bawah", spot.hit.bottom <= h)
            }
        }
    }

    @Test
    fun propsNeverOverlapEachOtherOrTheTvAndDeck() {
        for ((w, h) in screens) {
            val f = StageLayout.compute(w, h)
            val list = f.props.entries.toList()
            for (i in list.indices) {
                for (j in i + 1 until list.size) {
                    assertFalse("${list[i].key}/${list[j].key} w=$w h=$h", list[i].value.hit.overlaps(list[j].value.hit))
                }
                assertFalse("${list[i].key} vs TV w=$w h=$h", list[i].value.hit.overlaps(f.tvFront))
            }
        }
    }

    @Test
    fun tabletsAndWideLandscapeFitAllThreeProps() {
        for ((w, h) in tablets) {
            val f = StageLayout.compute(w, h)
            assertEquals("w=$w h=$h", Prop.entries.toSet(), f.props.keys)
        }
    }

    @Test
    fun placementIsDeterministic() {
        val a = StageLayout.compute(800f, 1280f).props.mapValues { it.value.hit }
        val b = StageLayout.compute(800f, 1280f).props.mapValues { it.value.hit }
        assertEquals(a, b)
    }
}
