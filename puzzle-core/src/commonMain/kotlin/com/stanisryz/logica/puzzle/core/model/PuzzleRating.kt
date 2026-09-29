package com.stanisryz.logica.puzzle.core.model

/**
 * A game's rating, the one rule both hosts show and Web submits to its leaderboards. Level games
 * earn points per Catalog level cleared — Easy 1, Medium 2, Hard 3, Expert 4 — counted from
 * progression, so replaying a cleared level adds nothing. 2048 is rated by its best score instead.
 */
object PuzzleRating {
    fun isScoreRated(puzzleType: PuzzleType): Boolean = puzzleType == PuzzleType.GAME_2048

    fun pointsPerLevel(difficulty: Difficulty): Int =
        when (difficulty) {
            Difficulty.EASY -> 1
            Difficulty.MEDIUM -> 2
            Difficulty.HARD -> 3
            Difficulty.EXPERT -> 4
        }

    /** Points for [clearedLevels] Catalog levels per difficulty (the current level minus one). */
    fun levelPoints(clearedLevels: Map<Difficulty, Long>): Long =
        clearedLevels.entries.sumOf { (difficulty, count) -> count.coerceAtLeast(0L) * pointsPerLevel(difficulty) }
}
