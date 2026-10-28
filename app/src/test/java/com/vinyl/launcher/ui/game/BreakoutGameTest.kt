package com.vinyl.launcher.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

class BreakoutGameTest {
    private val r = Breakout.BALL_R
    private val frame = 1f / 120f

    /** Permainan yang sedang berjalan dengan bola di tempat dan kecepatan yang ditentukan test. */
    private fun running(
        x: Float, y: Float, vx: Float, vy: Float,
        paddleX: Float = 8f, bricks: Long = Breakout.wall(1), level: Int = 1, lives: Int = 3, score: Int = 0,
        hits: Int = 0, best: Int = 0,
    ): BreakoutState = Breakout.newGame(best).copy(
        phase = GamePhase.RUNNING, ballX = x, ballY = y, ballVx = vx, ballVy = vy, paddleX = paddleX,
        bricks = bricks, level = level, lives = lives, score = score, hits = hits,
    )

    private fun speedOf(s: BreakoutState) = sqrt(s.ballVx * s.ballVx + s.ballVy * s.ballVy)

    private fun one(s: BreakoutState) = Breakout.advance(s, frame)

    // ---------- permainan baru, susunan dinding ----------

    @Test
    fun aNewGameWaitsOnThePaddleWithAFullWallAndThreeLives() {
        val s = Breakout.newGame(best = 24)
        assertEquals(GamePhase.READY, s.phase)
        assertEquals(Breakout.FIELD_W / 2f, s.paddleX, 0f)
        assertEquals(s.paddleX, s.ballX, 0f)
        assertEquals(Breakout.PADDLE_TOP - r, s.ballY, 1e-6f)
        assertEquals(0f, s.ballVx, 0f)
        assertEquals(0f, s.ballVy, 0f)
        assertEquals(3, s.lives)
        assertEquals(1, s.level)
        assertEquals(0, s.score)
        assertEquals(24, s.best)
        assertFalse(s.newBest)
        assertEquals(Breakout.wall(1), s.bricks)
        assertEquals(40, s.brickCount)
        assertTrue(s.untouched)
    }

    @Test
    fun aNegativeRecordIsTreatedAsNone() {
        assertEquals(0, Breakout.newGame(best = -9).best)
    }

    @Test
    fun theFourWallShapesHaveTheirDocumentedBrickCounts() {
        assertEquals(40, Breakout.wall(1).countOneBits()) // 5 baris × 8
        assertEquals(36, Breakout.wall(2).countOneBits()) // 6 baris × 6 (lorong 2 kolom di tengah)
        assertEquals(28, Breakout.wall(3).countOneBits()) // papan catur 7 baris
        assertEquals(38, Breakout.wall(4).countOneBits()) // corong: 8+8+6+6+4+4+2
        assertEquals(56, Breakout.wall(5).countOneBits()) // bentuk penuh lagi, kini 7 baris
    }

    @Test
    fun theCorridorWallLeavesTheMiddleTwoColumnsOpen() {
        val w = Breakout.wall(2)
        for (row in 0 until Breakout.rowsFor(2)) {
            assertEquals(0L, w and Breakout.bit(row, 3))
            assertEquals(0L, w and Breakout.bit(row, 4))
            assertNotEquals(0L, w and Breakout.bit(row, 2))
            assertNotEquals(0L, w and Breakout.bit(row, 5))
        }
    }

    @Test
    fun everyLevelHasABoundedNonEmptyWall() {
        for (level in 1..2000) {
            val w = Breakout.wall(level)
            assertNotEquals("tingkat $level kosong", 0L, w)
            assertEquals("tingkat $level melewati 56 bit", 0L, w ushr (Breakout.COLS * Breakout.MAX_ROWS))
        }
        assertEquals(5, Breakout.rowsFor(1))
        assertEquals(6, Breakout.rowsFor(2))
        assertEquals(7, Breakout.rowsFor(3))
        assertEquals(7, Breakout.rowsFor(500))
    }

    @Test
    fun theFieldHasTheSameShapeAsTheSnakeBoardSoTheLayoutIsShared() {
        assertEquals(Snake.COLS, Breakout.FIELD_COLS)
        assertEquals(Snake.ROWS, Breakout.FIELD_ROWS)
        assertEquals(Breakout.FIELD_COLS.toFloat(), Breakout.FIELD_W, 0f)
        assertEquals(Breakout.FIELD_ROWS.toFloat(), Breakout.FIELD_H, 0f)
        // dinding bata tidak boleh menyentuh palet: tujuh baris berakhir jauh di atas garis palet
        assertTrue(Breakout.WALL_TOP + Breakout.MAX_ROWS * Breakout.BRICK_H < Breakout.PADDLE_TOP - 4f)
        assertTrue(Breakout.PADDLE_TOP + Breakout.PADDLE_H < Breakout.FIELD_H)
        assertEquals(Breakout.FIELD_W, Breakout.COLS * Breakout.BRICK_W, 1e-6f)
    }

    @Test
    fun theTopRowsAreWorthMore() {
        assertEquals(listOf(5, 4, 3, 2, 1, 1, 1), (0 until 7).map { Breakout.rowPoints(it) })
    }

    // ---------- kecepatan ----------

    @Test
    fun theBallStartsAtTheBaseSpeedAndSpeedsUpWithHitsAndLevelsUpToACap() {
        assertEquals(Breakout.BASE_SPEED, Breakout.speed(1, 0), 1e-6f)
        assertTrue(Breakout.speed(1, 10) > Breakout.speed(1, 9))
        assertTrue(Breakout.speed(2, 0) > Breakout.speed(1, 0))
        assertEquals(Breakout.MAX_SPEED, Breakout.speed(50, 10_000), 0f)
        for (l in 1..30) for (h in 0..100) assertTrue(Breakout.speed(l, h) in Breakout.BASE_SPEED..Breakout.MAX_SPEED)
        // masukan yang tidak masuk akal tidak membuat bola lebih lambat dari dasar
        assertEquals(Breakout.BASE_SPEED, Breakout.speed(-3, -50), 1e-6f)
    }

    // ---------- ketukan, jeda ----------

    @Test
    fun tappingServesTheBallUpwardAtTheBaseSpeed() {
        val s = Breakout.tap(Breakout.newGame(0))
        assertEquals(GamePhase.RUNNING, s.phase)
        assertTrue(s.ballVy < 0f)
        assertEquals(Breakout.speed(1, 0), speedOf(s), 1e-4f)
        assertTrue("luncuran pertama ke kanan", s.ballVx > 0f)
        assertFalse(s.serveRight)
    }

    @Test
    fun theServeAlternatesSidesEachTime() {
        val first = Breakout.tap(Breakout.newGame(0))
        val lost = Breakout.advance(running(8f, 13.4f, 0f, 7f, lives = 3).copy(serveRight = first.serveRight), frame).state
        assertEquals(GamePhase.READY, lost.phase)
        val second = Breakout.tap(lost)
        assertTrue("luncuran kedua ke kiri", second.ballVx < 0f)
    }

    @Test
    fun tappingPausesAndResumesAndAfterTheEndStartsOverKeepingTheRecord() {
        val playing = Breakout.tap(Breakout.newGame(0))
        val paused = Breakout.tap(playing)
        assertEquals(GamePhase.PAUSED, paused.phase)
        assertEquals(GamePhase.RUNNING, Breakout.tap(paused).phase)

        val over = playing.copy(phase = GamePhase.OVER, score = 31, best = 31, newBest = true, lives = 0)
        val fresh = Breakout.tap(over)
        assertEquals(GamePhase.READY, fresh.phase)
        assertEquals(31, fresh.best)
        assertEquals(0, fresh.score)
        assertEquals(3, fresh.lives)
        assertFalse(fresh.newBest)
    }

    @Test
    fun pauseOnlyStopsAGameThatIsRunning() {
        val ready = Breakout.newGame(0)
        assertSame(ready, Breakout.pause(ready))
        val playing = Breakout.tap(ready)
        assertEquals(GamePhase.PAUSED, Breakout.pause(playing).phase)
        val over = playing.copy(phase = GamePhase.OVER)
        assertSame(over, Breakout.pause(over))
    }

    @Test
    fun nothingMovesUnlessTheGameIsRunning() {
        for (phase in listOf(GamePhase.READY, GamePhase.PAUSED, GamePhase.OVER, GamePhase.WON)) {
            val s = running(5f, 5f, 3f, 5f).copy(phase = phase)
            val step = Breakout.advance(s, 0.016f)
            assertSame(s, step.state)
            assertTrue(step.events.isEmpty())
        }
    }

    // ---------- palet ----------

    @Test
    fun thePaddleStopsAtBothWalls() {
        val half = Breakout.PADDLE_W / 2f
        val ready = Breakout.newGame(0)
        assertEquals(half, Breakout.movePaddle(ready, -100f).paddleX, 0f)
        assertEquals(Breakout.FIELD_W - half, Breakout.movePaddle(ready, 100f).paddleX, 0f)
    }

    @Test
    fun theBallRidesThePaddleWhileWaitingButNotWhilePlaying() {
        val ready = Breakout.movePaddle(Breakout.newGame(0), 2.5f)
        assertEquals(ready.paddleX, ready.ballX, 0f)
        val playing = running(5f, 5f, 2f, 6f)
        val moved = Breakout.movePaddle(playing, 3f)
        assertEquals(11f, moved.paddleX, 1e-6f)
        assertEquals(5f, moved.ballX, 0f)
    }

    @Test
    fun thePaddleCannotBeRepositionedWhilePausedOrAfterTheEnd() {
        val base = running(5f, 5f, 2f, 6f)
        for (phase in listOf(GamePhase.PAUSED, GamePhase.OVER, GamePhase.WON)) {
            val s = base.copy(phase = phase)
            assertSame(s, Breakout.movePaddle(s, 4f))
        }
    }

    @Test
    fun aDeckKeyNudgesThePaddleByAFixedStepAndZeroDoesNothing() {
        val s = Breakout.newGame(0)
        assertEquals(s.paddleX + Breakout.NUDGE, Breakout.nudge(s, 1).paddleX, 1e-6f)
        assertEquals(s.paddleX - Breakout.NUDGE, Breakout.nudge(s, -1).paddleX, 1e-6f)
        assertSame(s, Breakout.nudge(s, 0))
    }

    // ---------- dinding ----------

    @Test
    fun theBallBouncesOffTheSideWallsAndTheCeiling() {
        val left = one(running(0.31f, 8f, -5f, -5f)).state
        assertEquals(r, left.ballX, 1e-6f)
        assertTrue(left.ballVx > 0f)

        val right = one(running(Breakout.FIELD_W - 0.31f, 8f, 5f, -5f)).state
        assertEquals(Breakout.FIELD_W - r, right.ballX, 1e-6f)
        assertTrue(right.ballVx < 0f)

        val top = Breakout.advance(running(8f, 0.31f, 1f, -5f), frame)
        assertEquals(r, top.state.ballY, 1e-6f)
        assertTrue(top.state.ballVy > 0f)
        assertEquals(listOf(BreakoutEvent.WALL), top.events)
    }

    @Test
    fun aBounceKeepsTheSpeedExactlyAndNeverLeavesTheBallNearlyHorizontal() {
        // nyaris mendatar dan terlalu cepat: setelah memantul tegak lurus ≥ 30% dan lajunya kembali ke yang seharusnya
        val s = one(running(0.31f, 8f, -9f, -0.2f)).state
        assertEquals(Breakout.speed(1, 0), speedOf(s), 1e-4f)
        assertTrue(abs(s.ballVy) >= Breakout.speed(1, 0) * Breakout.MIN_VERTICAL - 1e-4f)
        assertTrue(s.ballVx > 0f)
    }

    // ---------- palet menangkap bola ----------

    private fun justAbovePaddle(x: Float, paddleX: Float = 8f) =
        running(x, Breakout.PADDLE_TOP - r - 0.02f, 0f, Breakout.speed(1, 0), paddleX = paddleX)

    @Test
    fun theCenterOfThePaddleSendsTheBallStraightUp() {
        val step = Breakout.advance(justAbovePaddle(8f), frame)
        assertEquals(listOf(BreakoutEvent.PADDLE), step.events)
        assertEquals(0f, step.state.ballVx, 1e-4f)
        assertEquals(-Breakout.speed(1, 0), step.state.ballVy, 1e-4f)
        assertEquals(Breakout.PADDLE_TOP - r, step.state.ballY, 1e-5f)
    }

    @Test
    fun theEdgesOfThePaddleSendTheBallOutAtTheWidestAngle() {
        val reach = Breakout.PADDLE_W / 2f + r * 0.6f
        val right = Breakout.advance(justAbovePaddle(8f + reach - 0.001f), frame).state
        val left = Breakout.advance(justAbovePaddle(8f - reach + 0.001f), frame).state
        val sp = Breakout.speed(1, 0)
        assertEquals(sp * kotlin.math.sin(Breakout.MAX_ANGLE), right.ballVx, 0.02f)
        assertEquals(-right.ballVx, left.ballVx, 0.05f)
        assertTrue(right.ballVy < 0f && left.ballVy < 0f)
        assertEquals(sp, speedOf(right), 1e-3f)
    }

    @Test
    fun aBallBeyondTheReachOfThePaddleIsNotCaught() {
        val reach = Breakout.PADDLE_W / 2f + r * 0.6f
        val step = Breakout.advance(justAbovePaddle(8f + reach + 0.2f), frame)
        assertTrue(BreakoutEvent.PADDLE !in step.events)
        assertTrue(step.state.ballVy > 0f)
    }

    @Test
    fun aBallAlreadyBelowThePaddleLineIsNotPulledBackUp() {
        val s = running(8f, Breakout.PADDLE_TOP + 0.1f, 0f, 7f)
        val step = Breakout.advance(s, frame)
        assertTrue(BreakoutEvent.PADDLE !in step.events)
        assertTrue(step.state.ballVy > 0f)
    }

    @Test
    fun aPaddleThatJumpsUnderTheBallInTheSameFrameStillCatchesIt() {
        // bola di atas palet yang baru saja digeser (ketukan tombol deck): garis atas palet dilewati dari atas
        val s = Breakout.nudge(justAbovePaddle(10f, paddleX = 8f), 1)
        assertEquals(10f, s.paddleX, 1e-6f)
        assertTrue(BreakoutEvent.PADDLE in Breakout.advance(s, frame).events)
    }

    // ---------- bata ----------

    /** Dinding berisi bata target dan satu bata jauh di pojok (supaya dinding tidak habis). */
    private fun wallOf(row: Int, col: Int) = Breakout.bit(row, col) or Breakout.bit(0, 0)

    @Test
    fun aBrickHitFromBelowBreaksItScoresItsRowAndTurnsTheBallBack() {
        // bata baris 0, kolom 3 = x 6..8, y 1,3..2,0
        val s = running(7f, 2.0f + r + 0.01f, 0f, -7f, bricks = wallOf(0, 3))
        val step = Breakout.advance(s, frame)
        assertEquals(listOf(BreakoutEvent.BRICK), step.events)
        assertEquals(0L, step.state.bricks and Breakout.bit(0, 3))
        assertEquals(5, step.state.score)
        assertEquals(1, step.state.hits)
        assertTrue(step.state.ballVy > 0f)
        assertEquals(Breakout.speed(1, 1), speedOf(step.state), 1e-4f)
    }

    @Test
    fun aBrickHitFromTheSideTurnsTheBallHorizontally() {
        // bata baris 2, kolom 3 = x 6..8, y 2,7..3,4; bola dari kiri
        val s = running(5.69f, 3.0f, 7f, -3f, bricks = wallOf(2, 3))
        val step = Breakout.advance(s, frame)
        assertEquals(listOf(BreakoutEvent.BRICK), step.events)
        assertEquals(3, step.state.score) // baris 2 bernilai 3
        assertTrue("memantul ke kiri", step.state.ballVx < 0f)
    }

    @Test
    fun aBrickHitFromAboveTurnsTheBallUp() {
        // bata baris 4 di bawah garis; bola turun dari atasnya
        val top = Breakout.WALL_TOP + 4 * Breakout.BRICK_H
        val s = running(7f, top - r - 0.01f, 0f, 7f, bricks = wallOf(4, 3))
        val step = Breakout.advance(s, frame)
        assertTrue(BreakoutEvent.BRICK in step.events)
        assertTrue(step.state.ballVy < 0f)
        assertEquals(1, step.state.score)
    }

    @Test
    fun onlyOneBrickBreaksPerStepEvenWhenTheBallTouchesTwo() {
        // bola tepat di antara kolom 2 dan 3 pada baris 1 (batas x = 6), menyentuh keduanya dari bawah
        val both = Breakout.bit(1, 2) or Breakout.bit(1, 3) or Breakout.bit(0, 0)
        val bottom = Breakout.WALL_TOP + 2 * Breakout.BRICK_H
        val step = Breakout.advance(running(6f, bottom + r + 0.005f, 0f, -7f, bricks = both), frame)
        assertEquals(1, step.events.count { it == BreakoutEvent.BRICK })
        assertEquals(1, (both and step.state.bricks.inv()).countOneBits())
    }

    @Test
    fun theBallNeverPassesThroughABrickAtTheFastestSpeed() {
        // laju tertinggi, bola ditembakkan lurus ke bata dari jauh, bingkai besar (50 ms): bata harus pecah, bukan terlewati
        val s = running(7f, 6f, 0f, -Breakout.MAX_SPEED, bricks = wallOf(0, 3), level = 40, hits = 500)
        var cur = s
        var broke = false
        repeat(60) {
            val step = Breakout.advance(cur, Breakout.MAX_FRAME_S)
            cur = step.state
            if (BreakoutEvent.BRICK in step.events) broke = true
        }
        assertTrue(broke)
        assertTrue(cur.ballY >= r - 1e-4f)
    }

    // ---------- tingkat, nyawa ----------

    @Test
    fun breakingTheLastBrickOpensTheNextLevelWithTheBallBackOnThePaddle() {
        val last = Breakout.bit(0, 3)
        val s = running(7f, 2.0f + r + 0.01f, 0f, -7f, bricks = last, score = 40, hits = 12, paddleX = 5f)
        val step = Breakout.advance(s, frame)
        val n = step.state
        assertEquals(listOf(BreakoutEvent.BRICK, BreakoutEvent.LEVEL_UP), step.events)
        assertEquals(2, n.level)
        assertEquals(Breakout.wall(2), n.bricks)
        assertEquals(40 + 5 + Breakout.LEVEL_BONUS, n.score)
        assertEquals(GamePhase.READY, n.phase)
        assertEquals(0, n.hits)
        assertEquals(3, n.lives)
        assertEquals(5f, n.paddleX, 0f)
        assertEquals(5f, n.ballX, 0f)
        assertEquals(Breakout.PADDLE_TOP - r, n.ballY, 1e-6f)
        assertEquals(0f, speedOf(n), 0f)
        // dan luncuran berikutnya sudah lebih cepat
        assertTrue(speedOf(Breakout.tap(n)) > Breakout.speed(1, 0))
    }

    @Test
    fun missingTheBallCostsALifeAndPutsAnotherOnThePaddle() {
        val s = running(3f, 13.4f, 0f, 7f, lives = 3, score = 12, paddleX = 6f)
        val step = Breakout.advance(s, frame)
        assertEquals(listOf(BreakoutEvent.LOST_BALL), step.events)
        assertEquals(2, step.state.lives)
        assertEquals(GamePhase.READY, step.state.phase)
        assertEquals(6f, step.state.ballX, 0f)
        assertEquals(12, step.state.score)
        assertEquals(s.bricks, step.state.bricks)
        assertFalse(step.state.untouched)
    }

    @Test
    fun theLastLifeEndsTheGameAndRecordsTheScore() {
        val s = running(3f, 13.4f, 0f, 7f, lives = 1, score = 55, best = 40)
        val step = Breakout.advance(s, frame)
        assertEquals(listOf(BreakoutEvent.GAME_OVER), step.events)
        assertEquals(GamePhase.OVER, step.state.phase)
        assertEquals(0, step.state.lives)
        assertEquals(55, step.state.best)
        assertTrue(step.state.newBest)
    }

    @Test
    fun aScoreBelowTheRecordIsNotANewRecord() {
        val step = Breakout.advance(running(3f, 13.4f, 0f, 7f, lives = 1, score = 30, best = 40), frame)
        assertEquals(40, step.state.best)
        assertFalse(step.state.newBest)
    }

    @Test
    fun theShownRecordRisesTheMomentTheScoreCrossesIt() {
        val s = Breakout.newGame(best = 10).copy(score = 9)
        assertEquals(10, s.shownBest)
        assertEquals(11, s.copy(score = 11).shownBest)
    }

    @Test
    fun theHudOnlyChangesWhenSomethingItShowsChanges() {
        val a = Breakout.tap(Breakout.newGame(7))
        assertEquals(GameHud(GamePhase.RUNNING, 0, 7, lives = 3, level = 1), a.hud())
        val moved = one(a).state
        assertEquals(a.hud(), moved.hud())
        assertNotEquals(a.hud(), a.copy(lives = 2).hud())
    }

    // ---------- bingkai ----------

    @Test
    fun aHugeOrInvalidFrameTimeNeverMovesTheBallFurtherThanTheCap() {
        val s = running(8f, 6f, 0f, -7f, bricks = wallOf(0, 0))
        val big = Breakout.advance(s, 10f).state
        assertTrue(abs(big.ballY - s.ballY) <= 7.01f * Breakout.MAX_FRAME_S + 1e-3f)
        assertSame(s, Breakout.advance(s, 0f).state)
        assertEquals(s.ballY, Breakout.advance(s, Float.NaN).state.ballY, 0f)
        assertEquals(s.ballY, Breakout.advance(s, -1f).state.ballY, 0f)
    }

    @Test
    fun theSameTimeInOneBigFrameOrManySmallOnesEndsAlmostInTheSamePlace() {
        val start = running(4f, 9f, 3f, -6f, bricks = wallOf(0, 0))
        val a = Breakout.advance(start, 0.04f).state
        var b = start
        repeat(4) { b = Breakout.advance(b, 0.01f).state }
        assertEquals(a.ballX, b.ballX, 0.02f)
        assertEquals(a.ballY, b.ballY, 0.02f)
    }

    // ---------- simulasi panjang ----------

    /** Palet "pintar" (uji saja): menunggu bola turun lalu menaruh palet agar mendarat pada [aims] (bergantian) di lebarnya. */
    private fun botPlay(seconds: Int, aims: List<Float>): BreakoutState {
        var s = Breakout.newGame(0)
        var bounces = 0
        val reach = Breakout.PADDLE_W / 2f + r * 0.6f
        val dt = 1f / 60f
        repeat(seconds * 60) {
            if (s.phase == GamePhase.READY) s = Breakout.tap(s)
            if (s.phase == GamePhase.RUNNING && s.ballVy > 0f) {
                val t = (Breakout.PADDLE_TOP - r - s.ballY) / s.ballVy
                var px = s.ballX + s.ballVx * t
                // lipat pantulan dinding
                val span = Breakout.FIELD_W - 2 * r
                var u = (px - r) % (2 * span)
                if (u < 0) u += 2 * span
                px = r + if (u > span) 2 * span - u else u
                s = Breakout.movePaddle(s, (px - aims[bounces % aims.size] * reach) - s.paddleX)
            }
            val step = Breakout.advance(s, dt)
            s = step.state
            bounces += step.events.count { it == BreakoutEvent.PADDLE }
            if (s.phase == GamePhase.OVER) return s
        }
        return s
    }

    @Test
    fun aPerfectPlayerClearsWallsWithoutEverGettingStuck() {
        val s = botPlay(seconds = 300, aims = listOf(-0.7f, 0.5f, 0.0f, 0.8f, -0.4f, 0.3f))
        assertEquals("bot tidak pernah kehilangan nyawa", 3, s.lives)
        assertTrue("dinding pertama habis dalam 300 dtk (tingkat ${s.level}, sisa ${s.brickCount})", s.level >= 2)
    }

    @Test
    fun randomPlayAlwaysKeepsTheRulesIntact() {
        val rnd = Random(20261028)
        var s = Breakout.newGame(0)
        var minutes = 0f
        var frames = 0
        while (minutes < 600f) {
            val dt = 0.002f + rnd.nextFloat() * 0.06f
            minutes += dt
            frames++
            val before = s
            when (rnd.nextInt(40)) {
                0 -> s = Breakout.tap(s)
                1, 2, 3 -> s = Breakout.movePaddle(s, (rnd.nextFloat() - 0.5f) * 8f)
                4 -> s = Breakout.nudge(s, if (rnd.nextBoolean()) 1 else -1)
                else -> Unit
            }
            if (s.phase == GamePhase.OVER && rnd.nextInt(30) == 0) s = Breakout.tap(s)
            val step = Breakout.advance(s, dt)
            s = step.state
            val now = "kejadian ke-$frames"

            for (v in listOf(s.ballX, s.ballY, s.ballVx, s.ballVy, s.paddleX)) assertTrue("$now: angka tidak terhingga", v.isFinite())
            assertTrue("$now: palet di luar lapangan", s.paddleX >= Breakout.PADDLE_W / 2f - 1e-4f && s.paddleX <= Breakout.FIELD_W - Breakout.PADDLE_W / 2f + 1e-4f)
            assertTrue("$now: nyawa ${s.lives}", s.lives in 0..Breakout.START_LIVES)
            assertTrue("$now: bit bata di luar dinding", s.bricks ushr (Breakout.COLS * Breakout.MAX_ROWS) == 0L)
            if (s.phase == GamePhase.RUNNING) {
                assertTrue("$now: bola menembus dinding samping (${s.ballX})", s.ballX >= r - 1e-3f && s.ballX <= Breakout.FIELD_W - r + 1e-3f)
                assertTrue("$now: bola menembus langit-langit (${s.ballY})", s.ballY >= r - 1e-3f)
                assertEquals("$now: laju bola", Breakout.speed(s.level, s.hits), speedOf(s), 2e-3f)
                assertTrue("$now: bola terlalu mendatar", abs(s.ballVy) >= speedOf(s) * Breakout.MIN_VERTICAL - 2e-3f)
            }
            val restarted = before.phase == GamePhase.OVER && s.phase != GamePhase.OVER
            if (restarted) {
                assertEquals(Breakout.wall(1), s.bricks)
                assertEquals(0, s.score)
                assertEquals(Breakout.START_LIVES, s.lives)
                assertTrue("$now: rekor hilang saat main lagi", s.best >= before.best)
            } else if (s.level == before.level) {
                // dalam satu tingkat bata hanya berkurang; skor dan hits hanya naik
                assertEquals("$now: bata muncul kembali", 0L, s.bricks and before.bricks.inv())
                assertTrue("$now: skor turun", s.score >= before.score)
                assertTrue("$now: hits turun", s.hits >= before.hits)
            } else {
                assertEquals("$now: tingkat naik lebih dari satu", before.level + 1, s.level)
                assertEquals(Breakout.wall(s.level), s.bricks)
                assertEquals(GamePhase.READY, s.phase)
                assertTrue("$now: skor turun", s.score >= before.score + Breakout.LEVEL_BONUS)
            }
            if (before.phase == GamePhase.RUNNING && s.phase != GamePhase.RUNNING) {
                assertTrue("$now: peralihan fase ${before.phase}→${s.phase}", s.phase == GamePhase.READY || s.phase == GamePhase.OVER || s.phase == GamePhase.PAUSED)
            }
            if (s.phase == GamePhase.READY) {
                assertEquals("$now: bola tidak di atas palet", s.paddleX, s.ballX, 1e-4f)
            }
            assertTrue("$now: rekor kurang dari skor saat berakhir", s.phase != GamePhase.OVER || s.best >= s.score)
        }
        assertTrue("simulasi cukup panjang", frames > 5000)
    }
}
