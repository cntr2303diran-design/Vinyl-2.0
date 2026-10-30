package com.vinyl.launcher.data.secure

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Brankas AES-256-GCM: format, kerahasiaan, keaslian, dan gagal-tertutup (di JVM dengan kunci perangkat lunak). */
class VaultTest {
    private val keys = FakeKeys()
    private val reports = ArrayList<String>()
    private val vault = Vault(keys) { reports += it }

    private fun sealed(purpose: VaultPurpose = VaultPurpose.DIARY, text: String = "rahasia") =
        vault.seal(purpose, text.toByteArray(Charsets.UTF_8))!!

    private fun contains(haystack: ByteArray, needle: ByteArray): Boolean =
        haystack.indices.any { i -> i + needle.size <= haystack.size && needle.indices.all { haystack[i + it] == needle[it] } }

    @Test
    fun everyPurposeRoundTripsIncludingUnicode() {
        for (p in VaultPurpose.entries) {
            val plain = "Halo ${p.name} — 日本語 😀".toByteArray(Charsets.UTF_8)
            assertArrayEquals(plain, vault.open(p, vault.seal(p, plain)!!))
        }
    }

    @Test
    fun emptyAndLargePayloadsSurvive() {
        assertArrayEquals(ByteArray(0), vault.open(VaultPurpose.POSTER, vault.seal(VaultPurpose.POSTER, ByteArray(0))!!))
        val big = ByteArray(1 shl 20) { (it * 31).toByte() }
        assertArrayEquals(big, vault.open(VaultPurpose.POSTER, vault.seal(VaultPurpose.POSTER, big)!!))
    }

    @Test
    fun blobLayoutIsMagicVersionIvLengthIvThenCiphertextAndTag() {
        val plain = "0123456789".toByteArray()
        val blob = vault.seal(VaultPurpose.PHONE, plain)!!
        assertEquals(0x56.toByte(), blob[0])
        assertEquals(1.toByte(), blob[1])
        assertEquals(12.toByte(), blob[2])
        // 3 (kepala) + 12 (IV) + isi + 16 (tag 128-bit)
        assertEquals(3 + 12 + plain.size + 16, blob.size)
    }

    @Test
    fun everySealUsesAFreshIv() {
        val a = sealed()
        val b = sealed()
        assertNotEquals(a.toList(), b.toList())
        assertNotEquals(a.copyOfRange(3, 15).toList(), b.copyOfRange(3, 15).toList())
        val ivs = (1..50).map { sealed().copyOfRange(3, 15).toList() }.toSet()
        assertEquals(50, ivs.size)
    }

    @Test
    fun theSealedBlobDoesNotShowThePlaintext() {
        val marker = "PENANDA-RAHASIA-12345"
        val blob = sealed(text = "judul $marker artis")
        assertFalse(contains(blob, marker.toByteArray()))
        assertFalse(contains(blob, "judul".toByteArray()))
    }

    @Test
    fun aBlobSealedForOnePurposeIsRejectedForTheOthers() {
        val blob = sealed(VaultPurpose.DIARY)
        assertNotNull(vault.open(VaultPurpose.DIARY, blob))
        assertNull(vault.open(VaultPurpose.PHONE, blob))
        assertNull(vault.open(VaultPurpose.POSTER, blob))
    }

    @Test
    fun anyChangedByteMakesTheBlobUnreadable() {
        val blob = sealed()
        for (i in blob.indices) {
            val bad = blob.copyOf().also { it[i] = (it[i].toInt() xor 0x01).toByte() }
            assertNull("byte $i diubah tetapi masih terbuka", vault.open(VaultPurpose.DIARY, bad))
        }
    }

    @Test
    fun truncatedExtendedAndEmptyBlobsAreRejected() {
        val blob = sealed()
        for (len in 0 until blob.size) assertNull("panjang $len", vault.open(VaultPurpose.DIARY, blob.copyOf(len)))
        assertNull(vault.open(VaultPurpose.DIARY, blob + byteArrayOf(0)))
        assertNull(vault.open(VaultPurpose.DIARY, ByteArray(0)))
        assertNull(vault.open(VaultPurpose.DIARY, ByteArray(200) { 7 }))
    }

    @Test
    fun aDifferentKeyCannotOpenIt() {
        val blob = sealed()
        keys.key = softwareKey()
        assertNull(vault.open(VaultPurpose.DIARY, blob))
    }

    @Test
    fun withoutAKeyNothingIsSealedOrOpenedAndNothingThrows() {
        val blob = sealed()
        keys.key = null
        assertNull(vault.seal(VaultPurpose.DIARY, "x".toByteArray()))
        assertNull(vault.open(VaultPurpose.DIARY, blob))
        assertFalse(vault.isAvailable())
        keys.key = softwareKey()
        assertTrue(vault.isAvailable())
    }

    @Test
    fun aKeyProviderThatThrowsFailsClosedAndReportsOnlyTheErrorType() {
        val secret = "ISI-RAHASIA-DI-PESAN-GALAT"
        val broken = Vault({ throw IllegalStateException(secret) }) { reports += it }
        assertNull(broken.seal(VaultPurpose.DIARY, secret.toByteArray()))
        assertNull(broken.open(VaultPurpose.DIARY, ByteArray(40) { 0x56 }.also { it[1] = 1; it[2] = 12 }))
        assertFalse(broken.isAvailable())
        assertTrue(reports.isNotEmpty())
        assertTrue(reports.all { it.contains("IllegalStateException") })
        assertTrue("laporan tidak boleh memuat isi", reports.none { it.contains(secret) })
    }

    @Test
    fun sealingMayCreateTheKeyButOpeningNeverDoes() {
        // Kunci yang hilang tidak boleh dibuat ulang diam-diam hanya karena ada yang membaca
        val blob = sealed()
        assertEquals(1, keys.createCalls)
        assertEquals(0, keys.openCalls)
        vault.open(VaultPurpose.DIARY, blob)
        assertEquals(1, keys.createCalls)
        assertEquals(1, keys.openCalls)
    }
}
