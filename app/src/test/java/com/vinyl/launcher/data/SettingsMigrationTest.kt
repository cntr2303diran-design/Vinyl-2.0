package com.vinyl.launcher.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMigrationTest {
    // Sama dengan konfigurasi SettingsRepository
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Dokumen seperti yang disimpan versi vinyl: semua field model sekarang, TANPA kunci "retro". */
    private fun legacyDoc(s: AppSettings): String {
        val obj = json.encodeToJsonElement(AppSettings.serializer(), s).jsonObject
        return JsonObject(obj - SettingsMigration.RETRO_KEY).toString()
    }

    @Test
    fun aDocumentWrittenByTheVinylVersionKeepsEveryOldValue() {
        val old = AppSettings(
            home = HomeSettings(vinylStyle = VinylStyle.TRANSPARENT, tonearmStyle = TonearmStyle.GOLD, rpm = 45, turntableSize = 0.7f),
            hiddenApps = setOf("com.a/com.a.Main"),
            onboardingDone = true,
            music = MusicSettings(sleepMinutes = 45),
        )
        val back = SettingsMigration.decode(json, legacyDoc(old))
        // Semua nilai lama utuh...
        assertEquals(old.home, back.home)
        assertEquals(old.hiddenApps, back.hiddenApps)
        assertEquals(old.music, back.music)
        assertTrue(back.onboardingDone)
        // ...dan gaya TV tabung diturunkan dari gaya vinyl, bukan diisi bawaan
        assertEquals(CassetteShell.TRANSPARENT, back.retro.cassetteShell)
        assertEquals(KnobStyle.BRASS, back.retro.knobStyle)
        assertEquals(ReelSpeed.FAST, back.retro.reelSpeed)
    }

    @Test
    fun aMinimalHandWrittenOldDocumentStillLoads() {
        // Bentuk dokumen lama yang hanya menyimpan sebagian field (field lain memakai bawaan)
        val raw = """{"home":{"vinylStyle":"COLORED","tonearmStyle":"BLACK","rpm":33},"onboardingDone":true}"""
        val s = SettingsMigration.decode(json, raw)
        assertEquals(VinylStyle.COLORED, s.home.vinylStyle)
        assertTrue(s.onboardingDone)
        assertEquals(TvBody.SPACE_AGE, s.retro.tvBody)
        assertEquals(CassetteShell.COLORED, s.retro.cassetteShell)
        assertEquals(KnobStyle.BAKELITE, s.retro.knobStyle)
        assertEquals(ReelSpeed.NORMAL, s.retro.reelSpeed)
    }

    @Test
    fun aDocumentThatAlreadyHasRetroSettingsIsUsedAsIs() {
        // Pengguna sudah memilih gaya TV tabung yang BERBEDA dari turunan gaya vinylnya: pilihannya tidak boleh ditimpa
        val s = AppSettings(
            home = HomeSettings(vinylStyle = VinylStyle.TRANSPARENT),
            retro = RetroSettings(cassetteShell = CassetteShell.PICTURE, tvBody = TvBody.WALNUT, crtStrength = 0.2f),
        )
        val back = SettingsMigration.decode(json, json.encodeToString(AppSettings.serializer(), s))
        assertEquals(s.retro, back.retro)
    }

    @Test
    fun everyVinylStyleHasAnEquivalent() {
        val expected = mapOf(
            VinylStyle.CLASSIC to CassetteShell.BLACK,
            VinylStyle.COLORED to CassetteShell.COLORED,
            VinylStyle.TRANSPARENT to CassetteShell.TRANSPARENT,
            VinylStyle.PICTURE_DISC to CassetteShell.PICTURE,
        )
        assertEquals(VinylStyle.entries.toSet(), expected.keys)
        expected.forEach { (v, shell) -> assertEquals(shell, SettingsMigration.fromLegacy(HomeSettings(vinylStyle = v)).cassetteShell) }
        val knobs = mapOf(TonearmStyle.SILVER to KnobStyle.CHROME, TonearmStyle.BLACK to KnobStyle.BAKELITE, TonearmStyle.GOLD to KnobStyle.BRASS)
        assertEquals(TonearmStyle.entries.toSet(), knobs.keys)
        knobs.forEach { (t, k) -> assertEquals(k, SettingsMigration.fromLegacy(HomeSettings(tonearmStyle = t)).knobStyle) }
    }

    @Test
    fun oldPresetsGetAnEquivalentWhenApplied() {
        val old = ThemePreset(1, "Lama", HomeSettings(vinylStyle = VinylStyle.PICTURE_DISC, rpm = 45), CoverSettings())
        assertNull(old.retro)
        val r = SettingsMigration.retroFor(old)
        assertEquals(CassetteShell.PICTURE, r.cassetteShell)
        assertEquals(ReelSpeed.FAST, r.reelSpeed)
        val mine = RetroSettings(tvBody = TvBody.CHARCOAL)
        assertEquals(mine, SettingsMigration.retroFor(old.copy(retro = mine)))
    }

    @Test
    fun anOldPresetInsideAStoredDocumentStaysWithoutRetroUntilApplied() {
        val s = AppSettings(presets = listOf(ThemePreset(7, "P", HomeSettings(vinylStyle = VinylStyle.COLORED), CoverSettings())))
        val raw = legacyDoc(s).replace(",\"retro\":null", "")
        val back = SettingsMigration.decode(json, raw)
        assertEquals(1, back.presets.size)
        assertEquals(CassetteShell.COLORED, SettingsMigration.retroFor(back.presets[0]).cassetteShell)
    }

    @Test
    fun aBackupMadeByTheVinylVersionImportsWithItsStyle() {
        val old = AppSettings(home = HomeSettings(vinylStyle = VinylStyle.TRANSPARENT, tonearmStyle = TonearmStyle.SILVER))
        val exported = SettingsBackup.export(old)
        // Cadangan versi vinyl: buang "retro" dari bagian settings
        val root = Json.parseToJsonElement(exported).jsonObject
        val settings = root["settings"]!!.jsonObject
        val legacy = JsonObject(root + ("settings" to JsonObject(settings - SettingsMigration.RETRO_KEY))).toString()
        val r = SettingsBackup.parse(legacy, AppSettings())
        assertTrue(r is SettingsBackup.ImportResult.Ok)
        val s = (r as SettingsBackup.ImportResult.Ok).settings
        assertEquals(VinylStyle.TRANSPARENT, s.home.vinylStyle)
        assertEquals(CassetteShell.TRANSPARENT, s.retro.cassetteShell)
        assertEquals(KnobStyle.CHROME, s.retro.knobStyle)
    }

    @Test
    fun aNewBackupRoundTripsRetroSettings() {
        val s = AppSettings(retro = RetroSettings(albumMotion = AlbumMotion.ZOOM, vfdColor = VfdColor.GREEN, swapPages = true))
        val r = SettingsBackup.parse(SettingsBackup.export(s), AppSettings()) as SettingsBackup.ImportResult.Ok
        assertEquals(s.retro, r.settings.retro)
    }

    @Test
    fun brokenBackupsAreStillRejectedCleanly() {
        for (bad in listOf("", "[]", "\"x\"", "{", """{"format":"vinyl-settings","version":1,"settings":[]}""")) {
            val r = SettingsBackup.parse(bad, AppSettings())
            assertTrue(bad, r is SettingsBackup.ImportResult.Error)
        }
    }

    @Test
    fun sanitizerRepairsRetroNumbers() {
        val s = SettingsSanitizer.sanitize(
            AppSettings(
                retro = RetroSettings(crtStrength = Float.NaN, glowIntensity = 7f),
                presets = listOf(ThemePreset(1, "x", HomeSettings(), CoverSettings(), RetroSettings(crtStrength = -3f))),
            ),
        )
        assertEquals(0.6f, s.retro.crtStrength)
        assertEquals(1f, s.retro.glowIntensity)
        assertEquals(0f, s.presets[0].retro!!.crtStrength)
    }
}
