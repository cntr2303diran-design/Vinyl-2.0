package com.vinyl.launcher.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerSettingsTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun defaultsAreUsableWithoutAnyPermission() {
        val d = DrawerSettings()
        assertTrue(d.showFrequent && d.showCategories && d.showBadges && d.searchSongs && d.searchSettings)
        assertFalse("pencarian kontak harus opt-in", d.searchContacts)
        assertTrue(d.categoryOverrides.isEmpty())
        assertTrue(d.frequentCount in SettingsSanitizer.MIN_FREQUENT..SettingsSanitizer.MAX_FREQUENT)
    }

    @Test fun settingsSavedBeforeDrawerExistedStillLoad() {
        // Pengaturan dari versi lama tidak punya kunci "drawer"; harus jatuh ke nilai awal, bukan gagal dibaca
        val old = json.encodeToString(AppSettings.serializer(), AppSettings())
        val withoutDrawer = Json.parseToJsonElement(old).let { el ->
            kotlinx.serialization.json.JsonObject(el.let { it as kotlinx.serialization.json.JsonObject }.filterKeys { it != "drawer" })
        }.toString()
        assertFalse(withoutDrawer.contains("\"drawer\""))
        val loaded = json.decodeFromString(AppSettings.serializer(), withoutDrawer)
        assertEquals(DrawerSettings(), loaded.drawer)
    }

    @Test fun frequentCountIsClampedBothWays() {
        assertEquals(SettingsSanitizer.MIN_FREQUENT, SettingsSanitizer.sanitize(AppSettings(drawer = DrawerSettings(frequentCount = -4))).drawer.frequentCount)
        assertEquals(SettingsSanitizer.MIN_FREQUENT, SettingsSanitizer.sanitize(AppSettings(drawer = DrawerSettings(frequentCount = 0))).drawer.frequentCount)
        assertEquals(SettingsSanitizer.MAX_FREQUENT, SettingsSanitizer.sanitize(AppSettings(drawer = DrawerSettings(frequentCount = 11))).drawer.frequentCount)
        assertEquals(6, SettingsSanitizer.sanitize(AppSettings(drawer = DrawerSettings(frequentCount = 6))).drawer.frequentCount)
    }

    @Test fun validOverridesSurviveSanitizing() {
        val d = DrawerSettings(categoryOverrides = mapOf("com.a/.Main" to "MUSIC", "com.b/com.b.A\$I#10" to "OTHER"))
        assertEquals(d, SettingsSanitizer.sanitize(AppSettings(drawer = d)).drawer)
    }

    @Test fun invalidOverridesAreDropped() {
        val d = DrawerSettings(categoryOverrides = mapOf("com.a/.Main" to "music", "" to "GAME", "x y/.M" to "GAME", "com.c/.M" to ""))
        assertTrue("nama kategori peka huruf besar-kecil; yang tak dikenal dibuang", SettingsSanitizer.sanitize(AppSettings(drawer = d)).drawer.categoryOverrides.isEmpty())
    }

    @Test fun traversalLookingKeysAreRejectedEverywhere() {
        // Regresi: dulu "../../etc" lolos isSafeKey; asersi dock lama hanya lulus karena daftar dipotong 8 item.
        val bad = listOf("../../etc", "a/../b", "..", "com.a/..Main")
        assertTrue(bad.none { SettingsSanitizer.isSafeKey(it) })
        val s = SettingsSanitizer.sanitize(
            AppSettings(
                dock = DockSettings(apps = bad),
                hiddenApps = bad.toSet(),
                drawer = DrawerSettings(categoryOverrides = bad.associateWith { "GAME" }),
            ),
        )
        assertTrue(s.dock.apps.isEmpty())
        assertTrue(s.hiddenApps.isEmpty())
        assertTrue(s.drawer.categoryOverrides.isEmpty())
    }

    @Test fun realComponentKeysStillPass() {
        listOf("com.a/com.a.Main", "com.b/.Main", "com.c/com.c.A\$Inner#12", "com.d-e/com.d_e.X1").forEach {
            assertTrue(it, SettingsSanitizer.isSafeKey(it))
        }
    }
}
