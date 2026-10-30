package com.vinyl.launcher.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Buku harian: jenis catatan (musik, film, game), saklar per jenis, dan keterbacaan catatan lama. */
class DiaryKindsTest {
    private fun entry(at: Long, title: String, detail: String = "", kind: DiaryKind = DiaryKind.MUSIC) = DiaryEntry(at, title, detail, kind)

    @Test
    fun entriesWithoutAKindAreMusic() {
        assertEquals(DiaryKind.MUSIC, DiaryEntry(1, "x").kind)
        val back = DiaryRules.decode("""[{"at":5,"title":"Lagu","artist":"A"}]""")
        assertEquals(listOf(DiaryKind.MUSIC), back.map { it.kind })
    }

    @Test
    fun anUnknownKindFallsBackToMusicInsteadOfDroppingTheWholeDiary() {
        val raw = """[{"at":5,"title":"Satu","kind":"HOLOGRAM"},{"at":6,"title":"Dua","kind":"GAME"}]"""
        val back = DiaryRules.decode(raw)
        assertEquals(listOf("Satu", "Dua"), back.map { it.title })
        assertEquals(listOf(DiaryKind.MUSIC, DiaryKind.GAME), back.map { it.kind })
    }

    @Test
    fun kindsSurviveEncodeAndDecode() {
        val list = listOf(entry(10, "Lagu", "A"), entry(20, "Kaset", kind = DiaryKind.FILM), entry(30, "Game Ular", "Skor 4", DiaryKind.GAME))
        assertEquals(list, DiaryRules.decode(DiaryRules.encode(list)))
    }

    @Test
    fun theSameTitleOfAnotherKindIsNotADuplicate() {
        val a = DiaryRules.append(emptyList(), entry(100_000, "Sama", kind = DiaryKind.MUSIC))
        val b = DiaryRules.append(a, entry(100_500, "Sama", kind = DiaryKind.FILM))
        assertEquals(2, b.size)
    }

    @Test
    fun filmsAreDedupedButGameRoundsNeverAre() {
        val film = DiaryRules.append(DiaryRules.append(emptyList(), entry(100_000, "Film", kind = DiaryKind.FILM)), entry(100_500, "Film", kind = DiaryKind.FILM))
        assertEquals(1, film.size)
        val game = DiaryRules.append(DiaryRules.append(emptyList(), entry(100_000, "Game Ular", "Skor 5", DiaryKind.GAME)), entry(100_500, "Game Ular", "Skor 5", DiaryKind.GAME))
        assertEquals(2, game.size)
    }

    @Test
    fun anExactDuplicateIsNeverStoredTwiceEvenForGamesAndEvenWhenNotAdjacent() {
        val g = entry(100_000, "Ular", "Skor 5", DiaryKind.GAME)
        val other = entry(200_000, "Bata", "Skor 9", DiaryKind.GAME)
        assertEquals(1, DiaryRules.append(DiaryRules.append(emptyList(), g), g).size)
        assertEquals(listOf(g, other), DiaryRules.append(DiaryRules.append(DiaryRules.append(emptyList(), g), other), g))
        // Waktu yang berbeda berarti ronde yang berbeda
        assertEquals(2, DiaryRules.append(DiaryRules.append(emptyList(), g), g.copy(at = g.at + 1)).size)
    }

    @Test
    fun decodingDropsExactDuplicatesSoListKeysStayUnique() {
        val raw = """[{"at":5,"title":"A","artist":"x"},{"at":5,"title":"A","artist":"x"},{"at":6,"title":"A","artist":"x"}]"""
        val back = DiaryRules.decode(raw)
        assertEquals(listOf(5L, 6L), back.map { it.at })
        val keys = back.map { "${it.at}|${it.kind}|${it.title}|${it.artist}" }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun theLabelsAreTheOnesShownInTheDiary() {
        assertEquals("Musik", DiaryRules.label(DiaryKind.MUSIC))
        assertEquals("Film", DiaryRules.label(DiaryKind.FILM))
        assertEquals("Game", DiaryRules.label(DiaryKind.GAME))
    }

    @Test
    fun aFilmEntryNeverCarriesAnAddressOnlyItsLabel() {
        // Film dicatat dengan nama kaset di label saja: tidak ada bidang untuk alamat situs atau halaman
        val json = Json { encodeDefaults = true }
        val fields = json.encodeToJsonElement(DiaryEntry.serializer(), DiaryEntry(1, "Kaset", kind = DiaryKind.FILM)).jsonObject.keys
        assertEquals(setOf("at", "title", "artist", "kind"), fields)
    }

    @Test
    fun flagsDefaultToOffWithEveryKindArmed() {
        val f = DiaryFlags()
        assertFalse(f.on)
        assertTrue(DiaryKind.entries.all { f.enabled(it) })
        assertTrue("saklar utama mati menolak semua jenis", DiaryKind.entries.none { f.accepts(it) })
    }

    @Test
    fun theMasterSwitchGatesTheKindSwitches() {
        val on = DiaryFlags(on = true).withKind(DiaryKind.FILM, false)
        assertTrue(on.accepts(DiaryKind.MUSIC))
        assertFalse(on.accepts(DiaryKind.FILM))
        assertTrue(on.accepts(DiaryKind.GAME))
        val off = on.copy(on = false)
        assertTrue(DiaryKind.entries.none { off.accepts(it) })
        // Pilihan per jenis tetap diingat walau saklar utama mati
        assertFalse(off.enabled(DiaryKind.FILM))
        assertTrue(off.enabled(DiaryKind.MUSIC))
    }

    @Test
    fun withKindChangesOnlyThatKind() {
        for (k in DiaryKind.entries) {
            val f = DiaryFlags(on = true).withKind(k, false)
            for (other in DiaryKind.entries) assertEquals("$k/$other", other != k, f.enabled(other))
        }
    }

    @Test
    fun storedListsCapAtTheLimitAcrossKinds() {
        val many = (1..(DiaryRules.MAX_ENTRIES + 30)).map { entry(it.toLong() * 1_000_000L, "T$it", kind = DiaryKind.entries[it % 3]) }
        val raw = Json.encodeToString(ListSerializer(DiaryEntry.serializer()), many)
        val back = DiaryRules.decode(raw)
        assertEquals(DiaryRules.MAX_ENTRIES, back.size)
        assertEquals("T${DiaryRules.MAX_ENTRIES + 30}", back.last().title)
    }
}
