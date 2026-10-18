package com.vinyl.launcher.data

import com.vinyl.launcher.audio.Eq
import com.vinyl.launcher.audio.EqValues
import com.vinyl.launcher.audio.OutputDevice
import kotlinx.serialization.Serializable

/** Preset EQ buatan pengguna. */
@Serializable
data class EqPreset(val name: String, val values: EqValues)

/** Profil perangkat: saat perangkat dengan [key] menjadi keluaran, preset [preset] dipakai. [name] hanya untuk tampilan. */
@Serializable
data class DeviceProfile(val key: String, val name: String, val preset: String)

/** Pengaturan Audio Console (EQ, preset, profil per perangkat, otomasi). Semua tersimpan lokal. */
@Serializable
data class AudioConsoleSettings(
    /** EQ mati secara default: launcher tidak memasang efek ke audio aplikasi lain tanpa diminta. */
    val eqEnabled: Boolean = false,
    val values: EqValues = EqValues(),
    /** Nama preset yang sedang dipakai, atau null bila nilai sudah diubah manual. */
    val preset: String? = BUILT_IN_DEFAULT,
    val customPresets: List<EqPreset> = emptyList(),
    val profiles: List<DeviceProfile> = emptyList(),
    /** Saat perangkat berprofil terputus, kembalikan preset sebelumnya. */
    val restoreOnDisconnect: Boolean = true,
    /** USB DAC tersambung → buka Audio Console saat beranda tampil berikutnya. */
    val openConsoleOnUsb: Boolean = false,
    /** Turunkan masukan sebesar penguatan EQ terbesar agar tidak clipping. */
    val safeHeadroom: Boolean = true,
    /** Efek pada keluaran gabungan (sesi 0). Tidak dijamin di semua perangkat; mati secara default. */
    val globalExperimental: Boolean = false,
) {
    companion object {
        const val BUILT_IN_DEFAULT = "Datar"
    }
}

object AudioConsoleRules {
    const val MAX_PRESETS = 30
    const val MAX_PROFILES = 50
    const val MAX_NAME = 40
    const val MAX_KEY = 120

    /** Semua preset: bawaan dulu, lalu buatan pengguna. */
    fun allPresets(s: AudioConsoleSettings): List<Pair<String, EqValues>> =
        Eq.BUILT_IN + s.customPresets.map { it.name to it.values }

    fun isBuiltIn(name: String): Boolean = Eq.BUILT_IN.any { it.first == name }

    fun valuesOf(s: AudioConsoleSettings, name: String): EqValues? = allPresets(s).firstOrNull { it.first == name }?.second

    /** Pakai preset [name] (bila ada). */
    fun applyPreset(s: AudioConsoleSettings, name: String): AudioConsoleSettings =
        valuesOf(s, name)?.let { s.copy(values = Eq.clamp(it), preset = name) } ?: s

    /** Ubah nilai manual: preset dilepas kecuali nilainya kebetulan sama persis dengan preset yang dipakai. */
    fun edit(s: AudioConsoleSettings, values: EqValues): AudioConsoleSettings {
        val v = Eq.clamp(values)
        val keep = s.preset?.takeIf { valuesOf(s, it) == v }
        return s.copy(values = v, preset = keep)
    }

    /**
     * Simpan nilai sekarang sebagai preset [rawName]. Nama bawaan tidak bisa ditimpa; nama buatan yang sama diperbarui.
     * @return pengaturan baru, atau null bila nama kosong/bawaan atau daftar penuh.
     */
    fun savePreset(s: AudioConsoleSettings, rawName: String): AudioConsoleSettings? {
        val name = cleanName(rawName) ?: return null
        if (isBuiltIn(name)) return null
        val exists = s.customPresets.any { it.name == name }
        if (!exists && s.customPresets.size >= MAX_PRESETS) return null
        val list = if (exists) s.customPresets.map { if (it.name == name) EqPreset(name, s.values) else it }
        else s.customPresets + EqPreset(name, s.values)
        return s.copy(customPresets = list, preset = name)
    }

    /** Hapus preset buatan; profil yang memakainya ikut dihapus agar tidak menunjuk preset yang hilang. */
    fun deletePreset(s: AudioConsoleSettings, name: String): AudioConsoleSettings {
        if (isBuiltIn(name)) return s
        return s.copy(
            customPresets = s.customPresets.filterNot { it.name == name },
            profiles = s.profiles.filterNot { it.preset == name },
            preset = s.preset.takeUnless { it == name },
        )
    }

    /** Pasang profil: perangkat [device] → preset [preset]. */
    fun setProfile(s: AudioConsoleSettings, device: OutputDevice, preset: String): AudioConsoleSettings {
        if (valuesOf(s, preset) == null) return s
        val key = device.profileKey
        val others = s.profiles.filterNot { it.key == key }
        if (others.size >= MAX_PROFILES) return s
        return s.copy(profiles = others + DeviceProfile(key, cleanName(device.name) ?: "Perangkat", preset))
    }

    fun removeProfile(s: AudioConsoleSettings, key: String): AudioConsoleSettings = s.copy(profiles = s.profiles.filterNot { it.key == key })

    fun profileMap(s: AudioConsoleSettings): Map<String, String> = s.profiles.associate { it.key to it.preset }

    fun cleanName(raw: String): String? =
        raw.filterNot { it.isISOControl() }.trim().take(MAX_NAME).trim().takeIf { it.isNotEmpty() }

    /** Batasi isi berkas/pengaturan dari luar (cadangan yang diimpor) agar tidak berisi nilai liar. */
    fun sanitize(s: AudioConsoleSettings): AudioConsoleSettings {
        val presets = s.customPresets
            .mapNotNull { p -> cleanName(p.name)?.takeUnless(::isBuiltIn)?.let { EqPreset(it, Eq.clamp(p.values)) } }
            .distinctBy { it.name }
            .take(MAX_PRESETS)
        val names = Eq.BUILT_IN.map { it.first }.toSet() + presets.map { it.name }
        val profiles = s.profiles
            .filter { it.key.isNotBlank() && it.key.length <= MAX_KEY && it.preset in names }
            .map { it.copy(name = cleanName(it.name) ?: "Perangkat") }
            .distinctBy { it.key }
            .take(MAX_PROFILES)
        return s.copy(
            values = Eq.clamp(s.values),
            preset = s.preset?.takeIf { it in names },
            customPresets = presets,
            profiles = profiles,
        )
    }
}
