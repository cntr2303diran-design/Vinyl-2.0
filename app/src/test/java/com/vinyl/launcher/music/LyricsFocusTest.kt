package com.vinyl.launcher.music

import com.vinyl.launcher.ui.theme.BackdropDim
import com.vinyl.launcher.ui.theme.ColorMath
import com.vinyl.launcher.ui.theme.Tokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class LyricsFocusTest {

    @Test fun activeLineIsFullStrength() {
        val a = LyricsFocus.look(0, blurEnabled = true)
        assertEquals(1f, a.alpha, 0f)
        assertEquals(1f, a.scale, 0f)
        assertEquals(0f, a.blurDp, 0f)
    }

    @Test fun neighbourIsSmallerButSharp() {
        val n = LyricsFocus.look(1, blurEnabled = true)
        assertTrue(n.scale < 1f)
        assertEquals(0f, n.blurDp, 0f)
    }

    @Test fun farLinesBlurOnlyWhenEnabled() {
        assertTrue(LyricsFocus.look(3, blurEnabled = true).blurDp > 0f)
        assertEquals(0f, LyricsFocus.look(3, blurEnabled = false).blurDp, 0f)
        assertEquals(0f, LyricsFocus.look(1, blurEnabled = true).blurDp, 0f)
    }

    @Test fun blurGrowsWithDistanceAndIsCapped() {
        var prev = -1f
        for (d in 0..50) {
            val b = LyricsFocus.look(d, true).blurDp
            assertTrue("d=$d", b >= prev)
            assertTrue(b <= LyricsFocus.MAX_BLUR_DP)
            prev = b
        }
        assertEquals(LyricsFocus.MAX_BLUR_DP, LyricsFocus.look(50, true).blurDp, 0f)
    }

    @Test fun scaleNeverGrowsWithDistanceAndStaysReadable() {
        var prev = 2f
        for (d in 0..20) {
            val s = LyricsFocus.look(d, true).scale
            assertTrue(s <= prev)
            assertTrue("terlalu kecil untuk dibaca", s >= 0.85f)
            prev = s
        }
    }

    @Test fun inactiveLinesNeverFallBelowTheContrastFloor() {
        for (d in 1..50) assertEquals(LyricsFocus.INACTIVE_ALPHA, LyricsFocus.look(d, true).alpha, 0f)
        assertTrue(LyricsFocus.INACTIVE_ALPHA < LyricsFocus.ACTIVE_ALPHA)
    }

    @Test fun negativeDistanceIsTreatedAsActive() {
        assertEquals(LyricsFocus.look(0, true), LyricsFocus.look(-4, true))
    }

    @Test fun distanceHandlesPreRollAndBothDirections() {
        assertEquals(1, LyricsFocus.distance(0, -1))      // sebelum baris pertama: baris 0 adalah tetangga
        assertEquals(0, LyricsFocus.distance(4, 4))
        assertEquals(3, LyricsFocus.distance(1, 4))
        assertEquals(3, LyricsFocus.distance(7, 4))
    }

    @Test fun inactiveTextColorMatchesItsAlpha() {
        assertEquals(Math.round(LyricsFocus.INACTIVE_ALPHA * 255f).toLong(), LyricsFocus.INACTIVE_TEXT_ARGB shr 24)
        assertEquals(0xFFFFFFL, LyricsFocus.INACTIVE_TEXT_ARGB and 0xFFFFFFL)
    }

    // ---- Bukti kontras: baris terredup tetap >= 4,5:1 di atas latar layar lirik ----

    private val target = Tokens.A11y.MIN_CONTRAST

    /**
     * Warna teks yang BENAR-BENAR digambar layar lirik: putih berbobot alpha tiap tingkat [LyricsFocus.look].
     * Sengaja diturunkan dari look(), bukan dari BACKDROP_TEXTS, supaya daftar acuan peredam tidak diuji terhadap dirinya sendiri.
     */
    private val drawnTexts: List<Long> =
        (0..8).map { LyricsFocus.look(it, true).alpha }.distinct()
            .map { a -> (Math.round(a * 255f).toLong() shl 24) or 0xFFFFFFL }

    private fun lowestContrastAfterDim(bg: Long): Double {
        // yang dipakai UI; layar lirik tidak pernah menggambar efek visual, jadi tanpa anggaran efek (effects = false)
        val overlay = BackdropDim.minOverlayAlpha(bg, LyricsFocus.BACKDROP_TEXTS, effects = false)
        val dark = BackdropDim.darken(bg, overlay)
        // yang dilihat pengguna: langsung di atas latar, dan di atas kapsul/kartu kaca (tombol di layar lirik)
        return drawnTexts.minOf { minOf(ColorMath.contrast(it, dark), ColorMath.contrast(it, BackdropDim.withGlass(dark))) }
    }

    @Test fun drawnTextsIncludeBothTiers() {
        assertEquals(2, drawnTexts.size)
        assertTrue(drawnTexts.contains(LyricsFocus.INACTIVE_TEXT_ARGB))
    }

    @Test fun worstCaseWhiteCoverStillMeetsContrastForEveryLyricTier() {
        val c = lowestContrastAfterDim(0xFFFFFFFFL)
        assertTrue("kontras $c < $target", c >= target)
        val overlay = BackdropDim.minOverlayAlpha(0xFFFFFFFFL, LyricsFocus.BACKDROP_TEXTS, effects = false)
        assertTrue("peredam $overlay terlalu pekat (layar jadi hitam pekat)", overlay < 0.9)
    }

    @Test fun randomBrightCoversAllMeetContrast() {
        val rnd = Random(2024)
        repeat(3000) {
            val bg = (0xFFL shl 24) or (rnd.nextInt(0x1000000).toLong())
            val c = lowestContrastAfterDim(bg)
            assertTrue("bg=${java.lang.Long.toHexString(bg)} kontras=$c", c >= target)
        }
    }

    @Test fun lyricBackdropIsNeverLighterThanTheDefaultOne() {
        // Teks tak aktif lebih redup daripada teks sekunder standar, jadi peredamnya harus >= peredam standar.
        val rnd = Random(11)
        repeat(1000) {
            val bg = (0xFFL shl 24) or rnd.nextInt(0x1000000).toLong()
            assertTrue(
                BackdropDim.minOverlayAlpha(bg, LyricsFocus.BACKDROP_TEXTS, effects = false) >= BackdropDim.minOverlayAlpha(bg, BackdropDim.TEXTS, effects = false) - 1e-9,
            )
        }
    }

    @Test fun withoutTheCustomTextListAMidGrayCoverWouldFailForInactiveLines() {
        // Alasan daftar teks khusus ada: peredam standar tidak menjamin teks yang lebih redup.
        val rnd = Random(5)
        var found = false
        repeat(2000) {
            val bg = (0xFFL shl 24) or rnd.nextInt(0x1000000).toLong()
            val dark = BackdropDim.darken(bg, BackdropDim.minOverlayAlpha(bg, BackdropDim.TEXTS, effects = false))
            if (ColorMath.contrast(LyricsFocus.INACTIVE_TEXT_ARGB, dark) < target) found = true
        }
        assertTrue(found)
    }

    // ---- AutoFollow ----

    @Test fun followsByDefaultAndPausesWhileTheUserScrolls() {
        assertTrue(AutoFollow.shouldFollow(null, 1_000, userScrolling = false))
        assertFalse(AutoFollow.shouldFollow(null, 1_000, userScrolling = true))
    }

    @Test fun followResumesOnlyAfterTheGracePeriod() {
        val last = 10_000L
        assertFalse(AutoFollow.shouldFollow(last, last + AutoFollow.GRACE_MS - 1, false))
        assertTrue(AutoFollow.shouldFollow(last, last + AutoFollow.GRACE_MS, false))
        assertFalse(AutoFollow.shouldFollow(last, last + AutoFollow.GRACE_MS * 5, userScrolling = true))
    }
}
