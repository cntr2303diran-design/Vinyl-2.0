package com.vinyl.launcher.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Field baru "light" pada pengaturan proyektor: dokumen lama terbaca NORMAL, pilihan bertahan, nilai asing jatuh ke NORMAL. */
class LightLevelSettingsTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    private fun withProjector(s: AppSettings, edit: (JsonObject) -> JsonObject): String {
        val root = json.encodeToJsonElement(AppSettings.serializer(), s).jsonObject
        val retro = root[SettingsMigration.RETRO_KEY]!!.jsonObject
        val projector = edit(retro["projector"]!!.jsonObject)
        return JsonObject(root + (SettingsMigration.RETRO_KEY to JsonObject(retro + ("projector" to projector)))).toString()
    }

    @Test
    fun aDocumentWithoutTheKeyReadsAsNormalAndKeepsTheRestOfTheProjector() {
        val s = AppSettings(retro = RetroSettings(projector = ProjectorSettings(brightness = 0.4f, size = 1.1f, light = LightLevel.OFF)))
        val back = SettingsMigration.decode(json, withProjector(s) { JsonObject(it - "light") })
        assertEquals(LightLevel.NORMAL, back.retro.projector.light)
        assertEquals(0.4f, back.retro.projector.brightness, 0f)
        assertEquals(1.1f, back.retro.projector.size, 0f)
    }

    @Test
    fun theChosenLevelSurvivesSavingLoadingAndBackup() {
        for (level in LightLevel.entries) {
            val s = AppSettings(retro = RetroSettings(projector = ProjectorSettings(light = level)))
            assertEquals(level, SettingsMigration.decode(json, json.encodeToString(AppSettings.serializer(), s)).retro.projector.light)
            val r = SettingsBackup.parse(SettingsBackup.export(s), AppSettings()) as SettingsBackup.ImportResult.Ok
            assertEquals(level, r.settings.retro.projector.light)
        }
    }

    @Test
    fun anUnknownLevelFallsBackToNormal() {
        val s = AppSettings(retro = RetroSettings(projector = ProjectorSettings(brightness = 0.3f)))
        val back = SettingsMigration.decode(json, withProjector(s) { JsonObject(it + ("light" to JsonPrimitive("BLINDING"))) })
        assertEquals(LightLevel.NORMAL, back.retro.projector.light)
        assertEquals(0.3f, back.retro.projector.brightness, 0f)
    }

    @Test
    fun theStoredNamesAreStable() {
        assertEquals(listOf("OFF", "SOFT", "NORMAL"), LightLevel.entries.map { it.name })
    }
}
