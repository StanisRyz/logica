package com.stanisryz.logica.puzzle.core.balance

import com.stanisryz.logica.puzzle.core.contract.PuzzleSolution
import com.stanisryz.logica.puzzle.core.contract.ValidationResult
import com.stanisryz.logica.puzzle.core.model.PuzzleId

class BalanceSolution private constructor(
    val puzzleId: PuzzleId,
    private val state: BalanceState,
) : PuzzleSolution {
    val size: Int
        get() = state.size

    fun cellAt(position: BalancePosition): BalanceCell = state.cellAt(position)

    fun asState(): BalanceState = state

    override fun equals(other: Any?): Boolean =
        this === other || other is BalanceSolution && puzzleId == other.puzzleId && state == other.state

    override fun hashCode(): Int = 31 * puzzleId.hashCode() + state.hashCode()

    override fun toString(): String = state.toString()

    internal companion object {
        fun fromValidated(
            puzzle: BalancePuzzle,
            state: BalanceState,
        ): BalanceSolution {
            require(BalanceRules.validate(puzzle, state) == ValidationResult.ValidComplete) {
                "A Balance solution must be complete and valid."
            }
            return BalanceSolution(puzzle.id, state)
        }
    }
}
