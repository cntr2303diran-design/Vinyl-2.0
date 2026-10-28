package com.vinyl.launcher.ui.film

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmNoticeTest {
    @Test
    fun noVideoNoticeNamesTheCauseAndTheRemedy() {
        val n = FilmNotice.NO_VIDEO
        assertEquals("TIDAK ADA VIDEO", n.title)
        assertTrue(n.detail!!.contains("DRM"))
        assertTrue(n.spoken.startsWith("TIDAK ADA VIDEO. "))
    }

    @Test
    fun resumedNoticeShowsTheTimeWithLatinDigits() {
        assertEquals("LANJUT 12:34", FilmNotice.resumed(754_000).title)
        assertEquals("LANJUT 1:02:03", FilmNotice.resumed(3_723_000).title)
        assertNull(FilmNotice.resumed(754_000).detail)
        assertEquals("LANJUT 12:34", FilmNotice.resumed(754_000).spoken)
    }

    @Test
    fun noticesDisappearOnTheirOwn() {
        assertTrue(FilmNotice.SHOW_MS in 2_000..8_000)
    }
}
