package com.vinyl.launcher.data

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Menyegel data pribadi versi lama yang masih polos di penyimpanan aplikasi (dijalankan sekali tiap proses dimulai, di thread
 * latar; murah bila tidak ada yang perlu dimigrasi):
 *  - favorit kontak dan buku harian: dibaca lewat [PhoneBook] dan [DiaryStore], yang menyegel isi lama saat pembacaan
 *    pertama lalu menghapus yang polos;
 *  - foto poster: gambar polos di folder gambar latar disegel ([PosterPhotoStore.sealLegacy]); pengaturan menunjuk berkas
 *    tersegel yang baru dan SESUDAH itu berkas polosnya dihapus. Foto yang tidak terbaca (rusak/hilang) dilepas dari poster.
 *    Bila enkripsi tidak tersedia di perangkat ini, foto lama dibiarkan dan dicoba lagi pada proses berikutnya (tidak
 *    dihapus diam-diam).
 * Selain itu sisa berkas di folder poster yang tidak dipakai pengaturan (impor yang terputus) dibersihkan.
 */
object LocalDataMigration {
    private const val TAG = "LocalDataMigration"

    suspend fun run(context: Context) {
        val app = context.applicationContext
        PhoneBook.get(app)
        DiaryStore.get(app)
        runCatching { sealLegacyPoster(app) }.onFailure { Log.w(TAG, "Migrasi poster gagal: ${it.javaClass.simpleName}") }
    }

    private suspend fun sealLegacyPoster(app: Context) {
        val repo = SettingsRepository.get(app)
        val photos = PosterPhotoStore.get(app)
        val path = repo.current().retro.poster.photoPath
        if (path != null && !photos.isSealedPath(path)) {
            val sealed = photos.sealLegacy(File(path))
            if (sealed != null) {
                repo.update { s ->
                    if (s.retro.poster.photoPath == path) s.copy(retro = s.retro.copy(poster = PosterSettings(PosterSource.PHOTO, sealed))) else s
                }
                // Salinan polos dihapus hanya setelah pengaturan menunjuk yang tersegel
                repo.deleteImage(path)
            } else if (photos.vaultAvailable()) {
                // Enkripsi berfungsi tetapi fotonya sendiri tidak terbaca: lepaskan dari poster dan hapus berkasnya
                repo.update { s ->
                    if (s.retro.poster.photoPath == path) s.copy(retro = s.retro.copy(poster = PosterSettings())) else s
                }
                repo.deleteImage(path)
            } else {
                Log.w(TAG, "Penyimpanan terenkripsi tidak tersedia; foto poster lama dibiarkan dan dicoba lagi nanti")
            }
        }
        val keep = repo.current().retro.poster.photoPath?.takeIf { photos.isSealedPath(it) }
        photos.deleteAllExcept(keep)
    }
}
