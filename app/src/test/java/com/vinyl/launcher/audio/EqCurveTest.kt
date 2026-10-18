package com.vinyl.launcher.audio

import com.vinyl.launcher.audio.dsp.EqBand
import com.vinyl.launcher.audio.dsp.FilterType
import com.vinyl.launcher.audio.dsp.ParametricProfile
import com.vinyl.launcher.data.AudioConsoleRules
import com.vinyl.launcher.data.AudioConsoleSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqCurveTest {
    @Test
    fun thirtyOneBandsCoverTheAudibleRangeInIncreasingOrder() {
        assertEquals(31, Eq.DP_CENTERS.size)
        for (i in 1 until Eq.DP_CUTOFFS.size) assertTrue(Eq.DP_CUTOFFS[i] > Eq.DP_CUTOFFS[i - 1])
        for (i in Eq.DP_CENTERS.indices) assertTrue(Eq.DP_CENTERS[i] <= Eq.DP_CUTOFFS[i])
        assertEquals(20_000f, Eq.DP_CUTOFFS.last())
    }

    @Test
    fun threeBandModeMapsOntoAnyBandLayoutLikeBefore() {
        val s = AudioConsoleSettings(values = EqValues(lowDb = 4f, midDb = -2f, highDb = 3f))
        // Tata letak lama (3 pita) memberi nilai yang sama persis dengan sebelum perubahan
        assertTrue(Eq.bandGains(s, Eq.CUTOFFS).contentEquals(floatArrayOf(4f, -2f, 3f)))
        val g = Eq.bandGains(s, Eq.DP_CUTOFFS)
        assertEquals(4f, g[Eq.DP_CENTERS.indexOfFirst { it == 100f }])
        assertEquals(-2f, g[Eq.DP_CENTERS.indexOfFirst { it == 1000f }])
        assertEquals(3f, g[Eq.DP_CENTERS.indexOfFirst { it == 10000f }])
    }

    @Test
    fun graphicModeInterpolatesBetweenOctaveSliders() {
        val graphic = List(10) { if (it == 5) 6f else 0f } // +6 dB di 1 kHz
        val s = AudioConsoleSettings(eqMode = EqMode.GRAPHIC10, graphic = graphic)
        assertEquals(6f, Eq.gainAt(s, 1000f), 1e-4f)
        assertEquals(3f, Eq.gainAt(s, 1414.2f), 0.01f) // setengah oktaf ke 2 kHz
        assertEquals(0f, Eq.gainAt(s, 20f), 0f)
        assertEquals(0f, Eq.gainAt(s, 20_000f), 0f)
        // Nilai liar dibersihkan
        assertEquals(10, Eq.cleanGraphic(listOf(99f, Float.NaN)).size)
        assertEquals(12f, Eq.cleanGraphic(listOf(99f))[0])
    }

    @Test
    fun profileModeFollowsTheParametricResponseAndKeepsItsPreamp() {
        val p = ParametricProfile("HP uji", preampDb = -6f, bands = listOf(EqBand(FilterType.PEAK, 1000f, 6f, 1f)))
        val s = AudioConsoleSettings(eqMode = EqMode.PROFILE, profile = p)
        assertEquals(6f, Eq.gainAt(s, 1000f), 0.01f)
        val gains = Eq.bandGains(s, Eq.DP_CUTOFFS)
        // Preamp profil (−6) sudah cukup sebagai headroom: pengaman tidak menurunkan lagi
        val (l, r) = Eq.inputGainsFor(s, gains)
        assertEquals(-6f, l, 0.01f)
        assertEquals(-6f, r, 0.01f)
        // Tanpa preamp di profil, pengaman menurunkan sebesar penguatan terbesar
        val (l2, _) = Eq.inputGainsFor(s.copy(profile = p.copy(preampDb = 0f)), gains)
        assertEquals(-(gains.max()), l2, 0.01f)
    }

    @Test
    fun applyingAPresetReturnsToThreeBandModeAndSanitizerRepairsTheRest() {
        val s = AudioConsoleSettings(eqMode = EqMode.GRAPHIC10)
        assertEquals(EqMode.THREE, AudioConsoleRules.applyPreset(s, "Bass hangat").eqMode)
        // Mode profil tanpa profil → kembali ke 3 pita
        assertEquals(EqMode.THREE, AudioConsoleRules.sanitize(AudioConsoleSettings(eqMode = EqMode.PROFILE, profile = null)).eqMode)
        assertEquals(10, AudioConsoleRules.sanitize(AudioConsoleSettings(graphic = listOf(1f))).graphic.size)
    }
}
