package com.vinyl.launcher.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class ToneSynthTest {
    private fun peak(pcm: ShortArray, ch: Int) = pcm.filterIndexed { i, _ -> i % 2 == ch }.maxOf { abs(it.toInt()) }

    @Test
    fun onlyTheChosenChannelSounds() {
        val left = ToneSynth.stereo(ToneSynth.Side.LEFT)
        assertTrue(peak(left, 0) > 1000)
        assertEquals(0, peak(left, 1))
        val right = ToneSynth.stereo(ToneSynth.Side.RIGHT)
        assertEquals(0, peak(right, 0))
        assertTrue(peak(right, 1) > 1000)
    }

    @Test
    fun theLevelIsCappedForSafety() {
        val loud = ToneSynth.stereo(ToneSynth.Side.LEFT, dbfs = 0f)
        val cap = 32767 * 10.0.pow(ToneSynth.MAX_DBFS / 20.0)
        assertTrue(peak(loud, 0) <= cap + 1)
    }

    @Test
    fun itFadesInAndOutWithoutClicks() {
        val pcm = ToneSynth.stereo(ToneSynth.Side.RIGHT, ms = 500)
        assertEquals(0, pcm[1].toInt())
        assertTrue(abs(pcm[pcm.size - 1].toInt()) < 50)
        assertEquals(ToneSynth.RATE / 2 * 2, pcm.size)
        // Durasi dibatasi
        assertEquals(ToneSynth.RATE * 5 * 2, ToneSynth.stereo(ToneSynth.Side.LEFT, ms = 60_000).size)
    }
}
