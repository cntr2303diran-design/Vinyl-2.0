package com.vinyl.launcher.data.secure

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Kunci AES-256 di Android Keystore: dibuat di perangkat ini, tidak bisa diekspor, dipakai tanpa kata sandi atau biometrik
 * (supaya perekaman buku harian di latar tetap jalan), dan hilang bersama aplikasinya. Kuncinya tidak ikut cadangan
 * (cadangan memang dimatikan), jadi data yang dipindah ke perangkat lain tidak akan terbaca di sana.
 *
 * Tidak memakai library Jetpack Security (sudah usang); hanya API platform.
 */
class AndroidKeyProvider(private val alias: String) : KeyProvider {
    @Volatile private var cached: SecretKey? = null

    @Synchronized
    override fun key(create: Boolean): SecretKey? {
        cached?.let { return it }
        return try {
            val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
            val found = store.getKey(alias, null) as? SecretKey
            val key = found ?: if (create) generate() else null
            cached = key
            key
        } catch (e: Exception) {
            Log.w(TAG, "Keystore tidak bisa dipakai: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun generate(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .build()
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val TAG = "Vault"
        const val KEY_BITS = 256
    }
}

/** Brankas yang dipakai aplikasi: kunci di Keystore, nama alias tetap supaya data tersegel tetap terbaca setelah pembaruan. */
object SystemVault {
    private const val ALIAS = "vinyl_local_vault_v1"

    val vault: Vault by lazy { Vault(AndroidKeyProvider(ALIAS)) { Log.w("Vault", it) } }
}
