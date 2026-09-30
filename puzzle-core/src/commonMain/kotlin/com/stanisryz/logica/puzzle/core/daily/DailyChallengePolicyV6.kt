package com.stanisryz.logica.puzzle.core.daily

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType

/**
 * V5's five games plus the Nonogram, all at Medium. The Nonogram entry is a real picture from
 * Nonogram Generator V2 whose seed is the date's epoch day, so consecutive days walk the frozen
 * picture set without a repeat inside one cycle. New runs use V6; a persisted V1–V5 run keeps its
 * own definition. Streaks follow V5's rule: one solved entry qualifies the date.
 */
object DailyChallengePolicyV6 {
    val VERSION = DailyPolicyVersion(6)

    val NONOGRAM_GENERATOR_VERSION = GeneratorVersion(2)

    fun definitionFor(date: DailyDate): DailyChallengeDefinition {
        val v5 = DailyChallengePolicyV5.definitionFor(date)
        return DailyChallengeDefinition(
            challengeDate = date,
            policyVersion = VERSION,
            entries =
                v5.entries +
                    DailyPuzzleEntry(
                        puzzleType = PuzzleType.NONOGRAM,
                        difficulty = Difficulty.MEDIUM,
                        seed = PuzzleSeed(date.toDailyEpochDay()),
                        generatorVersion = NONOGRAM_GENERATOR_VERSION,
                    ),
        )
    }
}
