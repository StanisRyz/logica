package com.stanisryz.logica.puzzle.core.game2048

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V2 never ends on the score. It ends on the last legal move, and only then is the score compared
 * with the difficulty target to decide victory or defeat.
 */
class Game2048V2LifecycleTest {
    private val puzzleId = Game2048PuzzleId(SEED, Difficulty.MEDIUM, Game2048GeneratorVersion.V2)
    private val engine = Game2048Engine(puzzleId)
    private val target = requireNotNull(puzzleId.rules.targetScore)

    @Test
    fun `crossing the score target keeps the game in progress`() {
        val playable = engine.restore(listOf(2, 4, 8, 16) + List(12) { 0 }, score = target + 1_000L, nextSpawnIndex = 30L)

        assertEquals(30_000L, target)
        assertTrue(playable.goalReached)
        assertEquals(Game2048Status.IN_PROGRESS, playable.status)

        // Playing on past the goal is ordinary gameplay: one spawn, one index, still unfinished.
        val next = engine.move(playable, Game2048Direction.DOWN)
        assertEquals(Game2048Status.IN_PROGRESS, next.status)
        assertTrue(next.goalReached)
        assertEquals(31L, next.nextSpawnIndex)
    }

    /** An invalid swipe is a complete no-op, goal reached or not. */
    @Test
    fun `an invalid swipe changes nothing`() {
        val state = engine.restore(listOf(2, 4, 8, 16) + List(12) { 0 }, score = target, nextSpawnIndex = 30L)

        assertEquals(state, engine.move(state, Game2048Direction.UP))
    }

    @Test
    fun `the final move decides the outcome by comparing the score with the target`() {
        // The one spawn this move consumes must be a 4 for the resulting board to be dead.
        val spawnIndex = (2L..MAX_SEARCHED_SPAWN).first { Game2048SpawnV1.samples(SEED.value, it).tileValue == 4 }

        val solved = engine.move(engine.restore(ALMOST_FULL, target, spawnIndex), Game2048Direction.LEFT)
        assertEquals(DEAD_BOARD, solved.board)
        assertEquals(Game2048Status.SOLVED, solved.status)
        // Exactly one tile was spawned by the move that ended the game.
        assertEquals(spawnIndex + 1L, solved.nextSpawnIndex)
        assertEquals(target, solved.score)

        val failed = engine.move(engine.restore(ALMOST_FULL, target - 1L, spawnIndex), Game2048Direction.LEFT)
        assertEquals(DEAD_BOARD, failed.board)
        assertEquals(Game2048Status.FAILED, failed.status)
        assertFalse(failed.goalReached)

        // Both endings are read-only, and a retry is a fresh attempt on the same V2 identity.
        assertEquals(solved, engine.move(solved, Game2048Direction.RIGHT))
        assertEquals(engine.start(), engine.retry(failed))
        assertEquals(puzzleId, engine.retry(failed).puzzleId)
    }

    /** Interrupting a V2 game must not change which tile appears next. */
    @Test
    fun `save and restore preserve the exact future spawn`() {
        var uninterrupted = engine.start()
        repeat(6) {
            uninterrupted = engine.move(uninterrupted, validDirection(uninterrupted))
        }
        val restored = Game2048SessionCodecV1.decode(Game2048SessionCodecV1.encode(uninterrupted))
        assertEquals(uninterrupted, restored)

        val direction = validDirection(uninterrupted)
        assertEquals(engine.move(uninterrupted, direction), engine.move(restored, direction))
    }

    private fun validDirection(state: Game2048State): Game2048Direction =
        Game2048Direction.entries.first { engine.move(state, it) != state }

    private companion object {
        val SEED = PuzzleSeed(0x2048_0038)

        /** One empty cell; sliding left moves it to the last column without merging anything. */
        val ALMOST_FULL = listOf(0, 2, 4, 2, 4, 2, 4, 2, 2, 4, 2, 4, 4, 2, 4, 2)

        /** The same tiles after the slide, with the spawned 4 completing a board of unequal neighbours. */
        val DEAD_BOARD = listOf(2, 4, 2, 4, 4, 2, 4, 2, 2, 4, 2, 4, 4, 2, 4, 2)

        const val MAX_SEARCHED_SPAWN = 500L
    }
}
