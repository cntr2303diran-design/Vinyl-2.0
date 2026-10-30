package com.vinyl.launcher.ui.theme

import com.vinyl.launcher.data.RoomEra
import com.vinyl.launcher.ui.theme.Tokens.Lamp
import com.vinyl.launcher.ui.theme.Tokens.Vhs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Palet era ([Tokens.Era]): teks OSD tetap terbaca, lampu tetap terbaca, dan bentuk ruangan tetap terbedakan. */
class EraPaletteTest {
    private val eras = RoomEra.entries.filter { it != RoomEra.DEFAULT }
    private val rooms: List<Triple<String, RoomEra, EraRoom>> = eras.flatMap { era ->
        listOf(true, false).map { dim -> Triple("$era/${if (dim) "gelap" else "terang"}", era, Tokens.Era.room(era, dim)!!) }
    }

    @Test
    fun defaultHasNoEraPaletteAndEveryOtherEraHasBothTones() {
        assertNull(Tokens.Era.room(RoomEra.DEFAULT, true))
        assertNull(Tokens.Era.room(RoomEra.DEFAULT, false))
        for (era in eras) { assertNotNull(Tokens.Era.room(era, true)); assertNotNull(Tokens.Era.room(era, false)) }
    }

    @Test
    fun everyColorIsOpaque() {
        for ((name, _, r) in rooms) {
            for (c in listOf(r.wallLit, r.wallDark, r.floor, r.woodLight, r.woodMid, r.woodDark, r.accent, r.accent2)) {
                assertEquals("$name %08X".format(c), 0xFF, (c ushr 24).toInt())
            }
        }
    }

    @Test
    fun osdTextStaysReadableOverEveryEraSurfaceThanksToTheScrim() {
        for ((name, _, r) in rooms) for (bg in listOf(r.wallLit, r.wallDark, r.woodLight, r.accent, r.accent2)) {
            val behind = BackdropDim.darken(bg, Vhs.OSD_SCRIM_ALPHA)
            for ((fgName, fg) in listOf("OSD_TEXT" to Vhs.OSD_TEXT, "OSD_ACCENT" to Vhs.OSD_ACCENT)) {
                val c = ColorMath.contrast(fg, behind)
                assertTrue("$name $fgName/%08X: %.2f:1 < 4,5:1".format(bg, c), c >= Tokens.A11y.MIN_CONTRAST)
            }
        }
    }

    @Test
    fun theWallLampStaysReadableOnEveryEraWall() {
        for ((name, _, r) in rooms) for (wall in listOf(r.wallLit, r.wallDark)) {
            for ((state, shade) in listOf("mati" to Lamp.SHADE_OFF, "menyala" to Lamp.SHADE_ON)) {
                val c = maxOf(ColorMath.contrast(shade, wall), ColorMath.contrast(Lamp.OUTLINE, wall))
                assertTrue("$name kap $state di atas %08X: %.2f:1 < 3:1".format(wall, c), c >= 3.0)
            }
        }
    }

    @Test
    fun wallFloorAndFurnitureCanBeToldApart() {
        for ((name, _, r) in rooms) {
            val wallFloor = ColorMath.contrast(r.wallLit, r.floor)
            assertTrue("$name dinding/lantai %.2f:1".format(wallFloor), wallFloor >= 1.15)
            // Meja berpelana terang di depan dan gelap di belakang: gradasinya harus terlihat
            val wood = ColorMath.contrast(r.woodLight, r.woodDark)
            assertTrue("$name kayu terang/gelap %.2f:1".format(wood), wood >= 1.8)
            assertTrue("$name kayu terang lebih terang dari tengah", ColorMath.luminance(r.woodLight) > ColorMath.luminance(r.woodMid))
            assertTrue("$name kayu tengah lebih terang dari gelap", ColorMath.luminance(r.woodMid) > ColorMath.luminance(r.woodDark))
        }
    }

    @Test
    fun wallDecorationAccentsStandOutFromTheWall() {
        for ((name, _, r) in rooms) for (accent in listOf(r.accent, r.accent2)) {
            val c = ColorMath.contrast(accent, r.wallLit)
            assertTrue("$name aksen %08X di dinding: %.2f:1 < 1,5:1".format(accent, c), c >= 1.5)
        }
    }

    @Test
    fun eachEraHasItsOwnLook() {
        val walls = eras.map { Tokens.Era.room(it, true)!!.wallLit }
        assertEquals("dinding gelap tiap era harus berbeda", walls.size, walls.toSet().size)
        val lightWalls = eras.map { Tokens.Era.room(it, false)!!.wallLit }
        assertEquals(lightWalls.size, lightWalls.toSet().size)
        // Terang memang lebih terang dari gelap di tiap era (lampu dinding tetap berarti)
        for (era in eras) {
            assertTrue("$era", ColorMath.luminance(Tokens.Era.room(era, false)!!.wallLit) > ColorMath.luminance(Tokens.Era.room(era, true)!!.wallLit))
        }
    }
}
