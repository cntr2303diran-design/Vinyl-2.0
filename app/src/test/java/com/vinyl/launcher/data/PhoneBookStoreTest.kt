package com.vinyl.launcher.data

import com.vinyl.launcher.data.secure.FakeKeys
import com.vinyl.launcher.data.secure.MapSlots
import com.vinyl.launcher.data.secure.SealedText
import com.vinyl.launcher.data.secure.SerialWorker
import com.vinyl.launcher.data.secure.Vault
import com.vinyl.launcher.data.secure.VaultPurpose
import com.vinyl.launcher.data.secure.drain
import com.vinyl.launcher.data.secure.softwareKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Favorit kontak: tersimpan terenkripsi, data lama polos dimigrasi, dan gagal-tertutup bila enkripsi tidak tersedia. */
class PhoneBookStoreTest {
    private val keys = FakeKeys()
    private val vault = Vault(keys)
    private val slots = MapSlots()

    private fun fav(d: Int, name: String = "Ibu $d") = PhoneFavorite(d, name, "content://com.android.contacts/contacts/lookup/abc$d/$d")

    /** Buka buku telepon di atas penyimpanan yang sama (seperti proses baru) dan tunggu pembacaan awal selesai. */
    private fun open(over: MapSlots = slots): Pair<PhoneBook, SerialWorker> {
        val worker = SerialWorker()
        val book = PhoneBook(SealedText(over, vault, VaultPurpose.PHONE, "favorites_sealed", "favorites"), worker)
        worker.drain()
        return book to worker
    }

    @Test
    fun startsEmptyAndWritesNothing() {
        val (book, _) = open()
        assertTrue(book.favorites.value.isEmpty())
        assertFalse(book.saveFailed.value)
        assertTrue(slots.keys().isEmpty())
    }

    @Test
    fun aSavedFavoriteIsEncryptedAndSurvivesARestart() {
        val (book, worker) = open()
        assertTrue(book.set(fav(3, "Budi Santoso")))
        worker.drain()
        assertEquals("Budi Santoso", book.favorites.value[3]?.name)
        assertEquals(setOf("favorites_sealed"), slots.keys())
        assertTrue("tidak boleh ada nama atau alamat polos", slots.values().none { it.contains("Budi Santoso") || it.contains("com.android.contacts") })
        val (again, _) = open()
        assertEquals(book.favorites.value, again.favorites.value)
    }

    @Test
    fun invalidFavoritesAreRefusedAndChangeNothing() {
        val (book, worker) = open()
        assertFalse(book.set(fav(0)))
        assertFalse(book.set(fav(10)))
        assertFalse(book.set(PhoneFavorite(2, "Siapa", "https://contoh.id/x")))
        assertFalse(book.set(PhoneFavorite(2, "   ", "content://com.android.contacts/contacts/lookup/a/1")))
        worker.drain()
        assertTrue(book.favorites.value.isEmpty())
        assertTrue(slots.keys().isEmpty())
    }

    @Test
    fun clearingOneAndClearingAllRemoveTheStoredValue() {
        val (book, worker) = open()
        book.set(fav(1))
        book.set(fav(2))
        worker.drain()
        book.clear(1)
        worker.drain()
        assertEquals(setOf(2), book.favorites.value.keys)
        val (again, _) = open()
        assertEquals(setOf(2), again.favorites.value.keys)
        book.clearAll()
        worker.drain()
        assertTrue(book.favorites.value.isEmpty())
        assertTrue(slots.keys().isEmpty())
    }

    @Test
    fun oneDigitHoldsOneContactAndTheLastOneWins() {
        val (book, worker) = open()
        book.set(fav(4, "Pertama"))
        book.set(fav(4, "Kedua"))
        worker.drain()
        assertEquals("Kedua", book.favorites.value[4]?.name)
    }

    @Test
    fun quickSuccessiveChangesDoNotOverwriteEachOther() {
        val (book, worker) = open()
        for (d in 1..9) book.set(fav(d))
        worker.drain()
        assertEquals((1..9).toSet(), book.favorites.value.keys)
        val (again, _) = open()
        assertEquals((1..9).toSet(), again.favorites.value.keys)
    }

    @Test
    fun anOldPlaintextFileIsSealedOnFirstReadAndThePlaintextRemoved() {
        val old = MapSlots(mapOf("favorites" to PhoneBookRules.encode(listOf(fav(5, "Dewi Lama")))))
        val (book, _) = open(old)
        assertEquals("Dewi Lama", book.favorites.value[5]?.name)
        assertEquals(setOf("favorites_sealed"), old.keys())
        assertTrue(old.values().none { it.contains("Dewi Lama") || it.contains("com.android.contacts") })
        val (again, _) = open(old)
        assertEquals("Dewi Lama", again.favorites.value[5]?.name)
    }

    @Test
    fun withoutEncryptionNothingIsStoredAndTheUserIsTold() {
        val (book, worker) = open()
        keys.key = null
        assertTrue(book.set(fav(6)))
        worker.drain()
        assertTrue(book.saveFailed.value)
        assertTrue("yang gagal disimpan tidak boleh tampak tersimpan", book.favorites.value.isEmpty())
        assertTrue(slots.keys().isEmpty())
        // Enkripsi kembali: berhasil dan peringatannya hilang
        keys.key = softwareKey()
        book.set(fav(6))
        worker.drain()
        assertFalse(book.saveFailed.value)
        assertEquals(setOf(6), book.favorites.value.keys)
    }

    @Test
    fun anUnreadableStoredValueIsTreatedAsEmptyAndCanBeOverwritten() {
        val bad = MapSlots(mapOf("favorites_sealed" to "bukan-segel-yang-sah"))
        val (book, worker) = open(bad)
        assertTrue(book.favorites.value.isEmpty())
        book.set(fav(7))
        worker.drain()
        assertEquals(setOf(7), book.favorites.value.keys)
        val (again, _) = open(bad)
        assertEquals(setOf(7), again.favorites.value.keys)
    }
}
