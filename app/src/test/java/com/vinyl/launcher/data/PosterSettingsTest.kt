package com.vinyl.launcher.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Poster di dinding: dokumen lama aman, path divalidasi, foto tidak ikut cadangan dan tidak ditimpa preset/pemulihan. */
class PosterSettingsTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    private val path = "/data/user/0/app/files/backgrounds/poster-1.img"
    private val withPhoto = AppSettings(retro = RetroSettings(poster = PosterSettings(PosterSource.PHOTO, path)))

    @Test
    fun oldDocumentsReadAsAlbumPosterWithFrameOn() {
        val root = json.encodeToJsonElement(AppSettings.serializer(), withPhoto).jsonObject
        val retro = root[SettingsMigration.RETRO_KEY]!!.jsonObject
        val old = JsonObject(root + (SettingsMigration.RETRO_KEY to JsonObject(retro - "poster"))).toString()
        val back = SettingsMigration.decode(json, old)
        assertEquals(PosterSettings(), back.retro.poster)
        assertTrue(back.retro.props.frame)
    }

    @Test
    fun sanitizerKeepsAnAllowedPhotoAndDropsTheRest() {
        assertEquals(path, SettingsSanitizer.sanitize(withPhoto) { it == path }.retro.poster.photoPath)
        val dropped = SettingsSanitizer.sanitize(withPhoto) { false }.retro.poster
        assertNull(dropped.photoPath)
        assertEquals(PosterSource.ALBUM, dropped.source)
        val long = AppSettings(retro = RetroSettings(poster = PosterSettings(PosterSource.PHOTO, "/" + "a".repeat(2000))))
        assertNull(SettingsSanitizer.sanitize(long) { true }.retro.poster.photoPath)
    }

    @Test
    fun backupNeverCarriesThePhotoPathAndRestoreKeepsTheCurrentPoster() {
        val text = SettingsBackup.export(withPhoto)
        assertFalse(text.contains("poster-1.img"))
        val mine = PosterSettings(PosterSource.PHOTO, "/data/mine.img")
        val r = SettingsBackup.parse(text, AppSettings(retro = RetroSettings(poster = mine)), { true }) as SettingsBackup.ImportResult.Ok
        assertEquals(mine, r.settings.retro.poster)
    }

    @Test
    fun applyingAPresetKeepsTheDevicePoster() {
        val preset = ThemePreset(1, "P", HomeSettings(), CoverSettings(), RetroSettings(tvBody = TvBody.WALNUT))
        val mine = PosterSettings(PosterSource.PHOTO, path)
        assertEquals(mine, SettingsMigration.retroToApply(preset, RetroSettings(poster = mine)).poster)
    }
}
