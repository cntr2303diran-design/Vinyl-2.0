package com.vinyl.launcher.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LrclibTest {

    private fun found(o: LyricsOutcome): Lyrics = (o as LyricsOutcome.Found).lyrics

    // ---- URL ----

    @Test fun getUrlEncodesEverythingAndOmitsEmptyParts() {
        val url = Lrclib.getUrl("Don't Stop & Go", "Ärtis/B", "", 0)
        assertEquals("https://lrclib.net/api/get?track_name=Don%27t+Stop+%26+Go&artist_name=%C3%84rtis%2FB", url)
    }

    @Test fun getUrlAddsAlbumAndWholeSecondsOfDuration() {
        val url = Lrclib.getUrl("T", "A", "Al Bum", 215_999)
        assertTrue(url.endsWith("&album_name=Al+Bum&duration=215"))
    }

    @Test fun urlsCannotBeInjectedThroughMetadata() {
        val url = Lrclib.getUrl("x&duration=1#frag", "a?b=c\nd", "", 0)
        assertFalse("fragmen '#' dari metadata tidak boleh lolos", url.contains('#'))
        assertFalse(url.contains('\n'))
        assertEquals("hanya satu '?' (awal query)", 1, url.count { it == '?' })
        assertEquals("hanya satu '&' (pemisah track_name/artist_name); '&duration=1' dari judul harus ter-encode", 1, url.count { it == '&' })
        assertFalse(url.contains("duration="))
    }

    @Test fun searchUrlHasOnlyTitleAndArtist() {
        assertEquals("https://lrclib.net/api/search?track_name=Judul&artist_name=Artis", Lrclib.searchUrl("Judul", "Artis"))
    }

    @Test fun onlyHttpsHostIsEverBuilt() {
        assertTrue(Lrclib.getUrl("a", "b", "c", 1).startsWith("https://lrclib.net/"))
        assertTrue(Lrclib.searchUrl("a", "b").startsWith("https://lrclib.net/"))
    }

    // ---- status HTTP ----

    @Test fun notFoundStatusesMeanNoLyricsButEverythingElseMeansUnknown() {
        for (code in listOf(400, 404, 410)) assertSame("$code", LyricsOutcome.NotFound, Lrclib.outcomeForStatus(code))
        for (code in listOf(401, 403, 408, 429, 500, 502, 503, 504, 302, 0, -1)) {
            assertSame("$code tidak boleh dianggap 'tidak ada lirik'", LyricsOutcome.Failed, Lrclib.outcomeForStatus(code))
        }
    }

    // ---- parseExact ----

    @Test fun exactWithSyncedAndPlain() {
        val l = found(Lrclib.parseExact("""{"id":1,"duration":200,"plainLyrics":"a\nb","syncedLyrics":"[00:01.00]a\n[00:02.00]b"}"""))
        assertEquals(2, l.synced.size)
        assertEquals("a\nb", l.plain)
        assertFalse(l.instrumental)
    }

    @Test fun exactWithOnlyPlainLyrics() {
        val l = found(Lrclib.parseExact("""{"plainLyrics":"hanya teks","syncedLyrics":null}"""))
        assertFalse(l.hasSynced)
        assertEquals("hanya teks", l.plain)
    }

    @Test fun instrumentalIsFoundNotMissing() {
        val l = found(Lrclib.parseExact("""{"instrumental":true,"plainLyrics":null,"syncedLyrics":null}"""))
        assertTrue(l.instrumental)
        assertTrue(l.synced.isEmpty())
        assertNull(l.plain)
    }

    @Test fun exactWithNoLyricsAtAllIsNotFound() {
        assertSame(LyricsOutcome.NotFound, Lrclib.parseExact("""{"plainLyrics":null,"syncedLyrics":null}"""))
        assertSame(LyricsOutcome.NotFound, Lrclib.parseExact("""{"plainLyrics":"   ","syncedLyrics":""}"""))
        assertSame(LyricsOutcome.NotFound, Lrclib.parseExact("{}"))
    }

    @Test fun garbageIsAFailureNotAVerdict() {
        for (body in listOf("", "<html>502 Bad Gateway</html>", "[1,2]", "null", "\"teks\"", "{", "[[[")) {
            assertSame("body=$body", LyricsOutcome.Failed, Lrclib.parseExact(body))
        }
    }

    @Test fun wrongFieldTypesNeverThrow() {
        val bodies = listOf(
            """{"plainLyrics":{"x":1},"syncedLyrics":[1,2]}""",
            """{"plainLyrics":123,"syncedLyrics":true}""",
            """{"instrumental":"yes","plainLyrics":"ok"}""",
            """{"duration":"abc","plainLyrics":"ok"}""",
        )
        for (b in bodies) Lrclib.parseExact(b)            // tidak boleh melempar
        assertEquals("ok", found(Lrclib.parseExact(bodies[2])).plain)   // "yes" bukan boolean sah → bukan instrumental
        assertEquals("ok", found(Lrclib.parseExact(bodies[3])).plain)
        assertSame(LyricsOutcome.NotFound, Lrclib.parseExact(bodies[0]))
    }

    @Test fun plainLyricsAreCapped() {
        val big = "x".repeat(Lrclib.MAX_PLAIN_CHARS * 3)
        assertEquals(Lrclib.MAX_PLAIN_CHARS, found(Lrclib.parseExact("""{"plainLyrics":"$big"}""")).plain!!.length)
    }

    // ---- parseSearch ----

    private fun cand(duration: Int?, synced: String?, plain: String?, instrumental: Boolean = false): String {
        fun q(s: String?) = if (s == null) "null" else "\"" + s.replace("\n", "\\n") + "\""
        return """{"duration":${duration ?: "null"},"syncedLyrics":${q(synced)},"plainLyrics":${q(plain)},"instrumental":$instrumental}"""
    }

    private fun arr(vararg c: String) = c.joinToString(",", "[", "]")

    @Test fun searchPrefersSyncedOverPlain() {
        val body = arr(cand(200, null, "polos"), cand(200, "[00:01.00]sinkron", "sinkron"))
        assertTrue(found(Lrclib.parseSearch(body, 200_000)).hasSynced)
    }

    @Test fun searchPrefersTheClosestDurationAmongSyncedCandidates() {
        val body = arr(
            cand(205, "[00:01.00]jauh", null),
            cand(200, "[00:01.00]dekat", null),
            cand(203, "[00:01.00]sedang", null),
        )
        assertEquals("dekat", found(Lrclib.parseSearch(body, 200_000)).synced.single().text)
    }

    @Test fun syncedLyricsOfAnotherVersionAreDroppedButPlainIsKept() {
        // Versi live 260 dtk vs lagu 200 dtk: waktu pasti melenceng, tapi teksnya masih berguna
        val l = found(Lrclib.parseSearch(arr(cand(260, "[00:01.00]x", "teks polos")), 200_000))
        assertFalse(l.hasSynced)
        assertEquals("teks polos", l.plain)
    }

    @Test fun syncedLyricsWithoutPlainFromAnotherVersionAreRejectedEntirely() {
        assertSame(LyricsOutcome.NotFound, Lrclib.parseSearch(arr(cand(260, "[00:01.00]x", null)), 200_000))
    }

    @Test fun durationToleranceBoundaryIsInclusive() {
        val atEdge = cand(208, "[00:01.00]x", null)                  // tepat 8 dtk
        val past = cand(209, "[00:01.00]x", null)
        assertTrue(found(Lrclib.parseSearch(arr(atEdge), 200_000)).hasSynced)
        assertSame(LyricsOutcome.NotFound, Lrclib.parseSearch(arr(past), 200_000))
    }

    @Test fun unknownTrackDurationDoesNotRejectAnything() {
        assertTrue(found(Lrclib.parseSearch(arr(cand(999, "[00:01.00]x", null)), 0)).hasSynced)
    }

    @Test fun candidateWithoutDurationIsAcceptedWhenTrackDurationKnown() {
        assertTrue(found(Lrclib.parseSearch(arr(cand(null, "[00:01.00]x", null)), 200_000)).hasSynced)
    }

    @Test fun instrumentalLosesToRealLyrics() {
        val body = arr(cand(200, null, null, instrumental = true), cand(200, null, "ada kata"))
        assertEquals("ada kata", found(Lrclib.parseSearch(body, 200_000)).plain)
    }

    @Test fun emptyOrUselessSearchIsNotFound() {
        assertSame(LyricsOutcome.NotFound, Lrclib.parseSearch("[]", 1))
        assertSame(LyricsOutcome.NotFound, Lrclib.parseSearch(arr(cand(1, null, null), "{}", "5", "null"), 1))
    }

    @Test fun searchGarbageIsAFailure() {
        for (b in listOf("", "{}", "null", "<html>", "[", "\"x\"")) assertSame("body=$b", LyricsOutcome.Failed, Lrclib.parseSearch(b, 1))
    }

    @Test fun searchOnlyLooksAtTheFirstCandidates() {
        val junk = List(Lrclib.MAX_CANDIDATES) { cand(1, null, null) }
        val body = arr(*junk.toTypedArray(), cand(200, "[00:01.00]terlambat", null))
        assertSame("kandidat ke-${Lrclib.MAX_CANDIDATES + 1} tidak boleh diproses", LyricsOutcome.NotFound, Lrclib.parseSearch(body, 200_000))
    }

    // ---- combine ----

    private val hit = LyricsOutcome.Found(Lyrics(emptyList(), "x"))

    @Test fun combineStopsAtAnExactHit() {
        var called = false
        assertSame(hit, Lrclib.combine(hit) { called = true; LyricsOutcome.NotFound })
        assertFalse(called)
    }

    @Test fun combineDoesNotRetryOnNetworkFailure() {
        var called = false
        assertSame(LyricsOutcome.Failed, Lrclib.combine(LyricsOutcome.Failed) { called = true; hit })
        assertFalse("jaringan putus: jangan menggandakan waktu tunggu", called)
    }

    @Test fun combineFallsBackToSearchOnNotFoundAndSearchDecides() {
        assertSame(hit, Lrclib.combine(LyricsOutcome.NotFound) { hit })
        assertSame(LyricsOutcome.NotFound, Lrclib.combine(LyricsOutcome.NotFound) { LyricsOutcome.NotFound })
        assertSame(LyricsOutcome.Failed, Lrclib.combine(LyricsOutcome.NotFound) { LyricsOutcome.Failed })
    }
}
