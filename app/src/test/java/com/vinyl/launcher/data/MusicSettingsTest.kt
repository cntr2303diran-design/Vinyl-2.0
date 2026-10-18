package com.vinyl.launcher.data

import com.vinyl.launcher.music.SleepTimerPlan
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSettingsTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun defaultsAreSafeAndNeedNoPermission() {
        val m = MusicSettings()
        assertTrue("perilaku lirik yang sudah ada tetap menyala", m.lyricsOnline)
        assertFalse("mengubah volume sistem harus opt-in", m.softSkip)
        assertEquals(ShelfStyle.FLOW, m.shelfStyle)
        assertTrue(m.cinematicColors && m.lyricsBlur)
        assertEquals(m.sleepMinutes, SleepTimerPlan.clampMinutes(m.sleepMinutes))
        assertTrue("menit awal adalah salah satu preset", m.sleepMinutes in SleepTimerPlan.PRESET_MINUTES)
    }

    @Test fun settingsSavedBeforeMusicExistedStillLoad() {
        val old = json.encodeToString(AppSettings.serializer(), AppSettings())
        val without = JsonObject((Json.parseToJsonElement(old) as JsonObject).filterKeys { it != "music" }).toString()
        assertFalse(without.contains("\"music\""))
        assertEquals(MusicSettings(), json.decodeFromString(AppSettings.serializer(), without).music)
    }

    @Test fun sleepMinutesAreClampedBothWays() {
        fun clean(m: Int) = SettingsSanitizer.sanitize(AppSettings(music = MusicSettings(sleepMinutes = m))).music.sleepMinutes
        assertEquals(SleepTimerPlan.MIN_MINUTES, clean(0))
        assertEquals(SleepTimerPlan.MIN_MINUTES, clean(-90))
        assertEquals(SleepTimerPlan.MAX_MINUTES, clean(100_000))
        assertEquals(45, clean(45))
        assertEquals(SleepTimerPlan.MAX_MINUTES, clean(Int.MAX_VALUE))
        assertEquals(SleepTimerPlan.MIN_MINUTES, clean(Int.MIN_VALUE))
    }

    @Test fun sanitizingKeepsTheOtherMusicChoices() {
        val m = MusicSettings(lyricsOnline = false, lyricsBlur = false, softSkip = true, shelfStyle = ShelfStyle.STACK, cinematicColors = false, sleepMinutes = 15)
        assertEquals(m, SettingsSanitizer.sanitize(AppSettings(music = m)).music)
    }

    @Test fun sanitizingIsIdempotent() {
        val once = SettingsSanitizer.sanitize(AppSettings(music = MusicSettings(sleepMinutes = 99_999)))
        assertEquals(once, SettingsSanitizer.sanitize(once))
    }

    @Test fun musicChoicesSurviveBackupExportAndImport() {
        val chosen = AppSettings(music = MusicSettings(lyricsOnline = false, softSkip = true, shelfStyle = ShelfStyle.STACK, sleepMinutes = 60))
        val text = SettingsBackup.export(chosen)
        val result = SettingsBackup.parse(text, current = AppSettings()) as SettingsBackup.ImportResult.Ok
        assertEquals(chosen.music, result.settings.music)
    }

    @Test fun hostileBackupCannotSmuggleOutOfRangeMinutes() {
        val text = SettingsBackup.export(AppSettings(music = MusicSettings(sleepMinutes = 30)))
            .replace("\"sleepMinutes\": 30", "\"sleepMinutes\": 2147483647")
        assertTrue(text.contains("2147483647"))
        val result = SettingsBackup.parse(text, current = AppSettings()) as SettingsBackup.ImportResult.Ok
        assertEquals(SleepTimerPlan.MAX_MINUTES, result.settings.music.sleepMinutes)
    }

    @Test fun backupFromBeforeMusicExistedImportsWithDefaults() {
        // Buang blok "music" lewat JSON (bukan regex rapuh) untuk meniru cadangan buatan versi lama
        val root = Json.parseToJsonElement(SettingsBackup.export(AppSettings())) as JsonObject
        val settings = root["settings"] as JsonObject
        val stripped = JsonObject(root + ("settings" to JsonObject(settings.filterKeys { it != "music" }))).toString()
        assertFalse(stripped.contains("\"music\""))
        val result = SettingsBackup.parse(stripped, current = AppSettings()) as SettingsBackup.ImportResult.Ok
        assertEquals(MusicSettings(), result.settings.music)
    }
}
