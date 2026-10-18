package com.vinyl.launcher.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/*
 * Migrasi pengaturan dari tema vinyl ke tema TV tabung. Murni (tanpa Android) agar bisa diuji dengan JSON versi lama.
 *
 * Aturannya: dokumen lama tidak punya kunci "retro". Untuk dokumen seperti itu, [RetroSettings] DITURUNKAN dari
 * pilihan gaya vinyl pengguna (bukan diisi default), supaya pengguna yang dulu memilih piringan transparan, tonearm
 * emas, atau 45 RPM mendapat padanannya di tema baru. Dokumen yang sudah punya "retro" dipakai apa adanya.
 * Preset tema lama juga tidak punya "retro"; padanannya diturunkan saat preset diterapkan ([retroFor]).
 */
object SettingsMigration {

    const val RETRO_KEY = "retro"

    /** Padanan tema TV tabung untuk gaya vinyl di [home]. */
    fun fromLegacy(home: HomeSettings): RetroSettings = RetroSettings(
        tvBody = if (home.vinylStyle == VinylStyle.COLORED) TvBody.SPACE_AGE else TvBody.CREAM,
        cassetteShell = when (home.vinylStyle) {
            VinylStyle.CLASSIC -> CassetteShell.BLACK
            VinylStyle.COLORED -> CassetteShell.COLORED
            VinylStyle.TRANSPARENT -> CassetteShell.TRANSPARENT
            VinylStyle.PICTURE_DISC -> CassetteShell.PICTURE
        },
        knobStyle = when (home.tonearmStyle) {
            TonearmStyle.SILVER -> KnobStyle.CHROME
            TonearmStyle.BLACK -> KnobStyle.BAKELITE
            TonearmStyle.GOLD -> KnobStyle.BRASS
        },
        reelSpeed = if (home.rpm == 45) ReelSpeed.FAST else ReelSpeed.NORMAL,
    )

    /** Pengaturan TV tabung untuk sebuah preset: miliknya sendiri bila ada, selain itu turunan dari gaya vinylnya. */
    fun retroFor(preset: ThemePreset): RetroSettings = preset.retro ?: fromLegacy(preset.home)

    /**
     * Terjemahkan [raw] menjadi [AppSettings] dengan [json]. Melempar exception yang sama dengan decodeFromString bila
     * [raw] rusak; pemanggil yang menentukan cadangannya.
     */
    fun decode(json: Json, raw: String): AppSettings = migrate(json, json.parseToJsonElement(raw).jsonObject)

    /** Sama dengan [decode] untuk objek JSON yang sudah diurai (mis. bagian "settings" dari berkas cadangan). */
    fun migrate(json: Json, obj: JsonObject): AppSettings {
        val settings = json.decodeFromJsonElement(AppSettings.serializer(), obj)
        return if (obj.containsKey(RETRO_KEY)) settings else settings.copy(retro = fromLegacy(settings.home))
    }
}
