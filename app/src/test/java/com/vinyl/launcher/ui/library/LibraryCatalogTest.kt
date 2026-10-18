package com.vinyl.launcher.ui.library

import com.vinyl.launcher.data.PlayRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCatalogTest {
    private val day = 86_400_000L
    private val now = 100 * day

    private fun rec(title: String, artist: String, album: String = "", first: Long = 0, last: Long = 0) =
        PlayRecord(LibraryCatalog.songKey(title, artist), title, artist, album, firstPlayed = first, lastPlayed = last)

    private val history = listOf(
        rec("Zebra", "Beyoncé", "Renaissance", first = 3, last = now - 2 * day),
        rec("Alien", "Beyoncé", "Renaissance", first = 1, last = now - 1000),
        rec("Hello", "adele", "25", first = 2, last = now - 40 * day),
        rec("99 Problems", "2Pac", "", first = 4, last = now - 10 * day),
        rec("Yellow", "Coldplay", "Parachutes", first = 5, last = now - 3 * day),
    )
    private val entries = LibraryCatalog.entries(
        history, favorites = setOf(LibraryCatalog.songKey("Yellow", "Coldplay")),
        activeKey = LibraryCatalog.songKey("Alien", "Beyoncé"), deckKeys = setOf(LibraryCatalog.songKey("Hello", "adele")),
    )

    @Test
    fun catalogNumbersFollowFirstListenAndStayStable() {
        val byTitle = entries.associateBy { it.record.title }
        assertEquals(1, byTitle.getValue("Alien").number)
        assertEquals(5, byTitle.getValue("Yellow").number)
        // Mengubah urutan tampilan tidak mengubah nomor
        val recent = LibraryCatalog.arrange(entries, RackFilter.RECENT, "")
        assertEquals(1, recent.first { it.record.title == "Alien" }.number)
        assertEquals("No. 007", LibraryCatalog.catalogNumber(7))
    }

    @Test
    fun markersAreSet() {
        val byTitle = entries.associateBy { it.record.title }
        assertTrue(byTitle.getValue("Alien").active)
        assertTrue(byTitle.getValue("Hello").inDeck)
        assertTrue(byTitle.getValue("Yellow").favorite)
        assertFalse(byTitle.getValue("Zebra").favorite || byTitle.getValue("Zebra").active || byTitle.getValue("Zebra").inDeck)
    }

    @Test
    fun searchIgnoresCaseAndAccentsAndCoversAlbum() {
        assertEquals(setOf("Zebra", "Alien"), LibraryCatalog.arrange(entries, RackFilter.ALL, "BEYONCE").map { it.record.title }.toSet())
        assertEquals(listOf("Yellow"), LibraryCatalog.arrange(entries, RackFilter.ALL, "parachu").map { it.record.title })
        assertEquals(entries.size, LibraryCatalog.arrange(entries, RackFilter.ALL, "   ").size)
        assertTrue(LibraryCatalog.arrange(entries, RackFilter.ALL, "tidak ada").isEmpty())
    }

    @Test
    fun theWallRackIsAlphabeticalByArtistWithSymbolsFirst() {
        val all = LibraryCatalog.arrange(entries, RackFilter.ALL, "")
        assertEquals(listOf("2Pac", "adele", "Beyoncé", "Beyoncé", "Coldplay"), all.map { it.record.artist })
        assertEquals(listOf("Alien", "Zebra"), all.filter { it.record.artist == "Beyoncé" }.map { it.record.title })
        assertEquals("#", LibraryCatalog.sectionLetter("2Pac"))
        assertEquals("E", LibraryCatalog.sectionLetter("Édith Piaf"))
        assertEquals("#", LibraryCatalog.sectionLetter(""))
    }

    @Test
    fun filtersSortAndGroup() {
        assertEquals("Alien", LibraryCatalog.arrange(entries, RackFilter.RECENT, "").first().record.title)
        assertEquals(listOf("Yellow"), LibraryCatalog.arrange(entries, RackFilter.FAVORITE, "").map { it.record.title })
        // Album kosong ditaruh paling akhir
        assertEquals("99 Problems", LibraryCatalog.arrange(entries, RackFilter.ALBUM, "").last().record.title)
        val e = entries.first { it.record.title == "99 Problems" }
        assertEquals("Tanpa album", LibraryCatalog.section(e, RackFilter.ALBUM, now))
        assertEquals("Minggu ini", LibraryCatalog.recency(now - 3 * day, now))
        assertEquals("Hari ini", LibraryCatalog.recency(now - 5, now))
        assertEquals("Lebih lama", LibraryCatalog.recency(now - 40 * day, now))
    }

    @Test
    fun compartmentsNeverMixSectionsAndHoldAtMostFour() {
        val many = (1..11).map { rec("Lagu $it", "Artis A", first = it.toLong()) } + (1..2).map { rec("Lagu $it", "Band B", first = 100L + it) }
        val es = LibraryCatalog.entries(many, emptySet(), null, emptySet())
        val comps = LibraryCatalog.compartments(LibraryCatalog.arrange(es, RackFilter.ARTIST, ""), RackFilter.ARTIST, now)
        assertEquals(listOf(4, 4, 3, 2), comps.map { it.entries.size })
        assertEquals(listOf("Artis A", "Artis A", "Artis A", "Band B"), comps.map { it.section })
        comps.forEach { c -> assertTrue(c.entries.all { LibraryCatalog.section(it, RackFilter.ARTIST, now) == c.section }) }
        assertEquals(es.size, comps.sumOf { it.entries.size })
        assertTrue(LibraryCatalog.compartments(emptyList(), RackFilter.ALL, now).isEmpty())
    }

    @Test
    fun sameAlbumListsOtherSongsOfThatAlbumAndArtist() {
        val alien = entries.first { it.record.title == "Alien" }
        assertEquals(listOf("Zebra"), LibraryCatalog.sameAlbum(entries, alien).map { it.record.title })
        val noAlbum = entries.first { it.record.title == "99 Problems" }
        assertTrue(LibraryCatalog.sameAlbum(entries, noAlbum).isEmpty())
    }

    @Test
    fun favoritesToggleAndAreBounded() {
        assertEquals(setOf("a"), LibraryCatalog.toggleFavorite(emptySet(), "a"))
        assertEquals(emptySet<String>(), LibraryCatalog.toggleFavorite(setOf("a"), "a"))
        val full = (1..LibraryCatalog.MAX_FAVORITES).map { "k$it" }.toSet()
        val after = LibraryCatalog.toggleFavorite(full, "baru")
        assertEquals(LibraryCatalog.MAX_FAVORITES, after.size)
        assertTrue("baru" in after)
    }

    @Test
    fun songKeyMatchesHistoryKeys() {
        assertEquals("hello|adele", LibraryCatalog.songKey(" Hello ", "Adele "))
    }
}
