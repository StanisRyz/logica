package com.stanisryz.logica.puzzle.core

import com.stanisryz.logica.puzzle.core.blocksudoku.BlockCell
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockPiece
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuEngine
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuRules
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuStatus
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlockSudokuTest {
    private val engine = BlockSudokuEngine(PuzzleSeed(42L), Difficulty.EASY)

    @Test
    fun theSameSeedDealsTheSamePiecesWhateverIsPlaced() {
        assertEquals(engine.start(), BlockSudokuEngine(PuzzleSeed(42L), Difficulty.EASY).start())
        assertEquals(3, engine.start().tray.size)
        assertTrue(engine.start().tray.all { it != null })
    }

    @Test
    fun aFullRowClearsAndScoresTwiceItsCellsTimesTheUnits() {
        val row = List(BlockSudokuRules.SIZE * BlockSudokuRules.SIZE) { it in 0 until 8 }
        val dot = BlockPiece(listOf(BlockCell(0, 0)))
        val state = engine.start().copy(board = row, tray = listOf(dot, null, dot))
        val placed = engine.place(state, 0, 0, 8)
        assertEquals(1 + 9 * 2 * 1, placed.score)
        assertTrue(placed.board.none { it })
        assertEquals(9, placed.lastCleared.size)
    }

    @Test
    fun anIllegalPlacementChangesNothingAndTheTargetClearsTheLevel() {
        val start = engine.start()
        val piece = start.tray.first()!!
        assertEquals(start, engine.place(start, 0, BlockSudokuRules.SIZE, 0))
        val nearTarget = start.copy(score = start.targetScore - 1)
        assertEquals(BlockSudokuStatus.SOLVED, engine.place(nearTarget, 0, 0, 0).status)
        assertTrue(piece.size >= 1)
    }

    @Test
    fun aTrayThatFitsNowhereFailsTheLevel() {
        val almostFull = List(BlockSudokuRules.SIZE * BlockSudokuRules.SIZE) { it % 2 == 0 && it != 80 }
        val bar = BlockPiece(listOf(BlockCell(0, 0), BlockCell(0, 1), BlockCell(0, 2), BlockCell(0, 3), BlockCell(0, 4)))
        val dot = BlockPiece(listOf(BlockCell(0, 0)))
        val state = engine.start().copy(board = almostFull.toMutableList().also { it[80] = false }, tray = listOf(dot, bar, null))
        val placed = engine.place(state, 0, 8, 8)
        assertEquals(BlockSudokuStatus.FAILED, placed.status)
    }
}
