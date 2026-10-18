package com.vinyl.launcher.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Ekspor/impor pengaturan sebagai satu berkas JSON berversi.
 *
 * - Ekspor tidak membawa path gambar (file gambar hidup di penyimpanan internal perangkat ini, dan path-nya
 *   tidak ada artinya di perangkat lain) — latar gambar dan foto widget perlu dipilih ulang setelah impor.
 * - Impor tidak dipercaya: ukuran dibatasi, format & versi diperiksa, lalu isinya dibersihkan oleh
 *   [SettingsSanitizer]. Status yang melekat pada perangkat (layar kunci aktif, onboarding, taskbar &
 *   tombol navigasi menyala, visualizer audio asli) tidak ditimpa karena bergantung pada izin di perangkat ini.
 */
object SettingsBackup {
    const val FORMAT = "vinyl-settings"
    const val VERSION = 1
    /** Batas ukuran berkas impor (byte). Pengaturan normal jauh di bawah 100 KB. */
    const val MAX_BYTES = 1_000_000
    const val SUGGESTED_FILE_NAME = "vinyl-launcher-cadangan.json"

    @Serializable
    internal data class Envelope(
        val format: String = "",
        val version: Int = 0,
        val settings: AppSettings? = null,
    )

    sealed interface ImportResult {
        data class Ok(val settings: AppSettings) : ImportResult
        data class Error(val message: String) : ImportResult
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun export(settings: AppSettings): String =
        json.encodeToString(Envelope.serializer(), Envelope(FORMAT, VERSION, stripPrivate(settings)))

    /** Buang path file lokal sebelum dibagikan. */
    fun stripPrivate(s: AppSettings): AppSettings = s.copy(
        home = stripHome(s.home),
        cover = stripCover(s.cover),
        presets = s.presets.map { it.copy(home = stripHome(it.home), cover = stripCover(it.cover)) },
    )

    private fun stripHome(h: HomeSettings) = h.copy(
        background = h.background.copy(imagePath = null),
        widgets = h.widgets.map { it.copy(images = emptyList()) },
    )

    private fun stripCover(c: CoverSettings) = c.copy(background = c.background.copy(imagePath = null))

    /**
     * @param current pengaturan perangkat ini (untuk mempertahankan status yang melekat pada perangkat)
     * @param isAllowedImage apakah path gambar boleh dipakai (lihat [SettingsSanitizer.sanitize])
     */
    fun parse(text: String, current: AppSettings, isAllowedImage: (String) -> Boolean = { false }): ImportResult {
        if (text.length > MAX_BYTES) return ImportResult.Error("Berkas terlalu besar untuk sebuah cadangan Vinyl.")
        val env = try {
            json.decodeFromString(Envelope.serializer(), text)
        } catch (e: IllegalArgumentException) {
            // SerializationException turunan IllegalArgumentException; pesan teknisnya sengaja tidak ditampilkan
            return ImportResult.Error("Berkas rusak atau bukan cadangan Vinyl Launcher.")
        }
        val incoming = env.settings
        if (env.format != FORMAT || incoming == null) return ImportResult.Error("Berkas ini bukan cadangan Vinyl Launcher.")
        if (env.version < 1) return ImportResult.Error("Versi cadangan tidak dikenali.")
        if (env.version > VERSION) return ImportResult.Error("Cadangan dibuat oleh versi aplikasi yang lebih baru. Perbarui aplikasi dulu.")

        val clean = SettingsSanitizer.sanitize(incoming, isAllowedImage)
        return ImportResult.Ok(
            clean.copy(
                coverEnabled = current.coverEnabled,
                onboardingDone = current.onboardingDone,
                dock = clean.dock.copy(enabled = current.dock.enabled),
                nav = clean.nav.copy(enabled = current.nav.enabled),
                // Izin kontak terikat pada perangkat: jangan menyalakan pencarian kontak tanpa izinnya
                drawer = clean.drawer.copy(searchContacts = current.drawer.searchContacts),
                // Visualizer audio asli bergantung pada izin mikrofon di perangkat ini: cadangan tidak boleh
                // menyalakannya diam-diam. Mode itu hanya aktif lewat alur izin di Pengaturan → Efek.
                visual = clean.visual.copy(
                    visualizer = if (clean.visual.visualizer == VisualizerMode.REAL) current.visual.visualizer else clean.visual.visualizer,
                ),
            ),
        )
    }
}
