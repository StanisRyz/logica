package com.stanisryz.logica.puzzle.core.crowns

import com.stanisryz.logica.puzzle.core.contract.PuzzleHintProvider

class CrownsHintProvider(
    private val logicEngine: CrownsLogicEngine = CrownsLogicEngine(),
    private val solver: CrownsSolver = CrownsSolver(logicEngine),
) : PuzzleHintProvider<CrownsPuzzle, CrownsState, CrownsHint> {
    override fun hint(
        puzzle: CrownsPuzzle,
        state: CrownsState,
    ): CrownsHint? = hint(puzzle, state, emptySet())

    fun hint(
        puzzle: CrownsPuzzle,
        state: CrownsState,
        userMarks: Set<CrownsPosition>,
    ): CrownsHint? {
        requirePositionsInside(puzzle, state.crowns)
        requirePositionsInside(puzzle, userMarks)
        require(state.crowns.intersect(userMarks).isEmpty()) { "A cell cannot contain both a crown and a user mark." }

        val uniqueSolution = uniqueSolution(puzzle)
        if (uniqueSolution != null) {
            findIncorrectCrown(puzzle, state, uniqueSolution)?.let { return it }
            findIncorrectMark(userMarks, uniqueSolution)?.let { return it }
        }

        var candidates = CrownsCandidateState.from(puzzle, state)
        if (uniqueSolution != null) {
            val applicableMarks = userMarks.filter { it in candidates.allowed }
            if (applicableMarks.isNotEmpty()) {
                candidates = candidates.exclude(applicableMarks).state
            }
        }
        val step = logicEngine.nextStep(puzzle, candidates) ?: return null
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

    private fun findIncorrectMark(
        userMarks: Set<CrownsPosition>,
        solution: CrownsSolution,
    ): CrownsHint? {
        val position = solution.crowns.sortedWith(POSITION_ORDER).firstOrNull { it in userMarks } ?: return null
        return CrownsHint(
            kind = CrownsHintKind.INCORRECT_MARK,
            action = CrownsHintAction.CLEAR_MARK,
            targetPositions = listOf(position),
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

    private companion object {
        val POSITION_ORDER = compareBy(CrownsPosition::row, CrownsPosition::column)
    }
}
