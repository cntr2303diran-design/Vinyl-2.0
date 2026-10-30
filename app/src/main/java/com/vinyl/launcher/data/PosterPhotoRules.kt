package com.vinyl.launcher.data

import java.io.File
import kotlin.math.roundToInt

/** Aturan foto poster (murni, teruji): nama berkas, ukuran akhir, dan pengambilan sampel saat decode. */
object PosterPhotoRules {
    /** Folder (di penyimpanan internal aplikasi) untuk foto poster tersegel; terpisah dari folder gambar latar. */
    const val DIR = "poster"
    const val PREFIX = "poster-"
    const val EXT = ".vault"

    /** Ekstensi foto poster versi lama (polos): dibuat oleh `SettingsRepository.importImage` di folder gambar latar. */
    const val LEGACY_EXT = ".img"

    /** Sisi terpanjang foto yang disimpan (px). Poster di dinding kecil (maks ±104 dp), jadi ini cukup di layar mana pun. */
    const val MAX_PX = 640
    const val JPEG_QUALITY = 88

    /** Berkas tersegel yang lebih besar dari ini (byte) tidak dibaca; foto yang kita simpan jauh di bawahnya. */
    const val MAX_SEALED_BYTES = 4L * 1024 * 1024

    fun fileName(millis: Long): String = "$PREFIX$millis$EXT"

    /** Nama berkas poster tersegel yang sah: "poster-<angka>.vault". Selain itu ditolak (tidak pernah dibuka atau dihapus). */
    fun isSealedName(name: String): Boolean = isNumbered(name, EXT)

    /**
     * Nama berkas poster versi lama (polos) yang sah: "poster-<angka>.img". "poster" adalah nama pemilih foto yang lama;
     * pemilih gambar latar memakai "home" dan "cover", jadi nama ini tidak pernah milik gambar latar.
     */
    fun isLegacyName(name: String): Boolean = isNumbered(name, LEGACY_EXT)

    private fun isNumbered(name: String, ext: String): Boolean {
        if (name.length > 64 || name.length < PREFIX.length + ext.length) return false
        if (!name.startsWith(PREFIX) || !name.endsWith(ext)) return false
        val digits = name.substring(PREFIX.length, name.length - ext.length)
        return digits.isNotEmpty() && digits.all { it in '0'..'9' }
    }

    /**
     * Hapus foto poster versi lama (polos) di folder gambar latar [dir] selain [keep] (yang masih dipakai pengaturan).
     * Menyapu sisa migrasi yang terputus (proses mati setelah pengaturan menunjuk berkas tersegel, sebelum berkas polosnya
     * terhapus) supaya tidak ada salinan polos yang tertinggal. Hanya berkas biasa bernama "poster-<angka>.img" yang
     * disentuh. Bila [keep] diberikan tetapi tidak bisa dibakukan, tidak ada yang dihapus. Mengembalikan jumlah yang
     * terhapus; tidak pernah melempar.
     */
    fun deleteLegacyExcept(dir: File, keep: String?): Int {
        val keepPath = keep?.let { runCatching { File(it).canonicalPath }.getOrNull() }
        if (keep != null && keepPath == null) return 0
        val files = runCatching { dir.listFiles() }.getOrNull() ?: return 0
        var deleted = 0
        for (f in files) {
            if (!isLegacyName(f.name) || !f.isFile) continue
            val path = runCatching { f.canonicalPath }.getOrNull() ?: continue
            if (path == keepPath) continue
            if (runCatching { f.delete() }.getOrDefault(false)) deleted++
        }
        return deleted
    }

    /** Ukuran akhir (lebar, tinggi) agar sisi terpanjang tidak melebihi [max]; gambar kecil tidak diperbesar. */
    fun scaledSize(w: Int, h: Int, max: Int = MAX_PX): Pair<Int, Int> {
        if (w <= 0 || h <= 0) return 1 to 1
        val longest = maxOf(w, h)
        if (longest <= max) return w to h
        val k = max.toFloat() / longest
        return (w * k).roundToInt().coerceAtLeast(1) to (h * k).roundToInt().coerceAtLeast(1)
    }

    /** `inSampleSize` (pangkat dua) terbesar yang masih menyisakan sisi terpanjang ≥ [target] px; 1 bila sumber tak lebih besar. */
    fun sampleSize(w: Int, h: Int, target: Int = MAX_PX): Int {
        var sample = 1
        val longest = maxOf(w, h)
        while (longest / (sample * 2) >= target) sample *= 2
        return sample
    }

    /**
     * Putaran searah jarum jam (derajat) yang dibutuhkan agar foto tegak menurut tag EXIF orientasi
     * (nilai `ExifInterface.ORIENTATION_*`: 3 = 180°, 6 = 90°, 8 = 270°). Nilai lain (normal, tak dikenal, cermin) = 0.
     */
    fun exifRotation(orientation: Int): Int = when (orientation) {
        3 -> 180
        6 -> 90
        8 -> 270
        else -> 0
    }
}
