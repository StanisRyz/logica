package com.stanisryz.logica.puzzle.core.crowns

import com.stanisryz.logica.puzzle.core.contract.PuzzleHintProvider

class CrownsHintProvider(
    private val logicEngine: CrownsLogicEngine = CrownsLogicEngine(),
    private val solver: CrownsSolver = CrownsSolver(logicEngine),
) : PuzzleHintProvider<CrownsPuzzle, CrownsState, CrownsHint> {
    override fun hint(
        puzzle: CrownsPuzzle,
        state: CrownsState,
    ): CrownsHint? {
        requirePositionsInside(puzzle, state.crowns)

        uniqueSolution(puzzle)?.let { solution ->
            findIncorrectCrown(puzzle, state, solution)?.let { return it }
        }

        val step = logicEngine.nextStep(puzzle, CrownsCandidateState.from(puzzle, state)) ?: return null
        return step.toHint()
    }

    private fun uniqueSolution(puzzle: CrownsPuzzle): CrownsSolution? {
        if (solver.countSolutions(puzzle, limit = 2) != 1) return null
        return solver.solve(puzzle)
    }

    private fun findIncorrectCrown(
        puzzle: CrownsPuzzle,
        state: CrownsState,
        solution: CrownsSolution,
    ): CrownsHint? {
        val position = orderedPositions(puzzle).firstOrNull { it in state.crowns && it !in solution.crowns } ?: return null
        val conflicts =
            CrownsRules
                .analyze(puzzle, state)
                .violations
                .filter { position in it.affectedPositions }
                .flatMap(CrownsViolation::affectedPositions)
        return CrownsHint(
            kind = CrownsHintKind.INCORRECT_CROWN,
            action = CrownsHintAction.CLEAR_CROWN,
            targetPositions = listOf(position),
            conflictPositions = conflicts,
        )
    }

    private fun CrownsLogicStep.toHint(): CrownsHint =
        CrownsHint(
            kind = CrownsHintKind.LOGICAL_DEDUCTION,
            action =
                when (this) {
                    is CrownsLogicStep.PlaceCrown -> CrownsHintAction.PLACE_CROWN
                    is CrownsLogicStep.ExcludePositions -> CrownsHintAction.MARK_POSITIONS
                },
            targetPositions = targetPositions,
            evidencePositions = evidencePositions,
            technique = technique,
        )

    private fun requirePositionsInside(
        puzzle: CrownsPuzzle,
        positions: Iterable<CrownsPosition>,
    ) {
        positions.forEach { CrownsBoardConstraints.requireInside(puzzle.size, it) }
    }
}
