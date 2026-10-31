package com.vinyl.launcher.ui.room.stage

import com.vinyl.launcher.data.RoomEra
import com.vinyl.launcher.data.RoomTone
import com.vinyl.launcher.ui.theme.Tokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.sqrt

/** Era Menara pengawas: benda-bendanya usang, era lain tidak berubah, dan piala yang kusam tetap bisa dibedakan. */
class LookoutPropLookTest {
    @Test
    fun onlyTheLookoutEraHasAgedProps() {
        for (era in RoomEra.entries) for (tone in RoomTone.entries) {
            val look = propLook(stagePalette(tone, null, era))
            assertEquals("$era/$tone", era == RoomEra.LOOKOUT, look.aged)
            if (era != RoomEra.LOOKOUT) {
                assertEquals("$era/$tone bingkai bawaan", look.rim, look.frame)
                assertEquals("$era/$tone tebal bingkai", 1f, look.frameScale, 0f)
                assertEquals("$era/$tone piala mengilap", 0f, look.tarnish, 0f)
            }
        }
    }

    @Test
    fun aWallColorChosenByTheUserDoesNotTurnTheLookoutPropsBackToNew() {
        val pal = stagePalette(RoomTone.DIM, Color(0xFF123456), RoomEra.LOOKOUT)
        assertTrue(propLook(pal).aged)
        assertEquals("hiasan dinding era tidak menimpa dinding pilihan pengguna", RoomEra.DEFAULT, pal.era)
        assertEquals(RoomEra.LOOKOUT, pal.propEra)
    }

    @Test
    fun theLookoutFrameIsRoughWoodNotBrass() {
        val look = propLook(stagePalette(RoomTone.DIM, null, RoomEra.LOOKOUT))
        assertTrue("bingkai lebih tebal dari bawaan", look.frameScale > 1.4f)
        assertNotEquals(look.rim, look.frame)
        assertTrue(look.frame.red > look.frame.blue)
    }

    @Test
    fun tarnishedGoldSilverAndBronzeCupsStillLookDifferent() {
        val look = propLook(stagePalette(RoomTone.DIM, null, RoomEra.LOOKOUT))
        val dull = Color(0xFF6E6A58)
        val cups = listOf(Tokens.Props.CUP_GOLD, Tokens.Props.CUP_SILVER, Tokens.Props.CUP_BRONZE).map { lerp(Color(it), dull, look.tarnish) }
        for (i in cups.indices) for (j in i + 1 until cups.size) {
            val dr = (cups[i].red - cups[j].red) * 255
            val dg = (cups[i].green - cups[j].green) * 255
            val db = (cups[i].blue - cups[j].blue) * 255
            val dist = sqrt(dr * dr + dg * dg + db * db)
            assertTrue("piala $i dan $j terlalu mirip (jarak $dist)", dist >= 30f)
        }
        assertFalse(look.tarnish <= 0f || look.tarnish >= 0.6f)
    }
}
