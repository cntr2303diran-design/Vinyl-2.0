package com.vinyl.launcher.drawer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerLayoutTest {
    private data class A(val key: String, val label: String, val cat: AppCategory = AppCategory.OTHER)

    private val apps = listOf(
        A("alarm", "Alarm", AppCategory.TOOLS),
        A("asisten", "Asisten", AppCategory.TOOLS),
        A("bank", "Bank", AppCategory.SHOPPING),
        A("musik", "Musik", AppCategory.MUSIC),
        A("spotify", "Spotify", AppCategory.MUSIC),
        A("satu", "1Password", AppCategory.TOOLS),
    ).sortedBy { it.label.lowercase() }   // "1Password", Alarm, Asisten, Bank, Musik, Spotify

    private fun build(
        list: List<A> = apps,
        overrides: Map<String, String> = emptyMap(),
        selected: AppCategory? = null,
        frequent: List<String> = emptyList(),
        count: Int = 6,
    ) = DrawerLayout.build(list, { it.key }, { it.label }, { it.cat }, overrides, selected, frequent, count)

    private fun DrawerLayout.Result<A>.describe(): List<String> = rows.map {
        when (it) {
            is DrawerRow.Header -> "#${it.title}"
            is DrawerRow.App -> (if (it.frequent) "*" else "") + it.item.key
        }
    }

    @Test fun groupsByLetterWithHeaders() {
        assertEquals(
            listOf("#A", "alarm", "asisten", "#B", "bank", "#M", "musik", "#S", "spotify", "##", "satu"),
            build().describe(),
        )
    }

    @Test fun frequentRowComesFirstAndOnlyOnAll() {
        val r = build(frequent = listOf("spotify", "alarm"))
        assertEquals(listOf("#Sering dipakai", "*spotify", "*alarm", "#A"), r.describe().take(4))
        val filtered = build(selected = AppCategory.MUSIC, frequent = listOf("spotify"))
        assertTrue("*spotify" !in filtered.describe() && "#Sering dipakai" !in filtered.describe())
    }

    @Test fun frequentIgnoresUnknownDuplicateAndRespectsLimit() {
        val r = build(frequent = listOf("hilang", "alarm", "alarm", "bank", "musik", "spotify"), count = 2)
        assertEquals(listOf("#Sering dipakai", "*alarm", "*bank", "#A"), r.describe().take(4))
        assertTrue("#Sering dipakai" !in build(frequent = listOf("hilang")).describe())     // tidak ada yang valid → tanpa judul kosong
        assertTrue("#Sering dipakai" !in build(frequent = listOf("alarm"), count = 0).describe())
    }

    @Test fun frequentAndRegularRowIdsNeverCollide() {
        val ids = build(frequent = listOf("alarm", "bank")).rows.map { it.id }
        assertEquals("id baris harus unik (key LazyGrid)", ids.size, ids.toSet().size)
    }

    @Test fun overrideMovesAppAcrossCategories() {
        val r = build(overrides = mapOf("bank" to "MUSIC"), selected = AppCategory.MUSIC)
        assertEquals(listOf("bank", "musik", "spotify"), r.rows.filterIsInstance<DrawerRow.App<A>>().map { it.item.key })
        assertTrue(build(overrides = mapOf("bank" to "MUSIC"), selected = AppCategory.SHOPPING).rows.isEmpty())
    }

    @Test fun invalidOverrideFallsBackToAutomatic() {
        assertEquals(AppCategory.TOOLS, DrawerLayout.effectiveCategory(AppCategory.TOOLS, "x", mapOf("x" to "BUKAN")))
        assertEquals(AppCategory.GAME, DrawerLayout.effectiveCategory(AppCategory.TOOLS, "x", mapOf("x" to "GAME")))
        assertEquals(AppCategory.TOOLS, DrawerLayout.effectiveCategory(AppCategory.TOOLS, "x", emptyMap()))
    }

    @Test fun availableCategoriesCountAndOrder() {
        val cats = DrawerLayout.availableCategories(apps, { it.key }, { it.cat }, emptyMap())
        assertEquals(listOf(AppCategory.MUSIC to 2, AppCategory.SHOPPING to 1, AppCategory.TOOLS to 3), cats)
        assertTrue("satu kategori saja → chip tidak berguna", DrawerLayout.availableCategories(apps.filter { it.cat == AppCategory.TOOLS }, { it.key }, { it.cat }, emptyMap()).isEmpty())
        assertTrue(DrawerLayout.availableCategories(emptyList<A>(), { it.key }, { it.cat }, emptyMap()).isEmpty())
    }

    @Test fun emptyInputProducesNoRowsAndNoJumpTargets() {
        val r = build(list = emptyList(), frequent = listOf("alarm"))
        assertTrue(r.rows.isEmpty())
        assertNull(r.jumpIndex('A'))
        assertNull(r.letterAt(0))
    }

    @Test fun jumpIndexPointsExactlyAtLetterHeader() {
        for (frequent in listOf(emptyList(), listOf("spotify", "alarm", "bank"))) {
            val r = build(frequent = frequent)
            for (c in r.lettersPresent) {
                val i = r.jumpIndex(c)!!
                val row = r.rows[i]
                assertTrue("huruf $c harus mendarat di judulnya (frequent=${frequent.size})", row is DrawerRow.Header && row.letter == c)
            }
        }
    }

    @Test fun jumpToMissingLetterLandsOnNextSection() {
        val r = build(frequent = listOf("alarm"))
        val i = r.jumpIndex('C')!!                       // tidak ada C → bagian berikutnya (M)
        assertEquals("M", (r.rows[i] as DrawerRow.Header).title)
        val last = r.jumpIndex('Z')!!                    // lewat huruf terakhir → bagian terakhir
        assertEquals("#", (r.rows[last] as DrawerRow.Header).title)
    }

    @Test fun letterAtTracksScrollPositionIncludingFrequentRow() {
        val r = build(frequent = listOf("spotify", "alarm"))
        // 0 judul Sering, 1-2 ikon sering → huruf pertama (A)
        assertEquals('A', r.letterAt(0)); assertEquals('A', r.letterAt(2))
        for (c in r.lettersPresent) {
            val start = r.jumpIndex(c)!!
            assertEquals(c, r.letterAt(start))
            assertEquals(c, r.letterAt(start + 1))
        }
        assertEquals('#', r.letterAt(10_000))
    }

    @Test fun hundredsOfAppsKeepRowCountConsistent() {
        val many = (0 until 600).map { A("k$it", ('A' + it % 26).toString() + "pp$it") }
        val r = build(list = many, frequent = many.take(8).map { it.key }, count = 6)
        // 600 aplikasi + 26 judul huruf + 1 judul Sering + 6 ikon Sering
        assertEquals(600 + 26 + 1 + 6, r.rows.size)
        assertEquals(26, r.lettersPresent.size)
        assertEquals(r.rows.size, r.rows.map { it.id }.toSet().size)
    }

    @Test fun veryLongAndOddLabelsDoNotBreakGrouping() {
        val odd = listOf(A("1", "A".repeat(5000)), A("2", "日本語"), A("3", "عربي"), A("4", ""), A("5", "😀 emoji"))
        val r = build(list = odd)
        assertEquals(5, r.rows.count { it is DrawerRow.App })
        assertEquals(setOf('A', '#', 'E'), r.lettersPresent)   // "😀 emoji" memakai huruf pertamanya (E)
    }
}
