package com.vinyl.launcher.ui.film

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrayTextTest {
    @Test
    fun hintExplainsTapAndHoldWithAMusicTapeInTheDeck() {
        assertEquals("Ketuk kaset untuk memasukkannya ke TV · tahan untuk mengubah", TrayText.hint(Tape.Music, scrollable = false))
    }

    @Test
    fun hintExplainsHowToEjectWithAFilmOrGameInTheDeck() {
        assertEquals("Ketuk kaset yang bertanda DI TV untuk mengembalikan kaset musik", TrayText.hint(Tape.Film("a"), scrollable = false))
        assertEquals("Ketuk kaset yang bertanda DI TV untuk mengembalikan kaset musik", TrayText.hint(Tape.Game("snake"), scrollable = false))
    }

    @Test
    fun hintMentionsSwipingOnlyWhenThereAreMoreCassettesOffScreen() {
        assertTrue(TrayText.hint(Tape.Music, scrollable = true).endsWith("· geser untuk kaset lain"))
        assertTrue(TrayText.hint(Tape.Film("a"), scrollable = true).endsWith("· geser untuk kaset lain"))
        assertFalse(TrayText.hint(Tape.Music, scrollable = false).contains("geser"))
    }

    @Test
    fun resumeBadgeShowsTheMinuteOrNothing() {
        assertNull(TrayText.resumeBadge(null))
        assertEquals("LANJUT 12:34", TrayText.resumeBadge(754_000L))
        assertEquals("LANJUT 1:02:03", TrayText.resumeBadge(3_723_000L))
    }

    @Test
    fun cardDescriptionAddsTheSpokenResumePointOnlyWhenThereIsOne() {
        assertEquals("Kaset Domain Publik, archive.org", TrayText.cardDescription("Domain Publik", "archive.org", null))
        assertEquals(
            "Kaset Domain Publik, archive.org. Lanjut dari 12 menit 34 detik",
            TrayText.cardDescription("Domain Publik", "archive.org", 754_000L),
        )
    }
}
