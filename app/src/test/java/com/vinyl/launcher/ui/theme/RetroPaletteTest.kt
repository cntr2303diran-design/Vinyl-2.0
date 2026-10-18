package com.vinyl.launcher.ui.theme

import com.vinyl.launcher.ui.theme.Tokens.Retro
import org.junit.Assert.assertTrue
import org.junit.Test

/** Setiap pasangan teks/latar tema TV tabung yang dipakai UI harus >= 4,5:1. */
class RetroPaletteTest {
    private fun assertReadable(name: String, fg: Long, bg: Long) {
        val c = ColorMath.contrast(fg, bg)
        assertTrue("$name: %.2f:1 < 4,5:1".format(c), c >= Tokens.A11y.MIN_CONTRAST)
    }

    @Test
    fun inkOnTheWhiteRoom() {
        for ((bgName, bg) in listOf("WARM_WHITE" to Retro.WARM_WHITE, "VOID_WALL_TOP" to Retro.VOID_WALL_TOP,
            "VOID_FLOOR" to Retro.VOID_FLOOR, "VOID_CORNER" to Retro.VOID_CORNER)) {
            assertReadable("INK/$bgName", Retro.INK, bg)
            assertReadable("INK_SECONDARY/$bgName", Retro.INK_SECONDARY, bg)
            assertReadable("INK_TERTIARY/$bgName", Retro.INK_TERTIARY, bg)
        }
    }

    @Test
    fun inkOnTheWhiteRoomEvenUnderTheDarkestRoomEffect() {
        // Vignette/bayangan boleh jatuh di bawah teks utama & sekunder (bukan tersier)
        for (bg in listOf(Retro.WARM_WHITE, Retro.VOID_FLOOR, Retro.VOID_CORNER)) {
            val shaded = BackdropLift.withShade(bg)
            assertReadable("INK/shaded", Retro.INK, shaded)
            assertReadable("INK_SECONDARY/shaded", Retro.INK_SECONDARY, shaded)
        }
    }

    @Test
    fun creamOnDarkPanels() {
        for ((bgName, bg) in listOf("CHARCOAL" to Retro.CHARCOAL, "CHARCOAL_DEEP" to Retro.CHARCOAL_DEEP, "CHARCOAL_RAISED" to Retro.CHARCOAL_RAISED)) {
            assertReadable("CREAM_TEXT/$bgName", Retro.CREAM_TEXT, bg)
            assertReadable("CREAM_TEXT_SECONDARY/$bgName", Retro.CREAM_TEXT_SECONDARY, bg)
            assertReadable("AMBER/$bgName", Retro.AMBER, bg)
        }
    }

    @Test
    fun vfdCharactersOnVfdGlass() {
        assertReadable("VFD_AMBER", Retro.VFD_AMBER, Retro.VFD_GLASS)
        assertReadable("VFD_GREEN", Retro.VFD_GREEN, Retro.VFD_GLASS)
    }

    @Test
    fun textOnTvBodiesAndLabels() {
        // Label kaset & tulisan di bodi TV krem memakai tinta gelap
        assertReadable("INK/CREAM_BEIGE", Retro.INK, Retro.CREAM_BEIGE)
        // Tulisan di panel kayu memakai krem
        assertReadable("CREAM_TEXT/WALNUT", Retro.CREAM_TEXT, Retro.WALNUT)
    }

    @Test
    fun mutedGreenIsDecorativeOnCharcoal() {
        // Didokumentasikan: hijau kalem TIDAK boleh menjadi teks di atas charcoal (hanya aksen); VFD memakai VFD_GREEN.
        assertTrue(ColorMath.contrast(Retro.MUTED_GREEN, Retro.CHARCOAL) < Tokens.A11y.MIN_CONTRAST)
    }
}
