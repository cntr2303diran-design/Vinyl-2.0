package com.vinyl.launcher.ui.film

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapeFlowTest {
    private val film = Tape.Film("f1")
    private val other = Tape.Film("f2")
    private val game = Tape.Game("ular")
    private val music = TapeState()

    @Test
    fun theDeckStartsWithTheMusicCassette() {
        assertEquals(Tape.Music, music.tape)
        assertFalse(music.musicWasPlaying)
    }

    @Test
    fun insertingAFilmWhileMusicPlaysPausesTheMusicAndRemembersIt() {
        val step = TapeFlow.press(music, film, musicPlaying = true, resumeMusic = true)
        assertEquals(film, step.state.tape)
        assertTrue(step.state.musicWasPlaying)
        assertEquals(MusicEffect.PAUSE, step.music)
    }

    @Test
    fun insertingAFilmWhileTheMusicIsPausedSendsNoCommand() {
        val step = TapeFlow.press(music, film, musicPlaying = false, resumeMusic = true)
        assertEquals(film, step.state.tape)
        assertFalse(step.state.musicWasPlaying)
        assertEquals(MusicEffect.NONE, step.music)
    }

    @Test
    fun pressingTheSameFilmAgainBringsTheMusicCassetteBackAndResumesTheMusic() {
        val inserted = TapeFlow.press(music, film, musicPlaying = true, resumeMusic = true).state
        val back = TapeFlow.press(inserted, film, musicPlaying = false, resumeMusic = true)
        assertEquals(Tape.Music, back.state.tape)
        assertFalse(back.state.musicWasPlaying)
        assertEquals(MusicEffect.RESUME, back.music)
    }

    @Test
    fun theMusicIsNotResumedWhenTheUserTurnedThatOff() {
        val inserted = TapeFlow.press(music, film, musicPlaying = true, resumeMusic = false).state
        val back = TapeFlow.eject(inserted, resumeMusic = false)
        assertEquals(Tape.Music, back.state.tape)
        assertEquals(MusicEffect.NONE, back.music)
    }

    @Test
    fun theMusicIsNotResumedWhenItWasPausedBeforeTheFilm() {
        val inserted = TapeFlow.press(music, film, musicPlaying = false, resumeMusic = true).state
        assertEquals(MusicEffect.NONE, TapeFlow.eject(inserted, resumeMusic = true).music)
    }

    @Test
    fun switchingFromOneFilmToAnotherKeepsTheOriginalMusicMemoryAndPausesNothingAgain() {
        val a = TapeFlow.press(music, film, musicPlaying = true, resumeMusic = true).state
        // Musik sudah dijeda; aplikasi musik melaporkan "tidak main" saat film lain dimasukkan
        val b = TapeFlow.press(a, other, musicPlaying = false, resumeMusic = true)
        assertEquals(other, b.state.tape)
        assertTrue(b.state.musicWasPlaying)
        assertEquals(MusicEffect.NONE, b.music)
        assertEquals(MusicEffect.RESUME, TapeFlow.eject(b.state, resumeMusic = true).music)
    }

    @Test
    fun aGameCassetteBehavesLikeAFilmCassette() {
        val g = TapeFlow.press(music, game, musicPlaying = true, resumeMusic = true)
        assertEquals(game, g.state.tape)
        assertEquals(MusicEffect.PAUSE, g.music)
        val f = TapeFlow.press(g.state, film, musicPlaying = false, resumeMusic = true)
        assertEquals(film, f.state.tape)
        assertEquals(MusicEffect.NONE, f.music)
        assertEquals(MusicEffect.RESUME, TapeFlow.press(f.state, film, musicPlaying = false, resumeMusic = true).music)
    }

    @Test
    fun pressingTheMusicCassetteEjectsWhateverIsInTheDeck() {
        val inserted = TapeFlow.press(music, game, musicPlaying = true, resumeMusic = true).state
        val back = TapeFlow.press(inserted, Tape.Music, musicPlaying = false, resumeMusic = true)
        assertEquals(Tape.Music, back.state.tape)
        assertEquals(MusicEffect.RESUME, back.music)
    }

    @Test
    fun ejectingWithTheMusicCassetteAlreadyInDoesNothing() {
        val step = TapeFlow.eject(music, resumeMusic = true)
        assertEquals(music, step.state)
        assertEquals(MusicEffect.NONE, step.music)
        assertEquals(music, TapeFlow.press(music, Tape.Music, musicPlaying = true, resumeMusic = true).state)
    }

    @Test
    fun aDeletedFilmCassetteReturnsTheDeckToMusicWithoutResuming() {
        val inserted = TapeFlow.press(music, film, musicPlaying = true, resumeMusic = true).state
        assertEquals(inserted, TapeFlow.reconcile(inserted, filmExists = { it == "f1" }))
        val after = TapeFlow.reconcile(inserted, filmExists = { false })
        assertEquals(TapeState(), after)
        assertEquals(music, TapeFlow.reconcile(music, filmExists = { false }))
        val g = TapeFlow.press(music, game, musicPlaying = false, resumeMusic = true).state
        assertEquals(g, TapeFlow.reconcile(g, filmExists = { false }))
    }

    @Test
    fun modeWordsAreShortEnoughForTheDeckDisplay() {
        for (t in listOf(Tape.Music, film, game)) assertTrue(TapeFlow.modeWord(t).length <= 5)
    }
}
