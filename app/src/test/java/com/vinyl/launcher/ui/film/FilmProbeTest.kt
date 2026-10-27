package com.vinyl.launcher.ui.film

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmProbeTest {
    @Test
    fun readsTheJsonStringThatEvaluateJavascriptReturns() {
        assertEquals(VideoStatus(true, 12_000, 5_400_000, false), FilmProbe.parse("\"1,12000,5400000,0\""))
        assertEquals(VideoStatus(false, 0, 90_000, false), FilmProbe.parse("\"0,0,90000,0\""))
        assertEquals(VideoStatus(false, 90_000, 90_000, true), FilmProbe.parse("0,90000,90000,1"))
    }

    @Test
    fun anUnknownDurationBecomesZero() {
        assertEquals(VideoStatus(true, 5_000, 0, false), FilmProbe.parse("\"1,5000,-1,0\""))
    }

    @Test
    fun positionNeverExceedsTheDuration() {
        assertEquals(VideoStatus(true, 60_000, 60_000, false), FilmProbe.parse("\"1,75000,60000,0\""))
    }

    @Test
    fun noVideoOrNoAnswerIsNull() {
        assertNull(FilmProbe.parse(null))
        assertNull(FilmProbe.parse("null"))
        assertNull(FilmProbe.parse("\"\""))
        assertNull(FilmProbe.parse(""))
    }

    @Test
    fun rejectsAnythingThatIsNotFourSaneIntegers() {
        val bad = listOf(
            "\"1,2,3\"", "\"1,2,3,4,5\"", "\"2,0,0,0\"", "\"1,0,0,2\"", "\"a,0,0,0\"", "\"1,-5,10,0\"", "\"1,1.5,10,0\"",
            "\"1,0,NaN,0\"", "\"1,0,Infinity,0\"", "\"1,0,-2,0\"", "\"1, 0,10,0\"", "\"1,0,10,0 \"", "\"1,99999999999,10,0\"",
            "\"1,400000000,10,0\"", "\"1,0,400000000,0\"", "\"1,+5,10,0\"", "\"1,0x10,10,0\"", "\"1,1e3,10,0\"",
            "<script>alert(1)</script>", "\"1,0,10,0\\n\"",
        )
        for (b in bad) assertNull("harus ditolak: $b", FilmProbe.parse(b))
    }

    @Test
    fun rejectsOversizedReplies() {
        assertNull(FilmProbe.parse("\"" + "1".repeat(500) + "\""))
        assertNull(FilmProbe.parse("\"1,0,10,0" + " ".repeat(200) + "\""))
    }

    @Test
    fun scriptsContainOnlyOurFixedTextAndIntegers() {
        for (js in listOf(FilmProbe.STATUS_JS, FilmProbe.TOGGLE_JS, FilmProbe.PAUSE_JS, FilmProbe.seekToJs(123_456), FilmProbe.seekByJs(-10_000))) {
            assertTrue(js.startsWith("(function("))
            assertTrue(js.endsWith("()") || js.endsWith(")"))
            assertEquals("kurung buka/tutup harus seimbang: $js", js.count { it == '(' }, js.count { it == ')' })
            assertEquals(js.count { it == '{' }, js.count { it == '}' })
            assertFalse(js.contains("addJavascriptInterface"))
        }
    }

    @Test
    fun seekArgumentsAreClampedIntegers() {
        assertTrue(FilmProbe.seekToJs(Long.MAX_VALUE).endsWith("})(360000000)"))
        assertTrue(FilmProbe.seekToJs(-5).endsWith("})(0)"))
        assertTrue(FilmProbe.seekByJs(Long.MIN_VALUE).endsWith("})(-360000000)"))
        assertTrue(FilmProbe.seekByJs(10_000).endsWith("})(10000)"))
        assertNotNull(FilmProbe.seekByJs(0))
    }

    @Test
    fun positionIsExtrapolatedWhilePlayingAndFrozenWhilePausedOrEnded() {
        val playing = FilmSample(VideoStatus(true, 10_000, 100_000, false), atMs = 1_000)
        assertEquals(10_000L, playing.positionAt(1_000))
        assertEquals(12_500L, playing.positionAt(3_500))
        assertEquals(100_000L, playing.positionAt(10_000_000))
        assertEquals(10_000L, playing.positionAt(0))
        assertEquals(10_000L, FilmSample(VideoStatus(false, 10_000, 100_000, false), 1_000).positionAt(50_000))
        assertEquals(100_000L, FilmSample(VideoStatus(false, 100_000, 100_000, true), 1_000).positionAt(50_000))
        assertEquals(15_000L, FilmSample(VideoStatus(true, 10_000, 0, false), 0).positionAt(5_000))
    }

    @Test
    fun updateKeepsTheSameSampleWhileNothingMeaningfulChanged() {
        val first = FilmProbe.update(null, VideoStatus(true, 10_000, 100_000, false), nowMs = 0)!!
        // Satu detik kemudian video benar-benar sudah di 11.000 → tidak ada yang perlu diumumkan
        assertTrue(first === FilmProbe.update(first, VideoStatus(true, 11_000, 100_000, false), 1_000))
        assertTrue(first === FilmProbe.update(first, VideoStatus(true, 11_400, 100_000, false), 1_000))
    }

    @Test
    fun updatePublishesPlayPauseSeeksEndAndDurationChanges() {
        val first = FilmProbe.update(null, VideoStatus(true, 10_000, 100_000, false), 0)!!
        assertFalse(first === FilmProbe.update(first, VideoStatus(false, 11_000, 100_000, false), 1_000))
        assertFalse(first === FilmProbe.update(first, VideoStatus(true, 60_000, 100_000, false), 1_000))
        assertFalse(first === FilmProbe.update(first, VideoStatus(true, 11_000, 120_000, false), 1_000))
        assertFalse(first === FilmProbe.update(first, VideoStatus(true, 11_000, 100_000, true), 1_000))
    }

    @Test
    fun whilePausedAnyPositionChangeIsPublishedAndNoVideoClearsTheSample() {
        val paused = FilmProbe.update(null, VideoStatus(false, 10_000, 100_000, false), 0)!!
        assertTrue(paused === FilmProbe.update(paused, VideoStatus(false, 10_000, 100_000, false), 5_000))
        assertFalse(paused === FilmProbe.update(paused, VideoStatus(false, 12_000, 100_000, false), 5_000))
        assertNull(FilmProbe.update(paused, null, 5_000))
        assertNull(FilmProbe.update(null, null, 0))
    }
}
