package com.vinyl.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class DiaryRulesTest {
    private fun e(at: Long, title: String = "Lagu", artist: String = "Artis") = DiaryEntry(at, title, artist)

    @Test
    fun blankTitlesAndNonPositiveTimesAreRejected() {
        assertEquals(emptyList<DiaryEntry>(), DiaryRules.append(emptyList(), e(1000, "   ")))
        assertEquals(emptyList<DiaryEntry>(), DiaryRules.append(emptyList(), e(0)))
        assertEquals(emptyList<DiaryEntry>(), DiaryRules.append(emptyList(), e(-5)))
    }

    @Test
    fun textIsStrippedTrimmedAndCapped() {
        val c = DiaryRules.clean(DiaryEntry(5, " Ha\u0007lo ${"x".repeat(300)}", "  A\nB  "))!!
        assertTrue(c.title.length <= DiaryRules.MAX_TEXT)
        assertTrue(c.title.startsWith("Halo"))
        assertEquals("AB", c.artist)
    }

    @Test
    fun theSameSongWithinAMinuteIsOneEntryButLaterCountsAgain() {
        val a = DiaryRules.append(emptyList(), e(100_000))
        val b = DiaryRules.append(a, e(100_000 + DiaryRules.DEDUPE_MS))
        assertEquals(1, b.size)
        val c = DiaryRules.append(b, e(100_000 + DiaryRules.DEDUPE_MS + 1))
        assertEquals(2, c.size)
        val d = DiaryRules.append(c, e(300_000, "Lagu lain"))
        assertEquals(3, d.size)
    }

    @Test
    fun theOldestEntriesDropOffBeyondTheCap() {
        var list = emptyList<DiaryEntry>()
        for (i in 1..(DiaryRules.MAX_ENTRIES + 25)) list = DiaryRules.append(list, e(i * 1_000_000L, "Lagu $i"))
        assertEquals(DiaryRules.MAX_ENTRIES, list.size)
        assertEquals("Lagu 26", list.first().title)
        assertEquals("Lagu ${DiaryRules.MAX_ENTRIES + 25}", list.last().title)
    }

    @Test
    fun encodeDecodeRoundTripsAndGarbageIsSafe() {
        val list = listOf(e(10, "A"), e(20, "B", ""))
        assertEquals(list, DiaryRules.decode(DiaryRules.encode(list)))
        assertTrue(DiaryRules.decode(null).isEmpty())
        assertTrue(DiaryRules.decode("").isEmpty())
        assertTrue(DiaryRules.decode("bukan json [[").isEmpty())
        val bad = """[{"at":0,"title":"x"},{"at":5,"title":""},{"at":9,"title":"ok"},{"at":7,"title":"lebih awal"}]"""
        assertEquals(listOf("lebih awal", "ok"), DiaryRules.decode(bad).map { it.title })
    }

    @Test
    fun oversizedStoredListsAreTrimmedOnLoad() {
        val many = (1..(DiaryRules.MAX_ENTRIES + 40)).map { e(it.toLong(), "L$it") }
        val raw = kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(DiaryEntry.serializer()), many)
        val back = DiaryRules.decode(raw)
        assertEquals(DiaryRules.MAX_ENTRIES, back.size)
        assertEquals("L${DiaryRules.MAX_ENTRIES + 40}", back.last().title)
    }

    @Test
    fun groupingSplitsByLocalDayNewestFirst() {
        val zone = ZoneId.of("Asia/Jakarta")
        fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = java.time.ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()
        val list = listOf(e(at(2026, 10, 29, 23, 50), "Malam"), e(at(2026, 10, 30, 0, 10), "Dini hari"), e(at(2026, 10, 30, 19, 0), "Petang"))
        val days = DiaryRules.groupByDay(list, zone)
        assertEquals(listOf(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 29)), days.map { it.date })
        assertEquals(listOf("Petang", "Dini hari"), days[0].entries.map { it.title })
        // 23:50 WIB = 16:50 UTC: harinya tergantung zona, bukan UTC
        val utcDays = DiaryRules.groupByDay(list, ZoneOffset.UTC)
        assertEquals(2, utcDays.size)
        assertTrue(DiaryRules.groupByDay(emptyList(), zone).isEmpty())
    }
}
