package com.vinyl.launcher.data

import kotlinx.serialization.Serializable

/*
 * Pengaturan tampilan tema "Retro Projection" (TV tabung di ruang putih).
 *
 * Field tema vinyl yang lama (vinylStyle, tonearmStyle, rpm, turntableSize/Position) TIDAK dihapus dan tidak diganti
 * nama: pengaturan tersimpan dan berkas cadangan lama harus tetap terbaca. Untuk pengguna lama, nilai di sini
 * diturunkan sekali dari field lama oleh [SettingsMigration]; setelah itu field di sini yang menjadi sumber kebenaran
 * untuk tampilan TV/kaset/knob. Ukuran & posisi TV tetap memakai turntableSize/turntablePosition yang sudah ada
 * (artinya sama: ukuran & posisi objek utama beranda), jadi tidak diduplikasi.
 */

/** Bodi TV tabung (menggantikan gaya piringan sebagai pilihan "objek utama"). */
@Serializable
enum class TvBody { CREAM, WALNUT, CHARCOAL, SPACE_AGE }

/** Cangkang kaset (menggantikan varian piringan: klasik, transparan, berwarna, bergambar). */
@Serializable
enum class CassetteShell { BLACK, TRANSPARENT, COLORED, PICTURE }

/** Gaya knob TV (menggantikan gaya tonearm). */
@Serializable
enum class KnobStyle { BAKELITE, CHROME, BRASS }

/** Kecepatan putar gulungan kaset (menggantikan 33/45 RPM). */
@Serializable
enum class ReelSpeed { NORMAL, FAST }

/**
 * Gerak cover di layar CRT. OFF = Mode B (Album Display: cover diam dengan tekstur analog);
 * selain OFF = Mode C (Album Motion) dengan gaya tersebut. Bodi TV tidak pernah ikut bergerak.
 */
@Serializable
enum class AlbumMotion { OFF, ROTATE, ZOOM, PERSPECTIVE, ANALOG }

/** Nada ruang utama: putih hangat (bawaan) atau "ruang redup" (abu hangat gelap) bila pengguna memilihnya. */
@Serializable
enum class RoomTone { LIGHT, DIM }

/**
 * Era dekor ruangan: satu pilihan yang mengganti palet dinding, lantai, kayu meja, dan hiasan dinding sekaligus
 * (palet dan hiasannya di [com.vinyl.launcher.ui.theme.Tokens.Era]). DEFAULT = tampilan lama tanpa perubahan.
 * Dinding Warna/Gradien pilihan pengguna tetap menang atas era (hanya meja, lantai, dan hiasan yang ikut era).
 * Nama konstanta disimpan apa adanya di berkas pengaturan dan cadangan: jangan diganti nama.
 */
@Serializable
enum class RoomEra { DEFAULT, SEVENTIES, EIGHTIES, NINETIES }

/** Warna karakter VFD. */
@Serializable
enum class VfdColor { AMBER, GREEN }

/**
 * Sumber gambar proyektor di dinding belakang TV. VISUALIZER memakai batang spektrum selama visualizer berjalan dan
 * kembali ke cover bila tidak. Video milik pengguna tetap diputar di layar TV, bukan di dinding.
 */
@Serializable
enum class ProjectorSource { COVER, VISUALIZER, OFF }

@Serializable
data class ProjectorSettings(
    val source: ProjectorSource = ProjectorSource.COVER,
    /** Kecerahan gambar proyeksi 0,1..1. */
    val brightness: Float = 0.6f,
    /** Ukuran gambar 0,6..1,2 (1 = ukuran rancangan). */
    val size: Float = 1f,
    /** Ketajaman 0 (sangat buram) .. 1 (tajam). Buram butuh Android 12+; di versi lama gambar tetap tajam. */
    val focus: Float = 0.85f,
    /** Gangguan pita VHS (goyang & garis tracking) pada gambar proyeksi. */
    val vhs: Boolean = true,
)

/** Teks layar kamera (OSD) di beranda: pengganti status bar Samsung saat beranda tampil. */
@Serializable
data class OsdSettings(
    /** Sembunyikan status bar sistem selama beranda tampil (tetap muncul sebentar saat diusap dari atas). */
    val hideStatusBar: Boolean = true,
    /** Tampilkan OSD (jam, tanggal, baterai, jaringan, dering, notifikasi, penghitung pita). */
    val visible: Boolean = true,
    val seconds: Boolean = true,
    /** Ikon & jumlah notifikasi (butuh akses notifikasi yang sudah dipakai untuk musik). */
    val notifications: Boolean = true,
    /** Butir film & garis tracking VHS di beranda. */
    val film: Boolean = true,
)

@Serializable
data class RetroSettings(
    val tvBody: TvBody = TvBody.CREAM,
    // Bawaan pengguna baru mengikuti gambar referensi: kaset transparan, knob logam, VFD hijau, ruang gelap
    val cassetteShell: CassetteShell = CassetteShell.TRANSPARENT,
    val knobStyle: KnobStyle = KnobStyle.CHROME,
    val reelSpeed: ReelSpeed = ReelSpeed.NORMAL,
    val albumMotion: AlbumMotion = AlbumMotion.OFF,
    /** Kekuatan efek layar CRT (scanline, lengkung, aberasi), 0 = mati. */
    val crtStrength: Float = 0.6f,
    /** Intensitas cahaya layar TV & pantulan proyeksi, 0 = mati. */
    val glowIntensity: Float = 0.7f,
    val room: RoomTone = RoomTone.DIM,
    val vfdColor: VfdColor = VfdColor.GREEN,
    /**
     * Tata letak tiga halaman: false (bawaan) = perpustakaan kaset di KIRI beranda dan Audio Console di KANAN.
     * true = ditukar. Pada bahasa RTL arah dicerminkan otomatis.
     */
    val swapPages: Boolean = false,
    /**
     * Ingat tombol daya TV: TV yang terakhir dimatikan dengan tombol dayanya tetap mati saat beranda dimulai lagi
     * (setelah proses dimatikan sistem atau ponsel dinyalakan ulang). Mati (bawaan) = beranda selalu mulai dengan TV
     * menyala. Pilihan ini adalah kebiasaan perangkat, bukan gaya: menerapkan preset tidak mengubahnya
     * ([SettingsMigration.retroToApply]). Keadaan mati-nyalanya sendiri ada di [TvPowerStore], bukan di sini.
     */
    val rememberTvPower: Boolean = false,
    val projector: ProjectorSettings = ProjectorSettings(),
    val osd: OsdSettings = OsdSettings(),
    /** Era dekor ruangan (lihat [RoomEra]). Field baru: dokumen lama tanpa kunci ini terbaca sebagai DEFAULT. */
    val era: RoomEra = RoomEra.DEFAULT,
)
