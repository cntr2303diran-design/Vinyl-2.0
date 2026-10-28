package com.vinyl.launcher.ui.theme

import com.vinyl.launcher.ui.theme.Tokens.Lamp
import com.vinyl.launcher.ui.theme.Tokens.Vhs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lampu dinding bertepi ganda (isi terang + garis tepi gelap): bentuknya terbaca >= 3:1 di atas dinding apa pun. */
class LampPaletteTest {
    private fun edge(fill: Long, wall: Long): Double = maxOf(ColorMath.contrast(fill, wall), ColorMath.contrast(Lamp.OUTLINE, wall))

    /** Dinding nyata di ruang TV: gelap (malam VHS), terang (putih hangat), dan ujung-ujung gradasinya. */
    private val walls = listOf(Vhs.WALL_LIT, Vhs.WALL_DARK, 0xFFE9E5DDL, 0xFFBDB7ACL)

    @Test
    fun theShadeIsReadableOnEveryRoomWallLitOrNot() {
        for (wall in walls) for ((name, shade) in listOf("mati" to Lamp.SHADE_OFF, "menyala" to Lamp.SHADE_ON)) {
            val c = edge(shade, wall)
            assertTrue("kap $name di atas %08X: %.2f:1 < 3:1".format(wall, c), c >= 3.0)
        }
    }

    /** Dinding pilihan sendiri tidak memasang lampu, tetapi bahkan abu-abu mana pun (kasus terburuk) tetap >= 3:1. */
    @Test
    fun theShadeIsReadableOnAnyGreyWallEvenTheWorstOne() {
        var worst = Double.MAX_VALUE
        for (level in 0..255) {
            val wall = 0xFF000000L or (level.toLong() shl 16) or (level.toLong() shl 8) or level.toLong()
            worst = minOf(worst, edge(Lamp.SHADE_OFF, wall), edge(Lamp.SHADE_ON, wall))
        }
        assertTrue("kasus terburuk %.2f:1 < 3:1".format(worst), worst >= 3.0)
    }

    @Test
    fun thePlateAndTheArmAreOutlinedToo() {
        for (wall in walls) {
            // Kuningan terang atau garis tepi gelap: salah satunya selalu menonjol
            val c = edge(Lamp.BRASS_LIGHT, wall)
            assertTrue("lengan/pelat di atas %08X: %.2f:1 < 3:1".format(wall, c), c >= 3.0)
        }
    }

    @Test
    fun theBulbStandsOutFromItsOutlineInBothStates() {
        for (bulb in listOf(Lamp.BULB_ON, Lamp.BULB_OFF)) {
            val c = ColorMath.contrast(bulb, Lamp.OUTLINE)
            assertTrue("bohlam %08X terhadap garis tepi: %.2f:1".format(bulb, c), c >= 3.0)
        }
    }

    @Test
    fun onAndOffLookDifferentBeyondColorAlone() {
        // Terang vs redup: selisih luminans nyata (bentuk tambahan—kerucut cahaya—ada di gambar, bukan di warna)
        assertTrue(ColorMath.luminance(Lamp.SHADE_ON) > ColorMath.luminance(Lamp.SHADE_OFF) + 0.1)
        assertTrue(ColorMath.luminance(Lamp.BULB_ON) > ColorMath.luminance(Lamp.BULB_OFF) + 0.2)
        assertEquals(0xFF, (Lamp.SHADE_ON ushr 24).toInt())
        assertEquals(0xFF, (Lamp.SHADE_OFF ushr 24).toInt())
        assertEquals(0xFF, (Lamp.OUTLINE ushr 24).toInt())
    }
}
