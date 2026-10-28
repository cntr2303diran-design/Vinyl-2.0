package com.vinyl.launcher.ui.film

import com.vinyl.launcher.data.FilmStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmResumeSeekTest {
    private val page = "https://archive.org/details/night_of_the_living_dead"
    private val now = 10_000L
    private val start = FilmStart(page, seekMs = 600_000L, durationMs = 5_400_000L)
    private fun seek(s: FilmStart = start) = ResumeSeekRules.plan(s, now)!!
    private fun video(dur: Long = 5_400_000L, pos: Long = 0L, playing: Boolean = false, ended: Boolean = false) = VideoStatus(playing, pos, dur, ended)

    @Test
    fun noPositionMeansNoPlan() {
        assertNull(ResumeSeekRules.plan(FilmStart(page), now))
        assertNull(ResumeSeekRules.plan(FilmStart(page, null, 5_400_000L), now))
    }

    @Test
    fun planKeepsTheTargetAndGivesTheVideoAWaitingTime() {
        val s = seek()
        assertEquals(page, s.page)
        assertEquals(600_000L, s.targetMs)
        assertEquals(5_400_000L, s.expectedDurationMs)
        assertEquals(now + ResumeSeekRules.WAIT_MS, s.deadlineMs)
    }

    @Test
    fun waitsUntilThePageIsShownAndTheVideoIsReadable() {
        assertEquals(SeekStep.Wait, ResumeSeekRules.step(seek(), null, video(), now))
        assertEquals(SeekStep.Wait, ResumeSeekRules.step(seek(), page, null, now))
    }

    @Test
    fun jumpsOnceTheSameVideoIsReadable() {
        assertEquals(SeekStep.Go(600_000L), ResumeSeekRules.step(seek(), page, video(), now))
        // adres yang sama walau parameter/fragmen ditambahkan situs
        assertEquals(SeekStep.Go(600_000L), ResumeSeekRules.step(seek(), "$page?autoplay=1#x", video(), now))
    }

    @Test
    fun dropsWhenTheUserIsOnAnotherPage() {
        assertEquals(SeekStep.Drop, ResumeSeekRules.step(seek(), "https://archive.org/details/other", video(), now))
        assertEquals(SeekStep.Drop, ResumeSeekRules.step(seek(), "https://archive.org/", video(), now))
    }

    @Test
    fun dropsAfterTheDeadline() {
        val s = seek()
        assertEquals(SeekStep.Wait, ResumeSeekRules.step(s, null, null, s.deadlineMs))
        assertEquals(SeekStep.Drop, ResumeSeekRules.step(s, page, video(), s.deadlineMs + 1))
        assertEquals(SeekStep.Drop, ResumeSeekRules.step(s, null, null, s.deadlineMs + 1))
    }

    @Test
    fun aDifferentVideoOnTheSamePageIsNotJumped() {
        // iklan 15 dtk lebih dulu terbaca; film aslinya (durasi sama dengan yang tercatat) menyusul
        val ad = video(dur = 15_000L)
        assertEquals(SeekStep.Wait, ResumeSeekRules.step(seek(), page, ad, now))
        assertEquals(SeekStep.Go(600_000L), ResumeSeekRules.step(seek(), page, video(dur = 5_401_500L), now))
        assertEquals(SeekStep.Wait, ResumeSeekRules.step(seek(), page, video(dur = 5_403_001L), now))
    }

    @Test
    fun withoutAKnownDurationTheTargetMustLeaveRoomBeforeTheEnd() {
        val noDur = seek(FilmStart(page, 600_000L, 0L))
        assertEquals(SeekStep.Go(600_000L), ResumeSeekRules.step(noDur, page, video(dur = 700_000L), now))
        assertEquals(SeekStep.Wait, ResumeSeekRules.step(noDur, page, video(dur = 620_000L), now))
    }

    @Test
    fun unknownDurationJumpsOnlyIfThisVideoWasSeenWithADuration() {
        // metadata belum dimuat (preload kosong): posisi mulai bawaan diatur untuk video yang sudah dikenal
        assertEquals(SeekStep.Go(600_000L), ResumeSeekRules.step(seek(), page, video(dur = 0L), now))
        // tidak ada catatan durasi (mungkin siaran langsung): tunggu sampai durasinya terbaca atau waktu habis
        val unknown = seek(FilmStart(page, 600_000L, 0L))
        assertEquals(SeekStep.Wait, ResumeSeekRules.step(unknown, page, video(dur = 0L), now))
    }

    @Test
    fun anEndedVideoIsNotJumped() {
        assertEquals(SeekStep.Wait, ResumeSeekRules.step(seek(), page, video(ended = true), now))
    }
}

class FilmSnapshotsTest {
    private val page = "https://archive.org/details/x"
    private val now = 100_000L
    private fun sample(pos: Long, playing: Boolean = true, at: Long = now, dur: Long = 5_400_000L, ended: Boolean = false) =
        FilmSample(VideoStatus(playing, pos, dur, ended), at)

    @Test
    fun noCassetteOrPageMeansNoSnapshot() {
        assertNull(FilmSnapshots.make(null, page, sample(1), page, now))
        assertNull(FilmSnapshots.make("a", null, sample(1), page, now))
    }

    @Test
    fun aFreshReadingOnTheSamePageIsExtrapolated() {
        val s = FilmSnapshots.make("a", page, sample(600_000, playing = true, at = now - 2_000), page, now)!!
        assertEquals("a", s.cassetteId)
        assertEquals(page, s.pageUrl)
        assertEquals(602_000L, s.positionMs)
        assertEquals(5_400_000L, s.durationMs)
        assertEquals(false, s.ended)
    }

    @Test
    fun aPausedReadingIsNotExtrapolated() {
        val s = FilmSnapshots.make("a", page, sample(600_000, playing = false, at = now - 2_000), page, now)!!
        assertEquals(600_000L, s.positionMs)
    }

    @Test
    fun aStaleReadingKeepsItsOwnPositionInsteadOfRunningAhead() {
        val s = FilmSnapshots.make("a", page, sample(600_000, playing = true, at = now - FilmSnapshots.FRESH_MS - 1), page, now)!!
        assertEquals(600_000L, s.positionMs)
    }

    @Test
    fun aReadingFromAnotherPageIsNeverAttributedToTheNewPage() {
        val s = FilmSnapshots.make("a", page, sample(600_000), "https://archive.org/details/other", now)!!
        assertNull(s.positionMs)
        assertNull(s.durationMs)
        assertEquals(false, s.ended)
        assertNull(FilmSnapshots.make("a", page, sample(600_000), null, now)!!.positionMs)
        assertNull(FilmSnapshots.make("a", page, null, page, now)!!.positionMs)
    }

    @Test
    fun theQueryOrFragmentOfAPageDoesNotInvalidateTheReading() {
        val s = FilmSnapshots.make("a", "$page?t=60", sample(600_000, playing = false), page, now)!!
        assertEquals(600_000L, s.positionMs)
    }

    @Test
    fun anEndedVideoIsReportedAsEnded() {
        val s = FilmSnapshots.make("a", page, sample(5_400_000, playing = false, ended = true), page, now)!!
        assertTrue(s.ended)
    }
}

class FilmRetryTest {
    private val home = "https://archive.org/details/feature_films"
    private val page = "https://archive.org/details/x"
    private val initial = FilmStart(page, 600_000L, 5_400_000L)

    @Test
    fun withoutAnySnapshotTheInitialPlanIsRepeated() {
        assertEquals(initial, FilmRetry.from(initial, home, null))
        assertEquals(FilmStart(home), FilmRetry.from(null, home, null))
    }

    @Test
    fun aKnownPositionWinsOverTheInitialOne() {
        val snap = FilmResumeSnapshot("a", page, 1_800_000L, 5_400_000L, false)
        assertEquals(FilmStart(page, 1_800_000L, 5_400_000L), FilmRetry.from(initial, home, snap))
    }

    @Test
    fun aPageWhoseVideoWasNeverReadKeepsTheInitialPlanWhenItIsTheSamePage() {
        val snap = FilmResumeSnapshot("a", "$page?x=1", null, null, false)
        assertEquals(initial, FilmRetry.from(initial, home, snap))
    }

    @Test
    fun anotherPageWithoutAVideoOpensFromItsStart() {
        val snap = FilmResumeSnapshot("a", "https://archive.org/details/other", null, null, false)
        assertEquals(FilmStart("https://archive.org/details/other"), FilmRetry.from(initial, home, snap))
    }

    @Test
    fun aVideoThatEndedOrJustStartedReopensFromItsStart() {
        val ended = FilmResumeSnapshot("a", page, 5_399_000L, 5_400_000L, true)
        assertNull(FilmRetry.from(initial, home, ended).seekMs)
        val early = FilmResumeSnapshot("a", page, 3_000L, 5_400_000L, false)
        assertNull(FilmRetry.from(initial, home, early).seekMs)
        assertEquals(page, FilmRetry.from(initial, home, early).url)
    }
}
