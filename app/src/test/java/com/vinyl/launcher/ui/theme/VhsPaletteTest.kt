package com.vinyl.launcher.ui.theme

import com.vinyl.launcher.ui.theme.Tokens.Vhs
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pasangan teks/latar beranda bergaya VHS harus >= 4,5:1. */
class VhsPaletteTest {
    private fun assertReadable(name: String, fg: Long, bg: Long) {
        val c = ColorMath.contrast(fg, bg)
        assertTrue("$name: %.2f:1 < 4,5:1".format(c), c >= Tokens.A11y.MIN_CONTRAST)
    }

    @Test
    fun osdTextStaysReadableOverAnyProjectionThanksToTheScrim() {
        // Kasus terburuk: proyeksi putih penuh tepat di belakang pita OSD
        for (bg in listOf(0xFFFFFFFFL, 0xFFFFF59DL, 0xFF9AD0FFL, Vhs.WALL_LIT, Vhs.WALL_DARK, Vhs.WOOD_LIGHT)) {
            val behind = BackdropDim.darken(bg, Vhs.OSD_SCRIM_ALPHA)
            assertReadable("OSD_TEXT/%08X".format(bg), Vhs.OSD_TEXT, behind)
            assertReadable("OSD_ACCENT/%08X".format(bg), Vhs.OSD_ACCENT, behind)
        }
    }

    @Test
    fun lowBatteryWarningUsesWhiteOnRed() {
        assertReadable("OSD_TEXT/OSD_WARN_FILL", Vhs.OSD_TEXT, Vhs.OSD_WARN_FILL)
    }

    @Test
    fun deckKeysAndCassetteLabel() {
        assertReadable("KEY_INK/KEY_FACE", Vhs.KEY_INK, Vhs.KEY_FACE)
        assertReadable("KEY_INK/KEY_FACE_DOWN", Vhs.KEY_INK, Vhs.KEY_FACE_DOWN)
        assertReadable("LABEL_INK/LABEL_PAPER", Vhs.LABEL_INK, Vhs.LABEL_PAPER)
        assertReadable("VFD_AMBER/DECK_VFD", Tokens.Retro.VFD_AMBER, Tokens.Retro.VFD_GLASS)
    }
}
