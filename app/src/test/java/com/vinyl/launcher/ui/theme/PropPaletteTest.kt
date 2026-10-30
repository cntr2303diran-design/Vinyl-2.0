package com.vinyl.launcher.ui.theme

import com.vinyl.launcher.ui.theme.Tokens.Props
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Warna benda ruangan ([Tokens.Props]): buram, dan bagian yang dibaca/ditekan tetap terbedakan. */
class PropPaletteTest {
    private val all = listOf(
        Props.CLOCK_FACE, Props.INK, Props.BAKELITE, Props.BAKELITE_DARK, Props.SILVER, Props.SILVER_DARK,
        Props.DIAL_PLATE, Props.NEEDLE, Props.LED_ON, Props.LED_OFF,
        Props.CUP_GOLD, Props.CUP_SILVER, Props.CUP_BRONZE, Props.DIARY_COVER, Props.DIARY_PAGES, Props.DIARY_REC,
    )

    @Test
    fun everyColorIsOpaque() {
        for (c in all) assertEquals("%08X".format(c), 0xFF, (c ushr 24).toInt())
    }

    @Test
    fun clockHandsAndNumeralsReadOnTheFace() {
        assertTrue(ColorMath.contrast(Props.INK, Props.CLOCK_FACE) >= 7.0)
    }

    @Test
    fun phoneDigitsReadOnTheirPlate() {
        assertTrue(ColorMath.contrast(Props.INK, Props.DIAL_PLATE) >= 7.0)
    }

    @Test
    fun radioNeedleShowsOnTheSilverDialAndLedStatesDiffer() {
        assertTrue(ColorMath.contrast(Props.NEEDLE, Props.SILVER) >= 3.0)
        assertTrue(ColorMath.contrast(Props.LED_ON, Props.LED_OFF) >= 3.0)
    }

    @Test
    fun trophyTiersAreToldApartAndShowAgainstTheDarkWall() {
        val cups = listOf(Props.CUP_GOLD, Props.CUP_SILVER, Props.CUP_BRONZE)
        for (i in cups.indices) for (j in i + 1 until cups.size) {
            assertTrue("$i/$j", ColorMath.contrast(cups[i], cups[j]) >= 1.15)
        }
        // Daftar piala memakai warna ini sebagai titik di atas latar gelap/terang: harus terlihat di keduanya
        for (c in cups) {
            assertTrue("%08X".format(c), ColorMath.contrast(c, 0xFF1C1B1FL) >= 3.0)
        }
    }

    @Test
    fun diaryRecordingDotShowsOnTheCover() {
        assertTrue(ColorMath.contrast(Props.DIARY_REC, Props.DIARY_COVER) >= 2.0)
        assertTrue(ColorMath.contrast(Props.DIARY_PAGES, Props.DIARY_COVER) >= 4.5)
    }
}
