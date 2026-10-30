package com.vinyl.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneBookRulesTest {
    private val uri = "content://com.android.contacts/contacts/lookup/abc/12"

    @Test
    fun onlyDigitsOneToNineAreAccepted() {
        assertFalse(PhoneBookRules.isDigit(0))
        assertTrue(PhoneBookRules.isDigit(1))
        assertTrue(PhoneBookRules.isDigit(9))
        assertFalse(PhoneBookRules.isDigit(10))
        assertNull(PhoneBookRules.clean(PhoneFavorite(0, "A", uri)))
        assertNull(PhoneBookRules.clean(PhoneFavorite(10, "A", uri)))
    }

    @Test
    fun onlyContactUrisAreAccepted() {
        assertTrue(PhoneBookRules.isContactUri(uri))
        assertFalse(PhoneBookRules.isContactUri("https://example.com/"))
        assertFalse(PhoneBookRules.isContactUri("tel:112"))
        assertFalse(PhoneBookRules.isContactUri("content://media/external/images/1"))
        assertFalse(PhoneBookRules.isContactUri(uri + "x".repeat(600)))
        assertFalse(PhoneBookRules.isContactUri("$uri\n"))
        assertNull(PhoneBookRules.clean(PhoneFavorite(1, "A", "tel:112")))
    }

    @Test
    fun namesAreTrimmedStrippedAndCapped() {
        val c = PhoneBookRules.clean(PhoneFavorite(2, "  Ibu\u0007 ${"x".repeat(100)}", uri))
        assertNotNull(c)
        assertTrue(c!!.name.length <= PhoneBookRules.MAX_NAME)
        assertFalse(c.name.any { it.isISOControl() })
        assertTrue(c.name.startsWith("Ibu"))
    }

    @Test
    fun encodeDecodeRoundTrips() {
        val list = listOf(PhoneFavorite(1, "Ibu", uri), PhoneFavorite(5, "Kantor", uri + "2"))
        val back = PhoneBookRules.decode(PhoneBookRules.encode(list))
        assertEquals(setOf(1, 5), back.keys)
        assertEquals("Kantor", back[5]!!.name)
    }

    @Test
    fun garbageAndBadEntriesDecodeToSafeValues() {
        assertTrue(PhoneBookRules.decode(null).isEmpty())
        assertTrue(PhoneBookRules.decode("").isEmpty())
        assertTrue(PhoneBookRules.decode("bukan json {{").isEmpty())
        val raw = """[{"digit":0,"name":"x","uri":"$uri"},{"digit":3,"name":"y","uri":"tel:1"},{"digit":4,"name":"ok","uri":"$uri"}]"""
        assertEquals(setOf(4), PhoneBookRules.decode(raw).keys)
    }

    @Test
    fun laterDuplicateOfADigitDoesNotCrash() {
        val raw = """[{"digit":4,"name":"a","uri":"$uri"},{"digit":4,"name":"b","uri":"$uri"}]"""
        assertEquals(1, PhoneBookRules.decode(raw).size)
    }
}
