package com.vinyl.launcher.data

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmRulesTest {
    private fun ok(input: String) = FilmRules.normalizeUrl(input)
    private fun problem(input: String) = (FilmRules.parseUrl(input) as? UrlResult.Bad)?.problem

    @Test
    fun plainHostsGetHttps() {
        assertEquals("https://archive.org/details/x", ok("archive.org/details/x"))
        assertEquals("https://www.example.com", ok("www.example.com"))
        assertEquals("https://example.com:8080/x", ok("example.com:8080/x"))
        assertEquals("https://example.com/a?b=1#c", ok("  https://example.com/a?b=1#c  "))
    }

    @Test
    fun httpIsUpgradedAndSchemeAndHostAreLowercasedButThePathIsKept() {
        assertEquals("https://example.com/a", ok("http://example.com/a"))
        assertEquals("https://archive.org/Path?Q=1#H", ok("HTTPS://Archive.ORG/Path?Q=1#H"))
        assertEquals("https://example.com/a", ok("https://example.com:443/a"))
        assertEquals("https://example.com:8443/a", ok("https://example.com:8443/a"))
        assertEquals("https://example.com/", ok("https://example.com./"))
    }

    @Test
    fun internationalNamesBecomePunycode() {
        assertEquals("https://xn--mnchen-3ya.de/", ok("https://münchen.de/"))
    }

    @Test
    fun otherSchemesAreRejected() {
        for (bad in listOf(
            "javascript:alert(1)", "JavaScript:alert(1)", "data:text/html,hi", "file:///sdcard/x", "content://x/y",
            "intent://scan/#Intent;scheme=zxing;end", "ftp://example.com", "mailto:a@b.com", "market://details?id=a",
            "about:blank", "view-source:https://example.com", "blob:https://example.com/1",
        )) {
            assertEquals(bad, UrlProblem.SCHEME, problem(bad))
        }
        // "tel:12345" terbaca sebagai "host:port" (tanpa skema) — tetap ditolak, hanya alasannya berbeda
        assertNull(ok("tel:12345"))
    }

    @Test
    fun credentialsAndTricksAreRejected() {
        assertEquals(UrlProblem.CREDENTIALS, problem("https://user:pw@example.com"))
        assertEquals(UrlProblem.CREDENTIALS, problem("https://good.com@evil.com/"))
        assertEquals(UrlProblem.HOST, problem("https://example.com\\evil"))
        assertEquals(UrlProblem.SPACES, problem("https://exa mple.com"))
        assertEquals(UrlProblem.SPACES, problem("https://example.com/a\nb"))
        assertEquals(UrlProblem.HOST, problem("https://[::1]/"))
        assertEquals(UrlProblem.HOST, problem("https://"))
        assertEquals(UrlProblem.HOST, problem("https:///path"))
        // Komentar/query tidak boleh dianggap bagian nama situs
        assertEquals("https://good.com#@evil.com", ok("https://good.com#@evil.com"))
        assertEquals("https://good.com?@evil.com", ok("https://good.com?@evil.com"))
    }

    @Test
    fun privateAndMalformedHostsAreRejected() {
        for (bad in listOf(
            "https://localhost/x", "https://foo", "https://192.168.1.1/x", "https://10.0.0.1:8080", "https://foo.local/",
            "https://nas.lan/", "https://printer.internal", "https://-bad.example.com", "https://bad-.example.com",
            "https://a..b.com", "https://example.c/", "https://exa_mple.com", "https://example.com:0", "https://example.com:99999",
            "https://example.com:abc", "https://1.2.3.4.5", "https://example.com:80:80",
        )) {
            assertEquals(bad, UrlProblem.HOST, problem(bad))
        }
    }

    @Test
    fun emptyAndOversizedInputsAreRejected() {
        assertEquals(UrlProblem.EMPTY, problem(""))
        assertEquals(UrlProblem.EMPTY, problem("   "))
        assertEquals(UrlProblem.TOO_LONG, problem("https://example.com/" + "a".repeat(FilmRules.MAX_URL)))
        assertNotNull(ok("https://example.com/" + "a".repeat(FilmRules.MAX_URL - 21)))
    }

    @Test
    fun everyProblemHasAMessage() {
        UrlProblem.entries.forEach { assertTrue(it.message.isNotBlank()) }
    }

    @Test
    fun hostAndSuggestedNameAreFriendly() {
        assertEquals("archive.org", FilmRules.hostOf("https://www.archive.org/details/x"))
        assertEquals("example.com", FilmRules.hostOf("https://example.com:8443/a"))
        assertEquals("Archive", FilmRules.suggestName("https://archive.org/x"))
        assertEquals("Blender", FilmRules.suggestName("https://studio.blender.org/films/"))
        assertEquals("Bbc", FilmRules.suggestName("https://www.bbc.co.uk/iplayer"))
        assertEquals("Nfb", FilmRules.suggestName("https://nfb.ca"))
        assertTrue(FilmRules.suggestName("https://" + "a".repeat(60) + ".com").length <= FilmRules.MAX_NAME)
    }

    @Test
    fun defaultCassettesAreValidAndLegalSourcesOnly() {
        val defaults = FilmRules.defaults()
        assertEquals(3, defaults.size)
        defaults.forEach {
            assertEquals(it.url, FilmRules.normalizeUrl(it.url))
            assertTrue(it.name.isNotBlank() && it.name.length <= FilmRules.MAX_NAME)
            assertTrue(it.color in FilmRules.COLORS)
        }
        assertEquals(defaults.size, defaults.map { it.id }.toSet().size)
        assertEquals(setOf("archive.org", "studio.blender.org", "nfb.ca"), defaults.map { FilmRules.hostOf(it.url) }.toSet())
        // Pengaturan baru langsung membawa kaset bawaan
        assertEquals(defaults, FilmSettings().cassettes)
    }

    @Test
    fun everyShellColorHasAName() {
        assertEquals(FilmRules.COLORS.size, FilmRules.COLOR_NAMES.size)
        assertEquals(FilmRules.COLORS.size, FilmRules.COLORS.toSet().size)
        assertTrue(FilmRules.COLOR_NAMES.all { it.isNotBlank() })
        assertEquals(FilmRules.COLOR_NAMES.size, FilmRules.COLOR_NAMES.toSet().size)
    }

    @Test
    fun addingKeepsTheListValidAndBounded() {
        val rnd = Random(1)
        var f = FilmSettings(cassettes = emptyList())
        f = FilmRules.add(f, "  Film   Saya  ", "kanopy.com", 0x00112233, rnd)!!
        val c = f.cassettes.single()
        assertEquals("Film Saya", c.name)
        assertEquals("https://kanopy.com", c.url)
        assertEquals(0xFF112233.toInt(), c.color)
        // Nama kosong → disarankan dari alamat
        f = FilmRules.add(f, "", "https://studio.blender.org/films/", FilmRules.COLORS[1], rnd)!!
        assertEquals("Blender", f.cassettes.last().name)
        // Alamat tidak sah tidak mengubah apa pun
        assertNull(FilmRules.add(f, "x", "javascript:alert(1)", 0, rnd))
        // Penuh
        var full = FilmSettings(cassettes = emptyList())
        repeat(FilmRules.MAX_CASSETTES) { full = FilmRules.add(full, "k$it", "example.com/$it", 0, rnd)!! }
        assertNull(FilmRules.add(full, "lagi", "example.com", 0, rnd))
        assertEquals(FilmRules.MAX_CASSETTES, full.cassettes.map { it.id }.toSet().size)
    }

    @Test
    fun updatingAndRemoving() {
        val f = FilmSettings()
        val id = f.cassettes.first().id
        val u = FilmRules.update(f, id, "Baru", "http://example.org/film", FilmRules.COLORS[5])!!
        assertEquals("Baru", u.cassettes.first().name)
        assertEquals("https://example.org/film", u.cassettes.first().url)
        assertEquals(f.cassettes.size, u.cassettes.size)
        assertNull(FilmRules.update(f, "tidak-ada", "x", "example.com", 0))
        assertNull(FilmRules.update(f, id, "x", "file:///x", 0))
        assertEquals(f.cassettes.size - 1, FilmRules.remove(f, id).cassettes.size)
        assertEquals(f, FilmRules.remove(f, "tidak-ada"))
    }

    @Test
    fun sanitizeDropsBadEntriesAndRepairsTheRest() {
        val messy = FilmSettings(
            cassettes = listOf(
                FilmCassette("a", "  Satu\u0000  ", "example.com/satu", 0x00ABCDEF),
                FilmCassette("a", "Duplikat id", "example.com/dua", 1),            // id kembar → id baru
                FilmCassette("bukan id sah!", "Id aneh", "example.com/tiga", 1),   // id tak sah → id baru
                FilmCassette("x", "Jahat", "javascript:alert(1)", 1),              // dibuang
                FilmCassette("y", "", "https://studio.blender.org/films/", 1),     // nama kosong → saran
                FilmCassette("z", "n".repeat(100), "example.com/panjang", 1),
            ),
            snakeBest = 10_000_000,
        )
        val s = FilmRules.sanitize(messy)
        assertEquals(5, s.cassettes.size)
        assertEquals("Satu", s.cassettes[0].name)
        assertEquals(0xFFABCDEF.toInt(), s.cassettes[0].color)
        assertEquals(s.cassettes.size, s.cassettes.map { it.id }.toSet().size)
        assertTrue(s.cassettes.all { Regex("[A-Za-z0-9_-]{1,40}").matches(it.id) })
        assertTrue(s.cassettes.none { it.url.contains("javascript") })
        assertEquals("Blender", s.cassettes.first { it.url.contains("blender") }.name)
        assertTrue(s.cassettes.all { it.name.length <= FilmRules.MAX_NAME })
        assertEquals(FilmRules.MAX_SNAKE, s.snakeBest)
        // Idempoten: membersihkan hasil bersih tidak mengubah apa pun
        assertEquals(s, FilmRules.sanitize(s))
    }

    @Test
    fun sanitizeCapsTheCountAndNegativeScores() {
        val many = FilmSettings(
            cassettes = (0 until 60).map { FilmCassette("id$it", "k$it", "example.com/$it") },
            snakeBest = -5,
        )
        val s = FilmRules.sanitize(many)
        assertEquals(FilmRules.MAX_CASSETTES, s.cassettes.size)
        assertEquals(0, s.snakeBest)
    }

    @Test
    fun theBestSnakeScoreOnlyGoesUp() {
        val f = FilmSettings(snakeBest = 30)
        assertEquals(30, FilmRules.withSnakeScore(f, 12).snakeBest)
        assertEquals(41, FilmRules.withSnakeScore(f, 41).snakeBest)
        assertEquals(FilmRules.MAX_SNAKE, FilmRules.withSnakeScore(f, 10_000_000).snakeBest)
    }

    @Test
    fun idsAreUniqueAndWellFormed() {
        val rnd = Random(7)
        val used = mutableSetOf<String>()
        repeat(500) {
            val id = FilmRules.newId(used, rnd)
            assertTrue(id.matches(Regex("f[0-9a-f]{8}")))
            assertTrue(used.add(id))
        }
        assertFalse(used.isEmpty())
    }

    @Test
    fun navigationAllowsHttpsToPublicSitesEvenWithLongQueries() {
        assertEquals(NavDecision.Allow, FilmRules.checkNavigation("https://archive.org/details/film"))
        assertEquals(NavDecision.Allow, FilmRules.checkNavigation("HTTPS://Archive.org/x"))
        assertEquals(NavDecision.Allow, FilmRules.checkNavigation("https://cdn.example.com/p?" + "a=b&".repeat(1_000)))
    }

    @Test
    fun navigationUpgradesHttpToHttps() {
        assertEquals(NavDecision.Redirect("https://archive.org/x?y=1"), FilmRules.checkNavigation("http://Archive.org/x?y=1"))
    }

    @Test
    fun navigationBlocksEverythingElse() {
        val blocked = listOf(
            "intent://scan/#Intent;scheme=zxing;end", "market://details?id=a.b", "tel:+62123", "mailto:a@b.c", "file:///sdcard/a.html",
            "content://com.android.contacts/x", "javascript:alert(1)", "data:text/html,<b>x</b>", "ftp://archive.org/", "about:blank",
            "https://192.168.1.1/admin", "https://127.0.0.1/", "https://localhost/", "https://printer.local/", "https://user:pw@archive.org/",
            "https://[::1]/", "https://archive.org/a b", "", "   ", "https://",
            "https://cdn.example.com/p?" + "a".repeat(9_000),
        )
        for (u in blocked) assertEquals("harus diblokir: $u", NavDecision.Block, FilmRules.checkNavigation(u))
    }
}
