package com.vinyl.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Aturan foto poster tersegel: nama berkas, ukuran akhir, pengambilan sampel, dan putaran EXIF. */
class PosterPhotoRulesTest {
    @Test
    fun aFileNameRoundTripsThroughTheValidator() {
        for (t in listOf(0L, 1L, 1_730_000_000_000L, Long.MAX_VALUE)) {
            val name = PosterPhotoRules.fileName(t)
            assertEquals("poster-$t.vault", name)
            assertTrue(name, PosterPhotoRules.isSealedName(name))
        }
    }

    @Test
    fun onlyOurOwnNamesAreAccepted() {
        for (bad in listOf(
            "", "poster-.vault", "poster-abc.vault", "poster-12.vault.tmp", "poster-12.jpg", "poster-12.VAULT",
            "Poster-12.vault", "poster12.vault", "../poster-12.vault", "poster-12/../x.vault", "poster-1 2.vault",
            "poster--12.vault", "poster-١٢.vault", ".vault", "poster-12.vault\u0000",
            "poster-" + "1".repeat(80) + ".vault",
        )) {
            assertFalse("seharusnya ditolak: '$bad'", PosterPhotoRules.isSealedName(bad))
        }
    }

    @Test
    fun smallImagesAreNeverEnlarged() {
        assertEquals(100 to 50, PosterPhotoRules.scaledSize(100, 50))
        assertEquals(640 to 640, PosterPhotoRules.scaledSize(640, 640))
    }

    @Test
    fun largeImagesShrinkSoTheLongestSideFitsAndTheRatioHolds() {
        assertEquals(640 to 480, PosterPhotoRules.scaledSize(4000, 3000))
        assertEquals(480 to 640, PosterPhotoRules.scaledSize(3000, 4000))
        assertEquals(640 to 640, PosterPhotoRules.scaledSize(2048, 2048))
        assertEquals(320 to 160, PosterPhotoRules.scaledSize(1000, 500, max = 320))
    }

    @Test
    fun extremeAspectRatiosKeepAtLeastOnePixel() {
        val (w, h) = PosterPhotoRules.scaledSize(100_000, 10)
        assertEquals(640, w)
        assertTrue(h >= 1)
    }

    @Test
    fun brokenDimensionsFallBackToOnePixel() {
        assertEquals(1 to 1, PosterPhotoRules.scaledSize(0, 100))
        assertEquals(1 to 1, PosterPhotoRules.scaledSize(100, -1))
        assertEquals(1 to 1, PosterPhotoRules.scaledSize(0, 0))
    }

    @Test
    fun theSampleSizeIsAPowerOfTwoThatKeepsAtLeastTheTargetResolution() {
        assertEquals(1, PosterPhotoRules.sampleSize(500, 400))
        assertEquals(1, PosterPhotoRules.sampleSize(1279, 800))
        assertEquals(2, PosterPhotoRules.sampleSize(1280, 800))
        assertEquals(4, PosterPhotoRules.sampleSize(4000, 3000))
        assertEquals(8, PosterPhotoRules.sampleSize(5120, 100))
        for (longest in listOf(10, 640, 641, 1279, 1280, 5000, 12_000)) {
            val sample = PosterPhotoRules.sampleSize(longest, longest / 2)
            assertTrue("pangkat dua: $sample", sample >= 1 && (sample and (sample - 1)) == 0)
            if (sample > 1) assertTrue("hasilnya tidak di bawah target", longest / sample >= PosterPhotoRules.MAX_PX)
            assertTrue("sampel berikutnya terlalu kecil", longest / (sample * 2) < PosterPhotoRules.MAX_PX)
        }
    }

    @Test
    fun theSampleSizeHonoursAnExplicitTarget() {
        assertEquals(1, PosterPhotoRules.sampleSize(640, 480, 512))
        assertEquals(4, PosterPhotoRules.sampleSize(640, 480, 100))
    }

    @Test
    fun exifOrientationMapsToTheRightTurn() {
        assertEquals(0, PosterPhotoRules.exifRotation(1))
        assertEquals(180, PosterPhotoRules.exifRotation(3))
        assertEquals(90, PosterPhotoRules.exifRotation(6))
        assertEquals(270, PosterPhotoRules.exifRotation(8))
        // Tak dikenal, tak ada (0), dan variasi cermin tidak diputar
        for (other in listOf(0, 2, 4, 5, 7, 9, -1, 99)) assertEquals("$other", 0, PosterPhotoRules.exifRotation(other))
    }

    @Test
    fun theStoredPhotoStaysSmallEnoughToBeReadBack() {
        // Foto 640 px q88 jauh di bawah batas baca; batas ini hanya melindungi dari berkas asing yang besar
        assertTrue(PosterPhotoRules.MAX_SEALED_BYTES >= 1_000_000L)
        assertEquals("poster", PosterPhotoRules.DIR)
        assertEquals(".vault", PosterPhotoRules.EXT)
    }
}
