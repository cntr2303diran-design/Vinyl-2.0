package com.vinyl.launcher.ui.home

import com.vinyl.launcher.ui.home.RoomPage.CONSOLE
import com.vinyl.launcher.ui.home.RoomPage.LIBRARY
import com.vinyl.launcher.ui.home.RoomPage.ROOM
import org.junit.Assert.assertEquals
import org.junit.Test

class PageNavTest {
    private val far = 400f

    @Test
    fun libraryIsOnTheLeftAndConsoleOnTheRightByDefault() {
        assertEquals(listOf(LIBRARY, ROOM, CONSOLE), PageNav.order(swap = false, rtl = false))
    }

    @Test
    fun swipingRightRevealsTheLeftPage() {
        assertEquals(LIBRARY, PageNav.afterSwipe(ROOM, +far, swap = false, rtl = false))
        assertEquals(CONSOLE, PageNav.afterSwipe(ROOM, -far, swap = false, rtl = false))
        // Kembali dari kedua sisi
        assertEquals(ROOM, PageNav.afterSwipe(LIBRARY, -far, swap = false, rtl = false))
        assertEquals(ROOM, PageNav.afterSwipe(CONSOLE, +far, swap = false, rtl = false))
    }

    @Test
    fun theEdgesDoNotWrapAround() {
        assertEquals(LIBRARY, PageNav.afterSwipe(LIBRARY, +far, swap = false, rtl = false))
        assertEquals(CONSOLE, PageNav.afterSwipe(CONSOLE, -far, swap = false, rtl = false))
    }

    @Test
    fun shortOrInvalidSwipesAreIgnored() {
        assertEquals(ROOM, PageNav.afterSwipe(ROOM, PageNav.SWIPE_PX - 1, swap = false, rtl = false))
        assertEquals(ROOM, PageNav.afterSwipe(ROOM, -(PageNav.SWIPE_PX - 1), swap = false, rtl = false))
        assertEquals(ROOM, PageNav.afterSwipe(ROOM, Float.NaN, swap = false, rtl = false))
        assertEquals(LIBRARY, PageNav.afterSwipe(ROOM, PageNav.SWIPE_PX, swap = false, rtl = false))
    }

    @Test
    fun swappingAndRtlMirrorTheLayout() {
        assertEquals(listOf(CONSOLE, ROOM, LIBRARY), PageNav.order(swap = true, rtl = false))
        assertEquals(listOf(CONSOLE, ROOM, LIBRARY), PageNav.order(swap = false, rtl = true))
        // Dua kali dicerminkan = urutan semula
        assertEquals(listOf(LIBRARY, ROOM, CONSOLE), PageNav.order(swap = true, rtl = true))
        // Pada RTL, geser ke kanan menampilkan halaman yang kini ada di kiri: konsol
        assertEquals(CONSOLE, PageNav.afterSwipe(ROOM, +far, swap = false, rtl = true))
        // Sisi ditukar: konsol kini di kiri
        assertEquals(CONSOLE, PageNav.afterSwipe(ROOM, +far, swap = true, rtl = false))
    }

    @Test
    fun offsetsPlaceEveryPageOneScreenApart() {
        for (swap in listOf(false, true)) for (rtl in listOf(false, true)) for (active in RoomPage.entries) {
            assertEquals(0, PageNav.offsetOf(active, active, swap, rtl))
            val order = PageNav.order(swap, rtl)
            for (p in RoomPage.entries) {
                assertEquals(order.indexOf(p) - order.indexOf(active), PageNav.offsetOf(p, active, swap, rtl))
            }
        }
        assertEquals(-1, PageNav.offsetOf(LIBRARY, ROOM, swap = false, rtl = false))
        assertEquals(1, PageNav.offsetOf(CONSOLE, ROOM, swap = false, rtl = false))
    }

    @Test
    fun parsingFallsBackToTheRoom() {
        assertEquals(LIBRARY, PageNav.parse("LIBRARY"))
        assertEquals(ROOM, PageNav.parse(null))
        assertEquals(ROOM, PageNav.parse("CRATE"))
        RoomPage.entries.forEach { assertEquals(it, PageNav.parse(it.name)) }
    }

    @Test
    fun everyPageHasASpokenName() {
        RoomPage.entries.forEach { assert(PageNav.label(it).isNotBlank()) }
    }
}
