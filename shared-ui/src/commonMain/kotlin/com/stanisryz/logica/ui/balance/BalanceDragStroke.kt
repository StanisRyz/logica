package com.stanisryz.logica.ui.balance

import com.stanisryz.logica.puzzle.core.balance.BalanceCell
import com.stanisryz.logica.puzzle.core.balance.BalanceGameState
import com.stanisryz.logica.puzzle.core.balance.BalancePosition

/**
 * One drag stroke over the Balance board, kept by the host for the length of the gesture. Each cell
 * it accepts goes through the host's ordinary tap path. The first cell sets the mode: if it already
 * holds the selected value the stroke removes that value from the editable cells it passes,
 * otherwise it places the value in the empty editable ones, skipping cells holding the other value.
 * The first mistake (or the end of the attempt) ends the stroke, so one stroke costs at most one
 * mistake. In Pencil the stroke is a tap on its first cell.
 */
class BalanceDragStroke(
    game: BalanceGameState,
    private val value: BalanceCell,
    private val pencil: Boolean,
    first: BalancePosition,
) {
    private val removing = game.board.cellAt(first) == value
    private var applied = 0
    private var stopped = false

    fun accepts(
        game: BalanceGameState,
        position: BalancePosition,
    ): Boolean {
        if (stopped || game.status.isTerminal) return false
        if (pencil) return applied == 0
        if (game.isLocked(position)) return false
        val current = game.board.cellAt(position)
        return if (removing) current == value else current == BalanceCell.EMPTY
    }

    /** Called after the host applied an accepted cell. */
    fun applied(
        before: BalanceGameState,
        after: BalanceGameState,
    ) {
        applied++
        if (after.mistakesUsed > before.mistakesUsed || after.status.isTerminal) stopped = true
    }
}
