package com.vinyl.launcher.ui.taskbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskbarPlannerTest {

    private fun input(
        avail: Float,
        fav: Int = 4,
        rec: Int = 4,
        np: Boolean = true,
        tray: Boolean = true,
        vertical: Boolean = false,
        fs: Float = 1f,
        collapsed: Boolean = false,
    ) = TaskbarInput(vertical, avail, fav, rec, np, tray, fs, collapsed)

    @Test
    fun wideScreen_showsEverythingWithoutScroll() {
        val p = TaskbarPlanner.plan(input(avail = 1800f))
        assertEquals(4, p.recents)
        assertEquals(ChipMode.FULL, p.chip)
        assertTrue(p.showDate)
        assertFalse(p.middleScrolls)
        assertTrue(p.fits)
        assertTrue(p.windowMainDp <= 1800f)
    }

    /** Skenario bug asli: semua 4 aplikasi terakhir terisi di layar ±800dp (tablet portrait). */
    @Test
    fun regression_fourRecentsOnNarrowTablet_keepsTrayAndCollapseVisible() {
        val p = TaskbarPlanner.plan(input(avail = 800f, fav = 4, rec = 4))
        assertTrue("bar harus muat di layar", p.windowMainDp <= 800f)
        assertTrue("jam/tray tidak boleh hilang", p.showTray)
        assertTrue(p.fits)
        // Prioritas buang: aplikasi terakhir dikorbankan lebih dulu, favorit tetap utuh
        assertEquals(4, p.favorites)
        assertTrue(p.recents < 4)
    }

    @Test
    fun legacyLayoutWouldOverflow_documentsTheOriginalBug() {
        // Hitung lebar total layout lama (46dp ikon, tanpa batas): jauh di atas 800dp.
        val legacy = 2 * 46f + 4 * 48f + 4 * 48f + 236f + 128f + 16f + 46f + 4 * 9f + 14 * 6f + 20f + 8f
        assertTrue("layout lama memang melebihi 800dp ($legacy)", legacy > 800f)
    }

    @Test
    fun dropOrder_recentsThenChipTextThenDate() {
        var lastRec = Int.MAX_VALUE
        var sawCompact = false
        var sawNoDate = false
        var avail = 1800f
        while (avail >= 360f) {
            val p = TaskbarPlanner.plan(input(avail = avail, fav = 3, rec = 4))
            assertTrue(p.recents <= lastRec)          // makin sempit, makin sedikit
            lastRec = p.recents
            if (p.chip == ChipMode.COMPACT) {
                sawCompact = true
                assertEquals("teks lagu baru dibuang setelah semua aplikasi terakhir", 0, p.recents)
            }
            if (!p.showDate) {
                sawNoDate = true
                assertTrue("tanggal dibuang hanya setelah teks lagu", p.chip != ChipMode.FULL)
            }
            avail -= 5f
        }
        assertTrue(sawCompact)
        assertTrue(sawNoDate)
    }

    @Test
    fun manyFavoritesOnSmallScreen_becomeScrollable() {
        val p = TaskbarPlanner.plan(input(avail = 420f, fav = 8, rec = 0))
        assertTrue(p.middleScrolls)
        assertTrue(p.showMiddle)
        assertTrue(p.middleMainDp >= TaskbarMetrics.MIN_VIEWPORT)
        assertTrue(p.windowMainDp <= 420f + 0.01f)
        assertTrue(p.showTray)
    }

    @Test
    fun invariant_windowAlwaysFitsWhenEssentialsFit() {
        for (vertical in listOf(false, true))
            for (fs in listOf(1f, 1.3f, 2f))
                for (tray in listOf(true, false))
                    for (np in listOf(true, false)) {
                        val min = TaskbarPlanner.essentialMainDp(vertical, tray, fs)
                        var avail = min
                        while (avail <= 2200f) {
                            for (fav in 0..8) for (rec in 0..4) {
                                val p = TaskbarPlanner.plan(input(avail, fav, rec, np, tray, vertical, fs))
                                val msg = "v=$vertical fs=$fs tray=$tray np=$np avail=$avail fav=$fav rec=$rec"
                                assertTrue("tidak muat: $msg (${p.windowMainDp})", p.windowMainDp <= avail + 0.01f)
                                assertTrue(msg, p.fits)
                                assertEquals("tray tidak boleh dibuang: $msg", tray, p.showTray)
                                assertTrue(msg, p.recents <= rec)
                                assertEquals(msg, fav, p.favorites)
                            }
                            avail += 13f
                        }
                    }
    }

    @Test
    fun moreRoom_neverRemovesElements() {
        for (fav in 0..8) for (rec in 0..4) {
            var prevRec = -1
            var prevChip = -1
            var prevDate = false
            var avail = 300f
            while (avail <= 2000f) {
                val p = TaskbarPlanner.plan(input(avail, fav, rec))
                assertTrue("recents turun saat ruang bertambah (fav=$fav rec=$rec avail=$avail)", p.recents >= prevRec)
                assertTrue(p.chip.ordinal >= prevChip)
                assertTrue(!prevDate || p.showDate)
                prevRec = p.recents; prevChip = p.chip.ordinal; prevDate = p.showDate
                avail += 3f
            }
        }
    }

    @Test
    fun collapsed_isSmallHandleWithTouchTarget() {
        val h = TaskbarPlanner.plan(input(avail = 1000f, collapsed = true))
        assertTrue(h.collapsed)
        assertEquals(128f, h.windowMainDp, 0.001f)
        assertEquals(48f, h.windowCrossDp, 0.001f)          // ≥ 48dp agar mudah disentuh
        val v = TaskbarPlanner.plan(input(avail = 1000f, vertical = true, collapsed = true))
        assertEquals(48f, v.windowWidthDp, 0.001f)
        assertEquals(128f, v.windowHeightDp, 0.001f)
    }

    @Test
    fun noFavoritesNorRecents_showsSettingsShortcut() {
        val p = TaskbarPlanner.plan(input(avail = 1200f, fav = 0, rec = 0))
        assertTrue(p.showSettingsShortcut)
        val q = TaskbarPlanner.plan(input(avail = 1200f, fav = 1, rec = 0))
        assertFalse(q.showSettingsShortcut)
    }

    @Test
    fun fontScale_isCappedAndGrowsTray() {
        val a = TaskbarPlanner.plan(input(avail = 2000f, fs = 1f))
        val b = TaskbarPlanner.plan(input(avail = 2000f, fs = 2f))
        assertEquals(1.5f, b.fontScale, 0.001f)
        assertTrue(b.trayMainDp > a.trayMainDp)
        assertTrue(b.barCrossDp > a.barCrossDp)            // bar menebal, teks tidak terpotong
    }

    @Test
    fun verticalDock_usesHeightAndCompactChip() {
        val p = TaskbarPlanner.plan(input(avail = 700f, vertical = true, fav = 4, rec = 4))
        assertTrue(p.vertical)
        assertEquals(ChipMode.COMPACT, p.chip)
        assertFalse(p.showDate)
        assertTrue(p.windowHeightDp <= 700f + 0.01f)
        assertTrue(p.showTray)
    }
}
