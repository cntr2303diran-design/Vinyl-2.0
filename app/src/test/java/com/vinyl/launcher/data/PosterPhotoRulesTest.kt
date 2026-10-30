package com.vinyl.launcher.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Aturan foto poster tersegel: nama berkas, ukuran akhir, pengambilan sampel, dan putaran EXIF. */
class PosterPhotoRulesTest {
    private val scratches = mutableListOf<File>()

    @After
    fun cleanUp() {
        scratches.forEach { it.deleteRecursively() }
        scratches.clear()
    }

    private fun scratchDir(): File = Files.createTempDirectory("poster-rules").toFile().also { scratches += it }

    private fun touch(dir: File, name: String): File = File(dir, name).also { it.writeText("x") }

    private fun names(dir: File): List<String> = dir.list().orEmpty().sorted()

    @Test
    fun legacyNamesAreOnlyPosterDigitsImg() {
        for (ok in listOf("poster-0.img", "poster-1.img", "poster-1730000000000.img")) {
            assertTrue(ok, PosterPhotoRules.isLegacyName(ok))
            assertFalse("bukan nama tersegel: $ok", PosterPhotoRules.isSealedName(ok))
        }
        for (bad in listOf(
            "", "poster-.img", "poster-abc.img", "poster-12.vault", "poster-12.IMG", "poster-12.img.tmp", "poster-12.jpg",
            "home-12.img", "cover-12.img", "photo0-12.img", "poster12.img", "Poster-12.img", "../poster-12.img",
            "poster-12/../x.img", "poster-1 2.img", "poster--12.img", "poster-١٢.img", ".img", "poster-12.img\u0000",
            "poster-" + "1".repeat(80) + ".img",
        )) {
            assertFalse("seharusnya ditolak: '$bad'", PosterPhotoRules.isLegacyName(bad))
        }
        assertFalse(PosterPhotoRules.isLegacyName("poster-12.vault"))
    }

    @Test
    fun sweepingKeepsTheLegacyPhotoStillInUseAndTouchesNothingElse() {
        val dir = scratchDir()
        for (n in listOf("poster-1.img", "poster-2.img", "poster-3.img", "home-9.img", "cover-9.img", "poster-5.vault", "poster-x.img", "notes.txt")) touch(dir, n)
        File(dir, "poster-7.img").mkdir() // direktori bernama mirip: bukan berkas biasa, tidak boleh disentuh
        touch(File(dir, "poster-7.img"), "isi.txt")

        val removed = PosterPhotoRules.deleteLegacyExcept(dir, File(dir, "poster-2.img").path)

        assertEquals(2, removed)
        assertEquals(
            listOf("cover-9.img", "home-9.img", "notes.txt", "poster-2.img", "poster-5.vault", "poster-7.img", "poster-x.img"),
            names(dir),
        )
        assertTrue(File(dir, "poster-7.img/isi.txt").exists())
    }

    @Test
    fun theKeptPathMayBeWrittenInAnotherFormButStillMatches() {
        val dir = scratchDir()
        touch(dir, "poster-1.img")
        touch(dir, "poster-2.img")
        // "dir/./poster-2.img" dan "dir/sub/../poster-2.img" menunjuk berkas yang sama
        File(dir, "sub").mkdir()
        val roundabout = dir.path + File.separator + "sub" + File.separator + ".." + File.separator + "poster-2.img"

        assertEquals(1, PosterPhotoRules.deleteLegacyExcept(dir, roundabout))
        assertEquals(listOf("poster-2.img", "sub"), names(dir))
    }

    @Test
    fun withNothingToKeepEveryLegacyPhotoGoes() {
        val dir = scratchDir()
        for (n in listOf("poster-1.img", "poster-2.img", "home-9.img", "poster-3.vault")) touch(dir, n)

        assertEquals(2, PosterPhotoRules.deleteLegacyExcept(dir, null))
        assertEquals(listOf("home-9.img", "poster-3.vault"), names(dir))
        // Kedua kali: tidak ada lagi yang dihapus
        assertEquals(0, PosterPhotoRules.deleteLegacyExcept(dir, null))
    }

    @Test
    fun anUnreadablePathToKeepMeansNothingIsDeleted() {
        val dir = scratchDir()
        touch(dir, "poster-1.img")
        touch(dir, "poster-2.img")

        // Path berisi karakter NUL tidak bisa dibakukan: lebih aman tidak menghapus apa pun daripada salah menghapus
        assertEquals(0, PosterPhotoRules.deleteLegacyExcept(dir, "poster-2.img\u0000"))
        assertEquals(listOf("poster-1.img", "poster-2.img"), names(dir))
    }

    @Test
    fun aMissingFolderIsNotAnError() {
        val dir = scratchDir()
        assertEquals(0, PosterPhotoRules.deleteLegacyExcept(File(dir, "tidak-ada"), null))
        assertEquals(0, PosterPhotoRules.deleteLegacyExcept(File(dir, "tidak-ada"), "poster-1.img"))
    }

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
