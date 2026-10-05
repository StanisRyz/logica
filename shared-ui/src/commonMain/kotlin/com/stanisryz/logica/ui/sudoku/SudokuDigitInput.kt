package com.stanisryz.logica.ui.sudoku

import com.stanisryz.logica.puzzle.core.sudoku.SudokuCellStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameState
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPosition

/**
 * The one Sudoku input rule both hosts follow, cell-first and digit-first alike. The active digit is
 * transient host state beside the selected cell; this only says what a tap means.
 */
object SudokuDigitInput {
    sealed interface Action {
        /** Enter (or, in Pencil, toggle) the digit in this cell through the ordinary engine path. */
        data class Enter(
            val position: SudokuPosition,
            val digit: Int,
        ) : Action

        /** Make this digit the active one (null: no active digit, back to cell-first). */
        data class Activate(
            val digit: Int?,
        ) : Action

        /** Select this cell, cell-first. */
        data class Select(
            val position: SudokuPosition,
        ) : Action

        data object None : Action
    }

    /** A digit from the pad (or the Web keyboard). */
    fun onDigit(
        game: SudokuGameState,
        selectedCell: SudokuPosition?,
        activeDigit: Int?,
        digit: Int,
    ): Action =
        when {
            activeDigit == digit -> Action.Activate(null)
            activeDigit == null && selectedCell != null && game.cellAt(selectedCell).takesDigit -> Action.Enter(selectedCell, digit)
            isComplete(game, digit) -> Action.None
            else -> Action.Activate(digit)
        }

    /** A tap on the board: with an active digit it fills the cell, or takes the digit of a fixed one. */
    fun onCellTap(
        game: SudokuGameState,
        activeDigit: Int?,
        position: SudokuPosition,
    ): Action {
        if (activeDigit == null) return Action.Select(position)
        val cell = game.cellAt(position)
        return when {
            cell.takesDigit -> Action.Enter(position, activeDigit)
            isComplete(game, cell.value) -> Action.None
            else -> Action.Activate(cell.value)
        }
    }

    /** The active digit after a move: it goes away once all nine of it are confirmed. */
    fun afterMove(
        game: SudokuGameState,
        activeDigit: Int?,
    ): Int? = activeDigit?.takeUnless { isComplete(game, it) }

    private fun isComplete(
        game: SudokuGameState,
        digit: Int,
    ): Boolean =
        game.cells.count { (it.status == SudokuCellStatus.GIVEN || it.status == SudokuCellStatus.CORRECT) && it.value == digit } >=
            DIGIT_INSTANCES

    private val com.stanisryz.logica.puzzle.core.sudoku.SudokuCellState.takesDigit: Boolean
        get() = status == SudokuCellStatus.EMPTY || status == SudokuCellStatus.INCORRECT

    private const val DIGIT_INSTANCES = 9
}
