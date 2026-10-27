package com.vinyl.launcher.ui.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SnakeGameTest {
    private val rnd = Random(5)

    /** Papan tangan: ular dari [cells] (kepala dulu) menghadap [dir] sedang berjalan. */
    private fun board(
        vararg cells: Pair<Int, Int>,
        dir: Dir = Dir.RIGHT,
        food: Pair<Int, Int>? = null,
        cols: Int = 8,
        rows: Int = 6,
        score: Int = 0,
        best: Int = 0,
        phase: SnakePhase = SnakePhase.RUNNING,
    ) = SnakeState(
        cols = cols, rows = rows, body = cells.map { Cell(it.first, it.second) }, dir = dir, queue = emptyList(),
        food = food?.let { Cell(it.first, it.second) }, score = score, phase = phase, best = best, newBest = false,
    )

    @Test
    fun aNewGameStartsInTheMiddleFacingRightWaitingForTheFirstMove() {
        val s = Snake.newGame(best = 12, random = rnd)
        assertEquals(Snake.START_LENGTH, s.body.size)
        assertEquals(Cell(Snake.COLS / 2, Snake.ROWS / 2), s.head)
        assertEquals(Dir.RIGHT, s.dir)
        assertEquals(SnakePhase.READY, s.phase)
        assertEquals(0, s.score)
        assertEquals(12, s.best)
        assertFalse(s.newBest)
        assertNotNull(s.food)
        assertFalse(s.food in s.body)
        assertEquals(s.body.size, s.body.toSet().size)
    }

    @Test
    fun aNegativeBestFromCorruptSettingsIsTreatedAsZero() {
        assertEquals(0, Snake.newGame(best = -5, random = rnd).best)
    }

    @Test
    fun theSpeedRisesWithTheScoreAndStopsAtTheFloor() {
        assertEquals(Snake.SLOWEST_MS, Snake.tickMs(0))
        assertEquals(Snake.SLOWEST_MS, Snake.tickMs(-3))
        var prev = Snake.tickMs(0)
        for (score in 1..200) {
            val t = Snake.tickMs(score)
            assertTrue("skor $score", t <= prev)
            assertTrue("skor $score", t >= Snake.FASTEST_MS)
            prev = t
        }
        assertEquals(Snake.FASTEST_MS, Snake.tickMs(200))
        assertTrue(Snake.tickMs(1) < Snake.tickMs(0))
    }

    @Test
    fun aStepMovesTheHeadOneCellAndTheTailFollows() {
        val s = board(3 to 2, 2 to 2, 1 to 2)
        val r = Snake.step(s, rnd)
        assertEquals(SnakeEvent.NONE, r.event)
        assertEquals(listOf(Cell(4, 2), Cell(3, 2), Cell(2, 2)), r.state.body)
    }

    @Test
    fun theBoardHasNoWallsTheSnakeAppearsOnTheOtherSide() {
        val right = Snake.step(board(7 to 2, 6 to 2, 5 to 2, dir = Dir.RIGHT), rnd).state
        assertEquals(Cell(0, 2), right.head)
        val left = Snake.step(board(0 to 2, 1 to 2, 2 to 2, dir = Dir.LEFT), rnd).state
        assertEquals(Cell(7, 2), left.head)
        val up = Snake.step(board(3 to 0, 3 to 1, 3 to 2, dir = Dir.UP), rnd).state
        assertEquals(Cell(3, 5), up.head)
        val down = Snake.step(board(3 to 5, 3 to 4, 3 to 3, dir = Dir.DOWN), rnd).state
        assertEquals(Cell(3, 0), down.head)
    }

    @Test
    fun eatingGrowsTheSnakeScoresAndPlacesNewFoodOffTheSnake() {
        val s = board(3 to 2, 2 to 2, 1 to 2, food = 4 to 2)
        val r = Snake.step(s, rnd)
        assertEquals(SnakeEvent.ATE, r.event)
        assertEquals(1, r.state.score)
        assertEquals(4, r.state.body.size)
        assertEquals(Cell(4, 2), r.state.head)
        assertNotNull(r.state.food)
        assertFalse(r.state.food in r.state.body)
        assertEquals(SnakePhase.RUNNING, r.state.phase)
    }

    @Test
    fun foodNeverLandsOnTheSnakeWhateverTheSeed() {
        val body = (0 until 5).map { Cell(it, 1) } + (0 until 3).map { Cell(it, 3) }
        for (seed in 0 until 300) {
            val f = Snake.spawnFood(body, 8, 6, Random(seed))
            assertNotNull(f)
            assertFalse("seed $seed", f in body)
            assertTrue(f!!.x in 0 until 8 && f.y in 0 until 6)
        }
    }

    @Test
    fun foodCanBeAnyFreeCellAndIsNullOnAFullBoard() {
        val all = (0 until 3).flatMap { y -> (0 until 4).map { x -> Cell(x, y) } }
        assertNull(Snake.spawnFood(all, 4, 3, rnd))
        val oneFree = all.filterNot { it == Cell(2, 1) }
        assertEquals(Cell(2, 1), Snake.spawnFood(oneFree, 4, 3, rnd))
    }

    @Test
    fun hittingItsOwnBodyEndsTheGameAndFreezesTheBoard() {
        // Ular melingkar: kepala di (2,2) menghadap bawah akan masuk ke (2,3) yang ditempati tubuh
        val s = board(2 to 2, 3 to 2, 3 to 3, 2 to 3, 1 to 3, 1 to 2, dir = Dir.DOWN, score = 4, best = 9)
        val r = Snake.step(s, rnd)
        assertEquals(SnakeEvent.DIED, r.event)
        assertEquals(SnakePhase.OVER, r.state.phase)
        assertEquals(s.body, r.state.body)
        assertEquals(4, r.state.score)
        assertEquals(9, r.state.best)
        assertFalse(r.state.newBest)
        // Setelah selesai, langkah berikutnya tidak mengubah apa pun
        val again = Snake.step(r.state, rnd)
        assertSame(r.state, again.state)
        assertEquals(SnakeEvent.NONE, again.event)
    }

    @Test
    fun theCellTheTailIsLeavingIsSafeToEnter() {
        // Ular melingkar membentuk persegi 2×2: kepala di (1,1), ekor di (0,1), dan kepala bergerak ke kiri ke sel ekor
        val s = board(1 to 1, 1 to 2, 0 to 2, 0 to 1, dir = Dir.LEFT)
        val r = Snake.step(s, rnd)
        assertEquals(SnakeEvent.NONE, r.event)
        assertEquals(SnakePhase.RUNNING, r.state.phase)
        assertEquals(Cell(0, 1), r.state.head)
    }

    @Test
    fun theTailCellIsNotSafeWhenTheSnakeIsGrowingIntoIt() {
        // Makanan tepat di sel ekor: ular bertambah panjang, ekor tidak bergeser, jadi itu tabrakan
        val s = board(1 to 1, 1 to 2, 0 to 2, 0 to 1, dir = Dir.LEFT, food = 0 to 1)
        val r = Snake.step(s, rnd)
        assertEquals(SnakeEvent.DIED, r.event)
    }

    @Test
    fun breakingTheRecordIsFlaggedWhenTheGameEnds() {
        val s = board(2 to 2, 3 to 2, 3 to 3, 2 to 3, 1 to 3, 1 to 2, dir = Dir.DOWN, score = 10, best = 7)
        val r = Snake.step(s, rnd)
        assertEquals(SnakePhase.OVER, r.state.phase)
        assertEquals(10, r.state.best)
        assertTrue(r.state.newBest)
        assertEquals(10, r.state.shownBest)
    }

    @Test
    fun theShownRecordRisesAsSoonAsTheScorePassesIt() {
        assertEquals(7, board(1 to 1, score = 3, best = 7).shownBest)
        assertEquals(9, board(1 to 1, score = 9, best = 7).shownBest)
    }

    @Test
    fun fillingTheBoardWins() {
        // Papan 4×2 = 8 sel; ular 7 sel, satu sel kosong berisi makanan di depan kepala
        val s = board(1 to 0, 0 to 0, 0 to 1, 1 to 1, 2 to 1, 3 to 1, 3 to 0, cols = 4, rows = 2, dir = Dir.RIGHT, food = 2 to 0, score = 4, best = 2)
        val r = Snake.step(s, rnd)
        assertEquals(SnakeEvent.WON, r.event)
        assertEquals(SnakePhase.WON, r.state.phase)
        assertNull(r.state.food)
        assertEquals(8, r.state.body.size)
        assertEquals(5, r.state.score)
        assertTrue(r.state.newBest)
    }

    @Test
    fun aTurnIsQueuedAndAppliedOnTheNextStep() {
        val s = Snake.turn(board(3 to 2, 2 to 2, 1 to 2), Dir.UP)
        assertEquals(listOf(Dir.UP), s.queue)
        val r = Snake.step(s, rnd).state
        assertEquals(Dir.UP, r.dir)
        assertEquals(Cell(3, 1), r.head)
        assertTrue(r.queue.isEmpty())
    }

    @Test
    fun turningBackOnItselfOrRepeatingTheDirectionIsIgnored() {
        val s = board(3 to 2, 2 to 2, 1 to 2, dir = Dir.RIGHT)
        assertTrue(Snake.turn(s, Dir.LEFT).queue.isEmpty())
        assertTrue(Snake.turn(s, Dir.RIGHT).queue.isEmpty())
        // Setelah UP menunggu, DOWN (kebalikannya) dan UP (sama) ditolak, LEFT diterima
        val up = Snake.turn(s, Dir.UP)
        assertEquals(listOf(Dir.UP), Snake.turn(up, Dir.DOWN).queue)
        assertEquals(listOf(Dir.UP), Snake.turn(up, Dir.UP).queue)
        assertEquals(listOf(Dir.UP, Dir.LEFT), Snake.turn(up, Dir.LEFT).queue)
    }

    @Test
    fun atMostTwoTurnsWaitAndTwoQuickTurnsRunInOrder() {
        var s = board(4 to 3, 3 to 3, 2 to 3, dir = Dir.RIGHT)
        s = Snake.turn(s, Dir.UP)
        s = Snake.turn(s, Dir.LEFT)
        s = Snake.turn(s, Dir.DOWN) // ketiga: ditolak (antrean penuh)
        assertEquals(listOf(Dir.UP, Dir.LEFT), s.queue)
        s = Snake.step(s, rnd).state
        assertEquals(Cell(4, 2), s.head)
        s = Snake.step(s, rnd).state
        assertEquals(Cell(3, 2), s.head)
        assertEquals(Dir.LEFT, s.dir)
    }

    @Test
    fun theFirstMoveStartsTheGameAndAReversalAtTheStartOnlyStartsIt() {
        val ready = Snake.newGame(0, rnd)
        val up = Snake.turn(ready, Dir.UP)
        assertEquals(SnakePhase.RUNNING, up.phase)
        assertEquals(listOf(Dir.UP), up.queue)
        val left = Snake.turn(ready, Dir.LEFT)
        assertEquals(SnakePhase.RUNNING, left.phase)
        assertTrue(left.queue.isEmpty())
    }

    @Test
    fun aTurnWhilePausedResumes() {
        val s = Snake.turn(board(3 to 2, 2 to 2, 1 to 2, phase = SnakePhase.PAUSED), Dir.DOWN)
        assertEquals(SnakePhase.RUNNING, s.phase)
        assertEquals(listOf(Dir.DOWN), s.queue)
    }

    @Test
    fun swipesAfterTheGameEndedDoNotRestartIt() {
        val over = board(3 to 2, 2 to 2, 1 to 2, phase = SnakePhase.OVER)
        assertSame(over, Snake.turn(over, Dir.UP))
        val won = board(3 to 2, 2 to 2, 1 to 2, phase = SnakePhase.WON)
        assertSame(won, Snake.turn(won, Dir.UP))
    }

    @Test
    fun tapStartsPausesResumesAndRestartsKeepingTheRecord() {
        var s = Snake.newGame(5, rnd)
        s = Snake.tap(s, rnd)
        assertEquals(SnakePhase.RUNNING, s.phase)
        s = Snake.tap(s, rnd)
        assertEquals(SnakePhase.PAUSED, s.phase)
        s = Snake.tap(s, rnd)
        assertEquals(SnakePhase.RUNNING, s.phase)
        val over = s.copy(phase = SnakePhase.OVER, score = 8, best = 8, newBest = true)
        val fresh = Snake.tap(over, rnd)
        assertEquals(SnakePhase.READY, fresh.phase)
        assertEquals(0, fresh.score)
        assertEquals(8, fresh.best)
        assertFalse(fresh.newBest)
        assertEquals(over.cols, fresh.cols)
        assertEquals(over.rows, fresh.rows)
    }

    @Test
    fun pauseOnlyAffectsAGameInProgress() {
        assertEquals(SnakePhase.PAUSED, Snake.pause(board(3 to 2, 2 to 2, 1 to 2)).phase)
        val ready = Snake.newGame(0, rnd)
        assertSame(ready, Snake.pause(ready))
        val over = board(3 to 2, 2 to 2, 1 to 2, phase = SnakePhase.OVER)
        assertSame(over, Snake.pause(over))
    }

    @Test
    fun nothingMovesUnlessTheGameIsRunning() {
        for (p in listOf(SnakePhase.READY, SnakePhase.PAUSED, SnakePhase.OVER, SnakePhase.WON)) {
            val s = board(3 to 2, 2 to 2, 1 to 2, phase = p)
            val r = Snake.step(s, rnd)
            assertSame(s, r.state)
            assertEquals(SnakeEvent.NONE, r.event)
        }
    }

    @Test
    fun aLongRandomGameNeverBreaksTheInvariants() {
        val r = Random(11)
        var s = Snake.turn(Snake.newGame(0, r), Dir.UP)
        var steps = 0
        while (s.phase == SnakePhase.RUNNING && steps < 5_000) {
            if (r.nextInt(3) == 0) s = Snake.turn(s, Dir.entries[r.nextInt(4)])
            val before = s.body.size
            s = Snake.step(s, r).state
            steps++
            assertEquals("tubuh unik", s.body.size, s.body.toSet().size)
            assertTrue(s.body.all { it.x in 0 until s.cols && it.y in 0 until s.rows })
            assertTrue(s.queue.size <= Snake.MAX_QUEUE)
            assertTrue(s.body.size >= before)
            assertEquals(Snake.START_LENGTH + s.score, s.body.size)
            s.food?.let { assertFalse(it in s.body) }
        }
        assertTrue(steps > 0)
    }

    @Test
    fun aSwipeSteerEmitsADirectionOnceThePathPassesTheThreshold() {
        val steer = SwipeSteer(threshold = 20f)
        assertNull(steer.drag(8f, 1f))
        assertNull(steer.drag(8f, -1f))
        assertEquals(Dir.RIGHT, steer.drag(8f, 0f))
        // Hitungan diulang setelah keluar
        assertNull(steer.drag(5f, 0f))
    }

    @Test
    fun aSwipeSteerPicksTheDominantAxisAndAllSignsWork() {
        assertEquals(Dir.LEFT, SwipeSteer(10f).drag(-12f, 3f))
        assertEquals(Dir.UP, SwipeSteer(10f).drag(2f, -14f))
        assertEquals(Dir.DOWN, SwipeSteer(10f).drag(-4f, 11f))
        assertEquals(Dir.RIGHT, SwipeSteer(10f).drag(10f, 0f))
    }

    @Test
    fun aSwipeSteerGivesSeveralTurnsInOneLongStroke() {
        val steer = SwipeSteer(10f)
        val out = ArrayList<Dir>()
        for (i in 1..6) steer.drag(6f, 0f)?.let { out += it }
        for (i in 1..6) steer.drag(0f, 6f)?.let { out += it }
        assertEquals(listOf(Dir.RIGHT, Dir.RIGHT, Dir.RIGHT, Dir.DOWN, Dir.DOWN, Dir.DOWN), out)
    }

    @Test
    fun aSwipeSteerResetForgetsTheStrokeSoFar() {
        val steer = SwipeSteer(10f)
        assertNull(steer.drag(8f, 0f))
        steer.reset()
        assertNull(steer.drag(8f, 0f))
    }
}
