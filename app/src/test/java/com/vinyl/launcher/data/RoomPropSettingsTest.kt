package com.vinyl.launcher.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Field baru "props": dokumen lama menampilkan semua benda, pilihan bertahan, dan cadangan membawanya. */
class RoomPropSettingsTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    @Test
    fun aDocumentWithoutTheKeyShowsEveryPropAndKeepsTheRest() {
        val s = AppSettings(retro = RetroSettings(era = RoomEra.EIGHTIES, props = RoomPropSettings(clock = false)))
        val root = json.encodeToJsonElement(AppSettings.serializer(), s).jsonObject
        val retro = root[SettingsMigration.RETRO_KEY]!!.jsonObject
        val old = JsonObject(root + (SettingsMigration.RETRO_KEY to JsonObject(retro - "props"))).toString()
        val back = SettingsMigration.decode(json, old)
        assertEquals(RoomPropSettings(), back.retro.props)
        assertEquals(RoomEra.EIGHTIES, back.retro.era)
    }

    @Test
    fun theChoiceSurvivesSavingLoadingAndBackup() {
        val s = AppSettings(retro = RetroSettings(props = RoomPropSettings(clock = false, radio = true, phone = false, frame = false, trophy = false, diary = false)))
        assertEquals(s.retro.props, SettingsMigration.decode(json, json.encodeToString(AppSettings.serializer(), s)).retro.props)
        val r = SettingsBackup.parse(SettingsBackup.export(s), AppSettings()) as SettingsBackup.ImportResult.Ok
        assertEquals(s.retro.props, r.settings.retro.props)
    }

    @Test
    fun theBackupNeverCarriesContactNames() {
        val text = SettingsBackup.export(AppSettings())
        assertTrue(!text.contains("content://com.android.contacts"))
        assertTrue(!text.contains("retro_phone"))
    }
}
