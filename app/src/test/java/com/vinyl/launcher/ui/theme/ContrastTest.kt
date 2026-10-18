package com.vinyl.launcher.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bukti kontras >= 4.5:1 (WCAG AA teks normal) untuk token warna dan latar ambient dari cover. */
class ContrastTest {
    private val min = Tokens.A11y.MIN_CONTRAST

    private val surfaces = mapOf(
        "ink" to Tokens.Argb.INK,
        "elevated" to Tokens.Argb.INK_ELEVATED,
        "card" to Tokens.Argb.INK_CARD,
        "cardHigh" to Tokens.Argb.INK_CARD_HIGH,
        "cardHighest" to Tokens.Argb.INK_CARD_HIGHEST,
    )
    private val texts = mapOf(
        "primary" to Tokens.Argb.TEXT_PRIMARY,
        "secondary" to Tokens.Argb.TEXT_SECONDARY,
        "tertiary" to Tokens.Argb.TEXT_TERTIARY,
    )

    private fun assertAtLeast(name: String, ratio: Double) =
        assertTrue("$name: kontras %.2f < %.1f".format(ratio, min), ratio >= min)

    @Test fun `rumus kontras cocok dengan nilai rujukan WCAG`() {
        assertEquals(21.0, ColorMath.contrast(0xFF000000L, 0xFFFFFFFFL), 1e-9)
        assertEquals(1.0, ColorMath.contrast(0xFF123456L, 0xFF123456L), 1e-9)
        // abu #767676 di atas putih = 4.54:1 (angka terkenal "lolos AA tipis")
        assertEquals(4.54, ColorMath.contrast(0xFF767676L, 0xFFFFFFFFL), 0.01)
        assertEquals(0.2126, ColorMath.luminance(0xFFFF0000L), 1e-4)
    }

    @Test fun `semua tingkat teks lolos di semua permukaan`() {
        texts.forEach { (tn, t) -> surfaces.forEach { (sn, s) -> assertAtLeast("$tn di $sn", ColorMath.contrast(t, s)) } }
    }

    @Test fun `tingkatan teks tetap berurutan`() {
        val order = listOf("primary", "secondary", "tertiary").map { texts.getValue(it) }
        order.zipWithNext().forEach { (a, b) -> assertTrue(ColorMath.contrast(a, Tokens.Argb.INK_CARD) > ColorMath.contrast(b, Tokens.Argb.INK_CARD)) }
    }

    @Test fun `pasangan warna aksen dan wadah lolos`() {
        assertAtLeast("aksen di ink", ColorMath.contrast(Tokens.Argb.ACCENT, Tokens.Argb.INK))
        assertAtLeast("aksen di card", ColorMath.contrast(Tokens.Argb.ACCENT, Tokens.Argb.INK_CARD))
        assertAtLeast("teks di atas aksen", ColorMath.contrast(Tokens.Argb.ACCENT_ON, Tokens.Argb.ACCENT))
        assertAtLeast("primaryContainer", ColorMath.contrast(Tokens.Argb.ON_PRIMARY_CONTAINER, Tokens.Argb.PRIMARY_CONTAINER))
        assertAtLeast("errorContainer", ColorMath.contrast(Tokens.Argb.ON_ERROR_CONTAINER, Tokens.Argb.ERROR_CONTAINER))
        assertAtLeast("secondary di card", ColorMath.contrast(Tokens.Argb.SECONDARY, Tokens.Argb.INK_CARD))
    }

    @Test fun `panel hampir buram tetap membuat teks terbaca di atas latar putih`() {
        // Skenario terburuk: panel di atas aplikasi berlatar putih. Komposit PANEL (alpha 0xF2) di atas putih.
        val a = 0xF2 / 255.0
        fun mix(shift: Int) = ((Tokens.Argb.PANEL shr shift) and 0xFF) * a + 255 * (1 - a)
        val panelOnWhite = (0xFFL shl 24) or (mix(16).toLong() shl 16) or (mix(8).toLong() shl 8) or mix(0).toLong()
        assertAtLeast("teks utama di panel di atas putih", ColorMath.contrast(Tokens.Argb.TEXT_PRIMARY, panelOnWhite))
        assertAtLeast("teks sekunder di panel di atas putih", ColorMath.contrast(Tokens.Argb.TEXT_SECONDARY, panelOnWhite))
    }

    // ------------------------------------------------------------------ latar ambient

    private val hues = (0 until 360 step 1).map { it.toFloat() }
    private val sats = listOf(0f, 0.1f, 0.25f, 0.5f, 0.85f, 1f)
    private val vals = listOf(0f, 0.18f, 0.3f, 0.55f, 0.8f, 1f)
    private val caps = listOf(0.55f, 0.4f, 0.35f, 1f)

    @Test fun `ambient dari warna apa pun menjaga semua tingkat teks lolos`() {
        var worst = Double.MAX_VALUE
        var worstCase = ""
        for (h in hues) for (s in sats) for (v in vals) for (cap in caps) {
            val out = AmbientColor.limit(Hsv(h, s, v), cap, 0.1f)
            val (r, g, b) = ColorMath.hsvToRgb(out.h, out.s, out.v)
            val bg = (0xFFL shl 24) or (Math.round(r * 255).toLong() shl 16) or (Math.round(g * 255).toLong() shl 8) or Math.round(b * 255).toLong()
            for ((tn, t) in texts) {
                val c = ColorMath.contrast(t, bg)
                if (c < worst) { worst = c; worstCase = "$tn h=$h s=$s v=$v cap=$cap -> $out" }
            }
        }
        assertTrue("kontras terburuk %.2f (%s)".format(worst, worstCase), worst >= min)
    }

    @Test fun `ambient tetap dalam batas rancangan`() {
        for (h in hues.filterIndexed { i, _ -> i % 7 == 0 }) for (s in sats) for (v in vals) {
            val out = AmbientColor.limit(Hsv(h, s, v), 0.55f, 0.25f)
            assertTrue(out.v in AmbientColor.MIN_VALUE..0.55f)
            assertTrue(out.s in 0.25f..AmbientColor.MAX_SATURATION)
            assertTrue(ColorMath.luminance(out) <= AmbientColor.MAX_LUMINANCE + 1e-4)
            assertEquals(h, out.h, 0f)
        }
    }

    @Test fun `biru tetap pekat sementara kuning diredupkan`() {
        val blue = AmbientColor.limit(Hsv(240f, 0.85f, 0.55f))
        assertEquals("biru tidak perlu diredupkan", 0.55f, blue.v, 1e-6f)
        val yellow = AmbientColor.limit(Hsv(60f, 0.85f, 0.55f))
        assertTrue("kuning harus diredupkan, v=${yellow.v}", yellow.v < 0.40f)
        assertTrue(yellow.v >= AmbientColor.MIN_VALUE)
    }

    @Test fun `parameter tidak masuk akal tidak melempar`() {
        // maxValue di bawah batas bawah dulu bisa melempar IllegalArgumentException (coerceIn min > max)
        val out = AmbientColor.limit(Hsv(10f, 0.5f, 0.5f), maxValue = 0.05f, minSat = 2f)
        assertTrue(out.v >= AmbientColor.MIN_VALUE)
        assertTrue(out.s <= AmbientColor.MAX_SATURATION)
    }

    // ------------------------------------------------------------------ teks di atas warna sembarang

    @Test fun `pilihan teks gelap atau terang selalu menghasilkan minimal 4,5`() {
        var step = 0
        for (r in 0..255 step 15) for (g in 0..255 step 15) for (b in 0..255 step 15) {
            val bg = (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
            val dark = ColorMath.darkTextIsBetter(bg)
            val chosen = if (dark) Tokens.Argb.TEXT_ON_LIGHT else Tokens.Argb.TEXT_ON_DARK
            assertAtLeast("teks di atas #%06X".format(bg and 0xFFFFFF), ColorMath.contrast(chosen, bg))
            step++
        }
        assertTrue(step > 4000)
    }

    @Test fun `latar putih memilih teks gelap dan latar hitam memilih teks terang`() {
        assertTrue(ColorMath.darkTextIsBetter(0xFFFFFFFFL))
        assertTrue(!ColorMath.darkTextIsBetter(0xFF000000L))
        // kuning terang dulu dapat teks putih karena ambang 0.55 pada luminans
        assertTrue(ColorMath.darkTextIsBetter(0xFFFFD60AL))
    }
}
