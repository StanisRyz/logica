package com.stanisryz.logica.puzzle.core.crowns

import com.stanisryz.logica.puzzle.core.contract.PuzzleSolver

data class CrownsSolveAnalysis(
    val logicalSteps: Int,
    val candidateEliminations: Int,
    val branchDecisions: Int,
    val maximumSearchDepth: Int,
    val techniqueCounts: CrownsTechniqueCounts,
)

data class CrownsTechniqueCounts(
    val singleCandidateRow: Int,
    val singleCandidateColumn: Int,
    val singleCandidateRegion: Int,
    val regionLockedToRow: Int,
    val regionLockedToColumn: Int,
)

data class CrownsSolveResult(
    val solution: CrownsSolution?,
    val analysis: CrownsSolveAnalysis,
)

class CrownsSolver(
    private val logicEngine: CrownsLogicEngine = CrownsLogicEngine(),
) : PuzzleSolver<CrownsPuzzle, CrownsSolution> {
    override fun solve(puzzle: CrownsPuzzle): CrownsSolution? = solveWithAnalysis(puzzle).solution

    fun solveWithAnalysis(puzzle: CrownsPuzzle): CrownsSolveResult {
        val outcome = search(puzzle, limit = 1)
        return CrownsSolveResult(
            solution = outcome.firstSolution?.let { CrownsSolution.fromValidated(puzzle, it) },
            analysis = outcome.metrics.toAnalysis(),
        )
    }

    override fun countSolutions(
        puzzle: CrownsPuzzle,
        limit: Int,
    ): Int {
        require(limit > 0) { "Solution count limit must be positive." }
        return search(puzzle, limit).solutionCount
    }

    private fun search(
        puzzle: CrownsPuzzle,
        limit: Int,
    ): SearchOutcome {
        require(limit > 0) { "Solution count limit must be positive." }
        val outcome = SearchOutcome(limit)
        searchState(
            puzzle = puzzle,
            initialState = CrownsCandidateState.from(puzzle),
            depth = 0,
            outcome = outcome,
        )
        return outcome
    }

    private fun searchState(
        puzzle: CrownsPuzzle,
        initialState: CrownsCandidateState,
        depth: Int,
        outcome: SearchOutcome,
    ) {
        if (outcome.reachedLimit) return
        outcome.metrics.maximumSearchDepth = maxOf(outcome.metrics.maximumSearchDepth, depth)

        var state = initialState
        while (true) {
            val ruleAnalysis = CrownsRules.analyze(puzzle, state.asState())
            if (ruleAnalysis.violations.isNotEmpty() || state.isContradictory(puzzle)) return
            if (ruleAnalysis.isComplete) {
                outcome.record(state.asState())
                return
            }

            val step = logicEngine.nextStep(puzzle, state) ?: break
            val transition = state.apply(puzzle, step)
            outcome.metrics.record(step.technique, transition.eliminatedPositions.size)
            state = transition.state
        }

        val choice = selectBranchGroup(puzzle, state) ?: return
        outcome.metrics.branchDecisions++
        choice.candidates.forEach { position ->
            val transition = state.placeCrown(puzzle, position)
            outcome.metrics.candidateEliminations += transition.eliminatedPositions.size
            searchState(
                puzzle = puzzle,
                initialState = transition.state,
                depth = depth + 1,
                outcome = outcome,
            )
            if (outcome.reachedLimit) return
        }
    }

    private fun selectBranchGroup(
        puzzle: CrownsPuzzle,
        state: CrownsCandidateState,
    ): BranchChoice? {
        var bestChoice: BranchChoice? = null
        constraintGroups(puzzle).forEach { group ->
            if (group.isSatisfiedBy(state)) return@forEach
            val candidates = group.candidatesIn(state)
            if (candidates.size <= 1) return@forEach
            val currentBest = bestChoice
            if (currentBest == null || candidates.size < currentBest.candidates.size) {
                bestChoice = BranchChoice(candidates)
            }
        }
        return bestChoice
    }

    private data class BranchChoice(
        val candidates: List<CrownsPosition>,
    )

    private class SearchOutcome(
        private val limit: Int,
    ) {
        var solutionCount: Int = 0
            private set
        var firstSolution: CrownsState? = null
            private set
        val metrics = MutableMetrics()
        val reachedLimit: Boolean
            get() = solutionCount >= limit

        fun record(solution: CrownsState) {
            if (firstSolution == null) firstSolution = solution
            solutionCount++
        }
    }

    private data class MutableMetrics(
        var logicalSteps: Int = 0,
        var candidateEliminations: Int = 0,
        var branchDecisions: Int = 0,
        var maximumSearchDepth: Int = 0,
        var singleCandidateRow: Int = 0,
        var singleCandidateColumn: Int = 0,
        var singleCandidateRegion: Int = 0,
        var regionLockedToRow: Int = 0,
        var regionLockedToColumn: Int = 0,
    ) {
        fun record(
            technique: CrownsLogicTechnique,
            eliminatedPositions: Int,
        ) {
            logicalSteps++
            candidateEliminations += eliminatedPositions
            when (technique) {
                CrownsLogicTechnique.SINGLE_CANDIDATE_ROW -> singleCandidateRow++
                CrownsLogicTechnique.SINGLE_CANDIDATE_COLUMN -> singleCandidateColumn++
                CrownsLogicTechnique.SINGLE_CANDIDATE_REGION -> singleCandidateRegion++
                CrownsLogicTechnique.REGION_LOCKED_TO_ROW -> regionLockedToRow++
                CrownsLogicTechnique.REGION_LOCKED_TO_COLUMN -> regionLockedToColumn++
            }
        }

        fun toAnalysis(): CrownsSolveAnalysis =
            CrownsSolveAnalysis(
                logicalSteps = logicalSteps,
                candidateEliminations = candidateEliminations,
                branchDecisions = branchDecisions,
                maximumSearchDepth = maximumSearchDepth,
                techniqueCounts =
                    CrownsTechniqueCounts(
                        singleCandidateRow = singleCandidateRow,
                        singleCandidateColumn = singleCandidateColumn,
                        singleCandidateRegion = singleCandidateRegion,
                        regionLockedToRow = regionLockedToRow,
                        regionLockedToColumn = regionLockedToColumn,
                    ),
            )
    }
}
