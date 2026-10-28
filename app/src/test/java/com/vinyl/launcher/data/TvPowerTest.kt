package com.vinyl.launcher.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pilihan "Ingat tombol daya TV": aturan, nilai bawaan, penyimpanan, cadangan, dan preset. */
class TvPowerTest {
    // Sama dengan konfigurasi SettingsRepository
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    @Test
    fun theTvOnlyCountsAsOffWhenTheChoiceIsOn() {
        // Tanpa pilihan: tidak ada yang diingat, apa pun yang tersimpan
        assertFalse(TvPowerRules.effectiveOff(remember = false, off = true))
        assertFalse(TvPowerRules.effectiveOff(remember = false, off = false))
        // Dengan pilihan: keadaan mati diingat, keadaan menyala tidak membuat TV mati
        assertTrue(TvPowerRules.effectiveOff(remember = true, off = true))
        assertFalse(TvPowerRules.effectiveOff(remember = true, off = false))
    }

    /** Dipakai dua arah di ViewModel: yang disimpan = berlaku(pilihan, TV mati?) dan keadaan awal = berlaku(pilihan, tersimpan). */
    @Test
    fun aStaleOffStateNeverSurvivesTurningTheChoiceOff() {
        // TV mati dan pilihan menyala → tersimpan "mati"
        val stored = TvPowerRules.effectiveOff(remember = true, off = true)
        assertTrue(stored)
        // Pilihan dimatikan: nilai yang ditulis ulang adalah "menyala" meski TV sedang mati...
        assertFalse(TvPowerRules.effectiveOff(remember = false, off = true))
        // ...jadi menyalakan pilihan lagi sesudah TV menyala tidak membangkitkan keadaan mati yang lama
        assertFalse(TvPowerRules.effectiveOff(remember = true, off = false))
    }

    @Test
    fun theChoiceIsOffByDefaultSoTheTvAlwaysStartsOn() {
        assertFalse(RetroSettings().rememberTvPower)
        assertFalse(AppSettings().retro.rememberTvPower)
    }

    @Test
    fun anOldDocumentWithoutTheKeyLoadsAsOffAndKeepsItsStyle() {
        val s = SettingsMigration.decode(json, """{"retro":{"tvBody":"WALNUT","swapPages":true}}""")
        assertFalse(s.retro.rememberTvPower)
        assertEquals(TvBody.WALNUT, s.retro.tvBody)
        assertTrue(s.retro.swapPages)
    }

    @Test
    fun theChoiceSurvivesStorageAndABackup() {
        val s = AppSettings(retro = RetroSettings(rememberTvPower = true))
        val stored = SettingsMigration.decode(json, json.encodeToString(AppSettings.serializer(), s))
        assertTrue(stored.retro.rememberTvPower)
        val backup = SettingsBackup.parse(SettingsBackup.export(s), AppSettings()) as SettingsBackup.ImportResult.Ok
        assertTrue(backup.settings.retro.rememberTvPower)
        // Keadaan mati-nyala TV bukan bagian dokumen pengaturan: tidak ada kuncinya di berkas cadangan
        assertFalse(SettingsBackup.export(s).contains("tvPowerOff"))
    }

    @Test
    fun applyingAPresetChangesTheStyleButNotTheDeviceHabit() {
        val preset = ThemePreset(1, "P", HomeSettings(), CoverSettings(), RetroSettings(tvBody = TvBody.CHARCOAL, rememberTvPower = true))
        // Preset dibuat saat pilihan menyala, pengguna kini mematikannya: tetap mati
        val offNow = SettingsMigration.retroToApply(preset, RetroSettings(tvBody = TvBody.CREAM, rememberTvPower = false))
        assertEquals(TvBody.CHARCOAL, offNow.tvBody)
        assertFalse(offNow.rememberTvPower)
        // Sebaliknya: pilihan yang sedang menyala tidak dimatikan oleh preset lama
        val calm = preset.copy(retro = RetroSettings(tvBody = TvBody.WALNUT, rememberTvPower = false))
        val onNow = SettingsMigration.retroToApply(calm, RetroSettings(rememberTvPower = true))
        assertEquals(TvBody.WALNUT, onNow.tvBody)
        assertTrue(onNow.rememberTvPower)
    }

    @Test
    fun aPresetFromBeforeTheTvThemeAlsoKeepsTheDeviceHabit() {
        val old = ThemePreset(2, "Lama", HomeSettings(vinylStyle = VinylStyle.PICTURE_DISC, rpm = 45), CoverSettings())
        val r = SettingsMigration.retroToApply(old, RetroSettings(rememberTvPower = true))
        assertTrue(r.rememberTvPower)
        assertEquals(CassetteShell.PICTURE, r.cassetteShell)
        assertEquals(ReelSpeed.FAST, r.reelSpeed)
    }

    @Test
    fun applyingAPresetChangesNothingElseAboutTheCurrentChoice() {
        // Selain pilihan itu, hasilnya persis gaya milik preset
        val preset = ThemePreset(3, "P", HomeSettings(), CoverSettings(), RetroSettings(tvBody = TvBody.SPACE_AGE, crtStrength = 0.3f, swapPages = true))
        val r = SettingsMigration.retroToApply(preset, RetroSettings(rememberTvPower = true))
        assertEquals(SettingsMigration.retroFor(preset).copy(rememberTvPower = true), r)
    }
}
