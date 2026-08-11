package com.stanisryz.logica.puzzle.core.game2048

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Game2048MergeAndMoveTest {
    @Test
    fun `canonical line merge compacts scores and never double merges`() {
        assertMerge(listOf(2, 2, 0, 0), listOf(4, 0, 0, 0), 4L)
        assertMerge(listOf(2, 2, 2, 0), listOf(4, 2, 0, 0), 4L)
        assertMerge(listOf(2, 2, 2, 2), listOf(4, 4, 0, 0), 8L)
        assertMerge(listOf(2, 2, 4, 4), listOf(4, 8, 0, 0), 12L)
        assertMerge(listOf(4, 0, 4, 4), listOf(8, 4, 0, 0), 8L)
        assertMerge(listOf(4, 4, 8, 8), listOf(8, 16, 0, 0), 24L)
    }

    @Test
    fun `complex merge trace identifies both source pairs and destinations`() {
        val result = Game2048Rules.moveWithTrace(board(row(2, 2, 2, 2)), Game2048Direction.LEFT)
        val trace = requireNotNull(result.trace)

        assertEquals(row(4, 4, 0, 0), result.board.take(4))
        assertEquals(Game2048Direction.LEFT, trace.direction)
        assertEquals(8L, trace.scoreGained)
        assertEquals(
            listOf(
                Game2048TileMovement(0, 0, 2),
                Game2048TileMovement(1, 0, 2),
                Game2048TileMovement(2, 1, 2),
                Game2048TileMovement(3, 1, 2),
            ),
            trace.movements,
        )
        assertEquals(
            listOf(
                Game2048MergeTrace(0, 1, 0, 2, 4),
                Game2048MergeTrace(2, 3, 1, 2, 4),
            ),
            trace.merges,
        )
        assertNull(trace.spawnedTile)
    }

    @Test
    fun `all directions share the same oriented line behavior`() {
        val horizontal = board(row(2, 0, 2, 2))
        assertEquals(row(4, 2, 0, 0), Game2048Rules.move(horizontal, Game2048Direction.LEFT).board.take(4))
        assertEquals(row(0, 0, 2, 4), Game2048Rules.move(horizontal, Game2048Direction.RIGHT).board.take(4))

        val vertical = listOf(2, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 2, 0, 0, 0)
        val up = Game2048Rules.move(vertical, Game2048Direction.UP)
        val down = Game2048Rules.move(vertical, Game2048Direction.DOWN)
        assertEquals(listOf(4, 2, 0, 0), List(4) { row -> up.board[row * 4] })
        assertEquals(listOf(0, 0, 2, 4), List(4) { row -> down.board[row * 4] })
        assertEquals(4L, up.scoreGained)
        assertEquals(4L, down.scoreGained)
    }

    private fun assertMerge(
        input: List<Int>,
        expected: List<Int>,
        score: Long,
    ) {
        val result = Game2048Rules.mergeLine(input)
        assertEquals(expected, result.values)
        assertEquals(score, result.scoreGained)
    }

    private fun row(vararg values: Int): List<Int> = values.toList()

    private fun board(firstRow: List<Int>): List<Int> = firstRow + List(12) { 0 }
}
