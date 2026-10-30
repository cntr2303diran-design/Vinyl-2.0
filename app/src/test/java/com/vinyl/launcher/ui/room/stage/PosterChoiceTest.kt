package com.vinyl.launcher.ui.room.stage

import com.vinyl.launcher.data.PosterSettings
import com.vinyl.launcher.data.PosterSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PosterChoiceTest {
    private val photo = PosterSettings(PosterSource.PHOTO, "/data/x/poster-1.img")

    @Test
    fun aLoadedPhotoWinsOverTheAlbumCover() {
        assertEquals(PosterShown.PHOTO, PosterChoice.shown(photo, photoLoaded = true, hasArt = true))
        assertEquals(PosterShown.PHOTO, PosterChoice.shown(photo, photoLoaded = true, hasArt = false))
    }

    @Test
    fun aMissingOrBrokenPhotoFallsBackToTheCover() {
        assertEquals(PosterShown.ALBUM, PosterChoice.shown(photo, photoLoaded = false, hasArt = true))
        assertEquals(PosterShown.EMPTY, PosterChoice.shown(photo, photoLoaded = false, hasArt = false))
    }

    @Test
    fun albumSourceNeverShowsAStoredPhoto() {
        val p = PosterSettings(PosterSource.ALBUM, "/data/x/poster-1.img")
        assertEquals(PosterShown.ALBUM, PosterChoice.shown(p, photoLoaded = true, hasArt = true))
        assertEquals(PosterShown.EMPTY, PosterChoice.shown(PosterSettings(), photoLoaded = true, hasArt = false))
    }

    @Test
    fun centerCropNeverStretchesAndKeepsTheFrameAspect() {
        val cases = listOf(
            Triple(1000, 500, 60f to 78f), Triple(500, 1000, 60f to 78f), Triple(600, 780, 60f to 78f), Triple(1, 1, 48f to 62f),
        )
        for ((w, h, dst) in cases) {
            val (fx, fy) = PosterChoice.cropFraction(w, h, dst.first, dst.second)
            assertTrue("$w x $h", fx in 0f..1f && fy in 0f..1f && (fx == 1f || fy == 1f))
            val cropAspect = (w * fx) / (h * fy)
            assertEquals("$w x $h", dst.first / dst.second, cropAspect, 0.001f)
        }
    }

    @Test
    fun degenerateSizesAreSafe() {
        assertEquals(1f to 1f, PosterChoice.cropFraction(0, 10, 5f, 5f))
        assertEquals(1f to 1f, PosterChoice.cropFraction(10, 10, 0f, 5f))
    }
}
