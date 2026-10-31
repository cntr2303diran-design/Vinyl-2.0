package com.vinyl.launcher.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Field baru "era": dokumen dan cadangan lama tetap terbaca, pilihan era bertahan, nilai asing jatuh ke bawaan. */
class RoomEraSettingsTest {
    // Sama dengan konfigurasi SettingsRepository
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    private fun docWithoutEra(s: AppSettings): String {
        val root = json.encodeToJsonElement(AppSettings.serializer(), s).jsonObject
        val retro = root[SettingsMigration.RETRO_KEY]!!.jsonObject
        return JsonObject(root + (SettingsMigration.RETRO_KEY to JsonObject(retro - "era"))).toString()
    }

    @Test
    fun aDocumentWrittenBeforeErasExistedReadsAsDefaultAndKeepsEverythingElse() {
        val s = AppSettings(retro = RetroSettings(tvBody = TvBody.WALNUT, room = RoomTone.LIGHT, era = RoomEra.EIGHTIES), onboardingDone = true)
        val back = SettingsMigration.decode(json, docWithoutEra(s))
        assertEquals(RoomEra.DEFAULT, back.retro.era)
        assertEquals(TvBody.WALNUT, back.retro.tvBody)
        assertEquals(RoomTone.LIGHT, back.retro.room)
        assertEquals(true, back.onboardingDone)
    }

    @Test
    fun theChosenEraSurvivesSavingAndLoading() {
        for (era in RoomEra.entries) {
            val s = AppSettings(retro = RetroSettings(era = era))
            val back = SettingsMigration.decode(json, json.encodeToString(AppSettings.serializer(), s))
            assertEquals(era, back.retro.era)
        }
    }

    @Test
    fun theChosenEraSurvivesABackupRoundTrip() {
        for (era in RoomEra.entries) {
            val s = AppSettings(retro = RetroSettings(era = era))
            val r = SettingsBackup.parse(SettingsBackup.export(s), AppSettings()) as SettingsBackup.ImportResult.Ok
            assertEquals(era, r.settings.retro.era)
        }
    }

    @Test
    fun anUnknownEraNameFallsBackToDefaultInsteadOfLosingTheWholeDocument() {
        val s = AppSettings(retro = RetroSettings(tvBody = TvBody.CHARCOAL, era = RoomEra.NINETIES), onboardingDone = true)
        val root = json.encodeToJsonElement(AppSettings.serializer(), s).jsonObject
        val retro = root[SettingsMigration.RETRO_KEY]!!.jsonObject
        val raw = JsonObject(root + (SettingsMigration.RETRO_KEY to JsonObject(retro + ("era" to JsonPrimitive("TWENTIES"))))).toString()
        val back = SettingsMigration.decode(json, raw)
        assertEquals(RoomEra.DEFAULT, back.retro.era)
        assertEquals(TvBody.CHARCOAL, back.retro.tvBody)
        assertEquals(true, back.onboardingDone)
    }

    @Test
    fun theStoredNamesAreStable() {
        // Nama konstanta ditulis ke berkas pengaturan dan cadangan: mengganti nama akan merusak data pengguna
        assertEquals(listOf("DEFAULT", "SEVENTIES", "EIGHTIES", "NINETIES", "LOOKOUT"), RoomEra.entries.map { it.name })
    }
}
