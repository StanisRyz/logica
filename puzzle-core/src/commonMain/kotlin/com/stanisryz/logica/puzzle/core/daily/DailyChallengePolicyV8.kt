package com.stanisryz.logica.puzzle.core.daily

import com.stanisryz.logica.puzzle.core.model.PuzzleType

/**
 * V7 without Word: Balance, Crowns, Sudoku, 2048, Nonogram, and Block Sudoku, all at Medium. Every
 * entry is V7's own — the same difficulty, generator version, and seed for the same date — so the day
 * new runs switch to V8 shows the very same six puzzles. New runs use V8; a persisted V1–V7 run keeps
 * its own definition, Word included. Streaks follow V5's rule: one solved entry qualifies the date.
 */
object DailyChallengePolicyV8 {
    val VERSION = DailyPolicyVersion(8)

    fun definitionFor(date: DailyDate): DailyChallengeDefinition =
        DailyChallengeDefinition(
            challengeDate = date,
            policyVersion = VERSION,
            entries = DailyChallengePolicyV7.definitionFor(date).entries.filter { it.puzzleType != PuzzleType.WORD },
        )
}
