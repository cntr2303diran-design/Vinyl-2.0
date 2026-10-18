package com.vinyl.launcher.data

import kotlinx.serialization.Serializable

/*
 * Model pengaturan Vinyl Launcher.
 * Seluruh pengaturan disimpan sebagai satu dokumen JSON di DataStore
 * (lihat SettingsRepository) sehingga mudah di-reset, disalin ke preset,
 * dan ditambah field baru tanpa migrasi (field baru memakai nilai default).
 * Warna disimpan sebagai Long ARGB (0xAARRGGBB).
 */

/** Posisi 3x3 di dalam sebuah area. */
@Serializable
enum class Anchor { TOP_START, TOP_CENTER, TOP_END, CENTER_START, CENTER, CENTER_END, BOTTOM_START, BOTTOM_CENTER, BOTTOM_END }

@Serializable
enum class ClockType { THIN, BOLD, FLIP, ANALOG, CIRCULAR }

@Serializable
enum class FontChoice { DEFAULT, SANS, SERIF, MONO, CURSIVE }

@Serializable
enum class BackgroundType { ALBUM, SOLID, GRADIENT, IMAGE }

@Serializable
enum class TurntablePosition { LEFT, CENTER, RIGHT }

@Serializable
enum class VinylStyle { CLASSIC, COLORED, TRANSPARENT, PICTURE_DISC }

@Serializable
enum class TonearmStyle { SILVER, BLACK, GOLD }

@Serializable
enum class GestureAction {
    NONE, APP_DRAWER, NOTIFICATIONS, LOCK_SCREEN, OPEN_APP, SETTINGS, CRATE,
    PLAY_PAUSE, NEXT_TRACK, PREV_TRACK, BACK, RECENTS, QUICK_SETTINGS, SCREENSHOT, EDIT_HOME, TASKBAR_TOGGLE,
}

@Serializable
enum class WidgetType { CALENDAR, WEATHER, BATTERY, PHOTO }

/** Gaya jam yang dipakai di home screen maupun layar kunci. */
@Serializable
data class ClockSettings(
    val visible: Boolean = true,
    val type: ClockType = ClockType.THIN,
    val font: FontChoice = FontChoice.DEFAULT,
    val color: Long = 0xFFF2EFEA,
    /** Skala ukuran relatif (0.5 – 2.0). */
    val scale: Float = 1f,
    val anchor: Anchor = Anchor.TOP_START,
    val use24h: Boolean = true,
    val showDate: Boolean = true,
)

@Serializable
data class BackgroundSettings(
    val type: BackgroundType = BackgroundType.ALBUM,
    val solidColor: Long = 0xFF1B1B1F,
    val gradientStart: Long = 0xFF2B1E3A,
    val gradientEnd: Long = 0xFF0E0E10,
    /** Path file internal hasil salinan gambar dari galeri. */
    val imagePath: String? = null,
)

/** Widget tambahan di home screen. Posisi dalam fraksi layar (0..1) agar tahan rotasi. */
@Serializable
data class WidgetConfig(
    val id: Long,
    val type: WidgetType,
    val x: Float = 0.75f,
    val y: Float = 0.1f,
    val scale: Float = 1f,
    /** Untuk widget foto: path gambar (lebih dari satu = slideshow). */
    val images: List<String> = emptyList(),
)

@Serializable
data class HomeSettings(
    val background: BackgroundSettings = BackgroundSettings(),
    /** Ukuran turntable relatif terhadap ruang yang tersedia (0.5 – 1.0). */
    val turntableSize: Float = 0.85f,
    val turntablePosition: TurntablePosition = TurntablePosition.CENTER,
    val vinylStyle: VinylStyle = VinylStyle.CLASSIC,
    val tonearmStyle: TonearmStyle = TonearmStyle.SILVER,
    val coverVisible: Boolean = true,
    val coverRounded: Boolean = true,
    val coverShadow: Boolean = true,
    val rpm: Int = 33,
    val clock: ClockSettings = ClockSettings(type = ClockType.BOLD, scale = 0.62f, anchor = Anchor.TOP_START),
    /** Judul, artis & progress di bawah turntable (ala Apple Music). */
    val trackInfoVisible: Boolean = true,
    /** Lirik tersinkron di bawah info lagu. */
    val lyricsVisible: Boolean = true,
    val widgets: List<WidgetConfig> = emptyList(),
)

/** Elemen yang bisa diatur di layar kunci. */
@Serializable
data class CoverElement(val visible: Boolean = true, val anchor: Anchor = Anchor.CENTER)

@Serializable
data class CoverSettings(
    val clock: ClockSettings = ClockSettings(type = ClockType.BOLD, scale = 1.5f, anchor = Anchor.TOP_START, showDate = false),
    val date: CoverElement = CoverElement(true, Anchor.TOP_START),
    val calendar: CoverElement = CoverElement(true, Anchor.CENTER_START),
    val songInfo: CoverElement = CoverElement(true, Anchor.BOTTOM_START),
    val battery: CoverElement = CoverElement(true, Anchor.TOP_END),
    val calendarEvents: Boolean = false,
    val background: BackgroundSettings = BackgroundSettings(type = BackgroundType.ALBUM),
    /** Lirik tersinkron di bawah kartu musik layar kunci. */
    val lyricsVisible: Boolean = true,
)

@Serializable
data class GestureBinding(val action: GestureAction = GestureAction.NONE, val packageName: String? = null)

@Serializable
data class GestureSettings(
    val swipeUp: GestureBinding = GestureBinding(GestureAction.APP_DRAWER),
    val swipeDown: GestureBinding = GestureBinding(GestureAction.NOTIFICATIONS),
    val doubleTap: GestureBinding = GestureBinding(GestureAction.LOCK_SCREEN),
)

/** Gestur gambar buatan pengguna: satu goresan jari, disimpan sebagai titik ternormalisasi (x,y berurutan). */
@Serializable
data class CustomGesture(
    val id: String,
    val name: String,
    val points: List<Float>,
    val binding: GestureBinding = GestureBinding(GestureAction.NONE),
)

@Serializable
enum class NavOrder { BACK_HOME_RECENTS, RECENTS_HOME_BACK }

/** Tombol navigasi Vinyl: Back / Home / Recents melayang yang posisinya bebas diatur. */
@Serializable
data class NavSettings(
    val enabled: Boolean = false,
    /** Posisi 0..1 pada layar (0 = kiri/atas, 1 = kanan/bawah). */
    val x: Float = 0.5f,
    val y: Float = 1f,
    /** Jarak antar tombol (dp). */
    val spacing: Int = 28,
    /** Ukuran tiap tombol (dp). */
    val size: Int = 52,
    val opacity: Float = 0.85f,
    val order: NavOrder = NavOrder.BACK_HOME_RECENTS,
    val vertical: Boolean = false,
)

/** Sisi layar tempat Taskbar Vinyl menempel. */
@Serializable
enum class DockPosition { BOTTOM, TOP, LEFT, RIGHT }

/**
 * Taskbar Vinyl: bar kaca yang melayang di atas semua aplikasi
 * (pengganti taskbar Samsung yang dimatikan sistem untuk launcher pihak ketiga).
 */
@Serializable
data class DockSettings(
    val enabled: Boolean = false,
    /** Aplikasi favorit (komponen "paket/kelas"), maks 8. */
    val apps: List<String> = emptyList(),
    /** Tampilkan aplikasi yang terakhir dibuka (butuh izin akses penggunaan). */
    val showRecents: Boolean = true,
    /** Tampilkan kartu kecil lagu yang sedang diputar. */
    val showNowPlaying: Boolean = true,
    /** Sembunyikan taskbar saat aplikasi berjalan layar penuh (video, game). */
    val hideInFullscreen: Boolean = true,
    /** Sisi layar tempat taskbar menempel (default bawah). */
    val position: DockPosition = DockPosition.BOTTOM,
    /** Jarak tambahan dari tepi layar (dp). */
    val margin: Int = 0,
    /** Gaya PC: jam, tanggal & baterai di taskbar, ikon membesar saat disentuh. */
    val pcStyle: Boolean = true,
    /** Buka aplikasi sebagai jendela melayang (butuh "jendela bentuk bebas" aktif di sistem). */
    val windowed: Boolean = false,
)

/** Efek suara & getar ala turntable sungguhan. */
@Serializable
data class EffectSettings(
    val needleSound: Boolean = true,
    val haptics: Boolean = true,
    /** Volume efek (0..1), relatif terhadap volume media. */
    val volume: Float = 0.7f,
    /** Kekuatan getar (0.3..1). */
    val hapticStrength: Float = 1f,
    /** Bunyi & getar saat vinyl diputar dengan jari, tonearm digeser, atau ganti lagu. */
    val interactionFx: Boolean = true,
    /** Kresek vinyl terus-menerus (pelan) selama lagu diputar di layar utama. */
    val crackle: Boolean = false,
)

@Serializable
data class WeatherSettings(
    val cityName: String = "Jakarta",
    val latitude: Double = -6.2088,
    val longitude: Double = 106.8456,
)

/** Preset tema: salinan gaya home + layar kunci. */
@Serializable
data class ThemePreset(
    val id: Long,
    val name: String,
    val home: HomeSettings,
    val cover: CoverSettings,
    /** Gaya TV tabung; null pada preset yang dibuat sebelum tema TV tabung (diturunkan lewat SettingsMigration.retroFor). */
    val retro: RetroSettings? = null,
)

/** Pengaturan app drawer. Nilai awal dipilih agar fitur terlihat tanpa izin tambahan. */
@Serializable
data class DrawerSettings(
    /** Baris "Sering dipakai" di atas daftar (dari riwayat peluncuran lokal). */
    val showFrequent: Boolean = true,
    val frequentCount: Int = 6,
    /** Chip kategori (Semua, Musik, Game, ...). */
    val showCategories: Boolean = true,
    /** Lencana jumlah notifikasi; butuh akses notifikasi yang sudah dipakai untuk membaca sesi musik. */
    val showBadges: Boolean = true,
    /** Hasil pencarian: lagu dari koleksi, pengaturan sistem, dan kontak (kontak butuh izin, default mati). */
    val searchSongs: Boolean = true,
    val searchSettings: Boolean = true,
    val searchContacts: Boolean = false,
    /** Kunci aplikasi → nama [com.vinyl.launcher.drawer.AppCategory] pilihan pengguna. */
    val categoryOverrides: Map<String, String> = emptyMap(),
)

/** Gaya rak piringan: sampul berderet (cover flow) atau bertumpuk ke belakang. */
@Serializable
enum class ShelfStyle { FLOW, STACK }

/** Pengaturan musik: lirik, rak, sleep timer, dan transisi. Semua bekerja tanpa izin tambahan. */
@Serializable
data class MusicSettings(
    /**
     * Cari lirik ke lrclib.net. Mati = judul dan artis lagu tidak pernah dikirim ke internet dan tidak ada
     * permintaan lirik sama sekali.
     */
    val lyricsOnline: Boolean = true,
    /** Kaburkan baris lirik yang jauh di layar penuh (hanya berefek di Android 12+). */
    val lyricsBlur: Boolean = true,
    /**
     * Pergantian lembut: volume media turun lalu naik di sekitar tombol lagu berikutnya/sebelumnya. Ini bukan crossfade
     * dua lagu (launcher tidak memutar audio). Default mati karena mengubah volume sistem sesaat.
     */
    val softSkip: Boolean = false,
    val shelfStyle: ShelfStyle = ShelfStyle.FLOW,
    /** Warna latar berganti perlahan mengikuti cover; mati = berganti seketika. */
    val cinematicColors: Boolean = true,
    /** Durasi sleep timer yang terakhir dipilih (menit), disorot di dialog berikutnya. */
    val sleepMinutes: Int = 30,
    /** Kaset favorit di perpustakaan kaset: kunci lagu "judul|artis" (sama dengan kunci riwayat). */
    val favorites: Set<String> = emptySet(),
)

/** Arah gerak parallax: lapisan latar seperti di balik kaca (jendela) atau melayang di depan layar. */
@Serializable
enum class ParallaxMode { OFF, WINDOW, FLOAT }

/**
 * Sumber visualizer audio. REAL membaca keluaran audio sistem dan butuh izin mikrofon (RECORD_AUDIO) —
 * Android mewajibkannya untuk Visualizer, walau launcher tidak merekam suara. DECORATIVE adalah animasi yang
 * TIDAK mengikuti suara asli dan selalu diberi label begitu di UI.
 */
@Serializable
enum class VisualizerMode { OFF, REAL, DECORATIVE }

/**
 * Efek visual: distorsi kaca & cahaya reaktif (AGSL, Android 13+), partikel, parallax sensor, visualizer.
 * Diberi nama berbeda dari [EffectSettings] (suara & getar) agar tidak tertukar.
 * Semua efek otomatis berhenti saat animasi dimatikan sistem atau penghemat baterai aktif.
 */
@Serializable
data class VisualSettings(
    val glassDistortion: Boolean = true,
    val reactiveGlow: Boolean = true,
    val particles: Boolean = true,
    val parallax: ParallaxMode = ParallaxMode.WINDOW,
    /** Kekuatan parallax 0.1..1. */
    val parallaxStrength: Float = 0.6f,
    /** Default mati: mode REAL meminta izin, jadi harus dipilih sendiri oleh pengguna. */
    val visualizer: VisualizerMode = VisualizerMode.OFF,
)

@Serializable
data class AppSettings(
    val home: HomeSettings = HomeSettings(),
    val cover: CoverSettings = CoverSettings(),
    val gestures: GestureSettings = GestureSettings(),
    val dock: DockSettings = DockSettings(),
    val nav: NavSettings = NavSettings(),
    val customGestures: List<CustomGesture> = emptyList(),
    val weather: WeatherSettings = WeatherSettings(),
    val effects: EffectSettings = EffectSettings(),
    val drawer: DrawerSettings = DrawerSettings(),
    val music: MusicSettings = MusicSettings(),
    val visual: VisualSettings = VisualSettings(),
    /** Tema TV tabung. Dokumen lama tanpa kunci ini dimigrasi dari gaya vinyl (SettingsMigration). */
    val retro: RetroSettings = RetroSettings(),
    /** Paket aplikasi musik default (null = pilih otomatis). */
    val defaultMusicPackage: String? = null,
    val hiddenApps: Set<String> = emptySet(),
    val presets: List<ThemePreset> = emptyList(),
    val coverEnabled: Boolean = false,
    val onboardingDone: Boolean = false,
)

const val MAX_DOCK_APPS = 8
