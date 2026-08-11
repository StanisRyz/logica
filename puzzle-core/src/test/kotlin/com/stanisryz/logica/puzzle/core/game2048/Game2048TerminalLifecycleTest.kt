package com.stanisryz.logica.puzzle.core.game2048

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import org.junit.Assert.assertEquals
import org.junit.Test

class Game2048TerminalLifecycleTest {
    private val puzzleId = Game2048PuzzleId(PuzzleSeed(36L), Difficulty.EASY)
    private val engine = Game2048Engine(puzzleId)

    @Test
    fun `target merge solves before spawning and terminal state is read only`() {
        val before =
            engine.restore(
                board = listOf(128, 128, 0, 0) + List(12) { 0 },
                score = 100L,
                nextSpawnIndex = 12L,
            )
        val solved = engine.move(before, Game2048Direction.LEFT)

        assertEquals(Game2048Status.SOLVED, solved.status)
        assertEquals(256, solved.board[0])
        assertEquals(1, solved.board.count { it != 0 })
        assertEquals(356L, solved.score)
        assertEquals(12L, solved.nextSpawnIndex)
        assertEquals(solved, engine.move(solved, Game2048Direction.DOWN))
    }

    @Test
    fun `no legal move fails and retry restores the identical initial puzzle`() {
        val fullBoard = listOf(2, 4, 2, 4, 4, 2, 4, 2, 2, 4, 2, 4, 4, 2, 4, 2)
        val failed = engine.restore(fullBoard, score = 88L, nextSpawnIndex = 20L)

        assertEquals(Game2048Status.FAILED, failed.status)
        assertEquals(failed, engine.move(failed, Game2048Direction.LEFT))
        assertEquals(engine.start(), engine.retry(failed))
    }
}
