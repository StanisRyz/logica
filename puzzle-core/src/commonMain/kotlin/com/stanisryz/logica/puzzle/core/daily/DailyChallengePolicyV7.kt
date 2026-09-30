package com.stanisryz.logica.puzzle.core.daily

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleType

/**
 * V6's six games plus Block Sudoku, all at Medium. The Block Sudoku entry is Rules V1 on the
 * ordinary Daily seed, cleared at the Medium target score like a Catalog level. New runs use V7; a
 * persisted V1–V6 run keeps its own definition. Streaks follow V5's rule: one solved entry
 * qualifies the date.
 */
object DailyChallengePolicyV7 {
    val VERSION = DailyPolicyVersion(7)

    val BLOCK_SUDOKU_RULES_VERSION = GeneratorVersion(1)

    fun definitionFor(date: DailyDate): DailyChallengeDefinition {
        val v6 = DailyChallengePolicyV6.definitionFor(date)
        return DailyChallengeDefinition(
            challengeDate = date,
            policyVersion = VERSION,
            entries =
                v6.entries +
                    DailyPuzzleEntry(
                        puzzleType = PuzzleType.BLOCK_SUDOKU,
                        difficulty = Difficulty.MEDIUM,
                        seed = DailyPuzzleSeedV1.derive(date, PuzzleType.BLOCK_SUDOKU, BLOCK_SUDOKU_RULES_VERSION),
                        generatorVersion = BLOCK_SUDOKU_RULES_VERSION,
                    ),
        )
    }
}
