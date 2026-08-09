package com.stanisryz.logica.puzzle.core.crowns

import com.stanisryz.logica.puzzle.core.contract.PuzzleDifficultyEvaluator
import com.stanisryz.logica.puzzle.core.model.Difficulty

class CrownsDifficultyEvaluator(
    private val solver: CrownsSolver = CrownsSolver(),
) : PuzzleDifficultyEvaluator<CrownsPuzzle> {
    override fun evaluate(puzzle: CrownsPuzzle): Difficulty = evaluate(puzzle, solver.solveWithAnalysis(puzzle))

    internal fun evaluate(
        puzzle: CrownsPuzzle,
        solveResult: CrownsSolveResult,
    ): Difficulty {
        if (solveResult.solution == null) return Difficulty.EXPERT

        return when (score(puzzle, solveResult.analysis)) {
            in 0 until MEDIUM_THRESHOLD -> Difficulty.EASY
            in MEDIUM_THRESHOLD until HARD_THRESHOLD -> Difficulty.MEDIUM
            in HARD_THRESHOLD until EXPERT_THRESHOLD -> Difficulty.HARD
            else -> Difficulty.EXPERT
        }
    }

    fun score(
        puzzle: CrownsPuzzle,
        analysis: CrownsSolveAnalysis,
    ): Int {
        val techniques = analysis.techniqueCounts
        val regionLocks = techniques.regionLockedToRow + techniques.regionLockedToColumn
        return analysis.logicalSteps * LOGICAL_STEP_POINTS +
            analysis.candidateEliminations / CANDIDATE_ELIMINATION_DIVISOR +
            techniques.singleCandidateRegion * REGION_SINGLE_POINTS +
            regionLocks * REGION_LOCK_POINTS +
            (puzzle.size - MINIMUM_PROFILE_SIZE).coerceAtLeast(0) * SIZE_POINT_STEP +
            analysis.branchDecisions * BRANCH_POINTS +
            analysis.maximumSearchDepth * DEPTH_POINTS
    }

    private companion object {
        const val MINIMUM_PROFILE_SIZE = 5
        const val LOGICAL_STEP_POINTS = 3
        const val CANDIDATE_ELIMINATION_DIVISOR = 4
        const val REGION_SINGLE_POINTS = 1
        const val REGION_LOCK_POINTS = 6
        const val SIZE_POINT_STEP = 12
        const val BRANCH_POINTS = 30
        const val DEPTH_POINTS = 15
        const val MEDIUM_THRESHOLD = 32
        const val HARD_THRESHOLD = 51
        const val EXPERT_THRESHOLD = 72
    }
}
