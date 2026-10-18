package com.vinyl.launcher.ui.theme

import com.vinyl.launcher.ui.theme.Tokens.Retro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropLiftTest {
    private val target = Tokens.A11y.MIN_CONTRAST + BackdropDim.SAFETY_MARGIN

    /** Warna setelah peredam putih dan efek penggelap terburuk, seperti yang dilihat teks. */
    private fun seen(bg: Long, alpha: Double, effects: Boolean = true): Long {
        val lifted = BackdropLift.lighten(bg, alpha)
        return if (effects) BackdropLift.withShade(lifted) else lifted
    }

    private fun worst(bg: Long) = BackdropLift.TEXTS.minOf { ColorMath.contrast(it, bg) }

    /** Grid kasar seluruh ruang warna + warna sulit. */
    private val samples: List<Long> = buildList {
        for (r in 0..255 step 51) for (g in 0..255 step 51) for (b in 0..255 step 51) add(0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong())
        addAll(listOf(0xFF000000L, 0xFF777777L, 0xFF0000FFL, 0xFF6A1B9AL, 0xFF1E1D1BL, 0xFF55524CL, Retro.WALNUT, Retro.CHARCOAL))
    }

    @Test
    fun everyBackgroundBecomesReadableForDarkText() {
        for (bg in samples) {
            val a = BackdropLift.minVeilAlpha(bg)
            assertTrue("bg=%08X alpha=%.3f".format(bg, a), a in 0.0..1.0)
            val c = worst(seen(bg, a))
            assertTrue("bg=%08X: %.2f".format(bg, c), c >= target - 1e-6)
        }
    }

    @Test
    fun theVeilIsMinimal() {
        // Sedikit di bawah alpha yang dihitung, teks belum lolos (kecuali alpha 0)
        for (bg in samples) {
            val a = BackdropLift.minVeilAlpha(bg)
            if (a > 0.01) assertTrue("bg=%08X".format(bg), worst(seen(bg, a - 0.01)) < target)
        }
    }

    @Test
    fun theWhiteRoomNeedsNoVeil() {
        for (bg in listOf(Retro.WARM_WHITE, Retro.VOID_WALL_TOP, Retro.VOID_FLOOR, Retro.VOID_CORNER)) {
            assertEquals(0.0, BackdropLift.minVeilAlpha(bg), 0.0)
        }
    }

    @Test
    fun theSearchIsSafeBecauseDarkBackgroundsCanNeverPass() {
        // Dasar pencarian biner: selama latar lebih gelap dari teks, kontras paling banyak (L_teks+0,05)/0,05 < target
        for (t in BackdropLift.TEXTS) {
            val ceiling = (ColorMath.luminance(t) + 0.05) / 0.05
            assertTrue("%.2f".format(ceiling), ceiling < target)
        }
    }

    @Test
    fun ignoringEffectsNeverNeedsMoreVeil() {
        for (bg in samples) assertTrue(BackdropLift.minVeilAlpha(bg, effects = false) <= BackdropLift.minVeilAlpha(bg) + 1e-9)
    }

    @Test
    fun withShadeDarkensByTheBudget() {
        assertEquals(0xFFE6E6E6L, BackdropLift.withShade(0xFFFFFFFFL, 0.1) or 0L)
    }

    @Test
    fun darkestPicksTheLowestLuminancePixel() {
        val px = intArrayOf(0xFFFFFFFF.toInt(), 0xFF101010.toInt(), 0xFF0000FF.toInt(), 0xFF808080.toInt())
        assertEquals(0xFF101010L, BackdropLift.darkest(px))
        assertEquals(null, BackdropLift.darkest(IntArray(0)))
    }
}
