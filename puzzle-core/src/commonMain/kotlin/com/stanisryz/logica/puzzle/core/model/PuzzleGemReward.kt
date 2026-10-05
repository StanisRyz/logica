package com.stanisryz.logica.puzzle.core.model

/**
 * Gems one solved attempt earns, the one rule both hosts pay: one gem, once, when an Expert level's
 * best result first reaches three stars — on a first solve with three stars, or on a replay that
 * raises the level's best to three. Every other solve, and every other difficulty, pays nothing. The
 * Daily is Medium, so it pays nothing either.
 *
 * Games without stars (2048 and Block Sudoku) count a solve as three stars, so their first cleared
 * Expert level pays the gem; they have no replays, so it never pays twice.
 */
object PuzzleGemReward {
    const val EXPERT_THREE_STARS_GEMS = 1

    /**
     * [stars] are the ones this solve earned (`null` for a game without stars); [previousBestStars] is
     * the level's best from earlier solves, `0` when it was solved without stars on record, and `null`
     * when it was never solved before. Hosts read it before this solve updates the level's best.
     */
    fun forSolved(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
        stars: Int?,
        previousBestStars: Int?,
    ): Int {
        if (difficulty != Difficulty.EXPERT) return 0
        val earnsStars = puzzleType !in STARLESS_GAMES
        val reached = if (earnsStars) stars ?: 0 else PuzzleStars.MAX_STARS
        val before =
            when {
                previousBestStars == null -> 0
                earnsStars -> previousBestStars
                else -> PuzzleStars.MAX_STARS
            }
        return if (reached >= PuzzleStars.MAX_STARS && before < PuzzleStars.MAX_STARS) EXPERT_THREE_STARS_GEMS else 0
    }

    private val STARLESS_GAMES = setOf(PuzzleType.GAME_2048, PuzzleType.BLOCK_SUDOKU)
}
