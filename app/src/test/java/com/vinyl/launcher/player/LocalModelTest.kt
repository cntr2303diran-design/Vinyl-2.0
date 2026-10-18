package com.vinyl.launcher.player

import com.vinyl.launcher.audio.dsp.EqBand
import com.vinyl.launcher.audio.dsp.ParametricProfile
import com.vinyl.launcher.data.AppSettings
import com.vinyl.launcher.data.PlayerRules
import com.vinyl.launcher.data.PlayerSettings
import com.vinyl.launcher.data.SettingsBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelTest {
    private fun t(id: String, title: String, album: String = "", artist: String = "", track: Int = 0, disc: Int = 0, modified: Long = 0) =
        LocalTrack(id, title, artist = artist, album = album, trackNo = track, discNo = disc, modified = modified)

    @Test
    fun codecAndQualityLabels() {
        assertEquals("FLAC", LocalFormat.codec("audio/flac", "a.flac"))
        assertEquals("FLAC", LocalFormat.codec("application/octet-stream", "a.FLAC"))
        assertEquals("MP3", LocalFormat.codec("audio/mpeg", "a.mp3"))
        assertEquals("OPUS", LocalFormat.codec("audio/ogg", "a.opus"))
        assertEquals("AAC", LocalFormat.codec("audio/mp4", "a.m4a"))
        assertEquals("FLAC 24/96", LocalFormat.quality(LocalTrack("1", "x", mime = "audio/flac", fileName = "x.flac", sampleRate = 96_000, bitsPerSample = 24)))
        assertEquals("WAV 16/44,1", LocalFormat.quality(LocalTrack("1", "x", mime = "audio/wav", fileName = "x.wav", sampleRate = 44_100, bitsPerSample = 16)))
        assertEquals("MP3 320k", LocalFormat.quality(LocalTrack("1", "x", mime = "audio/mpeg", fileName = "x.mp3", bitrate = 320_000)))
        // M4A berisi ALAC dikenali dari kedalaman bitnya
        assertEquals("ALAC 24/48", LocalFormat.quality(LocalTrack("1", "x", mime = "audio/mp4", fileName = "x.m4a", sampleRate = 48_000, bitsPerSample = 24)))
        assertTrue(LocalFormat.hiRes(LocalTrack("1", "x", mime = "audio/flac", fileName = "x.flac", sampleRate = 44_100, bitsPerSample = 24)))
        assertFalse(LocalFormat.hiRes(LocalTrack("1", "x", mime = "audio/flac", fileName = "x.flac", sampleRate = 44_100, bitsPerSample = 16)))
        assertFalse(LocalFormat.hiRes(LocalTrack("1", "x", mime = "audio/mpeg", fileName = "x.mp3", sampleRate = 96_000)))
    }

    @Test
    fun onlyAudioFilesAreScanned() {
        assertTrue(LocalFormat.isAudio("audio/flac", "a.flac"))
        assertTrue(LocalFormat.isAudio("application/octet-stream", "a.flac"))
        assertTrue(LocalFormat.isAudio(null, "a.opus"))
        assertFalse(LocalFormat.isAudio("image/jpeg", "cover.jpg"))
        assertFalse(LocalFormat.isAudio("application/octet-stream", "notes.txt"))
        assertFalse(LocalFormat.isAudio("application/pdf", "a.flac.pdf"))
    }

    @Test
    fun albumOrderFollowsDiscAndTrackNumbers() {
        val list = listOf(
            t("4", "Akhir", album = "Biru", artist = "Z", disc = 2, track = 1),
            t("2", "Dua", album = "Biru", artist = "Z", disc = 1, track = 2),
            t("1", "Satu", album = "Biru", artist = "Z", disc = 1, track = 1),
            t("3", "Lain", album = "Abu", artist = "A", track = 1),
        )
        assertEquals(listOf("3", "1", "2", "4"), LocalLibraryModel.sorted(list, LocalSort.ALBUM).map { it.id })
        assertEquals(listOf("3", "1", "2", "4"), LocalLibraryModel.sorted(list, LocalSort.ARTIST).map { it.id })
        assertEquals(listOf("4", "2", "3", "1"), LocalLibraryModel.sorted(list, LocalSort.TITLE).map { it.id })
    }

    @Test
    fun sortingIgnoresAccentsCaseAndTheLeadingThe() {
        assertEquals("beatles", LocalLibraryModel.sortKey("The Beatles"))
        assertEquals("elan", LocalLibraryModel.sortKey("Élan"))
        val list = listOf(t("1", "Zebra"), t("2", "élan"), t("3", "Apel"))
        assertEquals(listOf("3", "2", "1"), LocalLibraryModel.sorted(list, LocalSort.TITLE).map { it.id })
    }

    @Test
    fun searchMatchesAllWordsInAnyField() {
        val list = listOf(
            LocalTrack("1", "Desert Moon", artist = "Dune Wanderer", album = "Sand"),
            LocalTrack("2", "Neon Lama", artist = "Band Kertas", album = "Kota"),
        )
        assertEquals(listOf("1"), LocalLibraryModel.search(list, "moon dune").map { it.id })
        assertEquals(listOf("2"), LocalLibraryModel.search(list, "KERTAS").map { it.id })
        assertEquals(2, LocalLibraryModel.search(list, "  ").size)
        assertTrue(LocalLibraryModel.search(list, "moon kertas").isEmpty())
    }

    @Test
    fun fallbackTitlesAndTrackNumbers() {
        assertEquals("Lagu Saya", LocalLibraryModel.titleFromFileName("01 - Lagu Saya.flac"))
        assertEquals("Intro", LocalLibraryModel.titleFromFileName("3. Intro.mp3"))
        assertEquals("2049", LocalLibraryModel.titleFromFileName("2049.flac"))
        assertEquals(3, LocalLibraryModel.parseTrackNo("3/12"))
        assertEquals(0, LocalLibraryModel.parseTrackNo("x"))
        assertEquals(0, LocalLibraryModel.parseTrackNo(null))
    }

    @Test
    fun playerSettingsAreSanitizedAndTheFolderStaysOnThisDevice() {
        val wild = PlayerSettings(
            folderUri = "file:///sdcard/Music",
            eq = ParametricProfile(preampDb = Float.NaN, bands = List(40) { EqBand(freq = 99_999f, gainDb = 99f) }),
        )
        val s = PlayerRules.sanitize(wild)
        assertNull(s.folderUri)
        assertEquals(0f, s.eq.preampDb)
        assertEquals(ParametricProfile.MAX_BANDS, s.eq.bands.size)
        assertTrue(s.eq.bands.all { it.freq <= EqBand.MAX_FREQ && it.gainDb <= EqBand.MAX_GAIN })
        // Cadangan tidak membawa folder (izinnya melekat di perangkat ini), tapi membawa pengaturan suara
        val mine = AppSettings(player = PlayerSettings(folderUri = "content://x/tree/1", folderName = "Musik", eqEnabled = true))
        val exported = SettingsBackup.export(mine)
        assertFalse(exported.contains("content://x/tree/1"))
        val other = AppSettings(player = PlayerSettings(folderUri = "content://y/tree/2", folderName = "Lagu"))
        val back = (SettingsBackup.parse(exported, other) as SettingsBackup.ImportResult.Ok).settings.player
        assertEquals("content://y/tree/2", back.folderUri)
        assertTrue(back.eqEnabled)
    }

    @Test
    fun savedEqListReplacesByNameAndIsBounded() {
        var p = PlayerSettings()
        p = PlayerRules.saveEq(p, ParametricProfile(name = "HD 600"))
        p = PlayerRules.saveEq(p, ParametricProfile(name = "HD 600", preampDb = -3f))
        assertEquals(1, p.savedEqs.size)
        assertEquals(-3f, p.savedEqs[0].preampDb)
        repeat(40) { p = PlayerRules.saveEq(p, ParametricProfile(name = "P$it")) }
        assertEquals(PlayerRules.MAX_SAVED_EQS, p.savedEqs.size)
        assertTrue(PlayerRules.deleteEq(p, "HD 600").savedEqs.none { it.name == "HD 600" })
    }
}
