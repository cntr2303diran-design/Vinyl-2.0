package com.vinyl.launcher.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualSettingsTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun defaultsNeverAskForAPermissionOrListenToTheMicrophone() {
        val v = VisualSettings()
        assertEquals("visualizer audio asli harus dipilih sendiri oleh pengguna", VisualizerMode.OFF, v.visualizer)
        assertTrue(v.glassDistortion && v.reactiveGlow && v.particles)
        assertEquals(ParallaxMode.WINDOW, v.parallax)
        assertEquals(v.parallaxStrength, SettingsSanitizer.sanitize(AppSettings(visual = v)).visual.parallaxStrength, 0f)
        assertTrue(v.parallaxStrength in SettingsSanitizer.MIN_PARALLAX..1f)
    }

    @Test fun settingsSavedBeforeVisualExistedStillLoad() {
        val old = json.encodeToString(AppSettings.serializer(), AppSettings())
        val without = JsonObject((Json.parseToJsonElement(old) as JsonObject).filterKeys { it != "visual" }).toString()
        assertFalse(without.contains("\"visual\""))
        assertEquals(VisualSettings(), json.decodeFromString(AppSettings.serializer(), without).visual)
    }

    @Test fun strengthIsClampedAndBadNumbersFallBack() {
        fun clean(v: Float) = SettingsSanitizer.sanitize(AppSettings(visual = VisualSettings(parallaxStrength = v))).visual.parallaxStrength
        assertEquals(SettingsSanitizer.MIN_PARALLAX, clean(0f), 0f)
        assertEquals(SettingsSanitizer.MIN_PARALLAX, clean(-5f), 0f)
        assertEquals(1f, clean(40f), 0f)
        assertEquals(0.35f, clean(0.35f), 0f)
        assertEquals(0.6f, clean(Float.NaN), 0f)
        assertEquals(0.6f, clean(Float.POSITIVE_INFINITY), 0f)
        assertEquals(0.6f, clean(Float.NEGATIVE_INFINITY), 0f)
    }

    @Test fun sanitizingKeepsTheOtherVisualChoices() {
        val v = VisualSettings(glassDistortion = false, reactiveGlow = false, particles = false, parallax = ParallaxMode.FLOAT, parallaxStrength = 0.9f, visualizer = VisualizerMode.DECORATIVE)
        assertEquals(v, SettingsSanitizer.sanitize(AppSettings(visual = v)).visual)
        val real = VisualSettings(visualizer = VisualizerMode.REAL)
        assertEquals("sanitizer tidak mengubah pilihan mode; kebijakan izin ada di impor cadangan", real, SettingsSanitizer.sanitize(AppSettings(visual = real)).visual)
    }

    @Test fun sanitizingIsIdempotent() {
        val once = SettingsSanitizer.sanitize(AppSettings(visual = VisualSettings(parallaxStrength = 99f)))
        assertEquals(once, SettingsSanitizer.sanitize(once))
    }

    @Test fun visualChoicesSurviveBackupExportAndImport() {
        val chosen = AppSettings(visual = VisualSettings(glassDistortion = false, particles = false, parallax = ParallaxMode.FLOAT, parallaxStrength = 0.8f, visualizer = VisualizerMode.DECORATIVE))
        val result = SettingsBackup.parse(SettingsBackup.export(chosen), current = AppSettings()) as SettingsBackup.ImportResult.Ok
        assertEquals(chosen.visual, result.settings.visual)
    }

    @Test fun importedBackupCannotTurnOnRealAudioCapture() {
        val text = SettingsBackup.export(AppSettings(visual = VisualSettings(visualizer = VisualizerMode.REAL)))
        assertTrue(text.contains("REAL"))
        // Perangkat ini belum memakai audio asli (belum ada izin): cadangan tidak boleh menyalakannya
        val off = SettingsBackup.parse(text, current = AppSettings(visual = VisualSettings(visualizer = VisualizerMode.OFF))) as SettingsBackup.ImportResult.Ok
        assertEquals(VisualizerMode.OFF, off.settings.visual.visualizer)
        val deco = SettingsBackup.parse(text, current = AppSettings(visual = VisualSettings(visualizer = VisualizerMode.DECORATIVE))) as SettingsBackup.ImportResult.Ok
        assertEquals(VisualizerMode.DECORATIVE, deco.settings.visual.visualizer)
        // Perangkat yang sudah memakainya (sudah lewat alur izin) tetap memakainya
        val real = SettingsBackup.parse(text, current = AppSettings(visual = VisualSettings(visualizer = VisualizerMode.REAL))) as SettingsBackup.ImportResult.Ok
        assertEquals(VisualizerMode.REAL, real.settings.visual.visualizer)
    }

    @Test fun otherVisualizerModesImportAsTheyAre() {
        for (mode in listOf(VisualizerMode.OFF, VisualizerMode.DECORATIVE)) {
            val text = SettingsBackup.export(AppSettings(visual = VisualSettings(visualizer = mode)))
            val r = SettingsBackup.parse(text, current = AppSettings(visual = VisualSettings(visualizer = VisualizerMode.REAL))) as SettingsBackup.ImportResult.Ok
            assertEquals(mode, r.settings.visual.visualizer)
        }
    }

    @Test fun hostileBackupCannotSmuggleOutOfRangeStrength() {
        val text = SettingsBackup.export(AppSettings(visual = VisualSettings(parallaxStrength = 0.5f)))
            .replace("\"parallaxStrength\": 0.5", "\"parallaxStrength\": 3.4e38")
        assertTrue(text.contains("3.4e38"))
        val r = SettingsBackup.parse(text, current = AppSettings()) as SettingsBackup.ImportResult.Ok
        assertEquals(1f, r.settings.visual.parallaxStrength, 0f)
    }

    @Test fun unknownModeNamesAreRejectedNotCrashed() {
        val text = SettingsBackup.export(AppSettings()).replace("\"parallax\": \"WINDOW\"", "\"parallax\": \"BANANA\"")
        assertTrue(text.contains("BANANA"))
        val r = SettingsBackup.parse(text, current = AppSettings())
        assertTrue(r is SettingsBackup.ImportResult.Error)
    }

    @Test fun backupFromBeforeVisualExistedImportsWithDefaults() {
        val root = Json.parseToJsonElement(SettingsBackup.export(AppSettings())) as JsonObject
        val settings = root["settings"] as JsonObject
        val stripped = JsonObject(root + ("settings" to JsonObject(settings.filterKeys { it != "visual" }))).toString()
        assertFalse(stripped.contains("\"visual\""))
        val r = SettingsBackup.parse(stripped, current = AppSettings()) as SettingsBackup.ImportResult.Ok
        assertEquals(VisualSettings(), r.settings.visual)
    }
}
