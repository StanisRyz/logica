package com.stanisryz.logica.puzzle.core.daily

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import java.time.LocalDate

object DailyChallengePolicyV1 {
    val VERSION = DailyPolicyVersion(1)

    fun definitionFor(date: LocalDate): DailyChallengeDefinition {
        val generatorVersion = GeneratorVersion(1)
        val puzzleType = PuzzleType.BALANCE
        return DailyChallengeDefinition(
            challengeDate = date,
            policyVersion = VERSION,
            entries =
                listOf(
                    DailyPuzzleEntry(
                        puzzleType = puzzleType,
                        difficulty = Difficulty.MEDIUM,
                        seed = DailyPuzzleSeedV1.derive(date, puzzleType, generatorVersion),
                        generatorVersion = generatorVersion,
                    ),
                ),
        )
    }
}
