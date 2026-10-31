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

/** Daftar stasiun radio: tersimpan terenkripsi, selamat dari mulai-ulang, dan gagal-tertutup bila enkripsi tidak tersedia. */
class RadioStationBookTest {
    private val keys = FakeKeys()
    private val vault = Vault(keys)
    private val slots = MapSlots()

    private fun st(n: Int) = RadioStation("Stasiun $n", "https://radio$n.contoh.id/stream")

    private fun open(over: MapSlots = slots): Pair<RadioStationBook, SerialWorker> {
        val worker = SerialWorker()
        val book = RadioStationBook(SealedText(over, vault, VaultPurpose.RADIO, "stations_sealed"), worker)
        worker.drain()
        return book to worker
    }

    @Test
    fun startsEmptyAndWritesNothing() {
        val (book, _) = open()
        assertTrue(book.stations.value.isEmpty())
        assertFalse(book.saveFailed.value)
        assertTrue(slots.keys().isEmpty())
    }

    @Test
    fun aSavedStationIsEncryptedAndSurvivesARestart() {
        val (book, worker) = open()
        assertTrue(book.add(RadioStation("Radio Rimba", "https://rimba.contoh.id/live.mp3")))
        worker.drain()
        assertEquals("Radio Rimba", book.stations.value.single().name)
        assertEquals(setOf("stations_sealed"), slots.keys())
        assertTrue("tidak boleh ada nama atau alamat polos", slots.values().none { it.contains("Rimba") || it.contains("contoh.id") })
        val (again, _) = open()
        assertEquals(book.stations.value, again.stations.value)
    }

    @Test
    fun invalidAndOverflowingStationsAreRefusedAndChangeNothing() {
        val (book, worker) = open()
        assertFalse(book.add(RadioStation("Polos", "http://polos.id/x")))
        assertFalse(book.add(RadioStation("  ", "https://a.id/x")))
        worker.drain()
        assertTrue(book.stations.value.isEmpty())
        assertTrue(slots.keys().isEmpty())
        repeat(RadioRules.MAX_STATIONS) { assertTrue(book.add(st(it))) ; worker.drain() }
        assertFalse("daftar penuh", book.add(st(99)))
        worker.drain()
        assertEquals(RadioRules.MAX_STATIONS, book.stations.value.size)
    }

    @Test
    fun removingOneAndClearingAllRemoveTheStoredValue() {
        val (book, worker) = open()
        book.add(st(1)); book.add(st(2))
        worker.drain()
        book.remove(st(1).url)
        worker.drain()
        assertEquals(listOf(st(2)), book.stations.value)
        assertEquals(listOf(st(2)), open().first.stations.value)
        book.clearAll()
        worker.drain()
        assertTrue(book.stations.value.isEmpty())
        assertTrue(slots.keys().isEmpty())
    }

    @Test
    fun addingTheSameAddressAgainOnlyRenamesIt() {
        val (book, worker) = open()
        book.add(RadioStation("Lama", "https://a.id/x"))
        book.add(RadioStation("Baru", "https://a.id/x"))
        worker.drain()
        assertEquals(listOf(RadioStation("Baru", "https://a.id/x")), book.stations.value)
    }

    @Test
    fun withoutEncryptionNothingIsStoredAndTheUserIsTold() {
        val (book, worker) = open()
        keys.key = null
        assertTrue(book.add(st(1)))
        worker.drain()
        assertTrue(book.saveFailed.value)
        assertTrue("yang gagal disimpan tidak boleh tampak tersimpan", book.stations.value.isEmpty())
        assertTrue(slots.keys().isEmpty())
        keys.key = softwareKey()
        book.add(st(1))
        worker.drain()
        assertFalse(book.saveFailed.value)
        assertEquals(listOf(st(1)), book.stations.value)
    }

    @Test
    fun anUnreadableStoredValueIsTreatedAsEmptyAndCanBeOverwritten() {
        val bad = MapSlots(mapOf("stations_sealed" to "bukan-segel-yang-sah"))
        val (book, worker) = open(bad)
        assertTrue(book.stations.value.isEmpty())
        book.add(st(2))
        worker.drain()
        assertEquals(listOf(st(2)), book.stations.value)
        assertEquals(listOf(st(2)), open(bad).first.stations.value)
    }

    @Test
    fun aSealedPhoneBookBlobCannotBePassedOffAsAStationList() {
        // Tujuan segel ikut terkunci: blob favorit kontak tidak terbuka sebagai daftar stasiun
        val phoneSlots = MapSlots()
        val phone = SealedText(phoneSlots, vault, VaultPurpose.PHONE, "stations_sealed")
        assertTrue(phone.write("""[{"name":"Peretas","url":"https://x.id/a"}]"""))
        val (book, _) = open(phoneSlots)
        assertTrue(book.stations.value.isEmpty())
    }
}
