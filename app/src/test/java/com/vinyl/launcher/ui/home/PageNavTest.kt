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

    // ---- Seretan langsung (halaman mengikuti jari) ----
    private val w = 1000f

    @Test
    fun aShortDragSnapsBackToTheStartPage() {
        assertEquals(1, PageNav.settleIndex(1, 1f + 100f / w, 0f, w))
        assertEquals(1, PageNav.settleIndex(1, 1f - 100f / w, 0f, w))
        assertEquals(1, PageNav.settleIndex(1, 1f, 0f, w))
    }

    @Test
    fun aDragPastTheOldSwipeThresholdMovesOnePage() {
        // (2 px selisih agar tidak bergantung pada pembulatan Float tepat di ambang)
        assertEquals(2, PageNav.settleIndex(1, 1f + (PageNav.SWIPE_PX + 2f) / w, 0f, w))
        assertEquals(0, PageNav.settleIndex(1, 1f - (PageNav.SWIPE_PX + 2f) / w, 0f, w))
        assertEquals(1, PageNav.settleIndex(1, 1f + (PageNav.SWIPE_PX - 2f) / w, 0f, w))
    }

    @Test
    fun aQuickShortFlickStillMovesThePage() {
        // 40 px saja, tetapi 2 halaman/detik → +200 px proyeksi
        assertEquals(2, PageNav.settleIndex(1, 1f + 40f / w, 2f, w))
        assertEquals(0, PageNav.settleIndex(1, 1f - 40f / w, -2f, w))
    }

    @Test
    fun aFlickAgainstTheDragCanCancelIt() {
        // Ditarik 200 px ke depan lalu dilempar kuat ke belakang
        assertEquals(1, PageNav.settleIndex(1, 1f + 200f / w, -2f, w))
    }

    @Test
    fun neverSkipsMoreThanOnePage() {
        assertEquals(2, PageNav.settleIndex(1, 1.9f, 9f, w))
        assertEquals(0, PageNav.settleIndex(1, 0.1f, -9f, w))
    }

    @Test
    fun theEndsDoNotOvershoot() {
        assertEquals(0, PageNav.settleIndex(0, -0.3f, -3f, w))
        assertEquals(2, PageNav.settleIndex(2, 2.4f, 3f, w))
        assertEquals(0, PageNav.settleIndex(0, 0f, 0f, w, count = 1))
    }

    @Test
    fun grabbingAPageMidFlightSettlesOnTheNearerSide() {
        // Sedang meluncur dari 0 ke 1, dipegang di 0,3 lalu dilepas diam → kembali ke 0
        assertEquals(0, PageNav.settleIndex(1, 0.3f, 0f, w))
        // Dipegang di 0,92 → lanjut ke 1
        assertEquals(1, PageNav.settleIndex(1, 0.92f, 0f, w))
    }

    @Test
    fun invalidInputKeepsTheStartPage() {
        assertEquals(1, PageNav.settleIndex(1, Float.NaN, 0f, w))
        assertEquals(1, PageNav.settleIndex(1, 1.5f, 0f, 0f))
        assertEquals(1, PageNav.settleIndex(1, 1.5f, 0f, Float.NaN))
        assertEquals(2, PageNav.settleIndex(1, 1.6f, Float.NaN, w))
        assertEquals(2, PageNav.settleIndex(7, 7f, 0f, w)) // awal di luar jangkauan dijepit
    }

    @Test
    fun pageAtMapsScreenIndexesAndFallsBackToTheRoom() {
        assertEquals(LIBRARY, PageNav.pageAt(0, swap = false, rtl = false))
        assertEquals(ROOM, PageNav.pageAt(1, swap = false, rtl = false))
        assertEquals(CONSOLE, PageNav.pageAt(2, swap = false, rtl = false))
        assertEquals(CONSOLE, PageNav.pageAt(0, swap = true, rtl = false))
        assertEquals(ROOM, PageNav.pageAt(9, swap = false, rtl = false))
        assertEquals(ROOM, PageNav.pageAt(-1, swap = false, rtl = false))
        for (swap in listOf(false, true)) for (rtl in listOf(false, true)) for (p in RoomPage.entries) {
            assertEquals(p, PageNav.pageAt(PageNav.indexOf(p, swap, rtl), swap, rtl))
        }
    }
}
