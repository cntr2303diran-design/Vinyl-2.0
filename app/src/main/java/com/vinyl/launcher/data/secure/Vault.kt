package com.vinyl.launcher.data.secure

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Untuk apa sebuah blob disegel. Tujuannya ikut dikunci ke dalam segel (data terotentikasi tambahan, AAD), jadi blob buku
 * harian tidak bisa ditempelkan ke tempat favorit kontak atau sebaliknya: dibuka dengan tujuan lain, segelnya ditolak.
 */
enum class VaultPurpose(val label: String) {
    POSTER("vinyl.vault.poster.v1"),
    PHONE("vinyl.vault.phone.v1"),
    DIARY("vinyl.vault.diary.v1"),
    RADIO("vinyl.vault.radio.v1"),
}

/**
 * Sumber kunci AES-256. Di perangkat: Android Keystore ([AndroidKeyProvider]). Di uji unit: kunci perangkat lunak biasa,
 * supaya format dan aturan gagal-tertutup bisa diuji di JVM tanpa Android.
 */
fun interface KeyProvider {
    /** Kunci yang dipakai; [create] = buat bila belum ada. null bila kunci tidak tersedia di perangkat ini. */
    fun key(create: Boolean): SecretKey?
}

/**
 * Brankas enkripsi lokal (AES-256-GCM). Data yang disegel hanya bisa dibuka di perangkat ini, karena kuncinya tidak pernah
 * meninggalkan Keystore dan tidak ikut cadangan apa pun. Format blob: 0x56 'V', versi, panjang IV (12), IV, lalu teks
 * sandi + tag 128-bit. IV acak dibuat tiap segel (tidak pernah dipakai ulang).
 *
 * Gagal-tertutup: bila kunci tidak tersedia atau ada yang salah, [seal] mengembalikan null (pemanggil TIDAK boleh menyimpan
 * versi polosnya) dan [open] mengembalikan null (pemanggil menganggap datanya kosong). Tidak ada yang melempar exception.
 *
 * @param report menerima keterangan singkat saat gagal (hanya nama jenis galatnya, tidak pernah isi data)
 */
class Vault(private val keys: KeyProvider, private val report: (String) -> Unit = {}) {

    /** Segel [plain] untuk [purpose]; null bila tidak bisa disegel. */
    fun seal(purpose: VaultPurpose, plain: ByteArray): ByteArray? {
        try {
            val key = keys.key(create = true) ?: return null
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            cipher.updateAAD(aad(purpose))
            val iv = cipher.iv ?: return null
            if (iv.size != IV_BYTES) return null
            val body = cipher.doFinal(plain)
            val out = ByteArray(HEADER_BYTES + IV_BYTES + body.size)
            out[0] = MAGIC
            out[1] = VERSION
            out[2] = IV_BYTES.toByte()
            System.arraycopy(iv, 0, out, HEADER_BYTES, IV_BYTES)
            System.arraycopy(body, 0, out, HEADER_BYTES + IV_BYTES, body.size)
            return out
        } catch (e: Exception) {
            report("seal gagal: ${e.javaClass.simpleName}")
            return null
        }
    }

    /** Buka [blob] hasil [seal] dengan [purpose] yang sama; null bila bukan segel yang sah, rusak, diubah, atau kuncinya hilang. */
    fun open(purpose: VaultPurpose, blob: ByteArray): ByteArray? {
        if (blob.size < MIN_BLOB_BYTES) return null
        if (blob[0] != MAGIC || blob[1] != VERSION || blob[2].toInt() != IV_BYTES) return null
        try {
            val key = keys.key(create = false) ?: return null
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, blob, HEADER_BYTES, IV_BYTES))
            cipher.updateAAD(aad(purpose))
            return cipher.doFinal(blob, HEADER_BYTES + IV_BYTES, blob.size - HEADER_BYTES - IV_BYTES)
        } catch (e: Exception) {
            report("open gagal: ${e.javaClass.simpleName}")
            return null
        }
    }

    /** Apakah kunci tersedia (dibuat bila perlu). Dipakai untuk memberi tahu pengguna sebelum memilih foto. */
    fun isAvailable(): Boolean = try {
        keys.key(create = true) != null
    } catch (e: Exception) {
        false
    }

    private fun aad(purpose: VaultPurpose): ByteArray = purpose.label.toByteArray(Charsets.UTF_8)

    private companion object {
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val MAGIC: Byte = 0x56
        const val VERSION: Byte = 1
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val TAG_BYTES = TAG_BITS / 8
        const val HEADER_BYTES = 3
        const val MIN_BLOB_BYTES = HEADER_BYTES + IV_BYTES + TAG_BYTES
    }
}
