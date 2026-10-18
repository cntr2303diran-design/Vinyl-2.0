package com.vinyl.launcher.media

import com.vinyl.launcher.data.AppSettings
import com.vinyl.launcher.data.SettingsBackup
import com.vinyl.launcher.data.VideoRules
import com.vinyl.launcher.data.VideoSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoMatchTest {
    private val files = listOf(
        VideoFile("d1", "Dune Wanderer - Desert Moon.mp4"),
        VideoFile("d2", "Arunika – Hujan Bulan Juni.webm"),
        VideoFile("d3", "Pelabuhan.mkv"),
        VideoFile("d4", "Hits.mp4"),
        VideoFile("d5", "Beyoncé - Renaissance.MP4"),
        VideoFile("d6", "tanpa pemisah.mp4"),
    )

    @Test
    fun normalizationIgnoresCaseAccentsPunctuationAndExtension() {
        assertEquals("beyonce renaissance", VideoMatch.normalize("Beyoncé: Renaissance!"))
        assertEquals("dune wanderer" to "desert moon", VideoMatch.parse("DUNE wanderer - Desert-Moon.mp4"))
        assertNull(VideoMatch.parse("tanpa pemisah.mp4"))
        assertEquals("a.b", VideoMatch.stripExtension("a.b.mp4"))
        assertEquals("v1.0 final cut version", VideoMatch.stripExtension("v1.0 final cut version"))
    }

    @Test
    fun matchesInPriorityOrder() {
        val none = emptyMap<String, String>()
        assertEquals(VideoMatch.Reason.ARTIST_ALBUM, VideoMatch.find(files, none, "Dune Wanderer", "Desert Moon", "Lagu 1")!!.reason)
        val t = VideoMatch.find(files, none, "Arunika", "Album Lain", "Hujan Bulan Juni")!!
        assertEquals("d2", t.file.documentId)
        assertEquals(VideoMatch.Reason.ARTIST_TITLE, t.reason)
        assertEquals("d3", VideoMatch.find(files, none, "Siapa", "Pelabuhan", "x")!!.file.documentId)
        assertEquals("d5", VideoMatch.find(files, none, "Beyonce", "RENAISSANCE", "")!!.file.documentId)
        // Album pendek / umum tidak dicocokkan berdasarkan nama album saja
        assertNull(VideoMatch.find(files, none, "Siapa", "Hi", "x"))
        assertNull(VideoMatch.find(files, none, "Artis Lain", "Album Lain", "Judul Lain"))
    }

    @Test
    fun aManualLinkWinsAndAStaleLinkFallsBack() {
        val key = VideoMatch.linkKey("Dune Wanderer", "Desert Moon", "")!!
        val m = VideoMatch.find(files, mapOf(key to "d4"), "Dune Wanderer", "Desert Moon", "Lagu")!!
        assertEquals("d4", m.file.documentId)
        assertEquals(VideoMatch.Reason.LINKED, m.reason)
        // Berkas tautan sudah hilang → aturan nama berkas dipakai
        assertEquals("d1", VideoMatch.find(files, mapOf(key to "hilang"), "Dune Wanderer", "Desert Moon", "Lagu")!!.file.documentId)
        // Tanpa album: kunci per judul
        assertTrue(VideoMatch.linkKey("A", "", "Judul")!!.startsWith("t:"))
        assertNull(VideoMatch.linkKey("", "", ""))
    }

    @Test
    fun rulesAndSanitizing() {
        var v = VideoSettings(enabled = true, folderUri = "content://tree/1", folderName = "Video")
        v = VideoRules.link(v, "Dune Wanderer", "Desert Moon", "Lagu", "d1", "Dune Wanderer - Desert Moon.mp4")
        assertEquals("Dune Wanderer – Desert Moon", v.links.single().label)
        assertEquals("d1", VideoRules.linkMap(v)[v.links.single().key])
        // Ganti folder menghapus tautan lama; folder sama mempertahankannya
        assertEquals(1, VideoRules.changeFolder(v, "content://tree/1", "Video").links.size)
        assertEquals(0, VideoRules.changeFolder(v, "content://tree/2", "Lain").links.size)
        assertEquals(0, VideoRules.unlink(v, v.links.single().key).links.size)
        // URI bukan content:// ditolak beserta tautannya
        val bad = VideoRules.sanitize(v.copy(folderUri = "file:///sdcard/Movies"))
        assertNull(bad.folderUri)
        assertTrue(bad.links.isEmpty())
    }

    @Test
    fun backupsNeverCarryTheFolderAndImportKeepsThisDevicesFolder() {
        val mine = AppSettings(video = VideoSettings(enabled = true, folderUri = "content://tree/1", folderName = "Video"))
        val exported = SettingsBackup.export(mine)
        assertTrue("content://tree/1" !in exported)
        val other = AppSettings(video = VideoSettings(enabled = false, folderUri = "content://tree/9", folderName = "Lain"))
        val imported = (SettingsBackup.parse(exported, other) as SettingsBackup.ImportResult.Ok).settings
        assertEquals(other.video, imported.video)
    }
}
