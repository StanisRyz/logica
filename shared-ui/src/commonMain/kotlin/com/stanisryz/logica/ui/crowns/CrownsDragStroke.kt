package com.stanisryz.logica.ui.crowns

import com.stanisryz.logica.puzzle.core.crowns.CrownsGameState
import com.stanisryz.logica.puzzle.core.crowns.CrownsPlayerCell
import com.stanisryz.logica.puzzle.core.crowns.CrownsPosition

/**
 * One drag stroke over the Crowns board, kept by the host for the length of the gesture. Each cell
 * it accepts goes through the host's ordinary tap path. Only the X note is drawn: if the first cell
 * holds an X the stroke removes the X from the cells it passes, otherwise it puts one on the empty
 * cells, never over a crown. With the crown tool, or in Pencil, the stroke is a tap on its first
 * cell: a row holds one crown.
 */
class CrownsDragStroke(
    game: CrownsGameState,
    private val value: CrownsPlayerCell,
    private val pencil: Boolean,
    first: CrownsPosition,
) {
    private val removing = game.cellAt(first) == CrownsPlayerCell.MARKED
    private var applied = 0
    private var stopped = false

    fun accepts(
        game: CrownsGameState,
        position: CrownsPosition,
    ): Boolean {
        if (stopped || game.status.isTerminal) return false
        if (pencil || value != CrownsPlayerCell.MARKED) return applied == 0
        if (game.isLocked(position)) return false
        val current = game.cellAt(position)
        return if (removing) current == CrownsPlayerCell.MARKED else current == CrownsPlayerCell.EMPTY
    }

    /** Called after the host applied an accepted cell. */
    fun applied(
        before: CrownsGameState,
        after: CrownsGameState,
    ) {
        applied++
        if (after.status.isTerminal || after.mistakesUsed > before.mistakesUsed) stopped = true
    }
}
