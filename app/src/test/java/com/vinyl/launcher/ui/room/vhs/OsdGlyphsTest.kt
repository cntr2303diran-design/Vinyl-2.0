package com.vinyl.launcher.ui.room.vhs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OsdGlyphsTest {
    @Test
    fun everyGlyphIsSmallAndNotEmpty() {
        for (g in OsdGlyphs.ALL) {
            assertTrue(g.width in 3..11 && g.height in 3..11)
            assertTrue(g.pixels().isNotEmpty())
        }
    }

    @Test
    fun wifiLevelsLightTheRightRows() {
        val g = OsdGlyphs.WIFI
        assertEquals(4, g.levels)
        // Titik bawah = grup 0, busur terluar = grup 3: setiap baris yang menyala termasuk satu grup
        for ((_, y) in g.pixels()) assertTrue("baris $y tanpa grup", g.groupOf(y) >= 0)
        assertEquals(0, g.groupOf(6))
        assertEquals(3, g.groupOf(0))
    }

    @Test
    fun outOfRangeIsOff() {
        val g = OsdGlyphs.BELL
        assertTrue(!g.isOn(-1, 0) && !g.isOn(0, -1) && !g.isOn(g.width, 0) && !g.isOn(0, g.height))
    }
}
