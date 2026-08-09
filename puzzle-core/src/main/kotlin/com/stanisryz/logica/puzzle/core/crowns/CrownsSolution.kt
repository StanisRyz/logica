package com.stanisryz.logica.puzzle.core.crowns

import com.stanisryz.logica.puzzle.core.contract.PuzzleSolution
import com.stanisryz.logica.puzzle.core.contract.ValidationResult
import com.stanisryz.logica.puzzle.core.model.PuzzleId

class CrownsSolution private constructor(
    val puzzleId: PuzzleId,
    private val state: CrownsState,
) : PuzzleSolution {
    val crowns: Set<CrownsPosition>
        get() = state.crowns

    fun asState(): CrownsState = state

    override fun equals(other: Any?): Boolean =
        this === other || other is CrownsSolution && puzzleId == other.puzzleId && state == other.state

    override fun hashCode(): Int = 31 * puzzleId.hashCode() + state.hashCode()

    override fun toString(): String = "CrownsSolution(puzzleId=$puzzleId, crowns=$crowns)"

    internal companion object {
        fun fromValidated(
            puzzle: CrownsPuzzle,
            state: CrownsState,
        ): CrownsSolution {
            require(CrownsValidator().validate(puzzle, state) == ValidationResult.ValidComplete) {
                "A Crowns solution must be complete and valid."
            }
            return CrownsSolution(puzzle.id, state)
        }
    }
}
