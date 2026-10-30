package com.vinyl.launcher.ui.room.stage

import androidx.compose.ui.graphics.Color
import com.vinyl.launcher.data.RoomEra
import com.vinyl.launcher.data.RoomTone
import com.vinyl.launcher.ui.theme.Tokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StagePaletteEraTest {
    @Test
    fun defaultEraIsExactlyTheOldPalette() {
        val dim = stagePalette(RoomTone.DIM, null)
        assertEquals(Color(Tokens.Vhs.WALL_LIT), dim.wallLit)
        assertEquals(Color(Tokens.Vhs.WOOD_MID), dim.woodMid)
        assertEquals(RoomEra.DEFAULT, dim.era)
        assertEquals(dim, stagePalette(RoomTone.DIM, null, RoomEra.DEFAULT))
        val light = stagePalette(RoomTone.LIGHT, null)
        assertEquals(Color(0xFFE9E5DD), light.wallLit)
    }

    @Test
    fun anEraChangesWallFloorAndFurnitureAndCarriesItsDecoration() {
        for (era in RoomEra.entries.filter { it != RoomEra.DEFAULT }) for (tone in RoomTone.entries) {
            val p = stagePalette(tone, null, era)
            val base = stagePalette(tone, null)
            assertEquals(era, p.era)
            assertNotEquals("$era/$tone dinding", base.wallLit, p.wallLit)
            assertNotEquals("$era/$tone meja", base.woodMid, p.woodMid)
            assertNotEquals("$era/$tone lantai", base.floor, p.floor)
        }
    }

    @Test
    fun aWallColorChosenByTheUserBeatsTheEraWallButTheFurnitureStaysInEra() {
        val mine = Color(0xFF336699)
        val p = stagePalette(RoomTone.DIM, mine, RoomEra.SEVENTIES)
        val era = Tokens.Era.room(RoomEra.SEVENTIES, true)!!
        assertEquals(mine, p.wallLit)
        assertEquals(Color(era.woodMid), p.woodMid)
        assertEquals("hiasan era tidak digambar di atas dinding pilihan pengguna", RoomEra.DEFAULT, p.era)
    }

    @Test
    fun aGradientWallAlsoSuppressesTheDecoration() {
        val p = stagePalette(RoomTone.DIM, null, RoomEra.EIGHTIES, userWall = true)
        assertEquals(RoomEra.DEFAULT, p.era)
        assertEquals(Color(Tokens.Era.room(RoomEra.EIGHTIES, true)!!.woodMid), p.woodMid)
    }
}
