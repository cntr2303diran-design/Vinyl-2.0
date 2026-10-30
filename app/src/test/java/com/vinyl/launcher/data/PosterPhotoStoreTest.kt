package com.vinyl.launcher.data

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vinyl.launcher.data.secure.FakeKeys
import com.vinyl.launcher.data.secure.Vault
import com.vinyl.launcher.data.secure.VaultPurpose
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

/**
 * Foto poster dari galeri → dikecilkan, ditegakkan, dibuang metadatanya, lalu disegel di folder aplikasi. Dijalankan di
 * Robolectric dengan grafis asli (decode/encode sungguhan) dan brankas berkunci perangkat lunak. Memakai Application polos
 * (bukan VinylApp) supaya tidak ada migrasi latar milik aplikasi yang ikut menyentuh folder poster saat tes berjalan.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class PosterPhotoStoreTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val keys = FakeKeys()
    private val vault = Vault(keys)
    private lateinit var store: PosterPhotoStore
    private lateinit var scratch: File

    private val posterDir get() = File(context.filesDir, "poster")

    @Before
    fun setUp() {
        store = PosterPhotoStore(context, vault)
        posterDir.deleteRecursively()
        scratch = File(context.cacheDir, "uji-poster").apply { deleteRecursively(); mkdirs() }
    }

    // ---------------------------------------------------------------- bahan uji

    private fun bytes(bmp: Bitmap, format: Bitmap.CompressFormat): ByteArray {
        val out = ByteArrayOutputStream()
        assertTrue(bmp.compress(format, 95, out))
        return out.toByteArray()
    }

    /** Kiri merah, kanan biru: arah gambar bisa diperiksa dari warna. */
    private fun halves(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint()
        paint.color = Color.RED
        c.drawRect(0f, 0f, w / 2f, h.toFloat(), paint)
        paint.color = Color.BLUE
        c.drawRect(w / 2f, 0f, w.toFloat(), h.toFloat(), paint)
        return bmp
    }

    private fun write(name: String, data: ByteArray): File = File(scratch, name).also { it.writeBytes(data) }

    /** Segmen APP1 "Exif" berisi tag orientasi; [extra] adalah data tambahan di dalam segmen (mis. penanda lokasi). */
    private fun exifSegment(orientation: Int, extra: ByteArray = ByteArray(0)): ByteArray {
        val tiff = byteArrayOf(
            0x49, 0x49, 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00, // "II", 42, offset IFD0
            0x01, 0x00, // satu entri
            0x12, 0x01, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00, // tag Orientation, SHORT, jumlah 1
            orientation.toByte(), 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, // tidak ada IFD berikutnya
        )
        val body = "Exif".toByteArray() + byteArrayOf(0, 0) + tiff + extra
        val length = body.size + 2
        return byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + body
    }

    /** JPEG biasa dengan segmen Exif disisipkan tepat setelah penanda awal berkas. */
    private fun jpegWithExif(bmp: Bitmap, orientation: Int, extra: ByteArray = ByteArray(0)): ByteArray {
        val plain = bytes(bmp, Bitmap.CompressFormat.JPEG)
        return plain.copyOfRange(0, 2) + exifSegment(orientation, extra) + plain.copyOfRange(2, plain.size)
    }

    private fun contains(haystack: ByteArray, needle: ByteArray): Boolean =
        haystack.indices.any { i -> i + needle.size <= haystack.size && needle.indices.all { haystack[i + it] == needle[it] } }

    private fun sealedFiles(): List<File> = posterDir.listFiles()?.toList().orEmpty()

    /** Isi foto tersegel yang sudah dibuka kembali, sebagai Bitmap. */
    private fun opened(path: String): Bitmap {
        val plain = vault.open(VaultPurpose.POSTER, File(path).readBytes())!!
        return BitmapFactory.decodeByteArray(plain, 0, plain.size)!!
    }

    private fun seal(file: File): String = runBlocking { store.sealLegacy(file) }!!

    // ---------------------------------------------------------------- menyimpan

    @Test
    fun aPhotoIsStoredAsOneEncryptedFileAndNothingElse() {
        val src = write("a.jpg", bytes(halves(800, 600), Bitmap.CompressFormat.JPEG))
        val path = seal(src)
        assertTrue(store.isSealedPath(path))
        assertEquals(1, sealedFiles().size)
        assertTrue(PosterPhotoRules.isSealedName(File(path).name))
        assertEquals("tidak ada sisa berkas sementara", listOf(File(path).name), sealedFiles().map { it.name })
        val raw = File(path).readBytes()
        assertFalse("bukan JPEG polos", raw[0] == 0xFF.toByte() && raw[1] == 0xD8.toByte())
        assertFalse(contains(raw, "JFIF".toByteArray()))
        assertEquals(0x56.toByte(), raw[0])
    }

    @Test
    fun theOriginalFileIsNotTouched() {
        val data = bytes(halves(300, 200), Bitmap.CompressFormat.JPEG)
        val src = write("asli.jpg", data)
        seal(src)
        assertTrue(src.exists())
        assertTrue(data.contentEquals(src.readBytes()))
    }

    @Test
    fun largePhotosAreShrunkToTheLongestSideAndKeepTheirRatio() {
        val path = seal(write("besar.jpg", bytes(halves(3000, 2000), Bitmap.CompressFormat.JPEG)))
        val back = opened(path)
        assertEquals(PosterPhotoRules.MAX_PX, back.width)
        assertTrue("tinggi ${back.height}", back.height in 426..428)
        assertTrue("berkas tersegel kecil", File(path).length() < PosterPhotoRules.MAX_SEALED_BYTES)
    }

    @Test
    fun smallPhotosAreNotEnlarged() {
        val back = opened(seal(write("kecil.png", bytes(halves(200, 100), Bitmap.CompressFormat.PNG))))
        assertEquals(200, back.width)
        assertEquals(100, back.height)
    }

    @Test
    fun aRotatedPhotoIsStoredUpright() {
        // Berkas 800x400 bertanda orientasi 6 (putar 90° searah jarum jam): tampil tegak 400x800, lalu dikecilkan ke 320x640.
        // Kiri (merah) menjadi atas, kanan (biru) menjadi bawah.
        val src = write("putar.jpg", jpegWithExif(halves(800, 400), orientation = 6))
        val back = opened(seal(src))
        assertEquals(320, back.width)
        assertEquals(640, back.height)
        val top = back.getPixel(back.width / 2, back.height / 4)
        val bottom = back.getPixel(back.width / 2, back.height * 3 / 4)
        assertTrue("atas harus merah: ${Integer.toHexString(top)}", Color.red(top) > 150 && Color.blue(top) < 100)
        assertTrue("bawah harus biru: ${Integer.toHexString(bottom)}", Color.blue(bottom) > 150 && Color.red(bottom) < 100)
    }

    @Test
    fun aPhotoWithoutRotationKeepsItsDirection() {
        val back = opened(seal(write("lurus.jpg", jpegWithExif(halves(400, 200), orientation = 1))))
        assertEquals(400, back.width)
        assertEquals(200, back.height)
        val left = back.getPixel(back.width / 4, back.height / 2)
        val right = back.getPixel(back.width * 3 / 4, back.height / 2)
        assertTrue(Color.red(left) > 150 && Color.blue(left) < 100)
        assertTrue(Color.blue(right) > 150 && Color.red(right) < 100)
    }

    @Test
    fun locationAndCameraMetadataAreNotCarriedIntoTheStoredPhoto() {
        val secret = "LOKASI-RAHASIA-6.2088S-106.8456E".toByteArray()
        val source = jpegWithExif(halves(400, 300), orientation = 6, extra = secret)
        assertTrue("sumber memang memuat penanda", contains(source, secret))
        val path = seal(write("lokasi.jpg", source))
        val plain = vault.open(VaultPurpose.POSTER, File(path).readBytes())!!
        assertEquals(0xFF.toByte(), plain[0])
        assertEquals(0xD8.toByte(), plain[1])
        assertFalse("tidak boleh ada penanda lokasi", contains(plain, secret))
        assertFalse("tidak boleh ada segmen Exif sama sekali", contains(plain, "Exif".toByteArray()))
        assertFalse(contains(File(path).readBytes(), secret))
    }

    @Test
    fun aTransparentPngIsFlattenedOntoWhiteNotBlack() {
        val clear = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) }
        val back = opened(seal(write("bening.png", bytes(clear, Bitmap.CompressFormat.PNG))))
        val p = back.getPixel(back.width / 2, back.height / 2)
        assertTrue("harus putih, bukan hitam: ${Integer.toHexString(p)}", Color.red(p) > 235 && Color.green(p) > 235 && Color.blue(p) > 235)
    }

    @Test
    fun importingFromAStreamOpenerGivesTheSameKindOfFile() {
        // Jalur yang sama dengan memilih foto di Photo Picker (di sana aliran dibuka lewat ContentResolver)
        val src = write("aliran.jpg", bytes(halves(500, 300), Bitmap.CompressFormat.JPEG))
        val path = runBlocking { store.importFrom { FileInputStream(src) } }
        assertNotNull(path)
        assertTrue(store.isSealedPath(path!!))
        assertEquals(500, opened(path).width)
        assertEquals(1, sealedFiles().size)
    }

    @Test
    fun anOpenerThatReturnsNothingOrThrowsStoresNothing() {
        assertNull(runBlocking { store.importFrom { null } })
        assertNull(runBlocking { store.importFrom { throw java.io.IOException("tidak bisa dibuka") } })
        assertTrue(sealedFiles().isEmpty())
    }

    // ---------------------------------------------------------------- gagal-tertutup

    @Test
    fun withoutEncryptionNothingIsWritten() {
        keys.key = null
        assertFalse(store.vaultAvailable())
        val src = write("x.jpg", bytes(halves(100, 100), Bitmap.CompressFormat.JPEG))
        assertNull(runBlocking { store.sealLegacy(src) })
        assertNull(runBlocking { store.importFrom { FileInputStream(src) } })
        assertTrue("tidak ada salinan polos di folder poster", sealedFiles().isEmpty())
    }

    @Test
    fun anUnreadableImageStoresNothing() {
        val junk = write("rusak.jpg", ByteArray(500) { (it * 7).toByte() })
        assertNull(runBlocking { store.sealLegacy(junk) })
        assertNull(runBlocking { store.sealLegacy(File(scratch, "tidak-ada.jpg")) })
        assertTrue(sealedFiles().isEmpty())
    }

    // ---------------------------------------------------------------- membuka

    @Test
    fun loadReturnsTheBitmapAtOrNearTheRequestedSize() {
        val path = seal(write("buka.jpg", bytes(halves(640, 480), Bitmap.CompressFormat.JPEG)))
        val full = store.load(path, 512)!!
        assertEquals(640, full.width)
        val small = store.load(path, 100)!!
        assertEquals("pengambilan sampel pangkat dua", 160, small.width)
        assertEquals(120, small.height)
    }

    @Test
    fun loadRefusesFilesThatAreNotOurOwnPosters() {
        val path = seal(write("buka.jpg", bytes(halves(100, 100), Bitmap.CompressFormat.JPEG)))
        val bytes = File(path).readBytes()
        // Di luar folder poster
        val outside = File(scratch, "poster-1.vault").also { it.writeBytes(bytes) }
        assertNull(store.load(outside.absolutePath, 512))
        // Di dalam folder poster tetapi bernama asing
        val oddName = File(posterDir, "foto.jpg").also { it.writeBytes(bytes) }
        assertNull(store.load(oddName.absolutePath, 512))
        // Di subfolder
        val sub = File(posterDir, "sub").apply { mkdirs() }
        val nested = File(sub, "poster-2.vault").also { it.writeBytes(bytes) }
        assertNull(store.load(nested.absolutePath, 512))
        assertFalse(store.isSealedPath(nested.absolutePath))
        // Tidak ada
        assertNull(store.load(File(posterDir, "poster-99.vault").absolutePath, 512))
        // Yang sah tetap terbuka
        assertNotNull(store.load(path, 512))
    }

    @Test
    fun loadRefusesTamperedOrForeignContent() {
        val path = seal(write("buka.jpg", bytes(halves(100, 100), Bitmap.CompressFormat.JPEG)))
        val good = File(path).readBytes()
        val tampered = good.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
        File(path).writeBytes(tampered)
        assertNull(store.load(path, 512))
        // JPEG polos yang ditaruh di folder poster dengan nama sah tidak dianggap foto kita
        val plain = bytes(halves(50, 50), Bitmap.CompressFormat.JPEG)
        val foreign = File(posterDir, "poster-7.vault").also { it.writeBytes(plain) }
        assertNull(store.load(foreign.absolutePath, 512))
        // Berkas yang terlalu besar tidak dibaca
        val huge = File(posterDir, "poster-8.vault").also { it.writeBytes(ByteArray((PosterPhotoRules.MAX_SEALED_BYTES + 1).toInt())) }
        assertNull(store.load(huge.absolutePath, 512))
    }

    @Test
    fun aPosterSealedForAnotherPurposeIsNotShownAsAPoster() {
        val plain = bytes(halves(50, 50), Bitmap.CompressFormat.JPEG)
        val asDiary = vault.seal(VaultPurpose.DIARY, plain)!!
        posterDir.mkdirs()
        val f = File(posterDir, "poster-5.vault").also { it.writeBytes(asDiary) }
        assertNull(store.load(f.absolutePath, 512))
    }

    // ---------------------------------------------------------------- menghapus

    @Test
    fun deleteRemovesOnlyOurOwnPosterFiles() {
        val path = seal(write("hapus.jpg", bytes(halves(60, 60), Bitmap.CompressFormat.JPEG)))
        val other = File(context.filesDir, "bukan-poster.txt").also { it.writeText("jangan hapus") }
        assertFalse(runBlocking { store.delete(other.absolutePath) })
        assertTrue(other.exists())
        assertFalse(runBlocking { store.delete("/etc/hosts") })
        assertTrue(runBlocking { store.delete(path) })
        assertFalse(File(path).exists())
        assertFalse("sudah tidak ada", runBlocking { store.delete(path) })
    }

    @Test
    fun deleteAllExceptKeepsOnlyTheNamedPhoto() {
        val a = seal(write("a.jpg", bytes(halves(60, 60), Bitmap.CompressFormat.JPEG)))
        Thread.sleep(3) // nama berkas memakai milidetik
        val b = seal(write("b.jpg", bytes(halves(60, 60), Bitmap.CompressFormat.JPEG)))
        File(posterDir, "poster-1.vault.tmp").writeText("sisa impor yang terputus")
        assertEquals(3, sealedFiles().size)
        runBlocking { store.deleteAllExcept(b) }
        assertEquals(listOf(File(b).name), sealedFiles().map { it.name })
        assertFalse(File(a).exists())
        runBlocking { store.deleteAllExcept(null) }
        assertTrue(sealedFiles().isEmpty())
    }
}
