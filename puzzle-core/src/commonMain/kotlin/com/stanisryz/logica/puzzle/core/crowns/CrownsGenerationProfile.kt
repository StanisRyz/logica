package com.stanisryz.logica.puzzle.core.crowns

import com.stanisryz.logica.puzzle.core.model.Difficulty

internal data class CrownsGenerationProfile(
    val boardSize: Int,
    val frozenRegionCount: Int,
    val maximumSolutionAttempts: Int,
    val maximumSolutionSearchNodes: Int,
    val maximumRegionAttemptsPerSolution: Int,
)

internal object CrownsGenerationProfiles {
    fun forDifficulty(difficulty: Difficulty): CrownsGenerationProfile =
        when (difficulty) {
            Difficulty.EASY ->
                CrownsGenerationProfile(
                    boardSize = 5,
                    frozenRegionCount = 0,
                    maximumSolutionAttempts = 4,
                    maximumSolutionSearchNodes = 5_000,
                    maximumRegionAttemptsPerSolution = 64,
                )
            Difficulty.MEDIUM ->
                CrownsGenerationProfile(
                    boardSize = 6,
                    frozenRegionCount = 1,
                    maximumSolutionAttempts = 4,
                    maximumSolutionSearchNodes = 10_000,
                    maximumRegionAttemptsPerSolution = 96,
                )
            Difficulty.HARD ->
                CrownsGenerationProfile(
                    boardSize = 7,
                    frozenRegionCount = 2,
                    maximumSolutionAttempts = 6,
                    maximumSolutionSearchNodes = 25_000,
                    maximumRegionAttemptsPerSolution = 128,
                )
            Difficulty.EXPERT ->
                CrownsGenerationProfile(
                    boardSize = 8,
                    frozenRegionCount = 3,
                    maximumSolutionAttempts = 8,
                    maximumSolutionSearchNodes = 50_000,
                    maximumRegionAttemptsPerSolution = 160,
                )
        }
}
