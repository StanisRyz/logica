package com.stanisryz.logica.ui.blocksudoku

import com.stanisryz.logica.puzzle.core.blocksudoku.BlockCell
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockPiece
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlockSudokuDropAnchorTest {
    @Test
    fun aFittingRoundedCellWins() {
        assertEquals(BlockCell(2, 5), blockSudokuDropAnchor(EMPTY, DOT, exactRow = 2.3f, exactColumn = 4.6f))
    }

    @Test
    fun aBlockedRoundedCellSnapsToTheNearestFittingOne() {
        val board = board(BlockCell(2, 5))

        // (2, 4) is 0.6 away in columns; (3, 5) is farther overall, (1, 5) and (2, 6) are out of reach.
        assertEquals(BlockCell(2, 4), blockSudokuDropAnchor(board, DOT, exactRow = 2.3f, exactColumn = 4.6f))
    }

    @Test
    fun aPieceOverhangingTheEdgeSnapsBackOntoTheBoard() {
        // Rounding puts the three-wide bar on columns 7..9, one past the edge; columns 6..8 are 0.6 away.
        assertEquals(BlockCell(0, 6), blockSudokuDropAnchor(EMPTY, BAR, exactRow = 0.1f, exactColumn = 6.6f))
    }

    @Test
    fun nothingFittingWithinReachLandsNowhere() {
        val crowded = board(*(3..5).flatMap { row -> (3..5).map { BlockCell(row, it) } }.toTypedArray())

        assertNull(blockSudokuDropAnchor(crowded, DOT, exactRow = 4f, exactColumn = 4f))
        // Off the board by more than the snap reach.
        assertNull(blockSudokuDropAnchor(EMPTY, DOT, exactRow = -1.6f, exactColumn = 0f))
    }

    private fun board(vararg filled: BlockCell): List<Boolean> =
        List(BlockSudokuRules.SIZE * BlockSudokuRules.SIZE) { index ->
            BlockCell(index / BlockSudokuRules.SIZE, index % BlockSudokuRules.SIZE) in filled
        }

    private companion object {
        val EMPTY = List(BlockSudokuRules.SIZE * BlockSudokuRules.SIZE) { false }
        val DOT = BlockPiece(listOf(BlockCell(0, 0)))
        val BAR = BlockPiece(listOf(BlockCell(0, 0), BlockCell(0, 1), BlockCell(0, 2)))
    }
}
