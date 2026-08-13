package com.stanisryz.logica.puzzle.core.game2048

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import org.junit.Assert.assertEquals
import org.junit.Test

class Game2048DeterminismTest {
    private val puzzleId =
        Game2048PuzzleId(
            PuzzleSeed(-8_204_800_036L),
            Difficulty.EXPERT,
            Game2048GeneratorVersion.V2,
        )
    private val engine = Game2048Engine(puzzleId)

    @Test
    fun `traced and normal moves reproduce the same state spawn and invalid no op`() {
        var first = engine.start()
        var second = engine.start()
        assertEquals(first, second)
        assertEquals(listOf(2, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), first.board)

        repeat(24) {
            val direction = Game2048Direction.entries.first { engine.move(first, it) != first }
            first = engine.move(first, direction)
            val transition = engine.moveWithTrace(second, direction)
            val trace = requireNotNull(transition.trace)
            val spawn = requireNotNull(trace.spawnedTile)
            val beforeSpawn = Game2048Rules.move(second.board, direction).board
            assertEquals(0, beforeSpawn[spawn.destinationIndex])
            assertEquals(spawn.value, transition.state.board[spawn.destinationIndex])
            second = transition.state
            assertEquals(first, second)
        }

        val aligned =
            engine.restore(
                board = listOf(2, 0, 0, 0, 4, 0, 0, 0) + List(8) { 0 },
                score = 12L,
                nextSpawnIndex = 9L,
            )
        assertEquals(aligned, engine.move(aligned, Game2048Direction.LEFT))
        assertEquals(Game2048MoveTransition(aligned, null), engine.moveWithTrace(aligned, Game2048Direction.LEFT))
    }

}
