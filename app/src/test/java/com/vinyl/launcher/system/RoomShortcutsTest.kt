package com.vinyl.launcher.system

import com.vinyl.launcher.data.PhoneFavorite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomShortcutsTest {
    private val favs = mapOf(3 to PhoneFavorite(3, "Ibu", "content://com.android.contacts/contacts/lookup/a/1"))

    @Test
    fun filledDigitOpensTheContact() {
        val a = RoomShortcuts.dialAction(3, favs)
        assertTrue(a is DialAction.OpenContact)
        assertEquals("Ibu", (a as DialAction.OpenContact).name)
    }

    @Test
    fun emptyDigitIsReportedAsEmpty() {
        assertEquals(DialAction.Empty(4), RoomShortcuts.dialAction(4, favs))
    }

    @Test
    fun zeroAndOutOfRangeOpenThePhoneApp() {
        for (d in listOf(0, -1, 10, 99)) assertEquals(DialAction.OpenPhone, RoomShortcuts.dialAction(d, favs))
    }
}
