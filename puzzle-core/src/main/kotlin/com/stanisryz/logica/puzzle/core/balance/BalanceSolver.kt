package com.stanisryz.logica.puzzle.core.balance

import com.stanisryz.logica.puzzle.core.contract.PuzzleSolver
import com.stanisryz.logica.puzzle.core.contract.ValidationResult

data class BalanceSolveAnalysis(
    val logicalSteps: Int,
    val branchDecisions: Int,
    val maximumSearchDepth: Int,
    val techniqueCounts: BalanceTechniqueCounts,
)

data class BalanceTechniqueCounts(
    val preventThree: Int,
    val completeQuota: Int,
    val preserveUniqueness: Int,
)

data class BalanceSolveResult(
    val solution: BalanceSolution?,
    val analysis: BalanceSolveAnalysis,
)

class BalanceSolver(
    private val logicEngine: BalanceLogicEngine = BalanceLogicEngine(),
) : PuzzleSolver<BalancePuzzle, BalanceSolution> {
    override fun solve(puzzle: BalancePuzzle): BalanceSolution? = solveWithAnalysis(puzzle).solution

    fun solveWithAnalysis(puzzle: BalancePuzzle): BalanceSolveResult {
        val outcome = search(puzzle, limit = 1)
        return BalanceSolveResult(
            solution = outcome.firstSolution?.let { BalanceSolution.fromValidated(puzzle, it) },
            analysis = outcome.metrics.toAnalysis(),
        )
    }

    override fun countSolutions(
        puzzle: BalancePuzzle,
        limit: Int,
    ): Int {
        require(limit > 0) { "Solution count limit must be positive." }
        return search(puzzle, limit).solutionCount
    }

    private fun search(
        puzzle: BalancePuzzle,
        limit: Int,
    ): SearchOutcome {
        require(limit > 0) { "Solution count limit must be positive." }
        val outcome = SearchOutcome(limit)
        searchState(
            puzzle = puzzle,
            initialState = BalanceState.fromPuzzle(puzzle),
            depth = 0,
            outcome = outcome,
        )
        return outcome
    }

    private fun searchState(
        puzzle: BalancePuzzle,
        initialState: BalanceState,
        depth: Int,
        outcome: SearchOutcome,
    ) {
        if (outcome.reachedLimit) return
        outcome.metrics.maximumSearchDepth = maxOf(outcome.metrics.maximumSearchDepth, depth)

        var state = initialState
        while (true) {
            when (BalanceRules.validate(puzzle, state)) {
                is ValidationResult.Invalid -> return
                ValidationResult.ValidComplete -> {
                    outcome.record(state)
                    return
                }
                ValidationResult.ValidPartial -> Unit
            }

            val step = logicEngine.nextStep(puzzle, state) ?: break
            state = step.applyTo(state)
            outcome.metrics.record(step.technique)
        }

        val choice = selectCell(puzzle, state) ?: return
        if (choice.candidates.size > 1) outcome.metrics.branchDecisions++
        val nextDepth = if (choice.candidates.size > 1) depth + 1 else depth

        choice.candidates.forEach { candidate ->
            searchState(
                puzzle = puzzle,
                initialState = state.withCell(choice.position, candidate),
                depth = nextDepth,
                outcome = outcome,
            )
            if (outcome.reachedLimit) return
        }
    }

    private fun selectCell(
        puzzle: BalancePuzzle,
        state: BalanceState,
    ): CellChoice? {
        var bestChoice: CellChoice? = null
        for (row in 0 until state.size) {
            for (column in 0 until state.size) {
                val position = BalancePosition(row, column)
                if (state.cellAt(position) != BalanceCell.EMPTY) continue
                val choice = CellChoice(position, BalanceRules.validCandidates(puzzle, state, position))
                if (choice.candidates.isEmpty()) return choice
                if (bestChoice == null || choice.candidates.size < bestChoice.candidates.size) {
                    bestChoice = choice
                }
                if (choice.candidates.size == 1) return choice
            }
        }
        return bestChoice
    }

    private data class CellChoice(
        val position: BalancePosition,
        val candidates: List<BalanceCell>,
    )

    private class SearchOutcome(
        private val limit: Int,
    ) {
        var solutionCount: Int = 0
            private set
        var firstSolution: BalanceState? = null
            private set
        val metrics = MutableMetrics()
        val reachedLimit: Boolean
            get() = solutionCount >= limit

        fun record(solution: BalanceState) {
            if (firstSolution == null) firstSolution = solution
            solutionCount++
        }
    }

    private data class MutableMetrics(
        var logicalSteps: Int = 0,
        var branchDecisions: Int = 0,
        var maximumSearchDepth: Int = 0,
        var preventThree: Int = 0,
        var completeQuota: Int = 0,
        var preserveUniqueness: Int = 0,
    ) {
        fun record(technique: BalanceLogicTechnique) {
            logicalSteps++
            when (technique) {
                BalanceLogicTechnique.PREVENT_THREE -> preventThree++
                BalanceLogicTechnique.COMPLETE_QUOTA -> completeQuota++
                BalanceLogicTechnique.PRESERVE_UNIQUENESS -> preserveUniqueness++
            }
        }

        fun toAnalysis() =
            BalanceSolveAnalysis(
                logicalSteps = logicalSteps,
                branchDecisions = branchDecisions,
                maximumSearchDepth = maximumSearchDepth,
                techniqueCounts =
                    BalanceTechniqueCounts(
                        preventThree = preventThree,
                        completeQuota = completeQuota,
                        preserveUniqueness = preserveUniqueness,
                    ),
            )
    }
}
