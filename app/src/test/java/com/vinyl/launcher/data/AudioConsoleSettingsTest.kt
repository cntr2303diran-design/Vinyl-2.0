package com.vinyl.launcher.data

import com.vinyl.launcher.audio.DeviceKind
import com.vinyl.launcher.audio.EqValues
import com.vinyl.launcher.audio.OutputDevice
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AudioConsoleSettingsTest {
    private val buds = OutputDevice(7, DeviceKind.BLUETOOTH, "Buds Pro", address = "AA:BB")

    @Test
    fun defaultsAreConservative() {
        val d = AudioConsoleSettings()
        assertFalse(d.eqEnabled)
        assertFalse(d.globalExperimental)
        assertEquals("Datar", d.preset)
        assertEquals(EqValues(), d.values)
    }

    @Test
    fun presetsApplyEditAndDetach() {
        var s = AudioConsoleRules.applyPreset(AudioConsoleSettings(), "Bass hangat")
        assertEquals("Bass hangat", s.preset)
        assertEquals(5f, s.values.lowDb)
        s = AudioConsoleRules.edit(s, s.values.copy(lowDb = 6f))
        assertNull(s.preset)
        s = AudioConsoleRules.edit(s, s.values.copy(lowDb = 5f))
        assertNull(s.preset) // preset sudah lepas; tidak "menempel" lagi diam-diam
        assertEquals(s, AudioConsoleRules.applyPreset(s, "Tidak ada"))
    }

    @Test
    fun customPresetsCannotShadowBuiltInsAndDeletingCleansProfiles() {
        val base = AudioConsoleSettings(values = EqValues(lowDb = 2f))
        assertNull(AudioConsoleRules.savePreset(base, "Datar"))
        assertNull(AudioConsoleRules.savePreset(base, "   "))
        var s = AudioConsoleRules.savePreset(base, "  Malam\u0007 ")!!
        assertEquals("Malam", s.preset)
        s = AudioConsoleRules.setProfile(s, buds, "Malam")
        assertEquals("Malam", AudioConsoleRules.profileMap(s)[buds.profileKey])
        assertEquals("Buds Pro", s.profiles.single().name)
        s = AudioConsoleRules.deletePreset(s, "Malam")
        assertEquals(0, s.customPresets.size)
        assertEquals(0, s.profiles.size)
        assertNull(s.preset)
        // Profil ke preset yang tidak ada ditolak
        assertEquals(s, AudioConsoleRules.setProfile(s, buds, "Hilang"))
    }

    @Test
    fun presetListIsBounded() {
        var s = AudioConsoleSettings()
        repeat(AudioConsoleRules.MAX_PRESETS) { s = AudioConsoleRules.savePreset(s, "P$it")!! }
        assertNull(AudioConsoleRules.savePreset(s, "Satu lagi"))
        // Memperbarui yang sudah ada tetap boleh
        assertEquals(AudioConsoleRules.MAX_PRESETS, AudioConsoleRules.savePreset(s, "P3")!!.customPresets.size)
    }

    @Test
    fun sanitizeCleansImportedValues() {
        val dirty = AudioConsoleSettings(
            values = EqValues(lowDb = 99f, balance = Float.NaN),
            preset = "Hantu",
            customPresets = listOf(EqPreset("Datar", EqValues(lowDb = 9f)), EqPreset("Ok", EqValues(midDb = -50f)), EqPreset("Ok", EqValues())),
            profiles = listOf(DeviceProfile("", "x", "Ok"), DeviceProfile("k", "n", "Hilang"), DeviceProfile("k2", "\u0000", "Ok")),
        )
        val c = AudioConsoleRules.sanitize(dirty)
        assertEquals(12f, c.values.lowDb)
        assertEquals(0f, c.values.balance)
        assertNull(c.preset)
        assertEquals(listOf("Ok"), c.customPresets.map { it.name })
        assertEquals(-12f, c.customPresets.single().values.midDb)
        assertEquals(listOf("k2"), c.profiles.map { it.key })
        assertEquals("Perangkat", c.profiles.single().name)
    }

    @Test
    fun oldSettingsWithoutAudioStillDecode() {
        val json = Json { ignoreUnknownKeys = true }
        val s = json.decodeFromString(AppSettings.serializer(), "{}")
        assertEquals(AudioConsoleSettings(), s.audio)
        val round = json.decodeFromString(AppSettings.serializer(), json.encodeToString(AppSettings.serializer(), s.copy(audio = AudioConsoleSettings(eqEnabled = true))))
        assertEquals(true, round.audio.eqEnabled)
    }
}
