package com.vinyl.launcher.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmResumeTest {
    private val archive = FilmCassette("domain-publik", "Domain Publik", "https://archive.org/details/feature_films")
    private val nfb = FilmCassette("nfb", "NFB Kanada", "https://www.nfb.ca/")
    private val now = 1_000_000_000_000L

    private fun rec(
        data: FilmResumeData = FilmResumeData(),
        c: FilmCassette = archive,
        page: String? = "https://archive.org/details/night_of_the_living_dead",
        pos: Long? = 600_000L,
        dur: Long? = 5_400_000L,
        ended: Boolean = false,
        at: Long = now,
    ) = FilmResume.record(data, c, page, pos, dur, ended, at)

    // ----- situs yang sama -----

    @Test
    fun sameSiteAcceptsTheSiteItselfWwwAndSubdomainsOnly() {
        assertTrue(FilmResume.sameSite("https://archive.org/details/x", "https://archive.org/details/y"))
        assertTrue(FilmResume.sameSite("https://www.nfb.ca/", "https://nfb.ca/film/abc"))
        assertTrue(FilmResume.sameSite("https://nfb.ca/", "https://www.nfb.ca/film/abc"))
        assertTrue(FilmResume.sameSite("https://archive.org/", "https://ia800000.us.archive.org/x.mp4"))
    }

    @Test
    fun sameSiteRejectsLookalikesAndOtherSites() {
        assertFalse(FilmResume.sameSite("https://archive.org/", "https://evilarchive.org/"))
        assertFalse(FilmResume.sameSite("https://archive.org/", "https://archive.org.evil.com/"))
        assertFalse(FilmResume.sameSite("https://archive.org/", "https://example.com/"))
        assertFalse(FilmResume.sameSite("https://studio.blender.org/", "https://blender.org/"))
    }

    @Test
    fun sameSiteRejectsInvalidAddresses() {
        assertFalse(FilmResume.sameSite("https://archive.org/", "javascript:alert(1)"))
        assertFalse(FilmResume.sameSite("https://archive.org/", "about:blank"))
        assertFalse(FilmResume.sameSite("https://archive.org/", "https://192.168.0.1/"))
        assertFalse(FilmResume.sameSite("not a url", "https://archive.org/"))
    }

    @Test
    fun recordablePageUpgradesHttpAndRejectsOffSiteOrTooLong() {
        assertEquals("https://archive.org/details/x", FilmResume.recordablePage(archive.url, "http://archive.org/details/x"))
        assertNull(FilmResume.recordablePage(archive.url, null))
        assertNull(FilmResume.recordablePage(archive.url, "about:blank"))
        assertNull(FilmResume.recordablePage(archive.url, "https://example.com/x"))
        assertNull(FilmResume.recordablePage(archive.url, "https://archive.org/" + "a".repeat(FilmResume.MAX_URL)))
        assertNotNull(FilmResume.recordablePage(archive.url, "https://archive.org/" + "a".repeat(FilmResume.MAX_URL - 30)))
    }

    @Test
    fun samePageIgnoresQueryFragmentAndTrailingSlash() {
        assertTrue(FilmResume.samePage("https://archive.org/details/x", "https://archive.org/details/x?autoplay=1#t=10"))
        assertTrue(FilmResume.samePage("https://archive.org/details/x/", "https://archive.org/details/x"))
        assertFalse(FilmResume.samePage("https://archive.org/details/x", "https://archive.org/details/y"))
        assertFalse(FilmResume.samePage("https://archive.org/details/x", "https://example.com/details/x"))
        assertFalse(FilmResume.samePage("about:blank", "about:blank"))
    }

    // ----- mencatat -----

    @Test
    fun recordKeepsPageAndPosition() {
        val d = rec()
        val p = FilmResume.point(d, archive, now)
        assertNotNull(p)
        assertEquals("https://archive.org/details/night_of_the_living_dead", p!!.url)
        assertEquals(600_000L, p.positionMs)
        assertEquals(5_400_000L, p.durationMs)
        assertEquals(now, p.savedAtMs)
    }

    @Test
    fun recordIgnoresPagesThatMustNotBeRemembered() {
        val before = rec()
        assertEquals(before, rec(before, page = "https://example.com/x"))
        assertEquals(before, rec(before, page = "about:blank"))
        assertEquals(before, rec(before, page = null))
        assertEquals(FilmResumeData(), rec(FilmResumeData(), page = "javascript:alert(1)"))
    }

    @Test
    fun earlyPositionIsStoredAsZeroButThePageIsKept() {
        val p = FilmResume.point(rec(pos = 5_000L), archive, now)!!
        assertEquals(0L, p.positionMs)
        assertEquals("https://archive.org/details/night_of_the_living_dead", p.url)
        assertNull(FilmResume.seekTarget(p))
    }

    @Test
    fun finishedVideoIsForgottenSoTheCassetteStartsAtItsHomeAgain() {
        val saved = rec()
        assertNotNull(FilmResume.point(saved, archive, now))
        assertNull(FilmResume.point(rec(saved, pos = 5_390_000L), archive, now))
        assertNull(FilmResume.point(rec(saved, pos = 3_000_000L, ended = true), archive, now))
        // tepat di batas sisa 30 dtk: belum dianggap selesai
        assertEquals(5_370_000L - 1, FilmResume.point(rec(pos = 5_370_000L - 1), archive, now)!!.positionMs)
        // titik kaset lain tidak ikut terhapus
        val both = rec(saved, c = nfb, page = "https://www.nfb.ca/film/x", pos = 90_000L, dur = 600_000L)
        val after = rec(both, c = nfb, page = "https://www.nfb.ca/film/x", pos = 590_000L, dur = 600_000L)
        assertNull(after.points["nfb"])
        assertNotNull(after.points["domain-publik"])
    }

    @Test
    fun finishedVideoWithNothingSavedChangesNothing() {
        val empty = FilmResumeData()
        assertTrue(rec(empty, pos = 5_390_000L) === empty)
    }

    // ----- dari mana kaset dimulai -----

    @Test
    fun startWithoutAPointIsTheCassetteHome() {
        assertEquals(FilmStart(archive.url), FilmResume.start(FilmResumeData(), archive, now))
    }

    @Test
    fun startWithAPointIsTheSavedPageWithItsPositionAndDuration() {
        val s = FilmResume.start(rec(), archive, now)
        assertEquals("https://archive.org/details/night_of_the_living_dead", s.url)
        assertEquals(600_000L, s.seekMs)
        assertEquals(5_400_000L, s.durationMs)
    }

    @Test
    fun startKeepsThePageButNotTheSeekForAnEarlyPosition() {
        val s = FilmResume.start(rec(pos = 5_000L), archive, now)
        assertEquals("https://archive.org/details/night_of_the_living_dead", s.url)
        assertNull(s.seekMs)
    }

    @Test
    fun startIgnoresAPointOfAnotherSiteOrAnExpiredOne() {
        val saved = rec()
        val moved = archive.copy(url = "https://studio.blender.org/films/")
        assertEquals(FilmStart(moved.url), FilmResume.start(saved, moved, now))
        assertEquals(FilmStart(archive.url), FilmResume.start(saved, archive, now + FilmResume.MAX_AGE_MS + 1))
    }

    @Test
    fun startNeverReturnsAnAddressThatIsNotHttpsOnTheCassetteSite() {
        val tampered = FilmResumeData(mapOf("domain-publik" to FilmResumePoint("javascript:alert(1)", 600_000, 0, now)))
        assertEquals(FilmStart(archive.url), FilmResume.start(tampered, archive, now))
        val offSite = FilmResumeData(mapOf("domain-publik" to FilmResumePoint("https://evil.example/x", 600_000, 0, now)))
        assertEquals(FilmStart(archive.url), FilmResume.start(offSite, archive, now))
        val http = FilmResumeData(mapOf("domain-publik" to FilmResumePoint("http://archive.org/details/x", 600_000, 0, now)))
        assertEquals("https://archive.org/details/x", FilmResume.start(http, archive, now).url)
    }

    @Test
    fun unknownDurationStillKeepsPositionOnceItIsLongEnough() {
        val p = FilmResume.point(rec(pos = 120_000L, dur = 0L), archive, now)!!
        assertEquals(120_000L, p.positionMs)
        assertEquals(0L, p.durationMs)
        assertEquals(120_000L, FilmResume.seekTarget(p))
    }

    @Test
    fun noReadableVideoOnTheSamePageKeepsTheOldPosition() {
        val first = rec()
        val later = rec(first, pos = null, dur = null, at = now + 5_000)
        val p = FilmResume.point(later, archive, now + 5_000)!!
        assertEquals(600_000L, p.positionMs)
        assertEquals(5_400_000L, p.durationMs)
        // Tidak ada yang berubah: tidak ditulis ulang (cap waktu lama dipertahankan sampai selang penyegaran)
        assertEquals(now, p.savedAtMs)
        val refreshed = rec(first, pos = null, dur = null, at = now + FilmResume.REFRESH_MS + 1)
        assertEquals(now + FilmResume.REFRESH_MS + 1, FilmResume.point(refreshed, archive, now + FilmResume.REFRESH_MS + 1)!!.savedAtMs)
        assertEquals(600_000L, FilmResume.point(refreshed, archive, now + FilmResume.REFRESH_MS + 1)!!.positionMs)
    }

    @Test
    fun noReadableVideoOnAnotherPageResetsThePosition() {
        val first = rec()
        val other = rec(first, page = "https://archive.org/details/other_film", pos = null, dur = null)
        val p = FilmResume.point(other, archive, now)!!
        assertEquals("https://archive.org/details/other_film", p.url)
        assertEquals(0L, p.positionMs)
    }

    @Test
    fun outOfRangeNumbersAreClamped() {
        val p = FilmResume.point(rec(pos = Long.MAX_VALUE, dur = 0L), archive, now)!!
        assertEquals(FilmResume.MAX_MS, p.positionMs)
        assertEquals(FilmResume.MAX_MS, FilmResume.point(rec(pos = 600_000L, dur = Long.MAX_VALUE), archive, now)!!.durationMs)
        // durasi sama dengan posisi (di luar batas) berarti sudah selesai: titiknya dibuang
        assertNull(FilmResume.point(rec(pos = Long.MAX_VALUE, dur = Long.MAX_VALUE), archive, now))
        val neg = FilmResume.point(rec(pos = -5L, dur = -1L), archive, now)!!
        assertEquals(0L, neg.positionMs)
        assertEquals(0L, neg.durationMs)
    }

    @Test
    fun eachCassetteHasItsOwnPoint() {
        val d = rec(c = archive)
        val d2 = rec(d, c = nfb, page = "https://www.nfb.ca/film/x", pos = 90_000L, dur = 600_000L)
        assertEquals(600_000L, FilmResume.point(d2, archive, now)!!.positionMs)
        assertEquals(90_000L, FilmResume.point(d2, nfb, now)!!.positionMs)
    }

    @Test
    fun theNumberOfPointsIsCappedAndTheOldestGoFirst() {
        var d = FilmResumeData()
        val cassettes = (0 until FilmRules.MAX_CASSETTES + 5).map { FilmCassette("id$it", "k$it", "https://archive.org/") }
        cassettes.forEachIndexed { i, c ->
            d = rec(d, c = c, page = "https://archive.org/details/f$i", at = now + i)
        }
        assertEquals(FilmRules.MAX_CASSETTES, d.points.size)
        assertNull(d.points["id0"])
        assertNotNull(d.points["id${FilmRules.MAX_CASSETTES + 4}"])
    }

    // ----- membaca -----

    @Test
    fun pointIsNullAfterTheCassetteMovedToAnotherSite() {
        val d = rec()
        val moved = archive.copy(url = "https://studio.blender.org/films/")
        assertNull(FilmResume.point(d, moved, now))
        assertNotNull(FilmResume.point(d, archive.copy(url = "https://archive.org/"), now))
    }

    @Test
    fun pointExpiresAfterSixMonths() {
        val d = rec()
        assertNotNull(FilmResume.point(d, archive, now + FilmResume.MAX_AGE_MS))
        assertNull(FilmResume.point(d, archive, now + FilmResume.MAX_AGE_MS + 1))
    }

    @Test
    fun seekTargetNeedsAWorthwhilePositionAwayFromTheEnd() {
        fun p(pos: Long, dur: Long) = FilmResumePoint("https://archive.org/x", pos, dur, now)
        assertNull(FilmResume.seekTarget(null))
        assertNull(FilmResume.seekTarget(p(0, 0)))
        assertNull(FilmResume.seekTarget(p(14_999, 0)))
        assertEquals(15_000L, FilmResume.seekTarget(p(15_000, 0)))
        assertEquals(600_000L, FilmResume.seekTarget(p(600_000, 5_400_000)))
        assertNull(FilmResume.seekTarget(p(5_380_000, 5_400_000)))
        assertNull(FilmResume.seekTarget(p(Long.MAX_VALUE, 0)))
    }

    @Test
    fun canSeekNowNeedsAKnownDurationWithRoomLeft() {
        assertFalse(FilmResume.canSeekNow(0, 60_000))
        assertFalse(FilmResume.canSeekNow(-1, 60_000))
        assertTrue(FilmResume.canSeekNow(5_400_000, 600_000))
        assertFalse(FilmResume.canSeekNow(100_000, 90_000))
        assertTrue(FilmResume.canSeekNow(100_000, 70_000))
    }

    // ----- membuang -----

    @Test
    fun forgetRemovesOnlyThatCassette() {
        val d = rec(rec(), c = nfb, page = "https://www.nfb.ca/film/x")
        val f = FilmResume.forget(d, "nfb")
        assertNull(f.points["nfb"])
        assertNotNull(f.points["domain-publik"])
        assertEquals(f, FilmResume.forget(f, "tidak-ada"))
    }

    @Test
    fun sanitizeDropsUnknownExpiredAndOffSitePoints() {
        val good = FilmResumePoint("https://archive.org/details/x", 600_000, 5_400_000, now)
        val raw = FilmResumeData(
            mapOf(
                "domain-publik" to good,
                "hilang" to good,
                "nfb" to FilmResumePoint("https://example.com/x", 100_000, 0, now),
            ),
        )
        val clean = FilmResume.sanitize(raw, listOf(archive, nfb), now)
        assertEquals(setOf("domain-publik"), clean.points.keys)
        val old = FilmResumeData(mapOf("domain-publik" to good.copy(savedAtMs = now - FilmResume.MAX_AGE_MS - 1)))
        assertTrue(FilmResume.sanitize(old, listOf(archive), now).points.isEmpty())
    }

    @Test
    fun sanitizeClampsNumbersAndFutureTimestamps() {
        val raw = FilmResumeData(mapOf("domain-publik" to FilmResumePoint("https://archive.org/x", Long.MAX_VALUE, -7, Long.MAX_VALUE)))
        val p = FilmResume.sanitize(raw, listOf(archive), now).points.getValue("domain-publik")
        assertEquals(FilmResume.MAX_MS, p.positionMs)
        assertEquals(0L, p.durationMs)
        assertEquals(now, p.savedAtMs)
    }

    @Test
    fun sanitizeDropsPositionBeyondDuration() {
        val raw = FilmResumeData(mapOf("domain-publik" to FilmResumePoint("https://archive.org/x", 900_000, 600_000, now)))
        assertEquals(0L, FilmResume.sanitize(raw, listOf(archive), now).points.getValue("domain-publik").positionMs)
    }

    // ----- tampilan -----

    @Test
    fun labelsUseLatinDigitsWithMinutesOrHours() {
        assertEquals("0:00", FilmResume.label(0))
        assertEquals("0:59", FilmResume.label(59_999))
        assertEquals("12:34", FilmResume.label(754_000))
        assertEquals("1:02:03", FilmResume.label(3_723_000))
        assertEquals("0:00", FilmResume.label(-5))
    }

    @Test
    fun spokenFormIsReadableAloud() {
        assertEquals("0 detik", FilmResume.spoken(0))
        assertEquals("12 menit 34 detik", FilmResume.spoken(754_000))
        assertEquals("1 jam 2 menit 3 detik", FilmResume.spoken(3_723_000))
        assertEquals("5 menit", FilmResume.spoken(300_000))
    }

    @Test
    fun dataSurvivesJsonRoundTripAndToleratesUnknownKeys() {
        val d = rec(rec(), c = nfb, page = "https://www.nfb.ca/film/x")
        val json = Json { ignoreUnknownKeys = true }
        val text = json.encodeToString(FilmResumeData.serializer(), d)
        assertEquals(d, json.decodeFromString(FilmResumeData.serializer(), text))
        assertEquals(
            FilmResumeData(),
            json.decodeFromString(FilmResumeData.serializer(), """{"hal-baru":1}"""),
        )
    }
}

class FilmResumeWriteSavingTest {
    private val archive = FilmCassette("domain-publik", "Domain Publik", "https://archive.org/details/feature_films")
    private val now = 1_000_000_000_000L
    private val page = "https://archive.org/details/x"

    @Test
    fun unchangedStateIsNotRewrittenUntilTheRefreshInterval() {
        val first = FilmResume.record(FilmResumeData(), archive, page, 600_000, 5_400_000, false, now)
        val same = FilmResume.record(first, archive, page, 600_000, 5_400_000, false, now + 60_000)
        assertTrue("instance sama = tidak perlu menulis", same === first)
        val refreshed = FilmResume.record(first, archive, page, 600_000, 5_400_000, false, now + FilmResume.REFRESH_MS)
        assertEquals(now + FilmResume.REFRESH_MS, refreshed.points.getValue(archive.id).savedAtMs)
    }

    @Test
    fun aChangedPositionIsWrittenRightAway() {
        val first = FilmResume.record(FilmResumeData(), archive, page, 600_000, 5_400_000, false, now)
        val moved = FilmResume.record(first, archive, page, 615_000, 5_400_000, false, now + 15_000)
        assertEquals(615_000L, moved.points.getValue(archive.id).positionMs)
        val otherPage = FilmResume.record(moved, archive, "https://archive.org/details/y", 615_000, 5_400_000, false, now + 16_000)
        assertEquals("https://archive.org/details/y", otherPage.points.getValue(archive.id).url)
    }

    @Test
    fun aClockMovingBackwardsStillWritesInsteadOfFreezingOnAStaleStamp() {
        val first = FilmResume.record(FilmResumeData(), archive, page, 600_000, 5_400_000, false, now)
        val back = FilmResume.record(first, archive, page, 600_000, 5_400_000, false, now - 1)
        assertEquals(now - 1, back.points.getValue(archive.id).savedAtMs)
    }
}
