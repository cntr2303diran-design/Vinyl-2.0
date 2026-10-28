package com.vinyl.launcher.screenshot

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.vinyl.launcher.data.CassetteShell
import com.vinyl.launcher.data.FilmRules
import com.vinyl.launcher.data.FilmSettings
import com.vinyl.launcher.data.RetroSettings
import com.vinyl.launcher.data.TvBody
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.ui.cassette.CassetteLabel
import com.vinyl.launcher.ui.game.Breakout
import com.vinyl.launcher.ui.game.BreakoutBoard
import com.vinyl.launcher.ui.game.BreakoutState
import com.vinyl.launcher.ui.game.Cell
import com.vinyl.launcher.ui.game.Dir
import com.vinyl.launcher.ui.game.GamePhase
import com.vinyl.launcher.ui.game.Games
import com.vinyl.launcher.ui.game.Snake
import com.vinyl.launcher.ui.game.SnakeBoard
import com.vinyl.launcher.ui.game.SnakeState
import com.vinyl.launcher.ui.room.stage.DeckTape
import com.vinyl.launcher.ui.room.stage.StageDrawerInfo
import androidx.compose.runtime.mutableStateOf
import kotlin.random.Random
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private fun gameColor(id: String = Games.SNAKE) = Games.color(id)

private fun gameNow(phase: GamePhase, id: String = Games.SNAKE) = NowPlaying(
    hasSession = true, title = Games.title(id), artist = "Game", isPlaying = phase == GamePhase.RUNNING,
    dominantColor = gameColor(id), accentColor = gameColor(id), swatches = listOf(gameColor(id)), speed = 0f,
)

/** Label kaset game seperti di deck, dengan teks kecil yang sama dengan baki ("Game · rekor N"). */
private fun gameLabel(id: String = Games.SNAKE, film: FilmSettings = FilmSettings()) = CassetteLabel(
    "game\u0000$id", Games.title(id).uppercase(), Games.subtitle(film, id), null, CassetteShell.COLORED, Color(gameColor(id)),
)

private fun drawerWithGame(open: Boolean): StageDrawerInfo {
    val colors = FilmRules.defaults().map { Color(it.color) } + Games.all.map { Color(gameColor(it)) }
    return StageDrawerInfo(open, colors, colors.size)
}

/** Ular sepanjang 14 petak yang berkelok, kepala menghadap kanan; makanan di kanan atas. */
private fun runningBoard(phase: GamePhase = GamePhase.RUNNING, score: Int = 11, best: Int = 20): SnakeState {
    val body = listOf(
        Cell(9, 6), Cell(8, 6), Cell(7, 6), Cell(6, 6), Cell(5, 6), Cell(5, 7), Cell(5, 8),
        Cell(4, 8), Cell(3, 8), Cell(3, 7), Cell(3, 6), Cell(3, 5), Cell(3, 4), Cell(4, 4),
    )
    return SnakeState(
        cols = Snake.COLS, rows = Snake.ROWS, body = body, dir = Dir.RIGHT, queue = emptyList(), food = Cell(12, 3),
        score = score, phase = phase, best = best, newBest = false,
    )
}

/** Ular yang melintasi sisi kanan papan: ekornya di sisi seberang tidak boleh tersambung ke kepala. */
private fun wrappedBoard(): SnakeState = runningBoard().copy(
    body = listOf(Cell(1, 2), Cell(0, 2), Cell(15, 2), Cell(14, 2), Cell(13, 2), Cell(13, 3), Cell(13, 4)),
    food = Cell(7, 9), score = 4, best = 20,
)

/**
 * Bata di tingkat 2 yang sedang berjalan: dinding berlubang di beberapa tempat, bola melaju ke kanan atas dari tengah
 * lapangan, dua nyawa tersisa.
 */
private fun runningBreakout(phase: GamePhase = GamePhase.RUNNING, score: Int = 46, best: Int = 120, lives: Int = 2): BreakoutState {
    val start = Breakout.newGame(best).copy(level = 2, bricks = Breakout.wall(2))
    var bricks = start.bricks
    for ((r, c) in listOf(0 to 2, 0 to 5, 1 to 2, 1 to 5, 2 to 2, 3 to 5)) bricks = bricks and Breakout.bit(r, c).inv()
    return start.copy(
        bricks = bricks, paddleX = 6.4f, ballX = 9.6f, ballY = 8.7f, ballVx = 5f, ballVy = -7f,
        lives = lives, score = score, phase = phase, hits = 9,
    )
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GameShots {
    @get:Rule
    val compose = createComposeRule()

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    private fun board(state: SnakeState): @androidx.compose.runtime.Composable () -> Unit =
        { SnakeBoard(state, enabled = false, motionAllowed = false, onTurn = {}, onTap = {}) }

    private fun stage(state: SnakeState, retro: RetroSettings = RetroSettings(tvBody = TvBody.WALNUT), landscape: Boolean = false) {
        val line = if (state.phase == GamePhase.READY) "REKOR ${state.best}" else "SKOR ${state.score}"
        compose.setContent {
            StagePreview(
                gameNow(state.phase), retro, landscape = landscape,
                tape = DeckTape.GAME, tapeLabel = gameLabel(), tvScreen = board(state), drawer = drawerWithGame(false), gameLine = line,
            )
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phoneReady() {
        stage(Snake.newGame(best = 24, random = Random(3)))
        shot("tahap14_game_siap_ponsel")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phoneRunning() {
        stage(runningBoard())
        shot("tahap14_game_jalan_ponsel")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phonePaused() {
        stage(runningBoard(phase = GamePhase.PAUSED))
        shot("tahap14_game_jeda_ponsel")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletGameOverWithNewRecord() {
        stage(runningBoard(phase = GamePhase.OVER, score = 21, best = 21).copy(newBest = true), retro = RetroSettings(), landscape = true)
        shot("tahap14_game_over_tablet")
    }

    @Test
    @Config(qualifiers = "w915dp-h412dp-land-xxhdpi")
    fun phoneLandscapeWrapAround() {
        stage(wrappedBoard(), retro = RetroSettings(tvBody = TvBody.SPACE_AGE), landscape = true)
        shot("tahap14_game_tepi_ponsel_mendatar")
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2.0f)
    fun smallPhoneLargeFont() {
        stage(Snake.newGame(best = 24, random = Random(3)), retro = RetroSettings(tvBody = TvBody.CHARCOAL))
        shot("tahap14_game_ponsel_kecil_font200")
    }

    @Test
    @Config(qualifiers = "ar-w411dp-h891dp-xxhdpi")
    fun phoneRtl() {
        stage(runningBoard())
        shot("tahap14_game_rtl")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phoneTvPoweredOff() {
        compose.setContent {
            StagePreview(playing(fakeCover()), RetroSettings(tvBody = TvBody.WALNUT), landscape = false, tvOff = true, drawer = drawerWithGame(false))
        }
        shot("tahap14_tv_mati_ponsel")
    }

    private fun breakoutBoard(state: BreakoutState): @androidx.compose.runtime.Composable () -> Unit {
        val holder = mutableStateOf<BreakoutState?>(state)
        return { BreakoutBoard(holder, enabled = false, motionAllowed = false, onMove = {}, onTap = {}) }
    }

    private fun breakoutStage(state: BreakoutState, retro: RetroSettings = RetroSettings(tvBody = TvBody.WALNUT), landscape: Boolean = false) {
        val line = if (state.phase == GamePhase.READY) "REKOR ${state.best}" else "SKOR ${state.score}"
        val film = FilmSettings(breakoutBest = state.best)
        compose.setContent {
            StagePreview(
                gameNow(state.phase, Games.BREAKOUT), retro, landscape = landscape,
                tape = DeckTape.PADDLE, tapeLabel = gameLabel(Games.BREAKOUT, film), tvScreen = breakoutBoard(state),
                drawer = drawerWithGame(false), gameLine = line,
            )
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun breakoutReadyOnPhone() {
        breakoutStage(Breakout.newGame(best = 180))
        shot("putaran2_bata_siap_ponsel")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun breakoutRunningOnPhone() {
        breakoutStage(runningBreakout())
        shot("putaran2_bata_jalan_ponsel")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun breakoutPausedOnPhone() {
        breakoutStage(runningBreakout(phase = GamePhase.PAUSED))
        shot("putaran2_bata_jeda_ponsel")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun breakoutGameOverWithNewRecordOnTablet() {
        breakoutStage(
            runningBreakout(phase = GamePhase.OVER, score = 214, best = 214, lives = 0).copy(newBest = true),
            retro = RetroSettings(), landscape = true,
        )
        shot("putaran2_bata_over_tablet")
    }

    @Test
    @Config(qualifiers = "w915dp-h412dp-land-xxhdpi")
    fun breakoutOnPhoneLandscape() {
        breakoutStage(runningBreakout(), retro = RetroSettings(tvBody = TvBody.SPACE_AGE), landscape = true)
        shot("putaran2_bata_ponsel_mendatar")
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2.0f)
    fun breakoutOnSmallPhoneWithLargeFont() {
        breakoutStage(Breakout.newGame(best = 180), retro = RetroSettings(tvBody = TvBody.CHARCOAL))
        shot("putaran2_bata_ponsel_kecil_font200")
    }

    @Test
    @Config(qualifiers = "ar-w411dp-h891dp-xxhdpi")
    fun breakoutRtl() {
        breakoutStage(runningBreakout())
        shot("putaran2_bata_rtl")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun theDrawerShowsBothGameCassettesWithTheirRecords() {
        val film = FilmSettings(snakeBest = 24, breakoutBest = 180)
        compose.setContent {
            StagePreview(
                gameNow(GamePhase.READY, Games.BREAKOUT), RetroSettings(tvBody = TvBody.WALNUT), landscape = false,
                tape = DeckTape.PADDLE, tapeLabel = gameLabel(Games.BREAKOUT, film), tvScreen = breakoutBoard(Breakout.newGame(180)),
                drawer = drawerWithGame(true), gameLine = "REKOR 180",
            )
        }
        shot("putaran2_laci_dua_game_ponsel")
    }
}
