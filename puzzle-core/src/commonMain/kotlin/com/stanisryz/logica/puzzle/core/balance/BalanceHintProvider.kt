package com.stanisryz.logica.puzzle.core.balance

import com.stanisryz.logica.puzzle.core.contract.PuzzleHintProvider

class BalanceHintProvider(
    private val logicEngine: BalanceLogicEngine = BalanceLogicEngine(),
    private val solver: BalanceSolver = BalanceSolver(logicEngine),
) : PuzzleHintProvider<BalancePuzzle, BalanceState, BalanceHint> {
    override fun hint(
        puzzle: BalancePuzzle,
        state: BalanceState,
    ): BalanceHint? {
        if (state.size != puzzle.size) return null

        findIncorrectValue(puzzle, state)?.let { return it }
        val step = logicEngine.nextStep(puzzle, state) ?: return null
        return BalanceHint(
            kind = BalanceHintKind.LOGICAL_DEDUCTION,
            position = step.position,
            suggestedValue = step.value,
            technique = step.technique,
        )
    }

    private fun findIncorrectValue(
        puzzle: BalancePuzzle,
        state: BalanceState,
    ): BalanceHint? {
        if (solver.countSolutions(puzzle, limit = 2) != 1) return null
        val solution = solver.solve(puzzle) ?: return null

        for (row in 0 until puzzle.size) {
            for (column in 0 until puzzle.size) {
                val position = BalancePosition(row, column)
                val currentValue = state.cellAt(position)
                if (
                    position !in puzzle.fixedClues &&
                    currentValue != BalanceCell.EMPTY &&
                    currentValue != solution.cellAt(position)
                ) {
                    return BalanceHint(
                        kind = BalanceHintKind.INCORRECT_VALUE,
                        position = position,
                        suggestedValue = solution.cellAt(position),
                        technique = null,
                    )
                }
            }
        }
        return null
    }
}
