package com.stanisryz.logica.puzzle.core.balance

import com.stanisryz.logica.puzzle.core.contract.PuzzleDifficultyEvaluator
import com.stanisryz.logica.puzzle.core.model.Difficulty

class BalanceDifficultyEvaluator(
    private val solver: BalanceSolver = BalanceSolver(),
) : PuzzleDifficultyEvaluator<BalancePuzzle> {
    override fun evaluate(puzzle: BalancePuzzle): Difficulty = evaluate(puzzle, solver.solveWithAnalysis(puzzle))

    internal fun evaluate(
        puzzle: BalancePuzzle,
        solveResult: BalanceSolveResult,
    ): Difficulty {
        if (solveResult.solution == null) return Difficulty.EXPERT

        val score = score(puzzle, solveResult.analysis)
        return when {
            score < MEDIUM_THRESHOLD -> Difficulty.EASY
            score < HARD_THRESHOLD -> Difficulty.MEDIUM
            score < EXPERT_THRESHOLD -> Difficulty.HARD
            else -> Difficulty.EXPERT
        }
    }

    internal fun score(
        puzzle: BalancePuzzle,
        analysis: BalanceSolveAnalysis,
    ): Int {
        val cellCount = puzzle.size * puzzle.size
        val removedClues = cellCount - puzzle.fixedClues.size
        val techniques = analysis.techniqueCounts

        return removedClues * 100 / cellCount +
            analysis.logicalSteps * 10 / cellCount +
            techniques.preventThree * 20 / cellCount +
            techniques.preserveUniqueness * 40 / cellCount +
            (puzzle.size - MINIMUM_PROFILE_SIZE).coerceAtLeast(0) * SIZE_POINT_STEP +
            analysis.branchDecisions * BRANCH_POINTS +
            analysis.maximumSearchDepth * DEPTH_POINTS
    }

    private companion object {
        const val MINIMUM_PROFILE_SIZE = 4
        const val SIZE_POINT_STEP = 5
        const val BRANCH_POINTS = 25
        const val DEPTH_POINTS = 10
        const val MEDIUM_THRESHOLD = 25
        const val HARD_THRESHOLD = 45
        const val EXPERT_THRESHOLD = 65
    }
}
