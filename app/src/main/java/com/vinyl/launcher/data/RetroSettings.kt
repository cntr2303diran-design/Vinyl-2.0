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

/** Warna karakter VFD. */
@Serializable
enum class VfdColor { AMBER, GREEN }

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
)
