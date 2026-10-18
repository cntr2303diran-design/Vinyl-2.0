package com.vinyl.launcher.ui.room

import com.vinyl.launcher.ui.theme.Tokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VfdTickerTest {
    private val cycle = Tokens.Motion.VFD_CYCLE_MS.toLong()
    private val lines = listOf(VfdTicker.line("clock", "12:34"), VfdTicker.line("state", "play"), VfdTicker.line("out", "Speaker"))

    @Test
    fun linesTakeTurns() {
        assertEquals("clock", VfdTicker.current(lines, 0)!!.key)
        assertEquals("clock", VfdTicker.current(lines, cycle - 1)!!.key)
        assertEquals("state", VfdTicker.current(lines, cycle)!!.key)
        assertEquals("out", VfdTicker.current(lines, 2 * cycle)!!.key)
        assertEquals("clock", VfdTicker.current(lines, 3 * cycle)!!.key)
    }

    @Test
    fun aNewOutputIsShownFirstForAWhile() {
        val p = VfdTicker.line("out", "Buds")
        assertEquals(p, VfdTicker.current(lines, 10_000, p, priorityAtMs = 9_000))
        assertEquals(p, VfdTicker.current(lines, 9_000 + Tokens.Motion.VFD_PRIORITY_MS - 1L, p, 9_000))
        // Setelah masa prioritas: kembali bergiliran
        val after = 9_000 + Tokens.Motion.VFD_PRIORITY_MS + 1000L
        assertEquals(VfdTicker.current(lines, after), VfdTicker.current(lines, after, p, 9_000))
        // Waktu prioritas di masa depan (jam berubah) tidak mengunci VFD
        assertEquals(VfdTicker.current(lines, 1_000), VfdTicker.current(lines, 1_000, p, priorityAtMs = 50_000))
    }

    @Test
    fun emptyMeansNothingAndNegativeTimeIsSafe() {
        assertNull(VfdTicker.current(emptyList(), 5_000))
        assertEquals("out", VfdTicker.current(lines, -cycle)!!.key)
    }

    @Test
    fun textIsUppercasedCleanedAndCut() {
        assertEquals("BLUETOOTH BU", VfdTicker.line("x", "  Bluetooth Buds Pro 3\n").text)
        assertEquals(VfdTicker.MAX_CHARS, VfdTicker.line("x", "a".repeat(40)).text.length)
    }

    @Test
    fun playbackWords() {
        assertEquals("STANDBY", VfdTicker.playback(false, true))
        assertEquals("PLAY", VfdTicker.playback(true, true))
        assertEquals("PAUSE", VfdTicker.playback(true, false))
    }
}
