package com.vinyl.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Aturan daftar stasiun radio: hanya https yang sah, daftar terbatas, urutan putar melingkar, dan format simpan yang tahan rusak. */
class RadioRulesTest {
    private fun st(n: Int) = RadioStation("Stasiun $n", "https://radio$n.contoh.id/stream")

    @Test
    fun onlyCompleteHttpsAddressesAreStreams() {
        assertTrue(RadioRules.isStreamUrl("https://radio.contoh.id/live.mp3"))
        assertTrue(RadioRules.isStreamUrl("HTTPS://radio.contoh.id:8443/a?b=c#d"))
        for (bad in listOf(
            "", "http://radio.contoh.id/live", "ftp://radio.contoh.id/x", "javascript:alert(1)", "file:///sdcard/a.mp3",
            "content://com.android.contacts/contacts/1", "https://", "https:///x", "radio.contoh.id/live", "//radio.contoh.id/x",
            "https://user:sandi@radio.contoh.id/x", "https://radio.contoh.id/a b", "https://radio.contoh.id/a\nb", "https://radio .id/x",
        )) assertFalse("harus ditolak: $bad", RadioRules.isStreamUrl(bad))
        assertFalse(RadioRules.isStreamUrl("https://radio.contoh.id/" + "a".repeat(RadioRules.MAX_URL)))
    }

    @Test
    fun cleaningTrimsAndStripsControlCharactersAndRefusesEmptyNames() {
        assertEquals(RadioStation("Radio Pos", "https://a.id/x"), RadioRules.clean(RadioStation("  Radio\u0007 Pos  ", "  https://a.id/x ")))
        assertEquals(RadioRules.MAX_NAME, RadioRules.clean(RadioStation("n".repeat(100), "https://a.id/x"))!!.name.length)
        assertNull(RadioRules.clean(RadioStation("   ", "https://a.id/x")))
        assertNull(RadioRules.clean(RadioStation("\u0001\u0002", "https://a.id/x")))
        assertNull(RadioRules.clean(RadioStation("Radio", "http://a.id/x")))
    }

    @Test
    fun sanitizeDropsInvalidKeepsTheLastOfDuplicateAddressesAndCapsTheList() {
        val list = listOf(
            RadioStation("Lama", "https://a.id/x"), RadioStation("Rusak", "http://b.id/x"), RadioStation("Baru", "https://a.id/x"),
        )
        assertEquals(listOf(RadioStation("Baru", "https://a.id/x")), RadioRules.sanitize(list))
        val many = (1..40).map(::st)
        assertEquals(RadioRules.MAX_STATIONS, RadioRules.sanitize(many).size)
        assertEquals(st(1), RadioRules.sanitize(many).first())
    }

    @Test
    fun addingAppendsRenamesTheSameAddressAndRefusesWhenFullOrInvalid() {
        val one = RadioRules.added(emptyList(), st(1))!!
        assertEquals(listOf(st(1)), one)
        val renamed = RadioRules.added(one, RadioStation("Nama Baru", st(1).url))!!
        assertEquals(listOf(RadioStation("Nama Baru", st(1).url)), renamed)
        assertNull(RadioRules.added(one, RadioStation("Rusak", "http://x.id/")))
        val full = (1..RadioRules.MAX_STATIONS).map(::st)
        assertNull("daftar penuh: alamat baru tidak muat", RadioRules.added(full, st(99)))
        assertNotNull("daftar penuh: alamat yang sudah ada tetap boleh diganti namanya", RadioRules.added(full, RadioStation("Ganti", st(3).url)))
    }

    @Test
    fun nextStationCyclesAndStartsAtTheFirstWhenTheLastOneIsUnknown() {
        val list = listOf(st(1), st(2), st(3))
        assertNull(RadioRules.next(emptyList(), null))
        assertEquals(st(1), RadioRules.next(list, null))
        assertEquals(st(1), RadioRules.next(list, "https://hilang.id/x"))
        assertEquals(st(2), RadioRules.next(list, st(1).url))
        assertEquals(st(1), RadioRules.next(list, st(3).url))
        assertEquals(st(1), RadioRules.next(listOf(st(1)), st(1).url))
    }

    @Test
    fun theNeedleSpreadsEvenlyAndStaysInsideTheScale() {
        assertEquals(0.5f, RadioRules.needleAt(0, 1), 0f)
        assertEquals(0.1f, RadioRules.needleAt(0, 5), 1e-6f)
        assertEquals(0.9f, RadioRules.needleAt(4, 5), 1e-6f)
        assertEquals(0.5f, RadioRules.needleAt(2, 5), 1e-6f)
        assertEquals(0.9f, RadioRules.needleAt(99, 5), 1e-6f)
        assertEquals(0.1f, RadioRules.needleAt(-3, 5), 1e-6f)
    }

    @Test
    fun mediaIdsRoundTripAndOnlyRadioIdsAreRadio() {
        val s = st(7)
        val id = RadioRules.idOf(s)
        assertTrue(RadioRules.isRadioId(id))
        assertEquals(s.url, RadioRules.urlOf(id))
        assertFalse(RadioRules.isRadioId("content://media/1"))
        assertFalse(RadioRules.isRadioId("1234"))
        assertFalse(RadioRules.isRadioId(""))
        assertFalse(RadioRules.isRadioId(null))
    }

    @Test
    fun encodingRoundTripsAndABrokenFileReadsAsEmpty() {
        val list = listOf(st(1), st(2))
        assertEquals(list, RadioRules.decode(RadioRules.encode(list)))
        assertTrue(RadioRules.decode(null).isEmpty())
        assertTrue(RadioRules.decode("").isEmpty())
        assertTrue(RadioRules.decode("{bukan json").isEmpty())
        // Entri tidak sah di dalam berkas (disusupi/rusak) dibuang, yang sah tetap terbaca
        val mixed = """[{"name":"Baik","url":"https://a.id/x"},{"name":"Jahat","url":"file:///data/x"},{"name":"","url":"https://b.id/x"}]"""
        assertEquals(listOf(RadioStation("Baik", "https://a.id/x")), RadioRules.decode(mixed))
    }
}
