package com.vinyl.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Aturan path gambar di pembersih pengaturan: latar memakai aturan gambar, foto poster punya aturannya sendiri. */
class SanitizerImageRulesTest {
    private val bg = "/data/user/0/app/files/backgrounds/latar-1.img"
    private val poster = "/data/user/0/app/files/poster/poster-1.vault"

    private val settings = AppSettings(
        home = HomeSettings(background = BackgroundSettings(type = BackgroundType.IMAGE, imagePath = bg)),
        retro = RetroSettings(poster = PosterSettings(PosterSource.PHOTO, poster)),
    )

    @Test
    fun aTrailingLambdaIsTheImageRuleAndAlsoJudgesThePoster() {
        // Panggilan bergaya lama tetap berarti "aturan gambar" (tidak terikat diam-diam ke aturan poster)
        val both = SettingsSanitizer.sanitize(settings) { it == bg || it == poster }
        assertEquals(bg, both.home.background.imagePath)
        assertEquals(poster, both.retro.poster.photoPath)
        val onlyBg = SettingsSanitizer.sanitize(settings) { it == bg }
        assertEquals(bg, onlyBg.home.background.imagePath)
        assertNull(onlyBg.retro.poster.photoPath)
        val none = SettingsSanitizer.sanitize(settings) { false }
        assertNull(none.home.background.imagePath)
        assertNull(none.retro.poster.photoPath)
    }

    @Test
    fun theDefaultRefusesEveryImage() {
        val clean = SettingsSanitizer.sanitize(settings)
        assertNull(clean.home.background.imagePath)
        assertNull(clean.retro.poster.photoPath)
        assertEquals(PosterSource.ALBUM, clean.retro.poster.source)
    }

    @Test
    fun thePosterRuleDecidesPostersAndTheImageRuleDecidesBackgrounds() {
        val c = SettingsSanitizer.sanitize(settings, { it == bg }, { it == poster })
        assertEquals(bg, c.home.background.imagePath)
        assertEquals(poster, c.retro.poster.photoPath)
        // Berkas poster tersegel tidak boleh dipakai sebagai gambar latar, dan sebaliknya
        val swapped = SettingsSanitizer.sanitize(settings, { it == poster }, { it == bg })
        assertNull(swapped.home.background.imagePath)
        assertNull(swapped.retro.poster.photoPath)
    }

    @Test
    fun thePosterRuleAlsoAppliesInsideSavedPresets() {
        val preset = ThemePreset(1, "P", HomeSettings(), CoverSettings(), RetroSettings(poster = PosterSettings(PosterSource.PHOTO, poster)))
        val s = AppSettings(presets = listOf(preset))
        assertEquals(poster, SettingsSanitizer.sanitize(s, { false }, { it == poster }).presets[0].retro!!.poster.photoPath)
        assertNull(SettingsSanitizer.sanitize(s, { true }, { false }).presets[0].retro!!.poster.photoPath)
    }
}
