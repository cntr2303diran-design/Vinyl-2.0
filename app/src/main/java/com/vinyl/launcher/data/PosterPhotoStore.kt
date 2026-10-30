package com.vinyl.launcher.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.vinyl.launcher.data.secure.SystemVault
import com.vinyl.launcher.data.secure.Vault
import com.vinyl.launcher.data.secure.VaultPurpose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Foto poster di dinding, disimpan TERENKRIPSI. Foto dari Photo Picker dibaca, diputar tegak menurut EXIF, dikecilkan ke
 * sisi terpanjang [PosterPhotoRules.MAX_PX], disimpan ulang sebagai JPEG (jadi lokasi GPS dan metadata kamera TIDAK ikut
 * tersimpan), lalu disegel dengan [Vault] (AES-256-GCM, kunci di Android Keystore) ke folder internal aplikasi. Berkas
 * asli di galeri tidak disentuh dan tidak ada salinan polos yang tertinggal. Tidak ikut cadangan.
 *
 * Bila enkripsi tidak tersedia, [importPhoto] mengembalikan null (foto tidak disimpan) alih-alih menyimpan salinan polos.
 */
class PosterPhotoStore internal constructor(private val context: Context, private val vault: Vault) {
    private val dir: File get() = File(context.filesDir, PosterPhotoRules.DIR)

    /** Path menunjuk berkas poster tersegel milik aplikasi ini: berkas biasa, langsung di folder poster, bernama sah. */
    fun isSealedPath(path: String): Boolean = runCatching {
        val f = File(path)
        f.isFile && f.canonicalFile.parentFile == dir.canonicalFile && PosterPhotoRules.isSealedName(f.name)
    }.getOrDefault(false)

    fun vaultAvailable(): Boolean = vault.isAvailable()

    /** Foto pilihan pengguna → berkas tersegel. null bila foto tidak terbaca atau enkripsi tidak tersedia. */
    suspend fun importPhoto(uri: Uri): String? = importFrom { context.contentResolver.openInputStream(uri) }

    /** Foto poster versi lama (gambar polos di folder gambar latar) → berkas tersegel. null bila gagal. */
    suspend fun sealLegacy(file: File): String? = importFrom { FileInputStream(file) }

    /** Jalur bersama keduanya. [open] dipanggil beberapa kali (tiap kali aliran baru) dan alirannya ditutup di sini. */
    internal suspend fun importFrom(open: () -> InputStream?): String? = withContext(Dispatchers.IO) {
        val jpeg = process(open) ?: return@withContext null
        store(jpeg)
    }

    /**
     * Buka foto tersegel sebagai Bitmap dengan sisi terpanjang ≈ [maxPx] (pengambilan sampel pangkat dua, jadi bisa sedikit
     * lebih besar). null bila bukan berkas poster kita, rusak, diubah, atau tidak bisa dibuka. Dipanggil di thread latar.
     */
    fun load(path: String, maxPx: Int): Bitmap? {
        if (!isSealedPath(path)) return null
        val file = File(path)
        if (file.length() > PosterPhotoRules.MAX_SEALED_BYTES) return null
        val blob = try {
            file.readBytes()
        } catch (e: IOException) {
            return null
        }
        val plain = vault.open(VaultPurpose.POSTER, blob) ?: return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(plain, 0, plain.size, bounds)
            val opts = BitmapFactory.Options().apply { inSampleSize = PosterPhotoRules.sampleSize(bounds.outWidth, bounds.outHeight, maxPx) }
            BitmapFactory.decodeByteArray(plain, 0, plain.size, opts)
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** Hapus satu foto tersegel (hanya berkas poster milik aplikasi ini). */
    suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        isSealedPath(path) && runCatching { File(path).delete() }.getOrDefault(false)
    }

    /** Buang semua berkas di folder poster kecuali [keep]: sisa impor yang gagal dan foto yang sudah tidak dipakai. */
    suspend fun deleteAllExcept(keep: String?) {
        withContext(Dispatchers.IO) {
            val keepName = keep?.let { File(it).name }
            dir.listFiles()?.forEach { f ->
                if (f.isFile && f.name != keepName) runCatching { f.delete() }
            }
        }
    }

    // ---------------------------------------------------------------- pengolahan gambar

    /** Foto dari [open] → JPEG kecil yang tegak; null bila tidak terbaca. [open] dipanggil beberapa kali (tiap kali aliran baru). */
    private fun process(open: () -> InputStream?): ByteArray? = try {
        decodeUpright(open)?.let { encodeJpeg(it) }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    private fun decodeUpright(open: () -> InputStream?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = PosterPhotoRules.sampleSize(bounds.outWidth, bounds.outHeight) }
        val raw = open()?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        // Dibungkus BufferedInputStream: ExifInterface membaca dari aliran yang bisa diputar ulang (mark/reset), tanpa bergantung
        // pada berkas yang bisa di-seek. Foto tanpa tag EXIF atau yang tag-nya tak terbaca dianggap sudah tegak.
        val orientation = runCatching {
            open()?.use { ExifInterface(BufferedInputStream(it)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val turn = PosterPhotoRules.exifRotation(orientation)
        val upright = if (turn == 0) raw else {
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(turn.toFloat()) }, true)
                .also { if (it !== raw) raw.recycle() }
        }
        val (w, h) = PosterPhotoRules.scaledSize(upright.width, upright.height)
        val scaled = if (w == upright.width && h == upright.height) upright else {
            upright.scale(w, h, true).also { if (it !== upright) upright.recycle() }
        }
        return flatten(scaled)
    }

    /** JPEG tak punya transparansi: gambar bertransparansi (PNG/WebP) dialasi putih supaya tidak jadi hitam. */
    private fun flatten(src: Bitmap): Bitmap {
        if (!src.hasAlpha()) return src
        val out = createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(src, 0f, 0f, null)
        }
        src.recycle()
        return out
    }

    private fun encodeJpeg(bmp: Bitmap): ByteArray? {
        val out = ByteArrayOutputStream()
        val ok = bmp.compress(Bitmap.CompressFormat.JPEG, PosterPhotoRules.JPEG_QUALITY, out)
        bmp.recycle()
        return if (ok) out.toByteArray() else null
    }

    /** Segel [jpeg] dan tulis secara atomik (berkas sementara lalu diganti nama). Mengembalikan path berkas, atau null. */
    private fun store(jpeg: ByteArray): String? {
        val blob = vault.seal(VaultPurpose.POSTER, jpeg) ?: return null
        val folder = dir
        if (!folder.isDirectory && !folder.mkdirs() && !folder.isDirectory) return null
        val name = PosterPhotoRules.fileName(System.currentTimeMillis())
        val tmp = File(folder, "$name.tmp")
        val target = File(folder, name)
        return try {
            tmp.writeBytes(blob)
            if (tmp.renameTo(target)) {
                target.absolutePath
            } else {
                tmp.delete()
                null
            }
        } catch (e: IOException) {
            tmp.delete()
            null
        }
    }

    companion object {
        // Yang disimpan adalah applicationContext (hidup sepanjang proses), bukan Activity: tidak bocor
        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: PosterPhotoStore? = null

        fun get(context: Context): PosterPhotoStore =
            instance ?: synchronized(this) {
                instance ?: PosterPhotoStore(context.applicationContext, SystemVault.vault).also { instance = it }
            }
    }
}
