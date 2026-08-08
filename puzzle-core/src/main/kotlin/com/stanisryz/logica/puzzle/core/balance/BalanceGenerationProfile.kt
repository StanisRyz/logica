package com.stanisryz.logica.puzzle.core.balance

import com.stanisryz.logica.puzzle.core.model.Difficulty

internal data class BalanceGenerationProfile(
    val boardSize: Int,
    val minimumRemovedClues: Int,
    val maximumRemovedClues: Int,
    val maximumAttempts: Int,
    val maximumFullBoardSearchNodes: Int,
)

internal object BalanceGenerationProfiles {
    fun forDifficulty(difficulty: Difficulty): BalanceGenerationProfile =
        when (difficulty) {
            Difficulty.EASY ->
                BalanceGenerationProfile(
                    boardSize = 4,
                    minimumRemovedClues = 2,
                    maximumRemovedClues = 4,
                    maximumAttempts = 4,
                    maximumFullBoardSearchNodes = 25_000,
                )
            Difficulty.MEDIUM ->
                BalanceGenerationProfile(
                    boardSize = 6,
                    minimumRemovedClues = 6,
                    maximumRemovedClues = 11,
                    maximumAttempts = 6,
                    maximumFullBoardSearchNodes = 250_000,
                )
            Difficulty.HARD ->
                BalanceGenerationProfile(
                    boardSize = 6,
                    minimumRemovedClues = 12,
                    maximumRemovedClues = 20,
                    maximumAttempts = 6,
                    maximumFullBoardSearchNodes = 250_000,
                )
            Difficulty.EXPERT ->
                BalanceGenerationProfile(
                    boardSize = 8,
                    minimumRemovedClues = 27,
                    maximumRemovedClues = 40,
                    maximumAttempts = 8,
                    maximumFullBoardSearchNodes = 2_000_000,
                )
        }
}
