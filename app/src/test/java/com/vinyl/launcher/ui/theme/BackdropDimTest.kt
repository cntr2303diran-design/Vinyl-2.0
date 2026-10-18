package com.vinyl.launcher.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropDimTest {
    private val target = Tokens.A11y.MIN_CONTRAST + BackdropDim.SAFETY_MARGIN
    private fun rgb(r: Int, g: Int, b: Int) = (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    private fun worst(bg: Long) = BackdropDim.TEXTS.minOf { ColorMath.contrast(it, bg) }
    /** Kontras terburuk di atas permukaan kaca yang menumpuk di latar itu (yang dilihat pengguna pada kartu/kapsul). */
    private fun worstOnGlass(bg: Long) = worst(BackdropDim.withGlass(bg))
    /**
     * Jalur render sebenarnya: latar → distorsi kaca (mengalikan warna sampai 1 + DIST_PEAK) → peredam hitam beralpha
     * [alpha] → efek (memutihkan sampai FX_VEIL).
     */
    private fun rendered(bg: Long, alpha: Double) = BackdropDim.withFx(BackdropDim.darken(BackdropDim.withDist(bg), alpha))

    @Test fun `latar putih butuh peredam kuat dan hitam tidak butuh sama sekali`() {
        val white = BackdropDim.minOverlayAlpha(rgb(255, 255, 255))
        assertTrue("alpha untuk putih = $white", white in 0.4..0.9)
        assertEquals(0.0, BackdropDim.minOverlayAlpha(rgb(0, 0, 0)), 0.0)
        assertEquals(0.0, BackdropDim.minOverlayAlpha(rgb(10, 10, 12)), 0.0)
    }

    @Test fun `hasil selalu lolos untuk setiap warna latar`() {
        var n = 0
        for (r in 0..255 step 15) for (g in 0..255 step 15) for (b in 0..255 step 15) {
            val bg = rgb(r, g, b)
            val a = BackdropDim.minOverlayAlpha(bg)
            assertTrue("alpha $a di luar 0..1", a in 0.0..1.0)
            val dark = rendered(bg, a)
            val c = worst(dark)
            assertTrue("latar #%06X alpha %.3f -> kontras %.3f < %.2f".format(bg and 0xFFFFFF, a, c, target), c >= target - 1e-9)
            val g = worstOnGlass(dark)
            assertTrue("di atas kaca: latar #%06X alpha %.3f -> kontras %.3f < %.2f".format(bg and 0xFFFFFF, a, g, target), g >= target - 1e-9)
            n++
        }
        assertTrue(n > 4000)
    }

    @Test fun `hasil minimal - sedikit lebih tipis sudah gagal`() {
        for (r in 0..255 step 51) for (g in 0..255 step 51) for (b in 0..255 step 51) {
            val bg = rgb(r, g, b)
            val a = BackdropDim.minOverlayAlpha(bg)
            if (a > 0.02) {
                val thinner = worstOnGlass(rendered(bg, a - 0.02))
                assertTrue("latar #%06X: alpha %.3f bukan minimum (kontras di %.3f = %.3f)".format(bg and 0xFFFFFF, a, a - 0.02, thinner), thinner < target)
            }
        }
    }

    @Test fun `tingkat teks tersier tidak dijamin dan memang tidak ikut dihitung`() {
        assertTrue(Tokens.Argb.TEXT_TERTIARY !in BackdropDim.TEXTS)
        assertTrue(Tokens.Argb.TEXT_PRIMARY in BackdropDim.TEXTS && Tokens.Argb.TEXT_SECONDARY in BackdropDim.TEXTS)
    }

    @Test fun `darken membulatkan seperti 8 bit dan membatasi alpha`() {
        assertEquals(rgb(100, 50, 0), BackdropDim.darken(rgb(200, 100, 0), 0.5))
        assertEquals(rgb(200, 100, 0), BackdropDim.darken(rgb(200, 100, 0), -3.0))
        assertEquals(rgb(0, 0, 0), BackdropDim.darken(rgb(200, 100, 7), 5.0))
    }

    @Test fun `brightest memilih luminans tertinggi bukan nilai terbesar`() {
        // hijau murni lebih terang (luminans) daripada biru murni walau nilai kanalnya sama
        val px = intArrayOf(0xFF0000FF.toInt(), 0xFF00FF00.toInt(), 0xFF800000.toInt())
        assertEquals(rgb(0, 255, 0), BackdropDim.brightest(px))
        assertNull(BackdropDim.brightest(IntArray(0)))
        // alpha piksel diabaikan (bitmap cover dianggap opak)
        assertEquals(rgb(255, 255, 255), BackdropDim.brightest(intArrayOf(0x00FFFFFF)))
    }

    @Test fun `titik gradien tidak pernah di luar 0 sampai 1 dan tidak di bawah kebutuhan`() {
        // Regresi: dim 0,8 × 1,35 = 1,08 membuat Color.copy(alpha) melempar exception
        for (dim in listOf(0f, 0.38f, 0.62f, 0.75f, 0.9f, 1f, 5f)) for (need in listOf(0.0, 0.3, 0.7, 1.0, 3.0)) {
            val (top, mid, bottom) = BackdropDim.stops(dim, need)
            for (s in listOf(top, mid, bottom)) {
                assertTrue("dim=$dim need=$need -> $s", s in 0f..1f)
                assertTrue(s >= need.coerceIn(0.0, 1.0).toFloat() - 1e-6f)
            }
            assertTrue(top <= mid && mid <= bottom)    // vignette: makin tebal ke bawah
        }
    }

    @Test fun `cover putih dengan dim bawaan drawer lolos di setiap titik gradien`() {
        val white = rgb(255, 255, 255)
        val needed = BackdropDim.minOverlayAlpha(white)
        val (top, mid, bottom) = BackdropDim.stops(0.62f, needed)
        for (a in listOf(top, mid, bottom)) {
            val dark = rendered(white, a.toDouble())
            val c = worst(dark)
            assertTrue("alpha $a -> kontras $c", c >= Tokens.A11y.MIN_CONTRAST)
            val g = worstOnGlass(dark)
            assertTrue("alpha $a di atas kaca -> kontras $g", g >= Tokens.A11y.MIN_CONTRAST)
        }
    }

    @Test fun `tanpa memperhitungkan kaca, cover putih gagal di atas kapsul kaca (alasan withGlass ada)`() {
        // Peredam lama (hanya latar polos) membuat teks di atas kaca turun di bawah 4,5:1. Tes ini mengunci alasannya.
        val white = rgb(255, 255, 255)
        var a = 0.0
        while (worst(BackdropDim.darken(white, a)) < target && a < 1.0) a += 0.001   // peredam minimum versi lama
        val g = worstOnGlass(rendered(white, a))
        assertTrue("kontras di atas kaca dengan peredam lama = $g", g < Tokens.A11y.MIN_CONTRAST)
        assertTrue(BackdropDim.minOverlayAlpha(white) > a)
    }

    @Test fun `withGlass menimpa putih secara proporsional dan membatasi veil`() {
        assertEquals(rgb(0, 0, 0), BackdropDim.withGlass(rgb(0, 0, 0), 0.0))
        assertEquals(rgb(255, 255, 255), BackdropDim.withGlass(rgb(0, 0, 0), 1.0))
        assertEquals(rgb(255, 255, 255), BackdropDim.withGlass(rgb(0, 0, 0), 9.0))
        assertEquals(rgb(0, 0, 0), BackdropDim.withGlass(rgb(0, 0, 0), -2.0))
        assertEquals(rgb(128, 128, 128), BackdropDim.withGlass(rgb(0, 0, 0), 0.5))
        assertEquals(rgb(255, 255, 255), BackdropDim.withGlass(rgb(255, 255, 255)))
    }

    @Test fun `isi kaca dibatasi ke batas yang dijamin`() {
        assertEquals(Tokens.A11y.MAX_GLASS_FILL, BackdropDim.glassFill(0.25f), 0f)
        assertEquals(Tokens.A11y.MAX_GLASS_FILL, BackdropDim.glassFill(1f), 0f)
        assertEquals(0.06f, BackdropDim.glassFill(0.06f), 0f)
        assertEquals(0f, BackdropDim.glassFill(-0.5f), 0f)
        assertEquals(0f, BackdropDim.glassFill(Float.NaN), 0f)
        assertEquals(Tokens.A11y.MAX_GLASS_FILL, BackdropDim.glassFill(Tokens.A11y.MAX_GLASS_FILL), 0f)
    }

    @Test fun `isi kaca maksimum tidak melebihi veil yang dijamin peredam`() {
        assertTrue(Tokens.A11y.MAX_GLASS_FILL < Tokens.A11y.GLASS_VEIL)       // sisa untuk kilau atas
        assertTrue(Tokens.A11y.GLASS_VEIL - Tokens.A11y.MAX_GLASS_FILL >= 0.03)
    }

    @Test fun `cover gelap tidak digelapkan lebih dari dim yang diminta`() {
        val (top, mid, bottom) = BackdropDim.stops(0.4f, BackdropDim.minOverlayAlpha(rgb(20, 20, 30)))
        assertEquals(0.4f * 0.7f, top, 1e-6f)
        assertEquals(0.4f, mid, 1e-6f)
        assertEquals(0.4f * 1.35f, bottom, 1e-6f)
    }

    @Test fun `anggaran efek - dua lapisan yang menumpuk tidak melebihi veil yang dijamin dan tidak boros`() {
        val two = 1.0 - (1.0 - Tokens.A11y.FX_PEAK) * (1.0 - Tokens.A11y.FX_PEAK)
        assertTrue("dua lapisan memutihkan $two > ${Tokens.A11y.FX_VEIL}", two <= Tokens.A11y.FX_VEIL)
        assertTrue("veil efek terlalu longgar dibanding dua lapisan", Tokens.A11y.FX_VEIL - two < 0.01)
    }

    @Test fun `withFx sama dengan menimpa putih sebesar veil efek`() {
        assertEquals(BackdropDim.withGlass(rgb(40, 90, 200), Tokens.A11y.FX_VEIL), BackdropDim.withFx(rgb(40, 90, 200)))
        assertEquals(rgb(0, 0, 0), BackdropDim.withFx(rgb(0, 0, 0), 0.0))
        assertEquals(rgb(255, 255, 255), BackdropDim.withFx(rgb(255, 255, 255)))
    }

    @Test fun `tanpa anggaran efek, sebagian latar gagal begitu partikel menyala (alasan withFx ada)`() {
        var failures = 0
        for (v in 0..255 step 5) {
            val bg = rgb(v, v, v)
            var a = 0.0                                                                 // peredam minimum tanpa memperhitungkan efek
            while (worstOnGlass(BackdropDim.darken(bg, a)) < target && a < 1.0) a += 0.001
            if (worstOnGlass(rendered(bg, a)) < Tokens.A11y.MIN_CONTRAST) failures++
            // Sedangkan peredam yang sebenarnya selalu cukup
            assertTrue(worstOnGlass(rendered(bg, BackdropDim.minOverlayAlpha(bg))) >= target - 1e-9)
        }
        assertTrue("seharusnya ada latar yang gagal tanpa anggaran efek (gagal=$failures)", failures > 0)
    }

    @Test fun `withDist mengalikan kanal, memotong di 255, dan tidak menyentuh hitam`() {
        assertEquals(rgb(0, 0, 0), BackdropDim.withDist(rgb(0, 0, 0)))
        assertEquals(rgb(255, 255, 255), BackdropDim.withDist(rgb(255, 255, 255)))
        assertEquals(rgb(255, 0, 255), BackdropDim.withDist(rgb(250, 0, 253)))                    // dipotong, bukan melingkar
        assertEquals(rgb(103, 206, 0), BackdropDim.withDist(rgb(100, 200, 0), 0.03))
        assertEquals(rgb(110, 220, 0), BackdropDim.withDist(rgb(100, 200, 0), 0.1))
        assertEquals(rgb(100, 200, 0), BackdropDim.withDist(rgb(100, 200, 0), 0.0))
        assertEquals(rgb(100, 200, 0), BackdropDim.withDist(rgb(100, 200, 0), -1.0))               // negatif tidak menggelapkan
        assertEquals(rgb(200, 255, 0), BackdropDim.withDist(rgb(100, 200, 0), 9.0))                // dibatasi gain <= 1
    }

    @Test fun `distorsi adalah perkalian, bukan putih - kanal terang naik lebih banyak daripada kanal gelap`() {
        val lit = BackdropDim.withDist(rgb(230, 100, 20))
        val asWhite = BackdropDim.withGlass(rgb(230, 100, 20), Tokens.A11y.DIST_PEAK.toDouble())
        assertTrue("perkalian menaikkan kanal merah lebih banyak daripada putih", ((lit shr 16) and 0xFF) > ((asWhite shr 16) and 0xFF))
        assertTrue("dan kanal gelap lebih sedikit", (lit and 0xFF) < (asWhite and 0xFF))
    }

    @Test fun `anggaran distorsi kecil dan sama dengan yang dipakai shader`() {
        assertTrue(Tokens.A11y.DIST_PEAK in 0.01f..0.05f)
        // Shader menulis konstanta ini ke sumber AGSL lewat toString(); harus berupa literal float biasa (bukan 3.0E-2)
        assertTrue(Tokens.A11y.DIST_PEAK.toString().matches(Regex("0\\.\\d+")))
    }

    @Test fun `tanpa memperhitungkan distorsi, sebagian latar tidak lagi mencapai target (alasan withDist ada)`() {
        var short = 0
        var total = 0
        for (r in 0..255 step 17) for (g in 0..255 step 17) for (b in 0..255 step 17) {
            val bg = rgb(r, g, b)
            var lo = 0.0
            var hi = 1.0
            repeat(30) {
                val mid = (lo + hi) / 2
                if (worstOnGlass(BackdropDim.withFx(BackdropDim.darken(bg, mid))) >= target) hi = mid else lo = mid
            }
            if (worstOnGlass(rendered(bg, hi)) < target - 1e-9) short++
            // Sedangkan peredam yang sebenarnya selalu cukup, termasuk distorsi
            assertTrue(worstOnGlass(rendered(bg, BackdropDim.minOverlayAlpha(bg))) >= target - 1e-9)
            total++
        }
        assertTrue("seharusnya ada latar yang kurang tanpa withDist ($short dari $total)", short > 0)
    }

    @Test fun `layar tanpa efek tidak membayar anggaran efek - jalur lama persis dan minimal`() {
        var strictlyLighter = 0
        for (r in 0..255 step 17) for (g in 0..255 step 17) for (b in 0..255 step 17) {
            val bg = rgb(r, g, b)
            val off = BackdropDim.minOverlayAlpha(bg, effects = false)
            val on = BackdropDim.minOverlayAlpha(bg)
            assertTrue("#%06X: tanpa efek $off > dengan efek $on".format(bg and 0xFFFFFF), off <= on + 1e-9)
            if (off < on - 0.005) strictlyLighter++
            assertTrue(worstOnGlass(BackdropDim.darken(bg, off)) >= target - 1e-9)                 // lolos di jalur tanpa efek
            if (off > 0.02) assertTrue("#%06X bukan minimum".format(bg and 0xFFFFFF), worstOnGlass(BackdropDim.darken(bg, off - 0.02)) < target)
        }
        assertTrue("seharusnya sebagian latar lebih tipis tanpa efek ($strictlyLighter)", strictlyLighter > 100)
        assertEquals(0.0, BackdropDim.minOverlayAlpha(rgb(0, 0, 0), effects = false), 0.0)
    }

    @Test fun `efek aktif adalah bawaan yang aman`() {
        val bg = rgb(220, 220, 220)
        assertEquals(BackdropDim.minOverlayAlpha(bg, effects = true), BackdropDim.minOverlayAlpha(bg), 0.0)
        assertTrue(BackdropDim.minOverlayAlpha(bg) > BackdropDim.minOverlayAlpha(bg, effects = false))
    }
}
