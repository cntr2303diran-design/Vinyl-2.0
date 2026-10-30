package com.vinyl.launcher.data.secure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/** Teks tersegel di penyimpanan: tidak pernah polos, migrasi data lama aman, dan gagal-tertutup. */
class SealedTextTest {
    private val keys = FakeKeys()
    private val vault = Vault(keys)
    private val slots = MapSlots()

    private fun text(
        purpose: VaultPurpose = VaultPurpose.DIARY,
        legacy: String? = "lama",
        over: TextSlots = slots,
    ) = SealedText(over, vault, purpose, "baru", legacy)

    @Test
    fun writeThenReadRoundTripsAndStoresNoPlaintext() {
        val t = text()
        assertTrue(t.write("isi rahasia — 日本語"))
        assertEquals("isi rahasia — 日本語", t.read())
        assertEquals(setOf("baru"), slots.keys())
        assertTrue(slots.values().none { it.contains("isi rahasia") || it.contains("日本語") })
        val blob = Base64.getDecoder().decode(slots.get("baru"))
        assertEquals(0x56.toByte(), blob[0])
        // Contoh baru di atas penyimpanan yang sama membaca hal yang sama (bertahan lintas proses)
        assertEquals("isi rahasia — 日本語", text().read())
    }

    @Test
    fun nullRemovesEverythingIncludingLeftoverPlaintext() {
        val t = text()
        t.write("a")
        slots.change(mapOf("lama" to "sisa polos"))
        assertTrue(t.write(null))
        assertTrue(slots.keys().isEmpty())
        assertNull(t.read())
    }

    @Test
    fun deletingWorksEvenWhenTheVaultIsUnavailable() {
        val t = text()
        t.write("a")
        keys.key = null
        assertTrue(t.write(null))
        assertTrue(slots.keys().isEmpty())
    }

    @Test
    fun oldPlaintextIsSealedThenRemovedInOneChange() {
        val s = MapSlots(mapOf("lama" to "polos-lama"))
        val t = text(over = s)
        assertEquals("polos-lama", t.read())
        assertEquals(setOf("baru"), s.keys())
        assertEquals("sealed dan polos berubah bersama, tidak bertahap", 1, s.changeCalls)
        assertTrue(s.values().none { it.contains("polos-lama") })
        // Pembacaan berikutnya datang dari yang tersegel
        assertEquals("polos-lama", text(over = s).read())
        assertEquals(1, s.changeCalls)
    }

    @Test
    fun ifSealingFailsTheOldPlaintextStaysAndIsRetriedLater() {
        keys.key = null
        val s = MapSlots(mapOf("lama" to "polos-lama"))
        val t = text(over = s)
        // Datanya tetap terbaca untuk sesi ini...
        assertEquals("polos-lama", t.read())
        // ...tetapi tidak ada yang dihapus dan tidak ada yang ditulis
        assertEquals(setOf("lama"), s.keys())
        assertEquals(0, s.changeCalls)
        keys.key = softwareKey()
        assertEquals("polos-lama", t.read())
        assertEquals(setOf("baru"), s.keys())
    }

    @Test
    fun theSealIsVerifiedBeforeThePlaintextIsDropped() {
        // Kunci bisa menyegel tetapi hasilnya tidak bisa dibuka kembali: yang polos tidak boleh dibuang
        keys.failOpen = true
        val s = MapSlots(mapOf("lama" to "polos-lama"))
        assertEquals("polos-lama", text(over = s).read())
        assertEquals(setOf("lama"), s.keys())
        assertEquals(0, s.changeCalls)
    }

    @Test
    fun theSealedValueWinsOverAStalePlaintextCopy() {
        val t = text()
        t.write("benar")
        slots.change(mapOf("lama" to "usang"))
        assertEquals("benar", t.read())
    }

    @Test
    fun aFailedWriteChangesNothing() {
        val t = text()
        assertTrue(t.write("a"))
        val original = keys.key
        val before = slots.values()
        val changes = slots.changeCalls
        keys.key = null
        assertFalse(t.write("b"))
        assertEquals(before, slots.values())
        assertEquals(changes, slots.changeCalls)
        // Kuncinya kembali: isi lama utuh
        keys.key = original
        assertEquals("a", t.read())
    }

    @Test
    fun aReplacedKeyMakesOldSealsUnreadableButNothingCrashes() {
        val t = text()
        t.write("a")
        keys.key = softwareKey()
        assertNull(t.read())
        // Menulis lagi dengan kunci yang sekarang berhasil dan bisa dibaca
        assertTrue(t.write("b"))
        assertEquals("b", t.read())
    }

    @Test
    fun garbageOrTamperedStoredValuesReadAsEmpty() {
        val t = text()
        slots.change(mapOf("baru" to "###bukan-base64###"))
        assertNull(t.read())
        slots.change(mapOf("baru" to Base64.getEncoder().encodeToString(ByteArray(60) { it.toByte() })))
        assertNull(t.read())
        t.write("asli")
        val blob = Base64.getDecoder().decode(slots.get("baru"))
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 1).toByte()
        slots.change(mapOf("baru" to Base64.getEncoder().encodeToString(blob)))
        assertNull(t.read())
        // Penulisan berikutnya menimpa segel yang rusak
        assertTrue(t.write("baru lagi"))
        assertEquals("baru lagi", t.read())
    }

    @Test
    fun aSealForOnePurposeIsNotReadableAsAnother() {
        text(VaultPurpose.DIARY).write("harian")
        assertNull(text(VaultPurpose.PHONE).read())
        assertEquals("harian", text(VaultPurpose.DIARY).read())
    }

    @Test
    fun worksWithoutALegacyKey() {
        val t = SealedText(slots, vault, VaultPurpose.POSTER, "baru")
        assertNull(t.read())
        assertTrue(t.write("x"))
        assertEquals("x", t.read())
        assertNotNull(slots.get("baru"))
    }
}
