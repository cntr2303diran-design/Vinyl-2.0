package com.vinyl.launcher.ui.cassette

import com.vinyl.launcher.data.ReelSpeed
import com.vinyl.launcher.ui.cassette.CassetteSwap.Phase
import com.vinyl.launcher.ui.theme.Tokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class CassetteModelTest {

    // ---------------- KnobSeek
    @Test
    fun clockwiseMovesForwardAndCounterClockwiseBack() {
        // Sumbu y layar ke bawah: dari kanan (0) ke bawah (π/2) = searah jarum jam
        assertEquals(0.25, KnobSeek.deltaTurns(0.0, PI / 2), 1e-9)
        assertEquals(-0.25, KnobSeek.deltaTurns(0.0, -PI / 2), 1e-9)
        assertEquals(60_000L + 7_500L, KnobSeek.target(60_000L, 0.25, 240_000L))
        assertEquals(60_000L - 7_500L, KnobSeek.target(60_000L, -0.25, 240_000L))
    }

    @Test
    fun crossingTheSeamDoesNotJump() {
        // Dari hampir +π ke hampir −π adalah gerakan kecil searah jarum jam, bukan satu putaran mundur
        val d = KnobSeek.deltaTurns(PI - 0.05, -PI + 0.05)
        assertEquals(0.1 / (2 * PI), d, 1e-9)
        assertTrue(KnobSeek.deltaTurns(-PI + 0.05, PI - 0.05) < 0)
    }

    @Test
    fun angleUsesScreenCoordinates() {
        assertEquals(0.0, KnobSeek.angle(10f, 0f, 0f, 0f), 1e-9)
        assertEquals(PI / 2, KnobSeek.angle(0f, 10f, 0f, 0f), 1e-9)
    }

    @Test
    fun positionIsClampedToTheSongAndEdgesAreReported() {
        assertEquals(0L, KnobSeek.target(5_000, -1.0, 240_000))
        assertEquals(240_000L, KnobSeek.target(230_000, 2.0, 240_000))
        assertTrue(KnobSeek.atEdge(5_000, -1.0, 240_000))
        assertTrue(KnobSeek.atEdge(230_000, 2.0, 240_000))
        assertFalse(KnobSeek.atEdge(60_000, 0.5, 240_000))
        // Durasi tak diketahui: hanya dibatasi di 0
        assertEquals(1_000_000L, KnobSeek.target(970_000, 1.0, 0))
    }

    @Test
    fun detentsTickEveryOneAndAQuarterSeconds() {
        assertEquals(0L, KnobSeek.detent(0.0))
        assertEquals(1L, KnobSeek.detent(1.0 / 24 + 1e-9))
        assertEquals(-1L, KnobSeek.detent(-0.001))
        assertEquals(1_250L, KnobSeek.MS_PER_TURN / KnobSeek.DETENTS_PER_TURN)
    }

    @Test
    fun bubbleText() {
        assertEquals("02:34 / 04:18", KnobSeek.bubble(154_000, 258_000))
        assertEquals("00:07", KnobSeek.bubble(7_900, 0))
        assertEquals("1:02:03 / 1:10:00", KnobSeek.bubble(3_723_000, 4_200_000))
        assertEquals("00:00", KnobSeek.clock(-5))
    }

    // ---------------- Reels
    @Test
    fun tapeMovesFromLeftToRightAsTheSongPlays() {
        val (l0, r0) = Reels.radii(0f)
        val (l1, r1) = Reels.radii(1f)
        assertEquals(Reels.FULL, l0, 1e-6f); assertEquals(Reels.HUB, r0, 1e-6f)
        assertEquals(Reels.HUB, l1, 1e-6f); assertEquals(Reels.FULL, r1, 1e-6f)
        var prev = Reels.radii(0f)
        for (i in 1..20) {
            val now = Reels.radii(i / 20f)
            assertTrue(now.first < prev.first && now.second > prev.second)
            // Luas pita kekal
            val area = now.first * now.first + now.second * now.second
            assertEquals(Reels.HUB * Reels.HUB + Reels.FULL * Reels.FULL, area, 1e-4f)
            prev = now
        }
        assertEquals(Reels.radii(0f), Reels.radii(Float.NaN))
        assertEquals(Reels.radii(1f), Reels.radii(7f))
    }

    @Test
    fun smallerReelsSpinFasterAndFastModeIsFaster() {
        val rpm = Reels.rpm(ReelSpeed.NORMAL)
        assertTrue(Reels.turnsPerSecond(Reels.HUB, rpm) > Reels.turnsPerSecond(Reels.FULL, rpm))
        assertTrue(Reels.rpm(ReelSpeed.FAST) > Reels.rpm(ReelSpeed.NORMAL))
        // Gulungan setengah penuh (luas) berputar tepat pada rpm yang diatur
        val half = Reels.radii(0.5f).first
        assertEquals(rpm / 60f, Reels.turnsPerSecond(half, rpm), 1e-4f)
    }

    // ---------------- CassetteSwap
    private val eject = Tokens.Motion.CASSETTE_EJECT_MS.toLong()
    private val insert = Tokens.Motion.CASSETTE_INSERT_MS.toLong()
    private val rapid = Tokens.Motion.CASSETTE_RAPID_MS.toLong()

    @Test
    fun aNormalChangeEjectsThenInserts() {
        val s = CassetteSwap("a")
        s.onTrack("b", 1_000, animate = true)
        assertEquals(Phase.EJECT, s.frame(1_000).phase)
        assertEquals("a", s.frame(1_000 + eject / 2).shownKey)          // kaset lama masih keluar
        val mid = s.frame(1_000 + eject + insert / 2)
        assertEquals(Phase.INSERT, mid.phase)
        assertEquals("b", mid.shownKey)
        assertEquals(0.5f, mid.progress, 0.01f)
        val end = s.frame(1_000 + eject + insert + 1)
        assertEquals(Phase.IDLE, end.phase)
        assertEquals("b", end.shownKey)
        assertFalse(s.running)
    }

    @Test
    fun rapidSkipsAreMergedIntoOneShortInsertOfTheNewestTrack() {
        val s = CassetteSwap("a")
        s.onTrack("b", 0, true)
        s.onTrack("c", 50, true)
        s.onTrack("d", 80, true)
        val f = s.frame(80)
        assertEquals(Phase.INSERT, f.phase)
        assertEquals("d", f.shownKey)
        assertEquals(Phase.IDLE, s.frame(80 + rapid + 1).phase)
        assertEquals("d", s.frame(10_000).shownKey)
    }

    @Test
    fun theSameTrackDoesNotAnimateAndReducedMotionSwapsInstantly() {
        val s = CassetteSwap("a")
        s.onTrack("a", 0, true)
        assertFalse(s.running)
        s.onTrack("b", 0, animate = false)
        assertEquals(CassetteSwap.Frame(Phase.IDLE, 1f, "b"), s.frame(0))
        // Pergantian ke "tidak ada lagu" juga dianimasikan sebagai kaset keluar
        s.onTrack(null, 100, true)
        assertEquals(Phase.EJECT, s.frame(100).phase)
        assertEquals(null, s.frame(100 + eject + insert + 1).shownKey)
    }

    @Test
    fun aLateFrameNeverSkipsTheFinalState() {
        val s = CassetteSwap(null)
        s.onTrack("x", 0, true)
        // Frame berikutnya baru datang sangat terlambat (layar tertutup): langsung selesai
        assertEquals(CassetteSwap.Frame(Phase.IDLE, 1f, "x"), s.frame(60_000))
    }
}
