package com.stanisryz.logica.puzzle.core.balance

import com.stanisryz.logica.puzzle.core.contract.PuzzleDefinition
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleType

class BalancePuzzle(
    override val id: PuzzleId,
    val size: Int,
    fixedClues: Iterable<BalanceClue>,
) : PuzzleDefinition {
    val fixedClues: Map<BalancePosition, BalanceCell>

    init {
        require(id.type == PuzzleType.BALANCE) { "Balance puzzle ID must have BALANCE type." }
        BalanceBoardConstraints.requireValidSize(size)

        val cluesByPosition = mutableMapOf<BalancePosition, BalanceCell>()
        fixedClues.forEach { clue ->
            BalanceBoardConstraints.requireInside(size, clue.position)
            val previous = cluesByPosition.put(clue.position, clue.value)
            require(previous == null || previous == clue.value) {
                "Contradictory clues at ${clue.position}."
            }
        }
        this.fixedClues = cluesByPosition.toMap()
    }
}
