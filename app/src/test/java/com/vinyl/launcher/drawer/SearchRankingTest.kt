package com.vinyl.launcher.drawer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankingTest {

    @Test fun normalizeLowercasesStripsAccentsAndCollapsesSpaces() {
        assertEquals("cafe creme", SearchText.normalize("  Café   Crème "))
        assertEquals("", SearchText.normalize("   "))
    }

    @Test fun scoreLevelsAreOrderedAsDocumented() {
        assertEquals(SearchRanking.EXACT, SearchRanking.score("spotify", "Spotify"))
        assertEquals(SearchRanking.PREFIX, SearchRanking.score("spo", "Spotify"))
        assertEquals(SearchRanking.WORD_PREFIX, SearchRanking.score("lock", "Screen lock"))
        assertEquals(SearchRanking.INITIALS, SearchRanking.score("gm", "Google Maps"))
        assertEquals(SearchRanking.CONTAINS, SearchRanking.score("oogle", "Google Maps"))
        assertEquals(SearchRanking.SUBSEQUENCE, SearchRanking.score("gglmp", "Google Maps"))
        assertEquals(0, SearchRanking.score("xyz", "Google Maps"))
        assertTrue(
            SearchRanking.EXACT > SearchRanking.PREFIX && SearchRanking.PREFIX > SearchRanking.WORD_PREFIX &&
                SearchRanking.WORD_PREFIX > SearchRanking.INITIALS && SearchRanking.INITIALS > SearchRanking.CONTAINS &&
                SearchRanking.CONTAINS > SearchRanking.SUBSEQUENCE && SearchRanking.SUBSEQUENCE > 0,
        )
    }

    @Test fun caseAndAccentsDoNotMatter() {
        assertEquals(SearchRanking.EXACT, SearchRanking.score("CAFE", "Café"))
        assertEquals(SearchRanking.PREFIX, SearchRanking.score("caf", "CAFÉ BAR"))
    }

    @Test fun emptyQueryOrTextNeverMatches() {
        assertEquals(0, SearchRanking.score("", "Spotify"))
        assertEquals(0, SearchRanking.score("   ", "Spotify"))
        assertEquals(0, SearchRanking.score("sp", ""))
        assertTrue(SearchRanking.rank("", listOf("a", "b")) { it }.isEmpty())
    }

    @Test fun multiWordQueryNeedsEveryWordAndScoresByWeakest() {
        assertEquals(SearchRanking.WORD_PREFIX, SearchRanking.score("goo map", "Google Maps"))
        assertEquals(0, SearchRanking.score("goo xyz", "Google Maps"))
        assertEquals(SearchRanking.EXACT, SearchRanking.score("google maps", "Google Maps"))
    }

    @Test fun twoLetterQueryDoesNotUseSubsequence() {
        // "ge" bukan awalan, bukan awalan kata, bukan inisial, tidak terkandung; hanya urutan huruf yang cocok → ditolak (<3 huruf)
        assertEquals(0, SearchRanking.score("ge", "Google Maps"))
    }

    @Test fun subsequenceIsDisabledForLongText() {
        val scattered = "a" + "x".repeat(10) + "b" + "x".repeat(10) + "c"           // 23 karakter
        assertEquals(SearchRanking.SUBSEQUENCE, SearchRanking.score("abc", scattered))
        val tooLong = "a" + "x".repeat(20) + "b" + "x".repeat(20) + "c"              // 43 karakter
        assertEquals(0, SearchRanking.score("abc", tooLong))
    }

    @Test fun rankOrdersByScoreAndDropsMisses() {
        val apps = listOf("Archrome", "Chrome Beta", "Kalkulator", "Chrome")
        assertEquals(listOf("Chrome", "Chrome Beta", "Archrome"), SearchRanking.rank("chrome", apps) { it })
    }

    @Test fun rankIsStableForEqualScores() {
        val apps = listOf("Camera", "Calendar", "Calculator")
        assertEquals(apps, SearchRanking.rank("ca", apps) { it })
    }

    @Test fun rankHonorsLimit() {
        val apps = (1..20).map { "App $it" }
        assertEquals(5, SearchRanking.rank("app", apps, limit = 5) { it }.size)
        assertTrue(SearchRanking.rank("app", apps, limit = 0) { it }.isEmpty())
        assertTrue(SearchRanking.rank("app", apps, limit = -1) { it }.isEmpty())
    }

    @Test fun veryLongLabelDoesNotBlowUp() {
        val label = "A".repeat(5000)
        assertEquals(SearchRanking.PREFIX, SearchRanking.score("aaa", label))
        assertEquals(0, SearchRanking.score("zzz", label))
    }

    @Test fun systemSettingsFindByTitleAndKeyword() {
        assertEquals(SystemSetting.WIFI, searchSystemSettings("wifi").first())
        assertEquals(SystemSetting.BLUETOOTH, searchSystemSettings("bluetooth").first())
        assertEquals(SystemSetting.BATTERY, searchSystemSettings("baterai").first())
        assertEquals(SystemSetting.ACCESSIBILITY, searchSystemSettings("talkback").first())
        assertEquals(SystemSetting.AIRPLANE, searchSystemSettings("pesawat").first())
    }

    @Test fun systemSettingsHonorLimitAndReturnEmptyForNonsense() {
        assertTrue(searchSystemSettings("a", limit = 4).size <= 4)
        assertTrue(searchSystemSettings("zzzzqq").isEmpty())
        assertTrue(searchSystemSettings("").isEmpty())
    }

    @Test fun systemSettingTitlesAreUniqueAndNonBlank() {
        val titles = SystemSetting.entries.map { it.title }
        assertEquals(titles.size, titles.toSet().size)
        assertTrue(titles.all { it.isNotBlank() })
    }
}

class SearchIndexTest {
    private val labels = listOf(
        "Spotify", "Google Maps", "Chrome", "Chrome Beta", "Café", "Kalkulator", "Kamera", "Pengaturan", "WhatsApp",
        "Screen Lock", "", "   ", "Yandex Music", "YouTube Music", "Äpfel & Birnen", "日本語アプリ",
    )
    private val queries = listOf("sp", "chrome", "cafe", "ka", "gm", "lock", "music", "goo map", "xyz", "", "  ", "ge", "apfel", "日本", "wtsp")

    @Test fun `hasil indeks identik dengan jalur rank biasa untuk setiap kueri`() {
        val index = SearchIndex(labels) { it }
        for (q in queries) {
            assertEquals("kueri '$q'", SearchRanking.rank(q, labels) { it }, index.rank(q))
        }
    }

    @Test fun `skor yang dinormalisasi di muka sama dengan skor langsung`() {
        for (q in queries) for (t in labels) {
            val nq = SearchText.normalize(q)
            val expected = SearchRanking.score(q, t)
            val actual = if (nq.isEmpty()) 0 else SearchRanking.scoreNormalized(nq, nq.split(' '), SearchText.normalize(t))
            assertEquals("'$q' vs '$t'", expected, actual)
        }
    }

    @Test fun `indeks dapat dipakai berulang dan menghormati limit`() {
        val index = SearchIndex((1..50).map { "App $it" }) { it }
        assertEquals(3, index.rank("app", 3).size)
        assertEquals(50, index.rank("app").size)
        assertEquals(index.rank("app", 3), index.rank("app", 3))    // tanpa state tersembunyi
        assertTrue(index.rank("app", 0).isEmpty())
    }

    @Test fun `indeks kosong aman`() {
        assertTrue(SearchIndex(emptyList<String>()) { it }.rank("a").isEmpty())
    }
}
