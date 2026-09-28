package com.stanisryz.logica.puzzle.core.model

/**
 * Gems one solved attempt earns, the one rule both hosts pay: 2048 and Sudoku pay 1 on Hard and 2
 * on Expert, Crowns pays 1 on Expert, and every other game or difficulty pays nothing — solving it
 * is its own reward. The same table applies in the Catalog and the Daily.
 */
object PuzzleGemReward {
    fun forSolved(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
    ): Int =
        when (puzzleType) {
            PuzzleType.GAME_2048, PuzzleType.SUDOKU ->
                when (difficulty) {
                    Difficulty.HARD -> 1
                    Difficulty.EXPERT -> 2
                    else -> 0
                }
            PuzzleType.CROWNS -> if (difficulty == Difficulty.EXPERT) 1 else 0
            else -> 0
        }
}
