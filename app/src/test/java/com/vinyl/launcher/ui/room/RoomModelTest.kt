package com.vinyl.launcher.ui.room

import com.vinyl.launcher.data.AlbumMotion
import com.vinyl.launcher.data.BackgroundType
import com.vinyl.launcher.data.RoomTone
import com.vinyl.launcher.ui.theme.BackdropDim
import com.vinyl.launcher.ui.theme.BackdropLift
import com.vinyl.launcher.ui.theme.ColorMath
import com.vinyl.launcher.ui.theme.Tokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomModelTest {

    // ---------- Mode layar
    @Test
    fun noTrackMeansStandbyWhateverTheSettings() {
        for (m in AlbumMotion.entries) for (p in listOf(true, false)) for (allowed in listOf(true, false)) {
            assertEquals(TvScreenMode.STANDBY, TvScreen.mode(hasTrack = false, isPlaying = p, motion = m, motionAllowed = allowed))
        }
    }

    @Test
    fun coverMovesOnlyWhenChosenPlayingAndAllowed() {
        assertEquals(TvScreenMode.MOTION, TvScreen.mode(true, true, AlbumMotion.ZOOM, true))
        assertEquals(TvScreenMode.DISPLAY, TvScreen.mode(true, false, AlbumMotion.ZOOM, true))   // jeda: gerak berhenti
        assertEquals(TvScreenMode.DISPLAY, TvScreen.mode(true, true, AlbumMotion.ZOOM, false))  // hapus animasi / hemat daya
        assertEquals(TvScreenMode.DISPLAY, TvScreen.mode(true, true, AlbumMotion.OFF, true))
    }

    // ---------- Geometri muka depan TV (tata letak panggung diuji di StageLayoutTest)
    @Test
    fun tvPartsDoNotOverlap() {
        assertTrue(!TvGeometry.SCREEN.overlaps(TvGeometry.PANEL))
        assertTrue(TvGeometry.SCREEN.inside() && TvGeometry.PANEL.inside() && TvGeometry.VFD.inside())
        assertTrue("VFD di dalam panel", TvGeometry.VFD.left >= TvGeometry.PANEL.left && TvGeometry.VFD.right <= TvGeometry.PANEL.right)
        val knobTop = TvGeometry.KNOB_CY - TvGeometry.KNOB_R * TvGeometry.ASPECT
        val knobBottom = TvGeometry.KNOB_CY + TvGeometry.KNOB_R * TvGeometry.ASPECT
        assertTrue("knob di atas VFD", knobBottom < TvGeometry.VFD.top)
        assertTrue(knobTop > TvGeometry.PANEL.top)
        // Urutan kolom kontrol dari atas: kisi speaker, knob penala, knob SEEK, VFD, lampu & tombol daya
        val tunerTop = TvGeometry.TUNER_CY - TvGeometry.TUNER_R * TvGeometry.ASPECT
        val tunerBottom = TvGeometry.TUNER_CY + TvGeometry.TUNER_R * TvGeometry.ASPECT
        assertTrue("kisi di atas knob penala", TvGeometry.GRILLE.bottom < tunerTop)
        assertTrue("knob penala di atas knob SEEK", tunerBottom < knobTop)
        assertTrue("lampu di bawah VFD", TvGeometry.LED_CY > TvGeometry.VFD.bottom && TvGeometry.POWER_CY < TvGeometry.PANEL.bottom)
        assertTrue(TvGeometry.SCREEN.left > TvGeometry.BEZEL.left && TvGeometry.SCREEN.right < TvGeometry.BEZEL.right)
    }

    // ---------- Kontras teks di atas proyeksi
    private val coverPixels: List<Long> = buildList {
        for (r in 0..255 step 51) for (g in 0..255 step 51) for (b in 0..255 step 51) add(0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong())
    }

    @Test
    fun roomTextStaysReadableOverAnyProjectionInTheLightRoom() {
        for (px in coverPixels) {
            val a = RoomInk.plateAlpha(px, RoomTone.LIGHT)
            val bg = BackdropLift.withShade(BackdropLift.lighten(RoomInk.over(px, RoomInk.wall(RoomTone.LIGHT), RoomInk.PROJECTION_ALPHA), a))
            for (t in BackdropLift.TEXTS) {
                val c = ColorMath.contrast(t, bg)
                assertTrue("px=%08X a=%.3f c=%.2f".format(px, a, c), c >= Tokens.A11y.MIN_CONTRAST)
            }
        }
    }

    @Test
    fun roomTextStaysReadableOverAnyProjectionInTheDimRoom() {
        for (px in coverPixels) {
            val a = RoomInk.plateAlpha(px, RoomTone.DIM)
            val bg = BackdropDim.darken(RoomInk.over(px, RoomInk.wall(RoomTone.DIM), RoomInk.PROJECTION_ALPHA), a)
            for (t in listOf(Tokens.Retro.CREAM_TEXT, Tokens.Retro.CREAM_TEXT_SECONDARY)) {
                assertTrue("px=%08X".format(px), ColorMath.contrast(t, bg) >= Tokens.A11y.MIN_CONTRAST)
            }
        }
    }

    @Test
    fun noProjectionNeedsNoPlate() {
        for (tone in RoomTone.entries) assertEquals(0.0, RoomInk.plateAlpha(null, tone), 0.0)
        // Cover putih di ruang terang juga tidak butuh pelat
        assertEquals(0.0, RoomInk.plateAlpha(0xFFFFFFFFL, RoomTone.LIGHT), 0.0)
    }

    @Test
    fun theOldCreamClockIsReplacedOnTheWhiteWallButKeptInTheDimRoom() {
        val oldDefault = 0xFFF2EFEAL   // warna jam bawaan tema vinyl
        assertEquals(Tokens.Retro.INK, RoomInk.readable(oldDefault, RoomTone.LIGHT))
        assertEquals(oldDefault, RoomInk.readable(oldDefault, RoomTone.DIM))
        // Warna gelap pilihan pengguna dipertahankan di ruang terang
        assertEquals(0xFF203040L, RoomInk.readable(0xFF203040L, RoomTone.LIGHT))
        // Alpha warna tersimpan diabaikan (teks digambar opak)
        assertEquals(0xFF203040L, RoomInk.readable(0x00203040L, RoomTone.LIGHT))
    }

    @Test
    fun aDarkWallChosenInTheVinylEraGetsCreamText() {
        assertEquals(RoomTone.DIM, RoomInk.textTone(RoomTone.LIGHT, 0xFF1B1B1FL))     // warna solid bawaan tema vinyl
        assertEquals(RoomTone.LIGHT, RoomInk.textTone(RoomTone.DIM, 0xFFF0E8D8L))
        assertEquals(RoomTone.LIGHT, RoomInk.textTone(RoomTone.LIGHT, null))
        assertEquals(RoomTone.DIM, RoomInk.textTone(RoomTone.DIM, null))
        // Untuk warna dinding apa pun, tinta yang dipilih >= 4,5:1 di atas dinding itu (dengan pelat bila perlu)
        for (r in 0..255 step 51) for (g in 0..255 step 51) for (b in 0..255 step 51) {
            val wall = 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
            val tone = RoomInk.textTone(RoomTone.LIGHT, wall)
            val a = RoomInk.plateAlpha(wall, tone, projectionAlpha = 1.0)
            val seen = if (tone == RoomTone.LIGHT) BackdropLift.withShade(BackdropLift.lighten(wall, a)) else BackdropDim.darken(wall, a)
            assertTrue("wall=%08X".format(wall), ColorMath.contrast(RoomInk.secondary(tone), seen) >= Tokens.A11y.MIN_CONTRAST)
        }
    }

    @Test
    fun overBlendsChannels() {
        assertEquals(0xFF808080L, RoomInk.over(0xFFFFFFFFL, 0xFF000000L, 0.5))
        assertEquals(0xFF000000L, RoomInk.over(0xFF000000L, 0xFFFFFFFFL, 1.0))
        assertEquals(0xFFFFFFFFL, RoomInk.over(0xFF000000L, 0xFFFFFFFFL, 0.0))
    }

    @Test
    fun theWallLampOnlyAppearsWhenTheRoomToneDecidesTheWall() {
        // Album dan gambar: dinding mengikuti nada ruang, jadi ketukan lampu terlihat
        assertTrue(RoomInk.toneDecidesWall(BackgroundType.ALBUM))
        assertTrue(RoomInk.toneDecidesWall(BackgroundType.IMAGE))
        // Warna atau gradasi pilihan sendiri: nada efektif mengikuti warna itu, lampu tidak dipasang
        assertEquals(false, RoomInk.toneDecidesWall(BackgroundType.SOLID))
        assertEquals(false, RoomInk.toneDecidesWall(BackgroundType.GRADIENT))
        // Dan benar: untuk dinding tanpa warna sendiri nada efektif = pilihan "Ruang", jadi mengubahnya mengubah ruangan
        for (room in RoomTone.entries) assertEquals(room, RoomInk.textTone(room, null))
    }
}
