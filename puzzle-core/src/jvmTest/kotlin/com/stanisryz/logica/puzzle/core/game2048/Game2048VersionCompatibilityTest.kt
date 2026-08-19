package com.stanisryz.logica.puzzle.core.game2048

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V1 is a frozen compatibility contract, not a previous draft of V2. The two versions share the
 * board, the merge, the score, and the deterministic spawn; only the win condition differs.
 */
class Game2048VersionCompatibilityTest {
    @Test
    fun `common generator version resolver accepts v1 and v2 only`() {
        assertEquals(Game2048GeneratorVersion.V1, GeneratorVersion(1).toGame2048GeneratorVersion())
        assertEquals(Game2048GeneratorVersion.V2, GeneratorVersion(2).toGame2048GeneratorVersion())
        assertThrows(IllegalArgumentException::class.java) {
            GeneratorVersion(3).toGame2048GeneratorVersion()
        }
    }

    @Test
    fun `v1 keeps its target tiles and immediate victory`() {
        assertEquals(256, Game2048RulesetV1(Difficulty.EASY).targetTile)
        assertEquals(512, Game2048RulesetV1(Difficulty.MEDIUM).targetTile)
        assertEquals(1024, Game2048RulesetV1(Difficulty.HARD).targetTile)
        assertEquals(2048, Game2048RulesetV1(Difficulty.EXPERT).targetTile)
        assertNull(Game2048RulesetV1(Difficulty.EASY).targetScore)

        val engine = Game2048Engine(Game2048PuzzleId(SEED, Difficulty.EASY, Game2048GeneratorVersion.V1))
        val before = engine.restore(listOf(128, 128, 0, 0) + List(12) { 0 }, score = 100L, nextSpawnIndex = 12L)
        val solved = engine.move(before, Game2048Direction.LEFT)

        assertEquals(Game2048Status.SOLVED, solved.status)
        // The winning move ends the attempt before it consumes a spawn sample.
        assertEquals(1, solved.board.count { it != 0 })
        assertEquals(12L, solved.nextSpawnIndex)
    }

    @Test
    fun `v2 uses score targets`() {
        assertEquals(12_000L, Game2048RulesetV2(Difficulty.EASY).targetScore)
        assertEquals(30_000L, Game2048RulesetV2(Difficulty.MEDIUM).targetScore)
        assertEquals(100_000L, Game2048RulesetV2(Difficulty.HARD).targetScore)
        assertEquals(250_000L, Game2048RulesetV2(Difficulty.EXPERT).targetScore)
        assertNull(Game2048RulesetV2(Difficulty.EASY).targetTile)
    }

    /** Same seed, same tiles, all the way down: the versions differ only in when they end. */
    @Test
    fun `both versions spawn identically on one seed while judging the board differently`() {
        val v1 = Game2048Engine(Game2048PuzzleId(SEED, Difficulty.EASY, Game2048GeneratorVersion.V1))
        val v2 = Game2048Engine(Game2048PuzzleId(SEED, Difficulty.EASY, Game2048GeneratorVersion.V2))
        var first = v1.start()
        var second = v2.start()
        assertEquals(first.board, second.board)

        repeat(20) {
            if (first.status.isTerminal || second.status.isTerminal) return@repeat
            val direction = Game2048Direction.entries.first { v1.move(first, it) != first }
            first = v1.move(first, direction)
            second = v2.move(second, direction)
            assertEquals(first.board, second.board)
            assertEquals(first.score, second.score)
            assertEquals(first.nextSpawnIndex, second.nextSpawnIndex)
        }

        // The very same live board is a V1 victory and an unfinished V2 game.
        val reached = listOf(256, 4, 2, 4) + List(12) { 0 }
        assertEquals(Game2048Status.SOLVED, v1.restore(reached, 5_000L, 40L).status)
        assertEquals(Game2048Status.IN_PROGRESS, v2.restore(reached, 5_000L, 40L).status)
        assertTrue(v1.restore(reached, 5_000L, 40L).goalReached)
        assertFalse(v2.restore(reached, 5_000L, 40L).goalReached)
    }

    private companion object {
        val SEED = PuzzleSeed(0x2048_0038)
    }
}
